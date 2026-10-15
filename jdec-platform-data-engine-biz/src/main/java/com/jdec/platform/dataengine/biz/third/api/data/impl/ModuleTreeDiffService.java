package com.jdec.platform.dataengine.biz.third.api.data.impl;

import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.dataengine.api.dto.model.SysModuleInfo;
import com.jdec.platform.dataengine.api.dto.model.VersionCompareItem;
import com.jdec.platform.dataengine.biz.config.service.DataModuleConfigService;
import com.jdec.platform.dataengine.biz.dto.VersionSnapShot;
import com.jdec.platform.shared.context.AppContext;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/** 模块树字段对比数据生成类 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModuleTreeDiffService {

    private final DataModuleConfigService dataModuleConfigService;

    /**
     * 入口：对比两棵模块树，【新树】原地增加 changeList
     *
     * @param oldTree 旧版本json树（只读）
     * @param newTree 新版本json树（原地增加changeList）
     */
    public void diffModuleTree(
            NestedModuleDataDTO oldTree,
            NestedModuleDataDTO newTree,
            Map<Long, Map<Long, Map<String, VersionSnapShot>>> currentVersionMap) {
        // 构建旧树 moduleId -> recordMap(业务id -> recordNode)
        Map<Long, Map<String, RecordDataDTO>> oldModuleRecordMap =
                buildOldModuleRecordIndex(oldTree);
        recursiveDiff(oldTree, newTree, oldModuleRecordMap, currentVersionMap);
    }

    /** 递归处理模块节点 */
    private void recursiveDiff(
            NestedModuleDataDTO oldNode,
            NestedModuleDataDTO newNode,
            Map<Long, Map<String, RecordDataDTO>> oldModuleRecordMap,
            Map<Long, Map<Long, Map<String, VersionSnapShot>>> currentVersionMap) {
        if (Objects.isNull(newNode)) {
            return;
        }
        Long moduleId = newNode.getModuleId();

        SysModuleInfo sysModuleInfo =
                dataModuleConfigService.getModuleCompleteById(
                        AppContext.getProjectNo(), AppContext.getSubjectId(), moduleId);

        String primaryTable = sysModuleInfo.getPrimaryTable();

        // 获取旧模块下所有record：business_no -> record节点
        Map<String, RecordDataDTO> oldRecordMap =
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
                String rowBusinessNo = extractRowBusinessNo(rowNode, primaryTable);
                RecordDataDTO oldRecordItem = oldRecordMap.get(rowBusinessNo);
                Map<String, Object> oldRow = oldRecordItem != null ? oldRecordItem.getRow() : null;

                Long rowPk = extractRowPk(rowNode, primaryTable);

                Map<String, VersionSnapShot> currentChangeMap = new HashMap<>();
                if (currentVersionMap.containsKey(moduleId)
                        && currentVersionMap.get(moduleId).containsKey(rowPk)) {
                    currentChangeMap = currentVersionMap.get(moduleId).get(rowPk);
                }

                // 对比row字段，生成changeList，挂在recordItem（和row平级）
                Map<String, VersionCompareItem> changeList =
                        buildRowChangeList(oldRow, rowNode, currentChangeMap);
                if (changeList.size() > 0) {
                    recordItem.setChangeList(changeList);
                    recordItem.setChangeType("改");
                } else if (Objects.isNull(oldRow) && Objects.nonNull(oldNode)) {
                    recordItem.setChangeType("新");
                }

                // 递归处理当前recordItem的children
                List<NestedModuleDataDTO> childrenNode = recordItem.getChildren();
                if (!CollectionUtils.isEmpty(childrenNode)) {
                    for (NestedModuleDataDTO childModule : childrenNode) {
                        recursiveDiff(oldNode, childModule, oldModuleRecordMap, currentVersionMap);
                    }
                }
            }
        }
    }

    /** 构建旧树索引：moduleId -> Map<业务主键, recordNode> */
    private Map<Long, Map<String, RecordDataDTO>> buildOldModuleRecordIndex(
            NestedModuleDataDTO root) {
        Map<Long, Map<String, RecordDataDTO>> index = new ConcurrentHashMap<>();
        traverseOldTree(root, index);
        return index;
    }

    private void traverseOldTree(
            NestedModuleDataDTO node, Map<Long, Map<String, RecordDataDTO>> index) {
        if (Objects.isNull(node)) return;
        Long moduleId = node.getModuleId();
        SysModuleInfo sysModuleInfo =
                dataModuleConfigService.getModuleCompleteById(
                        AppContext.getProjectNo(), AppContext.getSubjectId(), moduleId);
        String primaryTable = sysModuleInfo.getPrimaryTable();
        if (moduleId != null) {
            List<RecordDataDTO> recordArr = node.getRecord();
            if (!CollectionUtils.isEmpty(recordArr)) {
                Map<String, RecordDataDTO> recordMap =
                        index.computeIfAbsent(moduleId, k -> new ConcurrentHashMap<>());
                for (RecordDataDTO recordItem : recordArr) {
                    Map<String, Object> rowNode = recordItem.getRow();
                    if (rowNode == null) continue;
                    String businessNoPk = extractRowBusinessNo(rowNode, primaryTable);
                    if (StringUtils.isNotBlank(businessNoPk)) {
                        recordMap.put(businessNoPk, recordItem);
                    }

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

    /** 从row中提取主键：查找key以 .business_no 结尾的字段，例如 info.business_no、basic_info.business_no */
    private String extractRowBusinessNo(Map<String, Object> rowNode, String tableName) {
        Object primaryTableId = rowNode.get(tableName + "." + "business_no");

        if (Objects.nonNull(primaryTableId)) {
            return String.valueOf(primaryTableId);
        } else {
            return "";
        }
    }

    /** 从row中提取主键：查找key以 .id 结尾的字段，例如 info.id、basic_info.id */
    private Long extractRowPk(Map<String, Object> rowNode, String tableName) {
        Object primaryTableId = rowNode.get(tableName + "." + "id");

        if (Objects.nonNull(primaryTableId)) {
            return Long.parseLong(String.valueOf(primaryTableId));
        } else {
            return null;
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
            Map<String, VersionSnapShot> currentChangeMap) {
        Map<String, VersionCompareItem> changeList = new HashMap<>();
        if (Objects.isNull(newRow) || Objects.isNull(oldRow)) {
            return changeList;
        }

        Iterator<Map.Entry<String, Object>> newFields = newRow.entrySet().iterator();
        while (newFields.hasNext()) {
            Map.Entry<String, Object> entry = newFields.next();
            String fieldKey = entry.getKey();
            Object newVal = entry.getValue();
            Object oldVal = oldRow == null ? null : oldRow.get(fieldKey);
            VersionCompareItem.VersionCompareItemBuilder versionCompareItem =
                    VersionCompareItem.builder();

            boolean addVersionItem = false;

            // 值相同跳过，只记录变更字段
            if (Objects.nonNull(oldRow) && !Objects.equals(oldVal, newVal)) {
                versionCompareItem.pre(oldVal).current(newVal);
                addVersionItem = true;
                //                    continue;
            }
            if (Objects.nonNull(currentChangeMap.get(fieldKey))) {
                VersionSnapShot changeObj = currentChangeMap.get(fieldKey);

                try {
                    if (Objects.nonNull(changeObj)) {
                        addVersionItem = true;

                        if (Objects.nonNull(changeObj.getSecondSnapshot())) {
                            if (Objects.equals(newVal, changeObj.getSecondSnapshot())) {
                                if (!Objects.equals(newVal, changeObj.getFirstSnapshot())) {
                                    versionCompareItem
                                            .currentOld(changeObj.getFirstSnapshot())
                                            .current(newVal);
                                }
                            } else {
                                versionCompareItem
                                        .currentOld(changeObj.getSecondSnapshot())
                                        .current(newVal);
                            }
                        } else {
                            if (!Objects.equals(newVal, changeObj.getFirstSnapshot())) {
                                versionCompareItem
                                        .currentOld(changeObj.getFirstSnapshot())
                                        .current(newVal);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("版本内对比构造数据失败");
                }
            }

            // 构造 {pre:旧值, currentOld:旧值, current:新值}
            if (addVersionItem) {
                changeList.put(entry.getKey(), versionCompareItem.build());
            }
        }
        return changeList;
    }
}
