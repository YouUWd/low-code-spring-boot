package com.jdec.platform.dataengine.biz.third.api.persistence;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.IdUtil;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.dataengine.api.dto.model.SysModuleCombineInfo;
import com.jdec.platform.dataengine.biz.dto.ChangeItemDTO;
import com.jdec.platform.dataengine.biz.dto.ModuleRowNodeDataDTO;
import com.jdec.platform.dataengine.biz.dto.ModuleTreeNodeDataDTO;
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

    private static final Set<String> UN_COMPARE_FIELDS =
            Set.of(
                    "id",
                    "subject_id",
                    "business_no",
                    "version_no",
                    "business_status",
                    "created_by",
                    "updated_by",
                    "created_date",
                    "updated_date",
                    "deleted",
                    "active",
                    "source_id",
                    "node_id",
                    "parent_node_id");

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
                                                    StringUtils.isEmpty(
                                                                    sysModuleInfo
                                                                            .getModuleInfo()
                                                                            .getBusinessCode())
                                                            ? sysModuleInfo
                                                                    .getModuleInfo()
                                                                    .getModuleCode()
                                                            : sysModuleInfo
                                                                    .getModuleInfo()
                                                                    .getBusinessCode(),
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
                                                    StringUtils.isEmpty(
                                                                    sysModuleInfo
                                                                            .getModuleInfo()
                                                                            .getBusinessCode())
                                                            ? sysModuleInfo
                                                                    .getModuleInfo()
                                                                    .getModuleCode()
                                                            : sysModuleInfo
                                                                    .getModuleInfo()
                                                                    .getBusinessCode(),
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
        // 从详情页中收集 数据 父子及级关系 , 父模块 1001 的数据 1233 ---> 子模块 1002的数据 1234,2345
        Map<Long, Map<Long, Map<Long, Set<Long>>>> parentToChildDataMap =
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
            Map<Long, Map<Long, Map<Long, Set<Long>>>> parentToChildDataMap,
            Map<Long, SysModuleCombineInfo> moduleCacheMap) {
        if (nestedStoreData == null) {
            return;
        }
        if (nestedStoreData.getModuleId() == null) {
            return;
        }

        // 这个地方 如果Record 为空 不能直接返回,有可能用户清空了 这个模块的数据，要删除掉
        //        if(CollectionUtil.isEmpty(nestedStoreData.getRecord())){
        //
        //        }

        Long currentModuleId = nestedStoreData.getModuleId();
        // 虚拟模块处理完直接 返回
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
            return;
        }
        // nestedStoreData.getRecord() 有可能为空
        List<RecordDataDTO> updatedRecords =
                nestedStoreData.getRecord() != null
                        ? nestedStoreData.getRecord()
                        : Collections.emptyList();

        // 处理子模块数据
        for (RecordDataDTO recordDataDTO : updatedRecords) {
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

        // 显示更新的 ID
        Set<Long> updateIds = new HashSet<>();
        // 计算 这个模块更新的ID
        for (RecordDataDTO recordDataDTO : updatedRecords) {
            Long dataId =
                    DataPersistenceUtil.getTableRowIdWithTabName(
                            recordDataDTO.getRow(),
                            moduleCacheMap.get(currentModuleId).getModuleInfo().getPrimaryTable());
            if (dataId != null && dataId > 0) {
                updateIds.add(dataId);
            }
        }
        // 详情页中的所有的ID  这个模块 所在父模块parentModuleId的父数据parentDataId 下所有子模块的数据ID
        Set<Long> dbIds =
                parentToChildDataMap
                        .getOrDefault(parentModuleId, new HashMap<>())
                        .getOrDefault(parentDataId, new HashMap<>())
                        .getOrDefault(currentModuleId, new HashSet<>());

        // 计算 隐士删除的ID
        Set<Long> deletionIds =
                dbIds.stream()
                        .filter(x -> x != null)
                        .filter(x -> !updateIds.contains(x))
                        .collect(Collectors.toSet());

        /// 处理子数据 完成后 补充 删除的数据ID
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

    public static Map<Long, Map<Long, Map<Long, Set<Long>>>> collectParentToChildDataMap(
            NestedModuleDataDTO detail, Map<Long, SysModuleCombineInfo> moduleCacheMap) {
        Map<Long, Map<Long, Map<Long, Set<Long>>>> result = new HashMap<>();
        collectDataMap(detail, 0L, 0L, moduleCacheMap, result);
        return result;
    }

    private static void collectDataMap(
            NestedModuleDataDTO base,
            Long parentModuleId,
            Long parentDataId,
            Map<Long, SysModuleCombineInfo> moduleCacheMap,
            Map<Long, Map<Long, Map<Long, Set<Long>>>> parentToChildDataMap) {
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
                            .computeIfAbsent(parentDataId, k -> new HashMap<>())
                            .computeIfAbsent(currentModuleId, k -> new HashSet<>())
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
                    // 没有行数据 包含许模块的情况  也收集下父模块到虚模块的 父子关系 虚模块的数据ID 来自父模块的数据ID
                    // 收集父子ID关系
                    parentToChildDataMap
                            .computeIfAbsent(parentModuleId, k -> new HashMap<>())
                            .computeIfAbsent(parentDataId, k -> new HashMap<>())
                            .computeIfAbsent(currentModuleId, k -> new HashSet<>())
                            .add(parentDataId);

                    // 向下递归,处理子模块
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

    /**
     * 根据详情数据，拷贝出 引用的外部数据
     *
     * @param detail
     * @return
     */
    public static NestedModuleDataDTO copyExternalReferenceData(
            NestedModuleDataDTO detail, Map<Long, SysModuleCombineInfo> moduleCacheMap) {
        if (detail == null) {
            return null;
        }
        NestedModuleDataDTO copy = new NestedModuleDataDTO();
        copy.setModuleId(detail.getModuleId());
        List<RecordDataDTO> recordList = new ArrayList<>();
        copy.setRecord(recordList);
        if (CollectionUtil.isEmpty(detail.getRecord())) {
            return copy;
        }
        for (RecordDataDTO recordDataDTO : detail.getRecord()) {
            RecordDataDTO recordData = new RecordDataDTO();

            // 拷贝数据
            if (CollectionUtil.isNotEmpty(recordDataDTO.getRow())) {
                recordData.setRow(
                        copyReferencDataMap(
                                recordDataDTO.getRow(), moduleCacheMap.get(copy.getModuleId())));
            }
            recordList.add(recordData);
            // 处理子模块数据
            if (CollectionUtil.isNotEmpty(recordDataDTO.getChildren())) {
                List<NestedModuleDataDTO> childrenList = new ArrayList<>();
                for (NestedModuleDataDTO child : recordDataDTO.getChildren()) {
                    childrenList.add(copyExternalReferenceData(child, moduleCacheMap));
                }
                recordData.setChildren(childrenList);
            }
        }
        return copy;
    }

    public static Map<String, Object> copyReferencDataMap(
            Map<String, Object> rowMap, SysModuleCombineInfo moduleInfo) {
        Map<String, Object> map = new HashMap<>();
        Set<String> remainFields = new HashSet<>();
        remainFields.addAll(DataPersistenceUtil.getAllTableIdFields(moduleInfo.getAllTables()));
        remainFields.addAll(moduleInfo.getExternalReferenceFields());
        for (Map.Entry<String, Object> entry : rowMap.entrySet()) {
            if (remainFields.contains(entry.getKey())) {
                map.put(entry.getKey(), copyObject(entry.getValue()));
            }
        }
        return map;
    }

    /**
     * 实时保存时 获取新增的行的 模块数据
     *
     * @param newData
     * @return
     */
    public static NestedModuleDataDTO copyNewNestedModuleData(
            NestedModuleDataDTO newData, Map<Long, SysModuleCombineInfo> moduleCacheMap) {
        if (newData == null) {
            return null;
        }
        Long currentModuleId = newData.getModuleId();
        if (CollectionUtil.isEmpty(newData.getRecord())) {
            return null;
        }
        RecordDataDTO rootData = newData.getRecord().get(0);
        if (rootData == null) {
            return null;
        }
        Long dataId =
                DataPersistenceUtil.getTableRowIdWithTabName(
                        rootData.getRow(),
                        moduleCacheMap.get(currentModuleId).getModuleInfo().getPrimaryTable());

        if (dataId == null || dataId <= 0) {
            return null;
        }
        if (CollectionUtil.isEmpty(rootData.getChildren())) {
            return null;
        }

        for (NestedModuleDataDTO child : rootData.getChildren()) {
            return copyNewNestedChildModuleData(child, currentModuleId, dataId, moduleCacheMap);
        }
        return null;
    }

    public static NestedModuleDataDTO copyNewNestedChildModuleData(
            NestedModuleDataDTO child,
            Long parentModuleId,
            Long parentDataId,
            Map<Long, SysModuleCombineInfo> moduleCacheMap) {
        if (child == null) {
            return null;
        }
        Long currentModuleId = child.getModuleId();

        if (CollectionUtil.isEmpty(child.getRecord())) {
            return null;
        }

        RecordDataDTO childData = child.getRecord().get(0);
        if (childData == null) {
            return null;
        }
        if (CollectionUtil.isNotEmpty(childData.getRow())) {
            Long childDataId =
                    DataPersistenceUtil.getTableRowIdWithTabName(
                            childData.getRow(),
                            moduleCacheMap.get(currentModuleId).getModuleInfo().getPrimaryTable());
            if (childDataId != null && childDataId > 0) {
                return null;
            }
            // 没有ID 新增的行数据

            Map<String, Object> rowMap = new HashMap<>();
            for (Map.Entry<String, Object> entry : childData.getRow().entrySet()) {
                rowMap.put(entry.getKey(), copyObject(entry.getValue()));
            }
            if (StringUtils.isEmpty(
                    moduleCacheMap.get(currentModuleId).getModuleInfo().getPrimaryTable())) {
                return null;
            }
            if (moduleCacheMap.get(parentModuleId).getMainTableRelationDTO() == null) {
                return null;
            }
            String fk =
                    moduleCacheMap.get(currentModuleId).getModuleInfo().getPrimaryTable()
                            + "."
                            + moduleCacheMap
                                    .get(parentModuleId)
                                    .getMainTableRelationDTO()
                                    .getJoinField();
            rowMap.put(fk, parentDataId);

            RecordDataDTO newChildData = new RecordDataDTO();
            newChildData.setRow(rowMap);
            List<RecordDataDTO> records = new ArrayList<>();
            records.add(newChildData);
            NestedModuleDataDTO newChild = new NestedModuleDataDTO();
            newChild.setModuleId(currentModuleId);
            newChild.setRecord(records);
            return newChild;
        } else {
            for (NestedModuleDataDTO ch : childData.getChildren()) {
                return copyNewNestedChildModuleData(
                        ch, currentModuleId, parentDataId, moduleCacheMap);
            }
        }
        return null;
    }

    public static Long getLeafRowDataId(
            NestedModuleDataDTO rootData, Map<Long, SysModuleCombineInfo> moduleCacheMap) {
        if (rootData == null) {
            return null;
        }
        if (CollectionUtil.isEmpty(rootData.getRecord())) {
            return null;
        }

        for (RecordDataDTO recordDataDTO : rootData.getRecord()) {
            if (recordDataDTO == null) {
                continue;
            }
            // 有子模块
            Long dataId = null;
            if (CollectionUtil.isNotEmpty(recordDataDTO.getChildren())) {
                for (NestedModuleDataDTO child : recordDataDTO.getChildren()) {
                    dataId = getLeafRowDataId(child, moduleCacheMap);
                    if (dataId != null && dataId > 0) {
                        return dataId;
                    }
                }
            }
            if (CollectionUtil.isNotEmpty(recordDataDTO.getRow())) {
                return DataPersistenceUtil.getTableRowIdWithTabName(
                        recordDataDTO.getRow(),
                        moduleCacheMap
                                .get(rootData.getModuleId())
                                .getModuleInfo()
                                .getPrimaryTable());
            }
        }
        return null;
    }

    public static Map<Long, Map<Long, Map<String, Object>>> collectModuleDataMap(
            NestedModuleDataDTO base, Map<Long, SysModuleCombineInfo> moduleCacheMap) {
        Map<Long, Map<Long, Map<String, Object>>> result = new HashMap<>();
        if (base == null) {
            return result;
        }
        collectModuleChildrenDataMap(ListUtil.of(base), moduleCacheMap, result);
        return result;
    }

    public static void collectModuleChildrenDataMap(
            List<NestedModuleDataDTO> children,
            Map<Long, SysModuleCombineInfo> moduleCacheMap,
            Map<Long, Map<Long, Map<String, Object>>> result) {
        if (CollectionUtil.isEmpty(children)) {
            return;
        }
        for (NestedModuleDataDTO child : children) {
            if (CollectionUtil.isNotEmpty(child.getRecord())) {
                for (RecordDataDTO recordDataDTO : child.getRecord()) {
                    // 有行数据
                    if (CollectionUtil.isNotEmpty(recordDataDTO.getRow())) {
                        Long dataId =
                                DataPersistenceUtil.getTableRowIdWithTabName(
                                        recordDataDTO.getRow(),
                                        moduleCacheMap
                                                .get(child.getModuleId())
                                                .getModuleInfo()
                                                .getPrimaryTable());
                        // 收集数据map
                        result.computeIfAbsent(child.getModuleId(), k -> new HashMap<>())
                                .computeIfAbsent(dataId, k -> new HashMap<>())
                                .putAll(recordDataDTO.getRow());
                    }
                    if (CollectionUtil.isNotEmpty(recordDataDTO.getChildren())) {
                        collectModuleChildrenDataMap(
                                recordDataDTO.getChildren(), moduleCacheMap, result);
                    }
                }
            }
        }
    }

    public static List<ChangeItemDTO> getChangeItemList(
            NestedModuleDataDTO baseData,
            NestedModuleDataDTO newData,
            Map<Long, SysModuleCombineInfo> moduleCacheMap) {
        // 从详情页中收集 数据
        Map<Long, Map<Long, Map<String, Object>>> dataMap =
                collectModuleDataMap(baseData, moduleCacheMap);
        // 从详情页中收集 数据 父子及级关系 , 父模块 1001 的数据 1233 ---> 子模块 1002的数据 1234,2345
        Map<Long, Map<Long, Map<Long, Set<Long>>>> parentToChildDataMap =
                collectParentToChildDataMap(baseData, moduleCacheMap);

        List<ChangeItemDTO> changeItemList = new ArrayList<>();

        RecordDataDTO rootBase = baseData.getRecord().get(0);

        RecordDataDTO rootNew = newData.getRecord().get(0);

        Long dataId =
                DataPersistenceUtil.getTableRowIdWithTabName(
                        rootNew.getRow(),
                        moduleCacheMap
                                .get(newData.getModuleId())
                                .getModuleInfo()
                                .getPrimaryTable());

        Long rootModuleId = newData.getModuleId();

        List<ChangeItemDTO> changes =
                getRowChangeItemList(
                        rootBase.getRow(),
                        rootNew.getRow(),
                        0L,
                        "U",
                        newData.getModuleId(),
                        moduleCacheMap);
        if (CollectionUtil.isNotEmpty(changes)) {
            changeItemList.addAll(changes);
        }
        for (NestedModuleDataDTO child : rootNew.getChildren()) {
            getChildChangeItemList(
                    child,
                    rootModuleId,
                    dataId,
                    false,
                    dataMap,
                    parentToChildDataMap,
                    moduleCacheMap,
                    changeItemList);
        }

        return changeItemList;
    }

    private static void getChildChangeItemList(
            NestedModuleDataDTO child,
            Long parentIdModuleId,
            Long parentDataId,
            Boolean isParentInsert,
            Map<Long, Map<Long, Map<String, Object>>> dataMap,
            Map<Long, Map<Long, Map<Long, Set<Long>>>> parentToChildDataMap,
            Map<Long, SysModuleCombineInfo> moduleCacheMap,
            List<ChangeItemDTO> changeItemList) {
        if (child == null) {
            return;
        }

        Long currentModuleId = child.getModuleId();

        // 虚拟模块 不处理
        if (StringUtils.isEmpty(
                moduleCacheMap.get(currentModuleId).getModuleInfo().getPrimaryTable())) {
            if (CollectionUtil.isNotEmpty(child.getRecord())) {
                for (RecordDataDTO recordDataDTO : child.getRecord()) {
                    if (CollectionUtil.isNotEmpty(recordDataDTO.getChildren())) {
                        for (NestedModuleDataDTO childChild : recordDataDTO.getChildren()) {
                            getChildChangeItemList(
                                    childChild,
                                    currentModuleId,
                                    parentDataId,
                                    isParentInsert,
                                    dataMap,
                                    parentToChildDataMap,
                                    moduleCacheMap,
                                    changeItemList);
                        }
                    }
                }
            }
            return;
        }

        // child.getRecord() 有可能为空
        List<RecordDataDTO> updatedRecords =
                child.getRecord() != null ? child.getRecord() : Collections.emptyList();

        // 先计算出 当前模块删除了哪些ID
        // 显示更新的 ID
        Set<Long> updateIds = new HashSet<>();
        // 计算 这个模块更新的ID
        for (RecordDataDTO recordDataDTO : updatedRecords) {
            Long dataId =
                    DataPersistenceUtil.getTableRowIdWithTabName(
                            recordDataDTO.getRow(),
                            moduleCacheMap.get(currentModuleId).getModuleInfo().getPrimaryTable());
            if (dataId != null && dataId > 0) {
                updateIds.add(dataId);
            }
        }
        // 详情页中的所有的ID  这个模块 所在父模块parentModuleId的父数据parentDataId 下所有子模块的数据ID
        Set<Long> dbIds =
                parentToChildDataMap
                        .getOrDefault(parentIdModuleId, new HashMap<>())
                        .getOrDefault(parentDataId, new HashMap<>())
                        .getOrDefault(currentModuleId, new HashSet<>());

        // 计算 隐士删除的ID
        Set<Long> deletionIds =
                dbIds.stream()
                        .filter(x -> x != null)
                        .filter(x -> !updateIds.contains(x))
                        .collect(Collectors.toSet());

        // 处理子模块数据
        for (RecordDataDTO recordDataDTO : updatedRecords) {
            Long dataId =
                    DataPersistenceUtil.getTableRowIdWithTabName(
                            recordDataDTO.getRow(),
                            moduleCacheMap.get(currentModuleId).getModuleInfo().getPrimaryTable());
            boolean currentInsert = (dataId == null || dataId <= 0);

            // 当前节点的数据是新增的
            if (isParentInsert || currentInsert) {
                Set<String> tables = moduleCacheMap.get(currentModuleId).getAllTables();
                for (String tab : tables) {
                    // 补充ID字段
                    DataPersistenceUtil.fillTableRowFieldInWideRow(
                            recordDataDTO.getRow(), tab, "id", IdUtil.getSnowflakeNextId());
                }

                // 新增的节点数据
                List<ChangeItemDTO> changes =
                        getRowChangeItemList(
                                null,
                                recordDataDTO.getRow(),
                                parentDataId,
                                "I",
                                currentModuleId,
                                moduleCacheMap);
                if (CollectionUtil.isNotEmpty(changes)) {
                    changeItemList.addAll(changes);
                }
                dataId =
                        DataPersistenceUtil.getTableRowIdWithTabName(
                                recordDataDTO.getRow(),
                                moduleCacheMap
                                        .get(currentModuleId)
                                        .getModuleInfo()
                                        .getPrimaryTable());
            } else {
                // 当前节点数是 修改的
                Map<String, Object> baseRow =
                        dataMap.getOrDefault(currentModuleId, new HashMap<>())
                                .getOrDefault(dataId, new HashMap<>());

                List<ChangeItemDTO> changes =
                        getRowChangeItemList(
                                baseRow,
                                recordDataDTO.getRow(),
                                parentDataId,
                                "U",
                                currentModuleId,
                                moduleCacheMap);
                if (CollectionUtil.isNotEmpty(changes)) {
                    changeItemList.addAll(changes);
                }
            }

            if (CollectionUtil.isNotEmpty(recordDataDTO.getChildren())) {
                for (NestedModuleDataDTO childChild : recordDataDTO.getChildren()) {
                    getChildChangeItemList(
                            childChild,
                            currentModuleId,
                            dataId,
                            (isParentInsert || currentInsert),
                            dataMap,
                            parentToChildDataMap,
                            moduleCacheMap,
                            changeItemList);
                }
            }
        }

        /// 处理子数据 完成后 补充 删除的数据ID
        if (CollectionUtil.isNotEmpty(deletionIds)) {
            // todo 构建删除的数据行
            getRowDeletionItemList(
                    deletionIds,
                    currentModuleId,
                    parentIdModuleId,
                    parentDataId,
                    dataMap,
                    parentToChildDataMap,
                    moduleCacheMap,
                    changeItemList);
        }
    }

    public static void getRowDeletionItemList(
            Set<Long> deletionIds,
            Long currentModuleId,
            Long parentIdModuleId,
            Long parentDataId,
            Map<Long, Map<Long, Map<String, Object>>> dataMap,
            Map<Long, Map<Long, Map<Long, Set<Long>>>> parentToChildDataMap,
            Map<Long, SysModuleCombineInfo> moduleCacheMap,
            List<ChangeItemDTO> changeItemList) {

        // 虚拟模块处理
        if (StringUtils.isEmpty(
                moduleCacheMap.get(currentModuleId).getModuleInfo().getPrimaryTable())) {
            SysModuleCombineInfo currentModule = moduleCacheMap.get(currentModuleId);
            // 子数据 的删除处理
            for (Long dataId : deletionIds) {
                // 如果当前模块又子模块
                if (CollectionUtil.isNotEmpty(
                        currentModule.getModuleTree().getChildModuleIds(currentModuleId))) {
                    for (Long childModuleId :
                            currentModule.getModuleTree().getChildModuleIds(currentModuleId)) {
                        // 获取当前 删除数据 的 子模块的子数据
                        Set<Long> childDataIds =
                                parentToChildDataMap
                                        .getOrDefault(parentIdModuleId, new HashMap<>())
                                        .getOrDefault(dataId, new HashMap<>())
                                        .getOrDefault(childModuleId, new HashSet<>());
                        if (CollectionUtil.isNotEmpty(childDataIds)) {
                            getRowDeletionItemList(
                                    childDataIds,
                                    childModuleId,
                                    currentModuleId,
                                    dataId,
                                    dataMap,
                                    parentToChildDataMap,
                                    moduleCacheMap,
                                    changeItemList);
                        }
                    }
                }
            }
            return;
        }

        // 先处理删除的数据
        for (Long dataId : deletionIds) {
            Map<String, Object> baseRow =
                    dataMap.getOrDefault(currentModuleId, new HashMap<>())
                            .getOrDefault(dataId, new HashMap<>());
            // 删除的行
            List<ChangeItemDTO> changes =
                    getRowChangeItemList(
                            baseRow, null, parentDataId, "D", currentModuleId, moduleCacheMap);
            if (CollectionUtil.isNotEmpty(changes)) {
                changeItemList.addAll(changes);
            }
        }

        SysModuleCombineInfo currentModule = moduleCacheMap.get(currentModuleId);
        // 子数据 的删除处理
        for (Long dataId : deletionIds) {
            // 如果当前模块又子模块
            if (CollectionUtil.isNotEmpty(
                    currentModule.getModuleTree().getChildModuleIds(currentModuleId))) {
                for (Long childModuleId :
                        currentModule.getModuleTree().getChildModuleIds(currentModuleId)) {
                    // 获取当前 删除数据 的 子模块的子数据
                    Set<Long> childDataIds =
                            parentToChildDataMap
                                    .getOrDefault(currentModuleId, new HashMap<>())
                                    .getOrDefault(dataId, new HashMap<>())
                                    .getOrDefault(childModuleId, new HashSet<>());
                    if (CollectionUtil.isNotEmpty(childDataIds)) {
                        getRowDeletionItemList(
                                childDataIds,
                                childModuleId,
                                currentModuleId,
                                dataId,
                                dataMap,
                                parentToChildDataMap,
                                moduleCacheMap,
                                changeItemList);
                    }
                }
            }
        }
    }

    public static List<ChangeItemDTO> getRowChangeItemList(
            Map<String, Object> rowBase,
            Map<String, Object> rowNew,
            Long parentDataId,
            String action,
            Long moduleId,
            Map<Long, SysModuleCombineInfo> moduleCacheMap) {
        List<ChangeItemDTO> result = new ArrayList<>();
        Set<String> subTables = moduleCacheMap.get(moduleId).getSubTables();
        switch (action) {
            case "I" -> {

                // 先处理主表数据
                Map<String, Object> tableRow =
                        DataPersistenceUtil.getTableRowMapFromWideRow(
                                rowNew,
                                moduleCacheMap.get(moduleId).getModuleInfo().getPrimaryTable());
                Long primaryDataId = DataPersistenceUtil.getTableRowId(tableRow);
                ChangeItemDTO changeItemDTO =
                        ChangeItemDTO.builder()
                                .tableName(
                                        moduleCacheMap
                                                .get(moduleId)
                                                .getModuleInfo()
                                                .getPrimaryTable())
                                .nodeId(String.valueOf(primaryDataId))
                                .parentNodeId(String.valueOf(parentDataId))
                                .changeContent(tableRow)
                                .action("INSERT")
                                .build();
                result.add(changeItemDTO);

                for (String table : subTables) {
                    Map<String, Object> subTableRow =
                            DataPersistenceUtil.getTableRowMapFromWideRow(rowNew, table);
                    Long subTableDataId = DataPersistenceUtil.getTableRowId(subTableRow);
                    ChangeItemDTO item =
                            ChangeItemDTO.builder()
                                    .tableName(table)
                                    .nodeId(String.valueOf(primaryDataId))
                                    .parentNodeId(String.valueOf(parentDataId))
                                    .changeContent(subTableRow)
                                    .action("INSERT")
                                    .build();
                    result.add(item);
                }
            }
            case "U" -> {

                // 先处理主表数据
                Map<String, Object> tableRowNew =
                        DataPersistenceUtil.getTableRowMapFromWideRow(
                                rowNew,
                                moduleCacheMap.get(moduleId).getModuleInfo().getPrimaryTable());
                Map<String, Object> tableRowBase =
                        DataPersistenceUtil.getTableRowMapFromWideRow(
                                rowBase,
                                moduleCacheMap.get(moduleId).getModuleInfo().getPrimaryTable());
                Long primaryDataId = DataPersistenceUtil.getTableRowId(tableRowNew);
                Map<String, Object> changeMap = getRowChangeContent(tableRowNew, tableRowBase);
                if (CollectionUtil.isNotEmpty(changeMap)) {
                    ChangeItemDTO changeItemDTO =
                            ChangeItemDTO.builder()
                                    .tableName(
                                            moduleCacheMap
                                                    .get(moduleId)
                                                    .getModuleInfo()
                                                    .getPrimaryTable())
                                    .nodeId(String.valueOf(primaryDataId))
                                    .parentNodeId(String.valueOf(parentDataId))
                                    .changeContent(changeMap)
                                    .action("UPDATE")
                                    .build();
                    result.add(changeItemDTO);
                }
                for (String table : subTables) {
                    Map<String, Object> subTableRow =
                            DataPersistenceUtil.getTableRowMapFromWideRow(rowNew, table);
                    Map<String, Object> subTableRowBase =
                            DataPersistenceUtil.getTableRowMapFromWideRow(rowBase, table);
                    Long subTableDataId = DataPersistenceUtil.getTableRowId(subTableRow);

                    Map<String, Object> subChangeMap =
                            getRowChangeContent(subTableRow, subTableRowBase);
                    if (CollectionUtil.isNotEmpty(subChangeMap)) {
                        ChangeItemDTO item =
                                ChangeItemDTO.builder()
                                        .tableName(table)
                                        .nodeId(String.valueOf(primaryDataId))
                                        .parentNodeId(String.valueOf(parentDataId))
                                        .changeContent(subChangeMap)
                                        .action("UPDATE")
                                        .build();
                        result.add(item);
                    }
                }
            }
            case "D" -> {

                // 先处理主表数据
                Map<String, Object> tableRow =
                        DataPersistenceUtil.getTableRowMapFromWideRow(
                                rowBase,
                                moduleCacheMap.get(moduleId).getModuleInfo().getPrimaryTable());
                Long primaryDataId = DataPersistenceUtil.getTableRowId(tableRow);
                ChangeItemDTO changeItemDTO =
                        ChangeItemDTO.builder()
                                .tableName(
                                        moduleCacheMap
                                                .get(moduleId)
                                                .getModuleInfo()
                                                .getPrimaryTable())
                                .nodeId(String.valueOf(primaryDataId))
                                .parentNodeId(String.valueOf(parentDataId))
                                .changeContent(Map.of("id", primaryDataId, "deleted", 1))
                                .action("DELETE")
                                .build();
                result.add(changeItemDTO);

                for (String table : subTables) {
                    Map<String, Object> subTableRow =
                            DataPersistenceUtil.getTableRowMapFromWideRow(rowBase, table);
                    Long subTableDataId = DataPersistenceUtil.getTableRowId(subTableRow);
                    ChangeItemDTO item =
                            ChangeItemDTO.builder()
                                    .tableName(table)
                                    .nodeId(String.valueOf(primaryDataId))
                                    .parentNodeId(String.valueOf(parentDataId))
                                    .changeContent(Map.of("id", subTableDataId, "deleted", 1))
                                    .action("DELETE")
                                    .build();
                    result.add(item);
                }
            }
        }
        return result;
    }

    public static Map<String, Object> getRowChangeContent(
            Map<String, Object> rowNew, Map<String, Object> rowBase) {
        Map<String, Object> changeMap = new HashMap<>();
        for (String key : rowNew.keySet()) {
            if (UN_COMPARE_FIELDS.contains(key)) {
                continue;
            }
            Object newValue = rowNew.get(key);
            Object dbValue = rowBase.get(key);
            // 没有修改
            // ModuleTreeDiffService.isEqual(newValue, dbValue);
            boolean isEqual = DataPersistenceUtil.isEqualValue(newValue, dbValue);
            if (isEqual) {
                continue;
            }
            changeMap.put(key, newValue);
        }
        return changeMap;
    }
}
