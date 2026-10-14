package com.jdec.platform.dataengine.biz.third.api.persistence;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.dataengine.biz.dto.ModuleRowNodeDataDTO;
import com.jdec.platform.dataengine.biz.dto.ModuleTreeNodeDataDTO;
import com.jdec.platform.dataengine.biz.dto.SysModuleCombineInfo;
import com.jdec.platform.dataengine.biz.util.ToolUtils;
import java.time.LocalDateTime;
import java.util.*;

public class ModuleTreeDataUtils {

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
        moduleTreeNodeDataDTO.setRecords(records);
        return moduleTreeNodeDataDTO;
    }

    public static ModuleTreeNodeDataDTO copyToModuleTreeNodeData(ModuleTreeNodeDataDTO paramData) {
        ModuleTreeNodeDataDTO moduleTreeNodeDataDTO = ModuleTreeNodeDataDTO.builder().build();
        moduleTreeNodeDataDTO.setModuleId(paramData.getModuleId());
        if (CollectionUtil.isEmpty(paramData.getRecords())) {
            return moduleTreeNodeDataDTO;
        }
        List<ModuleRowNodeDataDTO> rowNodeDataDTOList = new ArrayList<>();
        for (ModuleRowNodeDataDTO record : paramData.getRecords()) {
            ModuleRowNodeDataDTO moduleRowNodeDataDTO = copyToModuleRowNodeDataDTO(record);
            rowNodeDataDTOList.add(moduleRowNodeDataDTO);
        }
        moduleTreeNodeDataDTO.setRecords(rowNodeDataDTOList);
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

        Map<String, Object> originalRow = new HashMap<>();
        if (CollectionUtil.isNotEmpty(paramData.getOriginalRow())) {
            for (Map.Entry<String, Object> entry : paramData.getOriginalRow().entrySet())
                originalRow.put(String.valueOf(entry.getKey()), copyObject(entry.getValue()));
        }
        moduleRowNodeDataDTO.setOriginalRow(originalRow);

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

        List<ModuleRowNodeDataDTO> baseRecords = baseDetail.getRecords();
        List<ModuleRowNodeDataDTO> newRecords = newData.getRecords();
        if (CollectionUtil.isEmpty(baseRecords)) {
            return copyToModuleTreeNodeData(newData);
        }
        ModuleRowNodeDataDTO baseFirst = baseRecords.get(0);
        ModuleRowNodeDataDTO newFirst = newRecords.get(0);

        ModuleRowNodeDataDTO mergedFirst = mergeModuleRowNodeData(baseFirst, newFirst);

        return ModuleTreeNodeDataDTO.builder()
                .moduleId(moduleId)
                .records(CollectionUtil.newArrayList(mergedFirst))
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

        Map<String, Object> originalRowMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : baseRow.getRow().entrySet()) {
            originalRowMap.put(String.valueOf(entry.getKey()), copyObject(entry.getValue()));
        }
        mergeRow.setOriginalRow(originalRowMap);

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
                List<ModuleRowNodeDataDTO> newChildRecords = newChild.getRecords();
                if (CollectionUtil.isNotEmpty(newChild.getRecords())) {
                    for (ModuleRowNodeDataDTO record : newChild.getRecords()) {
                        newChildRecords.add(copyToModuleRowNodeDataDTO(record));
                    }
                }
                mergedChildren.add(
                        ModuleTreeNodeDataDTO.builder()
                                .moduleId(moduleId)
                                .records(newChildRecords)
                                .build());
            } else {
                // 对比数据ID，进行合并
                List<ModuleRowNodeDataDTO> mergedChildRows =
                        mergedChildModuleRows(baseChild.getRecords(), newChild.getRecords());
                mergedChildren.add(
                        ModuleTreeNodeDataDTO.builder()
                                .moduleId(moduleId)
                                .records(mergedChildRows)
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
            Object id = extractId(baseRow.getRow());
            if (id != null) {
                baseRowMap.put(id.toString(), baseRow);
            }
        }
        List<ModuleRowNodeDataDTO> mergedRows = new ArrayList<>();
        for (ModuleRowNodeDataDTO newRow : newRows) {
            Object id = extractId(newRow.getRow());
            if (id != null && baseRowMap.containsKey(id.toString())) {
                ModuleRowNodeDataDTO baseRow = baseRowMap.get(id.toString());
                mergedRows.add(mergeModuleRowNodeData(baseRow, newRow));
            } else {
                mergedRows.add(copyToModuleRowNodeDataDTO(newRow));
            }
        }
        return mergedRows;
    }

    public static Object extractId(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().contains(".id")) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static void cleanMapId(ModuleTreeNodeDataDTO moduleTreeNodeDataDTO) {
        if (moduleTreeNodeDataDTO == null) {
            return;
        }
        if (CollectionUtil.isNotEmpty(moduleTreeNodeDataDTO.getRecords())) {
            for (ModuleRowNodeDataDTO moduleRowNodeDataDTO : moduleTreeNodeDataDTO.getRecords()) {
                cleanRowId(moduleRowNodeDataDTO.getRow());
                if (CollectionUtil.isNotEmpty(moduleRowNodeDataDTO.getChildren())) {
                    for (ModuleTreeNodeDataDTO child : moduleRowNodeDataDTO.getChildren()) {
                        cleanMapId(child);
                    }
                }
            }
        }
    }

    public static void cleanRowId(Map<String, Object> rowMap) {
        if (rowMap == null) {
            return;
        }

        for (String key : rowMap.keySet()) {
            if (key.contains(".id")) {
                rowMap.remove(key);
            }
        }
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
                    Long dataId =
                            DataPersistenceUtil.getTableRowIdWithTabName(
                                    recordDataDTO.getRow(),
                                    sysModuleInfo.getModuleInfo().getPrimaryTable());
                    // 新增加
                    if (dataId == null || dataId <= 0) {
                        recordDataDTO
                                .getRow()
                                .put(
                                        sysModuleInfo.getModuleInfo().getPrimaryTable()
                                                + ".business_no",
                                        ToolUtils.nextNoWithFixedLen(
                                                sysModuleInfo.getModuleInfo().getPrimaryTable()));
                        recordDataDTO
                                .getRow()
                                .put(
                                        sysModuleInfo.getModuleInfo().getPrimaryTable()
                                                + ".version_no",
                                        versionNo);
                        // 如果是根模块主表, 设置状态
                        if (sysModuleInfo.getModuleInfo().getParentId() == null
                                || sysModuleInfo.getModuleInfo().getParentId().equals(0L)) {
                            if (isApproval) {
                                recordDataDTO
                                        .getRow()
                                        .put(
                                                sysModuleInfo.getModuleInfo().getPrimaryTable()
                                                        + ".business_status",
                                                1);
                            } else {
                                recordDataDTO
                                        .getRow()
                                        .put(
                                                sysModuleInfo.getModuleInfo().getPrimaryTable()
                                                        + ".business_status",
                                                99);
                                recordDataDTO
                                        .getRow()
                                        .put(
                                                sysModuleInfo.getModuleInfo().getPrimaryTable()
                                                        + ".registration_time",
                                                LocalDateTime.now());
                            }
                        }
                    } else {
                        // 修改 不需要设置
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
}
