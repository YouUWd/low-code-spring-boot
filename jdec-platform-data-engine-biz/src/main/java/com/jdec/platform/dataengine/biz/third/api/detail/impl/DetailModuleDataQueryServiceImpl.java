package com.jdec.platform.dataengine.biz.third.api.detail.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.dataengine.api.dto.enums.QueryType;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.dataengine.biz.dsl.JooqContextFactory;
import com.jdec.platform.dataengine.biz.dsl.JooqSqlBuilder;
import com.jdec.platform.dataengine.biz.dto.ModuleFilterInfo;
import com.jdec.platform.dataengine.biz.dto.ModuleTable;
import com.jdec.platform.dataengine.biz.dto.context.DetailModuleQueryContext;
import com.jdec.platform.dataengine.biz.dto.context.StatementContext;
import com.jdec.platform.dataengine.biz.service.MetadataCacheService;
import com.jdec.platform.dataengine.biz.third.api.condition.ConditionBuilderFactory;
import com.jdec.platform.dataengine.biz.third.api.detail.DetailModuleDataQueryService;
import com.jdec.platform.dataengine.biz.third.api.module.ModuleInfoWithPermissionService;
import com.jdec.platform.dataengine.biz.third.api.select.SelectBuilderFactory;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jooq.*;
import org.jooq.Record;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/** 查询 db 模块的业务数据 详情数据 包含 非审批链 和 审批链的 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DetailModuleDataQueryServiceImpl implements DetailModuleDataQueryService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final JooqContextFactory jooqContextFactory;

    private final ConditionBuilderFactory conditionBuilderFactory;

    private final SelectBuilderFactory selectBuilderFactory;

    private final JooqSqlBuilder jooqSqlBuilder;

    private final MetadataCacheService metadataCacheService;

    private final ModuleInfoWithPermissionService moduleInfoWithPermissionService;

    @Override
    public List<RecordDataDTO> queryDetailModuleData(DetailModuleQueryContext context) {

        List<RecordDataDTO> recordDataDTOList = new ArrayList<>();

        DSLContext dsl = jooqContextFactory.getContext();

        ModuleFilterInfo moduleFilterInfo =
                moduleInfoWithPermissionService.filterModuleInfo(
                        AppContext.getSubjectId(),
                        UserContext.getRoleId(),
                        context.getModuleId(),
                        context.getBtnType(),
                        List.of());

        String primaryTable = moduleFilterInfo.getModuleInfo().getPrimaryTable();

        if (StringUtils.isBlank(primaryTable)) {
            log.debug(
                    "主表名称为空，模块{},id:{}为虚模块或配置数据错误。",
                    moduleFilterInfo.getModuleInfo().getModuleName(),
                    moduleFilterInfo.getModuleInfo().getModuleId());
            return Collections.emptyList();
        }

        StatementContext statementContext = StatementContext.builder().build();

        statementContext.setDataRightConfigResps(context.getDataRightConfigResps());

        // 构建 SQL拼装 上下文对象
        BeanUtil.copyProperties(
                context, statementContext, CopyOptions.create().ignoreError().ignoreNullValue());

        Boolean mainModuleMainTableQuery = false;

        if (Objects.isNull(context.getId())) {
            mainModuleMainTableQuery = false;
            statementContext.setQueryType(QueryType.DETAIL_SUBMODULE_QUERY);
            statementContext.setRelateIds(context.getRelateIds());
        } else {
            mainModuleMainTableQuery = true;
            statementContext.setId(context.getId());
        }
        DSLContext dslContext = jooqContextFactory.getContext();

        statementContext.setDsl(dslContext);
        statementContext.setModuleFilterInfo(moduleFilterInfo);
        statementContext.setTableName(primaryTable);
        statementContext.setFields(context.getFields());

        // 构建SQL select 语句
        SelectJoinStep<Record> selectStep =
                selectBuilderFactory.buildSelectStatement(statementContext);
        // 构造SQL 查询条件
        List<Condition> conditions = conditionBuilderFactory.buildConditions(statementContext);
        selectStep.where(conditions);

        //        Map<String, Object> tableData = new HashMap<>();

        List<Long> relateIds = new ArrayList<>();

        Map<Long, Map<String, Object>> mainTableIdMap = new HashMap<>();
        //        Map<Long , List<Map<String , Object>>> subTableIdMap = new HashMap<>();

        if (mainModuleMainTableQuery) {
            // 获取查询结果
            Record recordsResult = selectStep.fetchOne();

            Map<String, Object> mainRow = new HashMap<>();
            if (Objects.nonNull(recordsResult)) {
                Map<String, Object> resultMap = recordsResult.intoMap();
                dealJsonField(resultMap);
                mainRow.putAll(resultMap);
                mainTableIdMap.put(statementContext.getId(), mainRow);
            }

            relateIds.add(statementContext.getId());
        } else {
            // 获取查询结果
            Result<org.jooq.Record> recordsResult = selectStep.fetch();

            // 3. 提取主表记录 ID 集合 (用于 1:N 从表批量查询)
            List<Map<String, Object>> mainRow = new ArrayList<>();
            for (Record r : recordsResult) {
                for (org.jooq.Field<?> field : r.fields()) {
                    String tName = primaryTable;
                    org.jooq.Name qualifiedName = field.getQualifiedName();
                    org.jooq.Name[] parts = qualifiedName.parts();
                    if (parts.length > 1) {
                        tName = parts[parts.length - 2].first();
                    }
                    String colName = field.getName();
                    Object val = r.get(field);

                    if (primaryTable.equalsIgnoreCase(tName) && "id".equalsIgnoreCase(colName)) {
                        if (val instanceof Number num) {
                            relateIds.add(num.longValue());
                        }
                        break;
                    }
                }
                Map<String, Object> rowData = r.intoMap();

                dealJsonField(rowData);

                mainTableIdMap.put(
                        Long.parseLong(
                                String.valueOf(
                                        rowData.get(statementContext.getTableName() + "." + "id"))),
                        rowData);

                mainRow.add(rowData);
            }
        }

        // 构造子表数据
        Map<String, Map<Long, List<Map<String, Object>>>> multiTableDataMap =
                fetchOneToManyTables(context, dsl, relateIds, moduleFilterInfo);

        if (statementContext.getModuleFilterInfo().getModuleTableList() != null) {
            for (ModuleTable tableDto :
                    statementContext.getModuleFilterInfo().getModuleTableList()) {
                String tName = tableDto.getTableName();
                String rType = tableDto.getRelationType();
                List<Map<String, Object>> subRows =
                        multiTableDataMap
                                .getOrDefault(tName, Collections.emptyMap())
                                .getOrDefault(statementContext.getId(), Collections.emptyList());

                Map<String, Object> subDataMap = new HashMap<>();
                subDataMap.put("data", subRows);
                //                tableData.put(tName, subDataMap);
            }
        }

        for (Map.Entry<Long, Map<String, Object>> mainTableItem : mainTableIdMap.entrySet()) {
            Map<String, Object> rowData = new HashMap<>();
            rowData.putAll(mainTableItem.getValue());

            for (ModuleTable tableDto :
                    statementContext.getModuleFilterInfo().getModuleTableList()) {
                List<Map<String, Object>> subTableROwDatas =
                        multiTableDataMap.get(tableDto.getTableName()).get(mainTableItem.getKey());
                if (!CollectionUtils.isEmpty(subTableROwDatas)) {

                    rowData.putAll(subTableROwDatas.get(0));
                }
            }

            recordDataDTOList.add(
                    RecordDataDTO.builder().row(rowData).children(new ArrayList<>()).build());
        }

        return recordDataDTOList;
    }

    private void dealJsonField(Map<String, Object> resultMap) {
        for (Map.Entry<String, Object> entry : resultMap.entrySet()) {
            Object val = parseIfJson(entry.getValue());
            resultMap.put(entry.getKey(), val);
        }
    }

    /** 自动将 JSON 字符串或 jOOQ JSON 对象转换为结构化的 List/Map，保持返回值纯净 */
    private Object parseIfJson(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof JSON jooqJson) {
            String jsonStr = jooqJson.data();
            if (jsonStr == null || jsonStr.isBlank()) {
                return null;
            }
            try {
                return objectMapper.readTree(jsonStr);
            } catch (Exception e) {
                return jsonStr;
            }
        }
        if (val instanceof String str) {
            String trimmed = str.trim();
            if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
                    || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                try {
                    return objectMapper.readTree(trimmed);
                } catch (Exception ignored) {
                    return val;
                }
            }
        }
        return val;
    }

    /** 批量抓取当前页主表 ID 集合关联的 1:N 从表记录 (从表同样应用主体作用域与行级数据权限) */
    private Map<String, Map<Long, List<Map<String, Object>>>> fetchOneToManyTables(
            DetailModuleQueryContext context,
            DSLContext dsl,
            List<Long> primaryIds,
            ModuleFilterInfo moduleFilterInfo) {

        Map<String, Map<Long, List<Map<String, Object>>>> result = new HashMap<>();
        if (primaryIds == null
                || primaryIds.isEmpty()
                || moduleFilterInfo.getModuleTableList() == null) {
            return result;
        }

        for (ModuleTable tableDto : moduleFilterInfo.getModuleTableList()) {
            String rType = tableDto.getRelationType();
            if ("1:N".equalsIgnoreCase(rType) || "ONE_TO_MANY".equalsIgnoreCase(rType)) {
                String tableName = tableDto.getTableName();
                String leftJoinField = tableDto.getJoinLeftField(); // 外键字段, 如 student_id

                StatementContext statementContext = StatementContext.builder().build();

                statementContext.setDataRightConfigResps(context.getDataRightConfigResps());
                // 构建 SQL拼装 上下文对象
                BeanUtil.copyProperties(
                        context,
                        statementContext,
                        CopyOptions.create().ignoreError().ignoreNullValue());
                statementContext.setMenuId(context.getMenuId());
                statementContext.setId(context.getId());
                DSLContext dslContext = jooqContextFactory.getContext();
                statementContext.setDsl(dslContext);
                statementContext.setQueryType(QueryType.DETAIL_SUBTABLE_QUERY);
                statementContext.setModuleFilterInfo(moduleFilterInfo);
                statementContext.setTableName(tableName);
                statementContext.setRelateIds(primaryIds);

                // 构建SQL select 语句
                SelectJoinStep<Record> selectStep =
                        selectBuilderFactory.buildSelectStatement(statementContext);
                // 构造SQL 查询条件
                List<Condition> conditions =
                        conditionBuilderFactory.buildConditions(statementContext);
                selectStep.where(conditions);

                Result<Record> subRecords = selectStep.fetch();

                Map<Long, List<Map<String, Object>>> grouped = new HashMap<>();
                for (Record r : subRecords) {
                    Map<String, Object> rMap = r.intoMap();
                    dealJsonField(rMap);
                    Object fkVal = rMap.get(leftJoinField);
                    if (fkVal instanceof Number fkNum) {
                        grouped.computeIfAbsent(fkNum.longValue(), k -> new ArrayList<>())
                                .add(rMap);
                    }
                }
                result.put(tableName, grouped);
            }
        }
        return result;
    }
}
