package com.jdec.platform.dataengine.biz.third.api.persistence;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.dataengine.biz.dto.ModuleRowNodeDataDTO;
import com.jdec.platform.dataengine.biz.dto.ModuleTreeNodeDataDTO;
import com.jdec.platform.dataengine.biz.dto.SysModuleCombineInfo;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class ModuleTreeDataUtils {

    private static final Set<String> CLEAN_FIELDS =
            Set.of(
                    "id",
                    "ID",
                    "subject_id",
                    "registration_time",
                    "created_by",
                    "updated_by",
                    "created_date",
                    "updated_date",
                    "deleted",
                    "active",
                    "source_id",
                    "version_no");

    /**
     * 根据 新增数据 构建模块树节点数据
     *
     * @param paramData
     * @return
     */
    public static ModuleTreeNodeDataDTO copyToModuleTreeNodeData(NestedModuleDataDTO paramData) {
        ModuleTreeNodeDataDTO moduleTreeNodeDataDTO = ModuleTreeNodeDataDTO.builder().build();
        moduleTreeNodeDataDTO.setModuleId(paramData.getModuleId());

        List<ModuleRowNodeDataDTO> records = new ArrayList<>(paramData.getRecord().size());

        for (RecordDataDTO recordDataDTO : paramData.getRecord()) {
            ModuleRowNodeDataDTO moduleRowNodeDataDTO = ModuleRowNodeDataDTO.builder().build();
            Map<String, Object> row = new HashMap<>();
            if (CollectionUtil.isNotEmpty(recordDataDTO.getRow())) {
                for (Map.Entry<String, Object> entry : recordDataDTO.getRow().entrySet())
                    row.put(String.valueOf(entry.getKey()), copyObject(entry.getValue()));
            }
            moduleRowNodeDataDTO.setRow(row);
            records.add(moduleRowNodeDataDTO);
            if (CollectionUtil.isNotEmpty(recordDataDTO.getChildren())) {
                List<ModuleTreeNodeDataDTO> children =
                        new ArrayList<>(recordDataDTO.getChildren().size());
                for (NestedModuleDataDTO child : recordDataDTO.getChildren()) {
                    ModuleTreeNodeDataDTO childModuleTreeNodeDataDTO =
                            copyToModuleTreeNodeData(child);
                    children.add(childModuleTreeNodeDataDTO);
                }
                moduleRowNodeDataDTO.setChildren(children);
            }
        }
        moduleTreeNodeDataDTO.setRecord(records);
        return moduleTreeNodeDataDTO;
    }

    public static ModuleTreeNodeDataDTO copyToModuleTreeNodeData(ModuleTreeNodeDataDTO paramData) {
        ModuleTreeNodeDataDTO moduleTreeNodeDataDTO = ModuleTreeNodeDataDTO.builder().build();
        moduleTreeNodeDataDTO.setModuleId(paramData.getModuleId());
        if (CollectionUtil.isEmpty(paramData.getRecord())) {
            return moduleTreeNodeDataDTO;
        }
        List<ModuleRowNodeDataDTO> rowNodeDataDTOList = new ArrayList<>();
        for (ModuleRowNodeDataDTO record : paramData.getRecord()) {
            ModuleRowNodeDataDTO moduleRowNodeDataDTO = copyToModuleRowNodeDataDTO(record);
            rowNodeDataDTOList.add(moduleRowNodeDataDTO);
        }
        moduleTreeNodeDataDTO.setRecord(rowNodeDataDTOList);
        return moduleTreeNodeDataDTO;
    }

    public static ModuleRowNodeDataDTO copyToModuleRowNodeDataDTO(ModuleRowNodeDataDTO paramData) {
        ModuleRowNodeDataDTO moduleRowNodeDataDTO = ModuleRowNodeDataDTO.builder().build();

        Map<String, Object> row = new HashMap<>();
        if (CollectionUtil.isNotEmpty(paramData.getRow())) {
            for (Map.Entry<String, Object> entry : paramData.getRow().entrySet())
                row.put(String.valueOf(entry.getKey()), copyObject(entry.getValue()));
        }
        moduleRowNodeDataDTO.setRow(row);
        if (CollectionUtil.isNotEmpty(paramData.getChildren())) {
            List<ModuleTreeNodeDataDTO> children = new ArrayList<>();
            for (ModuleTreeNodeDataDTO child : paramData.getChildren()) {
                children.add(copyToModuleTreeNodeData(child));
            }
            moduleRowNodeDataDTO.setChildren(children);
        }
        return moduleRowNodeDataDTO;
    }

    public static Object copyObject(Object val) {
        if (val instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null) {
                    copy.put(String.valueOf(e.getKey()), copyObject(e.getValue()));
                }
            }
            return copy;
        } else if (val instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            for (Object item : list) {
                copy.add(copyObject(item));
            }
            return copy;
        }
        return val;
    }

    public static ModuleTreeNodeDataDTO mergeModuleTreeNodeData(
            ModuleTreeNodeDataDTO baseDetail, ModuleTreeNodeDataDTO newData) {
        if (baseDetail == null) {
            return newData != null ? copyToModuleTreeNodeData(newData) : null;
        }
        if (newData == null) {
            return baseDetail != null ? copyToModuleTreeNodeData(baseDetail) : null;
        }

        Long moduleId =
                baseDetail.getModuleId() != null ? baseDetail.getModuleId() : newData.getModuleId();

        List<ModuleRowNodeDataDTO> baseRecords = baseDetail.getRecord();
        List<ModuleRowNodeDataDTO> newRecords = newData.getRecord();
        if (CollectionUtil.isEmpty(baseRecords)) {
            return copyToModuleTreeNodeData(newData);
        }
        ModuleRowNodeDataDTO baseFirst = baseRecords.get(0);
        ModuleRowNodeDataDTO newFirst = newRecords.get(0);

        ModuleRowNodeDataDTO mergedFirst = mergeModuleRowNodeData(baseFirst, newFirst);

        return ModuleTreeNodeDataDTO.builder()
                .moduleId(moduleId)
                .record(CollectionUtil.newArrayList(mergedFirst))
                .build();
    }

    public static ModuleRowNodeDataDTO mergeModuleRowNodeData(
            ModuleRowNodeDataDTO baseRow, ModuleRowNodeDataDTO newRow) {
        if (baseRow == null) {
            return newRow != null ? copyToModuleRowNodeDataDTO(newRow) : null;
        }
        if (newRow == null) {
            return baseRow != null ? copyToModuleRowNodeDataDTO(baseRow) : null;
        }

        ModuleRowNodeDataDTO mergeRow = ModuleRowNodeDataDTO.builder().build();

        Map<String, Object> rowMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : baseRow.getRow().entrySet()) {
            rowMap.put(String.valueOf(entry.getKey()), copyObject(entry.getValue()));
        }

        for (Map.Entry<String, Object> entry : newRow.getRow().entrySet()) {
            rowMap.put(String.valueOf(entry.getKey()), copyObject(entry.getValue()));
        }
        mergeRow.setRow(rowMap);

        List<ModuleTreeNodeDataDTO> baseChildren =
                baseRow.getChildren() != null ? baseRow.getChildren() : Collections.emptyList();
        List<ModuleTreeNodeDataDTO> newChildren =
                newRow.getChildren() != null ? newRow.getChildren() : Collections.emptyList();

        Map<Long, ModuleTreeNodeDataDTO> baseChildMap = new LinkedHashMap<>();
        for (ModuleTreeNodeDataDTO baseChild : baseChildren) {
            baseChildMap.put(baseChild.getModuleId(), baseChild);
        }

        Map<Long, ModuleTreeNodeDataDTO> newChildMap = new LinkedHashMap<>();
        for (ModuleTreeNodeDataDTO newChild : newChildren) {
            newChildMap.put(newChild.getModuleId(), newChild);
        }
        Set<Long> allChildModules = new HashSet<>();
        allChildModules.addAll(baseChildMap.keySet());
        allChildModules.addAll(newChildMap.keySet());

        List<ModuleTreeNodeDataDTO> mergedChildren = new ArrayList<>();

        for (Long moduleId : allChildModules) {
            ModuleTreeNodeDataDTO baseChild = baseChildMap.get(moduleId);
            ModuleTreeNodeDataDTO newChild = newChildMap.get(moduleId);

            if (newChild == null) {
                mergedChildren.add(copyToModuleTreeNodeData(baseChild));
            } else if (baseChild == null) {
                // 新增的行 和 节点
                List<ModuleRowNodeDataDTO> newChildRecords = new ArrayList<>();
                if (CollectionUtil.isNotEmpty(newChild.getRecord())) {
                    for (ModuleRowNodeDataDTO record : newChild.getRecord()) {
                        newChildRecords.add(copyToModuleRowNodeDataDTO(record));
                    }
                }
                mergedChildren.add(
                        ModuleTreeNodeDataDTO.builder()
                                .moduleId(moduleId)
                                .record(newChildRecords)
                                .build());
            } else {
                // 对比数据ID，进行合并
                List<ModuleRowNodeDataDTO> mergedChildRows =
                        mergedChildModuleRows(baseChild.getRecord(), newChild.getRecord());
                mergedChildren.add(
                        ModuleTreeNodeDataDTO.builder()
                                .moduleId(moduleId)
                                .record(mergedChildRows)
                                .build());
            }
        }
        mergeRow.setChildren(mergedChildren);
        return mergeRow;
    }

    public static List<ModuleRowNodeDataDTO> mergedChildModuleRows(
            List<ModuleRowNodeDataDTO> baseRows, List<ModuleRowNodeDataDTO> newRows) {

        baseRows = baseRows != null ? baseRows : Collections.emptyList();
        newRows = newRows != null ? newRows : Collections.emptyList();

        Map<String, ModuleRowNodeDataDTO> baseRowMap = new HashMap<>();

        for (ModuleRowNodeDataDTO baseRow : baseRows) {
            Object id = extractId(baseRow.getRow(), null);
            if (id != null) {
                baseRowMap.put(id.toString(), baseRow);
            }
        }
        List<ModuleRowNodeDataDTO> mergedRows = new ArrayList<>();
        for (ModuleRowNodeDataDTO newRow : newRows) {
            Object id = extractId(newRow.getRow(), null);
            if (id != null && baseRowMap.containsKey(id.toString())) {
                ModuleRowNodeDataDTO baseRow = baseRowMap.get(id.toString());
                mergedRows.add(mergeModuleRowNodeData(baseRow, newRow));
            } else {
                mergedRows.add(copyToModuleRowNodeDataDTO(newRow));
            }
        }
        return mergedRows;
    }

    public static Object extractId(Map<String, Object> row, String primaryTabName) {
        if (row == null) {
            return null;
        }
        if (StringUtils.isEmpty(primaryTabName)) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (entry.getKey().contains(".id")) {
                    return entry.getValue();
                }
            }
        } else {
            return DataPersistenceUtil.getTableRowIdWithTabName(row, primaryTabName);
        }
        return null;
    }

    public static NestedModuleDataDTO mergeNestedModuleData(
            NestedModuleDataDTO baseDetail,
            NestedModuleDataDTO newData,
            Map<Long, SysModuleCombineInfo> moduleCacheMap) {
        if (baseDetail == null) {
            return newData != null ? copyToNestedModuleData(newData) : null;
        }
        if (newData == null) {
            return baseDetail != null ? copyToNestedModuleData(baseDetail) : null;
        }

        Long moduleId =
                baseDetail.getModuleId() != null ? baseDetail.getModuleId() : newData.getModuleId();

        List<RecordDataDTO> baseRecords = baseDetail.getRecord();
        List<RecordDataDTO> newRecords = newData.getRecord();
        if (CollectionUtil.isEmpty(baseRecords)) {
            return copyToNestedModuleData(newData);
        }
        RecordDataDTO baseFirst = baseRecords.get(0);
        RecordDataDTO newFirst = newRecords.get(0);

        RecordDataDTO mergedFirst = mergeNestedModuleRowData(baseFirst, newFirst, moduleCacheMap);

        return NestedModuleDataDTO.builder()
                .moduleId(moduleId)
                .record(CollectionUtil.newArrayList(mergedFirst))
                .build();
    }

    public static NestedModuleDataDTO copyToNestedModuleData(NestedModuleDataDTO nestedModuleData) {
        NestedModuleDataDTO nestedModuleDataDTO = NestedModuleDataDTO.builder().build();
        nestedModuleDataDTO.setModuleId(nestedModuleData.getModuleId());
        if (CollectionUtil.isEmpty(nestedModuleData.getRecord())) {
            return nestedModuleDataDTO;
        }
        List<RecordDataDTO> rowNodeDataDTOList = new ArrayList<>();
        for (RecordDataDTO record : nestedModuleData.getRecord()) {
            RecordDataDTO moduleRowNodeDataDTO = copyToNestedModuleRowData(record);
            rowNodeDataDTOList.add(moduleRowNodeDataDTO);
        }
        nestedModuleDataDTO.setRecord(rowNodeDataDTOList);
        return nestedModuleDataDTO;
    }

    public static RecordDataDTO copyToNestedModuleRowData(RecordDataDTO paramData) {
        RecordDataDTO nestedModuleRow = RecordDataDTO.builder().build();

        Map<String, Object> row = new HashMap<>();
        if (CollectionUtil.isNotEmpty(paramData.getRow())) {
            for (Map.Entry<String, Object> entry : paramData.getRow().entrySet())
                row.put(String.valueOf(entry.getKey()), copyObject(entry.getValue()));
        }
        nestedModuleRow.setRow(row);

        if (CollectionUtil.isNotEmpty(paramData.getChildren())) {
            List<NestedModuleDataDTO> children = new ArrayList<>();
            for (NestedModuleDataDTO child : paramData.getChildren()) {
                children.add(copyToNestedModuleData(child));
            }
            nestedModuleRow.setChildren(children);
        }
        return nestedModuleRow;
    }

    public static RecordDataDTO mergeNestedModuleRowData(
            RecordDataDTO baseRow,
            RecordDataDTO newRow,
            Map<Long, SysModuleCombineInfo> moduleCacheMap) {
        if (baseRow == null) {
            return newRow != null ? copyToNestedModuleRowData(newRow) : null;
        }
        if (newRow == null) {
            return baseRow != null ? copyToNestedModuleRowData(baseRow) : null;
        }

        RecordDataDTO mergeRow = RecordDataDTO.builder().build();

        Map<String, Object> rowMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : baseRow.getRow().entrySet()) {
            rowMap.put(String.valueOf(entry.getKey()), copyObject(entry.getValue()));
        }

        // 前端参数 覆盖 数据库中查出来的行
        for (Map.Entry<String, Object> entry : newRow.getRow().entrySet()) {
            rowMap.put(String.valueOf(entry.getKey()), copyObject(entry.getValue()));
        }
        mergeRow.setRow(rowMap);

        List<NestedModuleDataDTO> baseChildren =
                baseRow.getChildren() != null ? baseRow.getChildren() : Collections.emptyList();
        List<NestedModuleDataDTO> newChildren =
                newRow.getChildren() != null ? newRow.getChildren() : Collections.emptyList();

        Map<Long, NestedModuleDataDTO> baseChildMap = new LinkedHashMap<>();
        for (NestedModuleDataDTO baseChild : baseChildren) {
            baseChildMap.put(baseChild.getModuleId(), baseChild);
        }

        Map<Long, NestedModuleDataDTO> newChildMap = new LinkedHashMap<>();
        for (NestedModuleDataDTO newChild : newChildren) {
            newChildMap.put(newChild.getModuleId(), newChild);
        }
        Set<Long> allChildModules = new HashSet<>();
        allChildModules.addAll(baseChildMap.keySet());
        allChildModules.addAll(newChildMap.keySet());

        // 所有的子模块数据
        List<NestedModuleDataDTO> mergedChildren = new ArrayList<>();

        for (Long moduleId : allChildModules) {
            NestedModuleDataDTO baseChild = baseChildMap.get(moduleId);
            NestedModuleDataDTO newChild = newChildMap.get(moduleId);

            if (newChild == null) {
                // 前端参数没有,拷贝详情页中的子模块和数据
                mergedChildren.add(copyToNestedModuleData(baseChild));
            } else if (baseChild == null) {
                // 详情页中没有 前端参数新加入的子模块和数据
                // 新增的行 和 节点
                List<RecordDataDTO> newChildRecords = new ArrayList<>();
                if (CollectionUtil.isNotEmpty(newChild.getRecord())) {
                    for (RecordDataDTO record : newChild.getRecord()) {
                        newChildRecords.add(copyToNestedModuleRowData(record));
                    }
                }
                mergedChildren.add(
                        NestedModuleDataDTO.builder()
                                .moduleId(moduleId)
                                .record(newChildRecords)
                                .build());
            } else {
                // 对比数据ID，进行合并
                List<RecordDataDTO> mergedChildRows =
                        mergedNestedChildModuleRows(
                                baseChild.getRecord(),
                                newChild.getRecord(),
                                moduleId,
                                moduleCacheMap);
                mergedChildren.add(
                        NestedModuleDataDTO.builder()
                                .moduleId(moduleId)
                                .record(mergedChildRows)
                                .build());
            }
        }
        mergeRow.setChildren(mergedChildren);
        return mergeRow;
    }

    public static List<RecordDataDTO> mergedNestedChildModuleRows(
            List<RecordDataDTO> baseRows,
            List<RecordDataDTO> newRows,
            Long moduleId,
            Map<Long, SysModuleCombineInfo> moduleCacheMap) {
        baseRows = baseRows != null ? baseRows : Collections.emptyList();
        newRows = newRows != null ? newRows : Collections.emptyList();

        Map<String, RecordDataDTO> baseRowMap = new HashMap<>();

        for (RecordDataDTO baseRow : baseRows) {
            Object id =
                    extractId(
                            baseRow.getRow(),
                            moduleCacheMap.get(moduleId).getModuleInfo().getPrimaryTable());
            if (id != null) {
                baseRowMap.put(id.toString(), baseRow);
            }
        }
        List<RecordDataDTO> mergedRows = new ArrayList<>();

        // 以newRows为基准，如果newRows中有数据 && baseRows中有数据 表示更新 则合并数据
        // 如果newRows中有数据 && baseRows中没有的数据  表示新增 则合并数据
        // 如果newRows没有的数据  && baseRows中有的数据  表示删除  丢弃
        for (RecordDataDTO newRow : newRows) {
            Object id =
                    extractId(
                            newRow.getRow(),
                            moduleCacheMap.get(moduleId).getModuleInfo().getPrimaryTable());
            // 如果Id不为空  并且在 base中有 表示这条数据 是更新的 合并该行数据
            if (id != null && baseRowMap.containsKey(id.toString())) {
                RecordDataDTO baseRow = baseRowMap.get(id.toString());
                mergedRows.add(mergeNestedModuleRowData(baseRow, newRow, moduleCacheMap));
            } else {
                // 如果ID 为空 表示这行数据是新增的行
                // 不存咋 ID不为空 && baseRowMap.containsKey(id.toString())
                mergedRows.add(copyToNestedModuleRowData(newRow));
            }
        }
        return mergedRows;
    }

    public static void fillApprovalData(
            NestedModuleDataDTO nestedModuleData,
            Integer versionNo,
            Boolean isApproval,
            Map<Long, SysModuleCombineInfo> moduleCacheMap) {
        if (nestedModuleData == null) {
            return;
        }
        SysModuleCombineInfo sysModuleInfo = moduleCacheMap.get(nestedModuleData.getModuleId());
        if (CollectionUtil.isNotEmpty(nestedModuleData.getRecord())) {
            for (RecordDataDTO recordDataDTO : nestedModuleData.getRecord()) {
                if (CollectionUtil.isNotEmpty(recordDataDTO.getRow())) {
                    // 补充主表及其子表的字段
                    Set<String> tabs = new HashSet<>();
                    tabs.add(sysModuleInfo.getModuleInfo().getPrimaryTable());
                    if (CollectionUtil.isNotEmpty(sysModuleInfo.getSubTables())) {
                        tabs.addAll(sysModuleInfo.getSubTables());
                    }
                    for (String tab : tabs) {

                        Long dataId =
                                DataPersistenceUtil.getTableRowIdWithTabName(
                                        recordDataDTO.getRow(), tab);
                        // 新增加
                        if (dataId == null || dataId <= 0) {
                            recordDataDTO
                                    .getRow()
                                    .put(
                                            tab + ".business_no",
                                            DataPersistenceUtil.getNewBusinessNo(
                                                    sysModuleInfo.getModuleInfo().getModuleCode(),
                                                    tab));
                            recordDataDTO.getRow().put(tab + ".version_no", versionNo);
                            // 如果是根模块主表, 设置状态
                            if (sysModuleInfo.getModuleInfo().getParentId() == null
                                    || sysModuleInfo.getModuleInfo().getParentId().equals(0L)) {
                                if (tab.equalsIgnoreCase(
                                        sysModuleInfo.getModuleInfo().getPrimaryTable())) {
                                    if (isApproval) {
                                        recordDataDTO.getRow().put(tab + ".business_status", 1);
                                    } else {
                                        recordDataDTO.getRow().put(tab + ".business_status", 99);
                                        recordDataDTO
                                                .getRow()
                                                .put(
                                                        tab + ".registration_time",
                                                        LocalDateTime.now());
                                    }
                                }
                            }
                        } else {
                            // 修改 不需要设置
                        }
                    }
                }
                if (CollectionUtil.isNotEmpty(recordDataDTO.getChildren())) {
                    for (NestedModuleDataDTO child : recordDataDTO.getChildren()) {
                        fillApprovalData(child, versionNo, isApproval, moduleCacheMap);
                    }
                }
            }
        }
    }

    // 变更保存时 填充 审批相关的数据
    public static void fillApprovalDataByChange(
            NestedModuleDataDTO nestedModuleData,
            Integer versionNo,
            Long sourceId,
            Boolean isApproval,
            Map<Long, SysModuleCombineInfo> moduleCacheMap) {
        if (nestedModuleData == null) {
            return;
        }
        SysModuleCombineInfo sysModuleInfo = moduleCacheMap.get(nestedModuleData.getModuleId());
        if (CollectionUtil.isNotEmpty(nestedModuleData.getRecord())) {
            for (RecordDataDTO recordDataDTO : nestedModuleData.getRecord()) {
                if (CollectionUtil.isNotEmpty(recordDataDTO.getRow())) {
                    // 补充主表及其子表的字段
                    Set<String> tabs = new HashSet<>();
                    tabs.add(sysModuleInfo.getModuleInfo().getPrimaryTable());
                    if (CollectionUtil.isNotEmpty(sysModuleInfo.getSubTables())) {
                        tabs.addAll(sysModuleInfo.getSubTables());
                    }

                    for (String tab : tabs) {
                        String businessNo =
                                DataPersistenceUtil.getTableRowBusinessNoWithTabName(
                                        recordDataDTO.getRow(), tab);

                        // 如果是根模块主表, 设置状态
                        if (sysModuleInfo.getModuleInfo().getParentId() == null
                                || sysModuleInfo.getModuleInfo().getParentId().equals(0L)) {
                            // 主表 才补充的字段
                            if (tab.equalsIgnoreCase(
                                    sysModuleInfo.getModuleInfo().getPrimaryTable())) {
                                recordDataDTO.getRow().put(tab + ".business_status", 1);
                                recordDataDTO.getRow().put(tab + ".source_id", sourceId);
                            } else {
                                // 变更都是 待审批链的
                            }
                        }

                        recordDataDTO.getRow().put(tab + ".version_no", versionNo);

                        // 新增加
                        if (StringUtils.isEmpty(businessNo)) {
                            recordDataDTO
                                    .getRow()
                                    .put(
                                            tab + ".business_no",
                                            DataPersistenceUtil.getNewBusinessNo(
                                                    sysModuleInfo.getModuleInfo().getModuleCode(),
                                                    tab));
                        }
                    }
                }
                if (CollectionUtil.isNotEmpty(recordDataDTO.getChildren())) {
                    for (NestedModuleDataDTO child : recordDataDTO.getChildren()) {
                        fillApprovalDataByChange(
                                child, versionNo, sourceId, isApproval, moduleCacheMap);
                    }
                }
            }
        }
    }

    public static void cleanNestedModuleData(
            NestedModuleDataDTO nestedModuleData, Map<Long, SysModuleCombineInfo> moduleCacheMap) {
        if (nestedModuleData == null) {
            return;
        }
        SysModuleCombineInfo sysModuleInfo = moduleCacheMap.get(nestedModuleData.getModuleId());
        if (CollectionUtil.isNotEmpty(nestedModuleData.getRecord())) {
            for (RecordDataDTO recordDataDTO : nestedModuleData.getRecord()) {
                if (CollectionUtil.isNotEmpty(recordDataDTO.getRow())) {
                    cleanRow(recordDataDTO.getRow(), sysModuleInfo);
                }
                if (CollectionUtil.isNotEmpty(recordDataDTO.getChildren())) {
                    for (NestedModuleDataDTO child : recordDataDTO.getChildren()) {
                        cleanNestedModuleData(child, moduleCacheMap);
                    }
                }
            }
        }
    }

    public static void cleanRow(
            Map<String, Object> row, SysModuleCombineInfo sysModuleCombineInfo) {
        // 哪些字段需要移除
        // 1.CLEAN_FIELDS 通用的字段
        // 2.表关联的上级的字段
        Set<String> cleanFields = new HashSet<>();
        cleanFields.addAll(CLEAN_FIELDS);
        if (sysModuleCombineInfo.getMainTableRelationDTO() != null) {
            cleanFields.add(sysModuleCombineInfo.getMainTableRelationDTO().getJoinField());
        }
        for (String subTable : sysModuleCombineInfo.getSubTables()) {
            if (sysModuleCombineInfo.getSubTableRelationMap().get(subTable) != null) {
                cleanFields.add(
                        sysModuleCombineInfo.getSubTableRelationMap().get(subTable).getJoinField());
            }
        }
        for (String field : cleanFields) {
            row.remove(sysModuleCombineInfo.getModuleInfo().getPrimaryTable() + "." + field);
            if (CollectionUtil.isNotEmpty(sysModuleCombineInfo.getSubTables())) {
                for (String subTable : sysModuleCombineInfo.getSubTables()) {
                    row.remove(subTable + "." + field);
                }
            }
        }
    }

    public static void mergeDeletionsNestedModuleData(
            NestedModuleDataDTO nestedStoreData,
            NestedModuleDataDTO detail,
            Map<Long, SysModuleCombineInfo> moduleCacheMap) {
        if (nestedStoreData == null) {
            return;
        }
        // 没有数据
        if (nestedStoreData.getModuleId() == null
                || CollectionUtil.isEmpty(nestedStoreData.getRecord())) {
            return;
        }
        RecordDataDTO rootRecord = nestedStoreData.getRecord().get(0);
        Long rootModuleId = nestedStoreData.getModuleId();
        Long rootDataId =
                DataPersistenceUtil.getTableRowIdWithTabName(
                        rootRecord.getRow(),
                        moduleCacheMap.get(rootModuleId).getModuleInfo().getPrimaryTable());
        if (rootDataId == null) {
            return;
        }
        Map<Long, Map<Long, Set<Long>>> parentToChildDataMap =
                collectParentToChildDataMap(detail, moduleCacheMap);
        if (CollectionUtil.isNotEmpty(rootRecord.getChildren())) {
            for (NestedModuleDataDTO child : rootRecord.getChildren()) {
                mergeDeletionsChild(
                        child, rootModuleId, rootDataId, parentToChildDataMap, moduleCacheMap);
            }
        }
    }

    public static void mergeDeletionsChild(
            NestedModuleDataDTO nestedStoreData,
            Long parentModuleId,
            Long parentDataId,
            Map<Long, Map<Long, Set<Long>>> parentToChildDataMap,
            Map<Long, SysModuleCombineInfo> moduleCacheMap) {
        if (nestedStoreData == null) {
            return;
        }
        if (nestedStoreData.getModuleId() == null
                || CollectionUtil.isEmpty(nestedStoreData.getRecord())) {
            return;
        }
        Long currentModuleId = nestedStoreData.getModuleId();
        if (StringUtils.isEmpty(
                moduleCacheMap.get(currentModuleId).getModuleInfo().getPrimaryTable())) {
            for (RecordDataDTO recordDataDTO : nestedStoreData.getRecord()) {
                if (CollectionUtil.isNotEmpty(recordDataDTO.getChildren())) {
                    for (NestedModuleDataDTO child : recordDataDTO.getChildren()) {
                        mergeDeletionsChild(
                                child,
                                currentModuleId,
                                parentDataId,
                                parentToChildDataMap,
                                moduleCacheMap);
                    }
                }
            }
        }

        // 显示更新的 ID
        Set<Long> updateIds = new HashSet<>();
        for (RecordDataDTO recordDataDTO : nestedStoreData.getRecord()) {
            Long dataId =
                    DataPersistenceUtil.getTableRowIdWithTabName(
                            recordDataDTO.getRow(),
                            moduleCacheMap.get(currentModuleId).getModuleInfo().getPrimaryTable());
            if (dataId != null && dataId > 0) {
                updateIds.add(dataId);
            }
        }
        // 详情页中的所有的ID
        Set<Long> dbIds =
                parentToChildDataMap
                        .getOrDefault(parentModuleId, new HashMap<>())
                        .getOrDefault(parentDataId, new HashSet<>());

        // 隐士删除的ID
        Set<Long> deletionIds =
                dbIds.stream()
                        .filter(x -> x != null)
                        .filter(x -> !updateIds.contains(x))
                        .collect(Collectors.toSet());

        // 处理子数据
        for (RecordDataDTO recordDataDTO : nestedStoreData.getRecord()) {
            Long dataId =
                    DataPersistenceUtil.getTableRowIdWithTabName(
                            recordDataDTO.getRow(),
                            moduleCacheMap.get(currentModuleId).getModuleInfo().getPrimaryTable());
            boolean insert = (dataId == null || dataId <= 0);

            if (CollectionUtil.isNotEmpty(recordDataDTO.getChildren())) {
                for (NestedModuleDataDTO child : recordDataDTO.getChildren()) {
                    if (!insert) {
                        mergeDeletionsChild(
                                child,
                                currentModuleId,
                                dataId,
                                parentToChildDataMap,
                                moduleCacheMap);
                    }
                }
            }
        }
        if (CollectionUtil.isNotEmpty(deletionIds)) {
            for (Long dataId : deletionIds) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put(
                        moduleCacheMap.get(currentModuleId).getModuleInfo().getPrimaryTable()
                                + ".id",
                        dataId);
                row.put(
                        moduleCacheMap.get(currentModuleId).getModuleInfo().getPrimaryTable()
                                + ".deleted",
                        (byte) 1);
                RecordDataDTO deletedRecord = new RecordDataDTO();
                deletedRecord.setRow(row);
                if (nestedStoreData.getRecord() == null) {
                    nestedStoreData.setRecord(new ArrayList<>());
                }
                nestedStoreData.getRecord().add(deletedRecord);
            }
        }
    }

    public static Map<Long, Map<Long, Set<Long>>> collectParentToChildDataMap(
            NestedModuleDataDTO detail, Map<Long, SysModuleCombineInfo> moduleCacheMap) {
        Map<Long, Map<Long, Set<Long>>> result = new HashMap<>();
        collectDataMap(detail, 0L, 0L, moduleCacheMap, result);
        return result;
    }

    private static void collectDataMap(
            NestedModuleDataDTO base,
            Long parentModuleId,
            Long parentDataId,
            Map<Long, SysModuleCombineInfo> moduleCacheMap,
            Map<Long, Map<Long, Set<Long>>> parentToChildDataMap) {
        if (base == null) {
            return;
        }
        Long currentModuleId = base.getModuleId();

        if (CollectionUtil.isNotEmpty(base.getRecord())) {
            for (RecordDataDTO recordDataDTO : base.getRecord()) {
                // 有行数据
                if (CollectionUtil.isNotEmpty(recordDataDTO.getRow())) {
                    Long dataId =
                            DataPersistenceUtil.getTableRowIdWithTabName(
                                    recordDataDTO.getRow(),
                                    moduleCacheMap
                                            .get(currentModuleId)
                                            .getModuleInfo()
                                            .getPrimaryTable());

                    // 收集父子ID关系
                    parentToChildDataMap
                            .computeIfAbsent(parentModuleId, k -> new HashMap<>())
                            .computeIfAbsent(parentDataId, k -> new HashSet<>())
                            .add(dataId);

                    if (CollectionUtil.isNotEmpty(recordDataDTO.getChildren())) {
                        for (NestedModuleDataDTO child : recordDataDTO.getChildren()) {
                            collectDataMap(
                                    child,
                                    currentModuleId,
                                    dataId,
                                    moduleCacheMap,
                                    parentToChildDataMap);
                        }
                    }
                } else {
                    // 没有行数据 向下递归
                    if (CollectionUtil.isNotEmpty(recordDataDTO.getChildren())) {
                        for (NestedModuleDataDTO child : recordDataDTO.getChildren()) {
                            collectDataMap(
                                    child,
                                    currentModuleId,
                                    parentDataId,
                                    moduleCacheMap,
                                    parentToChildDataMap);
                        }
                    }
                }
            }
        }
    }
}
