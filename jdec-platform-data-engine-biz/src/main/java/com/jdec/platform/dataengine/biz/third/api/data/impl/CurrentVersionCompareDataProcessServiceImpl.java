package com.jdec.platform.dataengine.biz.third.api.data.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.dataengine.api.dto.enums.QueryType;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.dataengine.api.dto.model.VersionCompareItem;
import com.jdec.platform.dataengine.biz.dsl.JooqContextFactory;
import com.jdec.platform.dataengine.biz.dto.InterFieldCompareItem;
import com.jdec.platform.dataengine.biz.dto.context.DataIntegrationContext;
import com.jdec.platform.dataengine.biz.third.api.data.DetailDataProcessService;
import com.jdec.platform.dataengine.biz.util.JsonUtil;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class CurrentVersionCompareDataProcessServiceImpl implements DetailDataProcessService {

    private final JooqContextFactory jooqContextFactory;

    private final Set<QueryType> TYPE_SET = Set.of(QueryType.DETAIL_APPROVAL_CURRENT);

    @Override
    public void processAssembleData(
            List<NestedModuleDataDTO> records, DataIntegrationContext context) {
        List<NestedModuleDataDTO> preVersionData = getPreVersionData(context);
        Map<Long, Map<Long, Map<String, Object>>> currentVersionMap =
                buildCurrentModuleChangeIndex(context.getModuleId(), context.getId());
        ModuleTreeDiffUtil.diffModuleTree(preVersionData.get(0), records.get(0), currentVersionMap);
    }

    private List<NestedModuleDataDTO> getPreVersionData(DataIntegrationContext context) {
        DSLContext dslContext = jooqContextFactory.getContext();

        SelectJoinStep<org.jooq.Record> query =
                dslContext.select(DSL.asterisk()).from(DSL.name("module_data_snapshot_content"));
        // 构造SQL 查询条件
        List<Condition> conditions = new ArrayList<>();

        conditions.add(
                DSL.field(DSL.name("module_data_snapshot_content", "root_module_id"))
                        .eq(context.getModuleId()));
        conditions.add(
                DSL.field(DSL.name("module_data_snapshot_content", "root_table_data_id"))
                        .eq(context.getId()));

        query.where(conditions);

        query.orderBy(
                DSL.field(DSL.name("module_data_snapshot_content", "root_table_data_version"))
                        .desc());

        query.stream().limit(1);

        Record recordsResult = query.fetchAny();

        if (Objects.nonNull(recordsResult)) {
            String jsonData = (String) recordsResult.intoMap().get("content");
            ObjectMapper objectMapper = new ObjectMapper();
            NestedModuleDataDTO nestedModuleDataDTO = null;
            try {
                nestedModuleDataDTO = objectMapper.readValue(jsonData, NestedModuleDataDTO.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

            return Arrays.asList(nestedModuleDataDTO);
        } else {
            return List.of();
        }
    }

    @Override
    public Boolean isReferTo(QueryType queryType) {
        return TYPE_SET.contains(queryType);
    }

    private Map<Long, Map<Long, Map<String, Object>>> buildCurrentModuleChangeIndex(
            Long moduleId, Long id) {
        DSLContext dslContext = jooqContextFactory.getContext();

        SelectJoinStep<org.jooq.Record> query =
                dslContext
                        .select(DSL.asterisk())
                        .from(DSL.name("module_data_field_change_content"));
        // 构造SQL 查询条件
        List<Condition> conditions = new ArrayList<>();

        conditions.add(
                DSL.field(DSL.name("module_data_field_change_content", "root_module_id"))
                        .eq(moduleId));
        conditions.add(
                DSL.field(DSL.name("module_data_field_change_content", "root_table_data_id"))
                        .eq(id));

        query.where(conditions);

        query.orderBy(DSL.field(DSL.name("module_data_field_change_content", "create_date")).asc());

        Result<Record> records = query.fetch();

        Map<Long, Map<Long, Map<String, Object>>> returnMap = new HashMap<>();

        for (Record r : records) {
            Map<String, Object> rMap = r.intoMap();
            Long itemModuleId = Long.parseLong(String.valueOf(rMap.get("module_id")));
            Long dataId = Long.parseLong(String.valueOf(rMap.get("data_id")));
            returnMap
                    .computeIfAbsent(itemModuleId, k -> new HashMap<>())
                    .computeIfAbsent(dataId, m -> new HashMap<>())
                    .put(String.valueOf(rMap.get("field_id")), rMap.get("content"));
        }

        return returnMap;
    }

    public static class ModuleTreeDiffUtil {

        /**
         * 入口：对比两棵模块树，【新树】原地增加 changeList
         *
         * @param oldTree 旧版本json树（只读）
         * @param newTree 新版本json树（原地增加changeList）
         */
        public static void diffModuleTree(
                NestedModuleDataDTO oldTree,
                NestedModuleDataDTO newTree,
                Map<Long, Map<Long, Map<String, Object>>> currentVersionMap) {
            // 构建旧树 moduleId -> recordMap(业务id -> recordNode)
            Map<Long, Map<Long, RecordDataDTO>> oldModuleRecordMap =
                    buildOldModuleRecordIndex(oldTree);
            recursiveDiff(oldTree, newTree, oldModuleRecordMap, currentVersionMap);
        }

        /** 递归处理模块节点 */
        private static void recursiveDiff(
                NestedModuleDataDTO oldNode,
                NestedModuleDataDTO newNode,
                Map<Long, Map<Long, RecordDataDTO>> oldModuleRecordMap,
                Map<Long, Map<Long, Map<String, Object>>> currentVersionMap) {
            if (Objects.isNull(newNode)) {
                return;
            }
            Long moduleId = newNode.getModuleId();

            // 获取旧模块下所有record：业务id -> record节点
            Map<Long, RecordDataDTO> oldRecordMap =
                    oldModuleRecordMap.getOrDefault(moduleId, Map.of());

            // 处理当前模块的 record 数组
            List<RecordDataDTO> recordArr = newNode.getRecord();
            if (!CollectionUtils.isEmpty(recordArr)) {
                Iterator<RecordDataDTO> recordIt = recordArr.iterator();
                while (recordIt.hasNext()) {
                    RecordDataDTO recordItem = recordIt.next();
                    Map<String, Object> rowNode = recordItem.getRow();
                    if (Objects.isNull(rowNode)) {
                        continue;
                    }
                    // 提取row主键：找到 xxx.id 的字段值
                    Long rowPk = extractRowPk(rowNode);
                    RecordDataDTO oldRecordItem = oldRecordMap.get(rowPk);
                    Map<String, Object> oldRow =
                            oldRecordItem != null ? oldRecordItem.getRow() : null;

                    Map<String, Object> currentChangeMap = new HashMap<>();
                    if (currentVersionMap.containsKey(moduleId)
                            && currentVersionMap.get(moduleId).containsKey(rowPk)) {
                        currentChangeMap = currentVersionMap.get(moduleId).get(rowPk);
                    }

                    // 对比row字段，生成changeList，挂在recordItem（和row平级）
                    Map<String, VersionCompareItem> changeList =
                            buildRowChangeList(oldRow, rowNode, currentChangeMap);
                    if (changeList.size() > 0) {
                        recordItem.setChangeList(changeList);
                    }

                    // 递归处理当前recordItem的children
                    List<NestedModuleDataDTO> childrenNode = recordItem.getChildren();
                    if (!CollectionUtils.isEmpty(childrenNode)) {
                        for (NestedModuleDataDTO childModule : childrenNode) {
                            recursiveDiff(
                                    oldNode, childModule, oldModuleRecordMap, currentVersionMap);
                        }
                    }
                }
            }
        }

        /** 构建旧树索引：moduleId -> Map<业务主键, recordNode> */
        private static Map<Long, Map<Long, RecordDataDTO>> buildOldModuleRecordIndex(
                NestedModuleDataDTO root) {
            Map<Long, Map<Long, RecordDataDTO>> index = new ConcurrentHashMap<>();
            traverseOldTree(root, index);
            return index;
        }

        private static void traverseOldTree(
                NestedModuleDataDTO node, Map<Long, Map<Long, RecordDataDTO>> index) {
            if (Objects.isNull(node)) return;
            Long moduleId = node.getModuleId();
            if (moduleId != null) {
                List<RecordDataDTO> recordArr = node.getRecord();
                if (!CollectionUtils.isEmpty(recordArr)) {
                    Map<Long, RecordDataDTO> recordMap =
                            index.computeIfAbsent(moduleId, k -> new ConcurrentHashMap<>());
                    for (RecordDataDTO recordItem : recordArr) {
                        Map<String, Object> rowNode = recordItem.getRow();
                        if (rowNode == null) continue;
                        Long pk = extractRowPk(rowNode);
                        recordMap.put(pk, recordItem);

                        // 递归children
                        List<NestedModuleDataDTO> childrenNode = recordItem.getChildren();
                        if (!CollectionUtils.isEmpty(childrenNode)) {
                            for (NestedModuleDataDTO child : childrenNode) {
                                traverseOldTree(child, index);
                            }
                        }
                    }
                }
            }
        }

        /** 从row中提取主键：查找key以 .id 结尾的字段，例如 info.id、basic_info.id */
        private static Long extractRowPk(Map<String, Object> rowNode) {
            // TODO 需要读取模块配置获取主表.id作为key进行主表主键查找
            Object primaryTableId =
                    rowNode.entrySet().stream()
                            .filter(ele -> ele.getKey().endsWith(".id"))
                            .map(item -> item.getValue())
                            .findFirst()
                            .orElse(null);

            if (Objects.nonNull(primaryTableId)) {
                return Long.parseLong(String.valueOf(primaryTableId));
            } else {
                return 0L;
            }
        }

        /**
         * 对比旧row和新row，生成 changeList
         *
         * @return ObjectNode changeList
         */
        private static Map<String, VersionCompareItem> buildRowChangeList(
                Map<String, Object> oldRow,
                Map<String, Object> newRow,
                Map<String, Object> currentChangeMap) {
            Map<String, VersionCompareItem> changeList = new HashMap<>();
            if (Objects.isNull(newRow) || Objects.isNull(oldRow)) {
                return changeList;
            }

            Iterator<Map.Entry<String, Object>> newFields = newRow.entrySet().iterator();
            while (newFields.hasNext()) {
                Map.Entry<String, Object> entry = newFields.next();
                String fieldKey = entry.getKey();
                Object newVal = entry.getValue();
                Object oldVal = oldRow.get(fieldKey);
                VersionCompareItem.VersionCompareItemBuilder versionCompareItem =
                        VersionCompareItem.builder();

                // 值相同跳过，只记录变更字段
                if (!Objects.equals(oldVal, newVal)) {
                    versionCompareItem.pre(oldVal).current(newVal);
                    //                    continue;
                }
                if (Objects.nonNull(currentChangeMap.get(fieldKey))) {
                    String changeStr = String.valueOf(currentChangeMap.get(fieldKey));

                    try {
                        InterFieldCompareItem interFieldCompareItem =
                                JsonUtil.json2Obj(changeStr, InterFieldCompareItem.class);
                        versionCompareItem
                                .current(newVal)
                                .currentOld(interFieldCompareItem.getOld());
                    } catch (JsonProcessingException e) {
                        log.debug("json字符串{}反序列化失败。", changeStr);
                    }
                }

                // 构造 {pre:旧值, currentOld:旧值, current:新值}
                changeList.put(entry.getKey(), versionCompareItem.build());
            }
            return changeList;
        }
    }
}
