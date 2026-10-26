package com.jdec.platform.dataengine.biz.service;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.dataengine.api.dto.model.*;
import com.jdec.platform.dataengine.biz.dto.SysModuleCombineInfo;
import com.jdec.platform.dataengine.biz.third.api.persistence.DataPersistenceUtil;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModuleDataChangeRowConvertService {

    public ModuleChangeDTO covertByModuleDataChangeRow(
            NestedModuleDataDTO nestedModuleDataDTO,
            Map<Long, Map<Long, RowChangeDTO>> changeMap,
            Map<Long, SysModuleCombineInfo> moduleCacheMap) {

        return buildModuleChangeDTO(nestedModuleDataDTO, changeMap, moduleCacheMap);
    }

    private ModuleChangeDTO buildModuleChangeDTO(
            NestedModuleDataDTO nestedModuleDataDTO,
            Map<Long, Map<Long, RowChangeDTO>> changeMap,
            Map<Long, SysModuleCombineInfo> moduleCacheMap) {

        if (nestedModuleDataDTO == null || nestedModuleDataDTO.getModuleId() == null) {
            return null;
        }
        SysModuleCombineInfo sysModuleInfo = moduleCacheMap.get(nestedModuleDataDTO.getModuleId());
        ModuleChangeDTO moduleChangeDTO = ModuleChangeDTO.builder().build();
        moduleChangeDTO.setMId(nestedModuleDataDTO.getModuleId());
        moduleChangeDTO.setName(
                sysModuleInfo != null ? sysModuleInfo.getModuleInfo().getModuleName() : null);

        // 模块没有数据 直接返回结构
        if (CollectionUtil.isEmpty(nestedModuleDataDTO.getRecord())) {
            return moduleChangeDTO;
        }

        ActionChangeDTO actionChangeDTO = ActionChangeDTO.builder().build();

        // 模块的多行数据
        for (RecordDataDTO recordDataDTO : nestedModuleDataDTO.getRecord()) {
            Long dataId =
                    DataPersistenceUtil.getTableRowIdWithTabName(
                            recordDataDTO.getRow(),
                            sysModuleInfo.getModuleInfo().getPrimaryTable());
            RowChangeDTO rowChange =
                    changeMap.getOrDefault(nestedModuleDataDTO.getModuleId(), Map.of()).get(dataId);

            RowChangeDTO rowChangeDTO = new RowChangeDTO();
            // 当前行 没有变更数据
            if (rowChange == null || CollectionUtil.isEmpty(rowChange.getColumns())) {
                if (actionChangeDTO.getU() == null) {
                    actionChangeDTO.setU(List.of(rowChangeDTO));
                } else {
                    actionChangeDTO.getU().add(rowChangeDTO);
                }

            } else {
                // 当前行 变更数据
                rowChangeDTO.setName(rowChange.getName());
                rowChangeDTO.setColumns(rowChange.getColumns());
                switch (rowChange.getAction()) {
                    case "i" -> {
                        if (actionChangeDTO.getI() == null) {
                            actionChangeDTO.setI(List.of(rowChangeDTO));
                        } else {
                            actionChangeDTO.getI().add(rowChangeDTO);
                        }
                    }
                    case "u" -> {
                        if (actionChangeDTO.getU() == null) {
                            actionChangeDTO.setU(List.of(rowChangeDTO));
                        } else {
                            actionChangeDTO.getU().add(rowChangeDTO);
                        }
                    }
                    case "d" -> {
                        if (actionChangeDTO.getD() == null) {
                            actionChangeDTO.setD(List.of(rowChangeDTO));
                        } else {
                            actionChangeDTO.getD().add(rowChangeDTO);
                        }
                    }
                }
            }
            rowChangeDTO.setChildren(
                    buildModuleChangeDTOList(
                            recordDataDTO.getChildren(), changeMap, moduleCacheMap));
        }
        moduleChangeDTO.setActions(actionChangeDTO);
        return moduleChangeDTO;
    }

    private List<ModuleChangeDTO> buildModuleChangeDTOList(
            List<NestedModuleDataDTO> nestedModuleDataDTOList,
            Map<Long, Map<Long, RowChangeDTO>> changeMap,
            Map<Long, SysModuleCombineInfo> moduleCacheMap) {

        List<ModuleChangeDTO> moduleChangeDTOList = new ArrayList<>();
        for (NestedModuleDataDTO nestedModuleDataDTO : nestedModuleDataDTOList) {
            ModuleChangeDTO moduleChangeDTO =
                    buildModuleChangeDTO(nestedModuleDataDTO, changeMap, moduleCacheMap);
            if (moduleChangeDTO != null) {
                moduleChangeDTOList.add(moduleChangeDTO);
            }
        }
        return moduleChangeDTOList;
    }

    public ModuleChangeDTO covertByModuleDataDiff(
            NestedModuleDataDTO oldData,
            NestedModuleDataDTO newData,
            Map<Long, SysModuleCombineInfo> moduleCacheMap) {
        if (oldData == null) {
            return null;
        }
        Map<String, Map<String, Object>> oldRowMap = getRowMap(oldData, moduleCacheMap);
        Map<String, Map<String, Object>> newRowMap = getRowMap(newData, moduleCacheMap);
        Map<Long, Map<Long, RowChangeDTO>> rowChangeMap =
                getRowChangeMap(oldRowMap, newRowMap, moduleCacheMap);
        Map<String, Map<Long, Set<Long>>> parentToChildrenDataIdOldMap =
                getParentToChildrenDataIdMap(oldData, moduleCacheMap);
        Map<String, Map<Long, Set<Long>>> parentToChildrenDataIdNewMap =
                getParentToChildrenDataIdMap(newData, moduleCacheMap);
        Map<String, Map<Long, Set<Long>>> parentToChildrenDataIdCombineMap =
                combineParentToChildrenDataIdMap(
                        parentToChildrenDataIdOldMap, parentToChildrenDataIdNewMap);

        Long rootModuleId = newData.getModuleId();
        Long rootDataId =
                parentToChildrenDataIdCombineMap
                        .getOrDefault("0_0", Map.of())
                        .getOrDefault(rootModuleId, Set.of())
                        .stream()
                        .findFirst()
                        .orElse(0L);
        if (rootModuleId == null || rootDataId == null) {
            return null;
        }
        ModuleTreeDTO moduleTreeDTO = moduleCacheMap.get(newData.getModuleId()).getModuleTree();
        ModuleChangeDTO moduleChangeDTO =
                buildModuleChangeDTOListByModuleTree(
                        moduleTreeDTO,
                        0L,
                        0L,
                        rootModuleId,
                        List.of(rootDataId),
                        parentToChildrenDataIdCombineMap,
                        rowChangeMap);
        return moduleChangeDTO;
    }

    /**
     * @param moduleTreeDTO
     * @param parentModuleId
     * @param parentDataId
     * @param currentModuleId
     * @param currentDataIds
     * @param parentToChildrenDataIdMap 父模块到子模块数据ID映射 1001_100--> 1002:(1,2,3),1003:(1,4,5)
     *     ,1004:(虚模块继承父ID 100) 1004_100--> 1005:(10,20,30),1006:(1,4,5) 1005_10-->
     *     1007:(11,22,33),1008:(1,4,5)
     * @param rowChangeMap
     * @return
     */
    private ModuleChangeDTO buildModuleChangeDTOListByModuleTree(
            ModuleTreeDTO moduleTreeDTO,
            Long parentModuleId,
            Long parentDataId,
            Long currentModuleId,
            List<Long> currentDataIds,
            Map<String, Map<Long, Set<Long>>> parentToChildrenDataIdMap,
            Map<Long, Map<Long, RowChangeDTO>> rowChangeMap) {

        // 当前模块不存在
        if (!moduleTreeDTO.getModuleMap().containsKey(currentModuleId)) {
            return null;
        }
        ModuleChangeDTO moduleChangeDTO = ModuleChangeDTO.builder().build();

        moduleChangeDTO.setMId(currentModuleId);
        moduleChangeDTO.setName(moduleTreeDTO.getModuleMap().get(currentModuleId).getModuleName());

        ActionChangeDTO actionChangeDTO = ActionChangeDTO.builder().build();

        // 这个地方 虚模块 不做特殊处理
        // 这是一个虚模块
        //
        // if(StringUtils.isEmpty(moduleTreeDTO.getModuleMap().get(currentModuleId).getPrimaryTable())){
        //            List<RowChangeDTO> rowChangeDTOList = new ArrayList<>();
        //            RowChangeDTO rowChangeDTO = RowChangeDTO.builder().build();
        //            rowChangeDTOList.add(rowChangeDTO);
        //            // 获取构建当前模块的子模块
        //
        // if(CollectionUtil.isNotEmpty(moduleTreeDTO.getParentToChildren().get(currentModuleId))){
        //                List<ModuleChangeDTO> children=new ArrayList<>();
        //                for(Long childModuleId :
        // moduleTreeDTO.getParentToChildren().get(currentModuleId)) {
        //
        //                    ModuleChangeDTO child=null;
        //                    // 子模是 个虚模块
        //
        // if(StringUtils.isEmpty(moduleTreeDTO.getModuleMap().get(childModuleId).getPrimaryTable())){
        //                        child=buildModuleChangeDTOListByModuleTree(moduleTreeDTO,
        // currentModuleId,parentDataId,childModuleId,currentDataIds,parentToChildrenDataIdMap,
        // rowChangeMap);
        //                    }else {
        //                        // 子模块不是 虚模块
        //                        // 需要 根据 parentId 构建 子模块的currentDataIds
        //                    }
        //                    if(child!=null){
        //                        children.add(child);
        //                    }
        //
        //                }
        //                rowChangeDTO.setChildren(children);
        //            }
        //            actionChangeDTO.setU(rowChangeDTOList);
        //            moduleChangeDTO.setActions(actionChangeDTO);
        //            return moduleChangeDTO;
        //        }

        // 当前模块没有数据,子模块也不用找了
        if (CollectionUtil.isEmpty(currentDataIds)) {
            List<RowChangeDTO> rowChangeDTOList = new ArrayList<>();
            RowChangeDTO rowChangeDTO = RowChangeDTO.builder().build();
            rowChangeDTOList.add(rowChangeDTO);
            actionChangeDTO.setU(rowChangeDTOList);
            moduleChangeDTO.setActions(actionChangeDTO);
            return moduleChangeDTO;
        }

        for (Long currentDataId : currentDataIds) {
            RowChangeDTO rowChangeDTO = new RowChangeDTO();

            // 当前是一个虚模块 不用构建数据
            if (StringUtils.isEmpty(
                    moduleTreeDTO.getModuleMap().get(currentModuleId).getPrimaryTable())) {
                if (actionChangeDTO.getU() == null) {
                    actionChangeDTO.setU(new ArrayList<>());
                }
                actionChangeDTO.getU().add(rowChangeDTO);
            } else {
                // 当前模块 当前数据行是否有变更数据
                RowChangeDTO rowChange =
                        rowChangeMap.getOrDefault(currentModuleId, Map.of()).get(currentDataId);
                // 当前行 没有变更数据
                if (rowChange == null
                        || StringUtils.isEmpty(rowChange.getAction())
                        || CollectionUtil.isEmpty(rowChange.getColumns())) {
                    if (actionChangeDTO.getU() == null) {
                        actionChangeDTO.setU(new ArrayList<>());
                    }
                    actionChangeDTO.getU().add(rowChangeDTO);
                } else {
                    // 当前行 变更数据
                    rowChangeDTO.setName(rowChange.getName());
                    rowChangeDTO.setColumns(rowChange.getColumns());
                    switch (rowChange.getAction()) {
                        case "i" -> {
                            if (actionChangeDTO.getI() == null) {
                                actionChangeDTO.setI(new ArrayList<>());
                            }
                            actionChangeDTO.getI().add(rowChangeDTO);
                        }
                        case "u" -> {
                            if (actionChangeDTO.getU() == null) {
                                actionChangeDTO.setU(new ArrayList<>());
                            }
                            actionChangeDTO.getU().add(rowChangeDTO);
                        }
                        case "d" -> {
                            if (actionChangeDTO.getD() == null) {
                                actionChangeDTO.setD(new ArrayList<>());
                            }
                            actionChangeDTO.getD().add(rowChangeDTO);
                        }
                    }
                }
            }

            // 获取构建当前模块的子模块
            List<ModuleChangeDTO> children = new ArrayList<>();
            if (CollectionUtil.isNotEmpty(
                    moduleTreeDTO.getParentToChildren().get(currentModuleId))) {
                for (Long childModuleId :
                        moduleTreeDTO.getParentToChildren().get(currentModuleId)) {

                    List<Long> childDataIdList = new ArrayList<>();
                    ModuleChangeDTO child = null;
                    //                    //虚模块没有数据行
                    //
                    // if(StringUtils.isEmpty(moduleTreeDTO.getModuleMap().get(childModuleId).getPrimaryTable())){
                    //
                    //                        // 虚模块 子数据 放入虚模块上级模块的数据ID
                    //                        //childDataIdList.add(currentDataId);
                    //
                    // child=buildModuleChangeDTOListByModuleTree(moduleTreeDTO,
                    // currentModuleId,currentDataId,childModuleId,childDataIdList,parentToChildrenDataIdMap, rowChangeMap);
                    //                    }else {
                    //                        // 不是虚模块直接找下级模块的 子数据
                    //                        // 获取当前模块 currentModuleId,当前数据行 currentDataId,的 子模块
                    // childModuleId 的子数据行
                    //                    }
                    // 虚模块能找到他的子数据
                    String key =
                            String.valueOf(currentModuleId) + "_" + String.valueOf(currentDataId);
                    Set<Long> childDataIds =
                            parentToChildrenDataIdMap
                                    .getOrDefault(key, Map.of())
                                    .getOrDefault(childModuleId, Set.of());
                    if (CollectionUtil.isNotEmpty(childDataIds)) {
                        childDataIdList.addAll(childDataIds);
                    }
                    child =
                            buildModuleChangeDTOListByModuleTree(
                                    moduleTreeDTO,
                                    currentModuleId,
                                    currentDataId,
                                    childModuleId,
                                    childDataIdList,
                                    parentToChildrenDataIdMap,
                                    rowChangeMap);

                    if (child != null) {
                        children.add(child);
                    }
                    rowChangeDTO.setChildren(children);
                }
            }
        }
        moduleChangeDTO.setActions(actionChangeDTO);
        return moduleChangeDTO;
    }

    private Map<String, Map<String, Object>> getRowMap(
            NestedModuleDataDTO nestedModuleDataDTO,
            Map<Long, SysModuleCombineInfo> moduleCacheMap) {
        Map<String, Map<String, Object>> rowMap = new HashMap<>();
        getRowMapRecursion(nestedModuleDataDTO, rowMap, moduleCacheMap);
        return rowMap;
    }

    private void getRowMapRecursion(
            NestedModuleDataDTO nestedModuleDataDTO,
            Map<String, Map<String, Object>> rowMap,
            Map<Long, SysModuleCombineInfo> moduleCacheMap) {
        SysModuleCombineInfo sysModuleInfo = moduleCacheMap.get(nestedModuleDataDTO.getModuleId());
        if (CollectionUtil.isNotEmpty(nestedModuleDataDTO.getRecord())) {
            for (RecordDataDTO recordDataDTO : nestedModuleDataDTO.getRecord()) {
                if (CollectionUtil.isNotEmpty(recordDataDTO.getRow())) {
                    Long dataId =
                            DataPersistenceUtil.getTableRowIdWithTabName(
                                    recordDataDTO.getRow(),
                                    sysModuleInfo.getModuleInfo().getPrimaryTable());
                    if (dataId != null) {
                        rowMap.put(
                                String.valueOf(nestedModuleDataDTO.getModuleId() + "_" + dataId),
                                recordDataDTO.getRow());
                    }
                }
                if (CollectionUtil.isNotEmpty(recordDataDTO.getChildren())) {
                    for (NestedModuleDataDTO nestData : recordDataDTO.getChildren()) {
                        getRowMapRecursion(nestData, rowMap, moduleCacheMap);
                    }
                }
            }
        }
    }

    private Map<String, Map<Long, Set<Long>>> getParentToChildrenDataIdMap(
            NestedModuleDataDTO nestedModuleDataDTO,
            Map<Long, SysModuleCombineInfo> moduleCacheMap) {
        Map<String, Map<Long, Set<Long>>> parentToChildrenDataIdMap = new HashMap<>();
        getParentToChildrenDataIdMapRecursion(
                nestedModuleDataDTO, moduleCacheMap, 0L, 0L, parentToChildrenDataIdMap);
        return parentToChildrenDataIdMap;
    }

    private void getParentToChildrenDataIdMapRecursion(
            NestedModuleDataDTO nestedModuleDataDTO,
            Map<Long, SysModuleCombineInfo> moduleCacheMap,
            Long parentModuleId,
            Long parentDataId,
            Map<String, Map<Long, Set<Long>>> parentToChildrenDataIdMap) {
        SysModuleCombineInfo sysModuleInfo = moduleCacheMap.get(nestedModuleDataDTO.getModuleId());
        if (CollectionUtil.isNotEmpty(nestedModuleDataDTO.getRecord())) {
            for (RecordDataDTO recordDataDTO : nestedModuleDataDTO.getRecord()) {

                // 虚 模块
                if (StringUtils.isEmpty(sysModuleInfo.getModuleInfo().getPrimaryTable())) {

                    Long dataId = parentDataId;
                    if (dataId != null) {
                        String key =
                                String.valueOf(parentModuleId) + "_" + String.valueOf(parentDataId);
                        parentToChildrenDataIdMap
                                .computeIfAbsent(key, k -> new HashMap<>())
                                .computeIfAbsent(
                                        nestedModuleDataDTO.getModuleId(), v -> new HashSet<>())
                                .add(dataId);
                    }

                    if (CollectionUtil.isNotEmpty(recordDataDTO.getChildren())) {
                        for (NestedModuleDataDTO nestData : recordDataDTO.getChildren()) {
                            getParentToChildrenDataIdMapRecursion(
                                    nestData,
                                    moduleCacheMap,
                                    nestedModuleDataDTO.getModuleId(),
                                    parentDataId,
                                    parentToChildrenDataIdMap);
                        }
                    }

                } else {
                    Long dataId = null;
                    if (CollectionUtil.isNotEmpty(recordDataDTO.getRow())) {
                        dataId =
                                DataPersistenceUtil.getTableRowIdWithTabName(
                                        recordDataDTO.getRow(),
                                        sysModuleInfo.getModuleInfo().getPrimaryTable());
                        if (dataId != null) {
                            String key =
                                    String.valueOf(parentModuleId)
                                            + "_"
                                            + String.valueOf(parentDataId);
                            parentToChildrenDataIdMap
                                    .computeIfAbsent(key, k -> new HashMap<>())
                                    .computeIfAbsent(
                                            nestedModuleDataDTO.getModuleId(), v -> new HashSet<>())
                                    .add(dataId);
                        }
                    }
                    if (CollectionUtil.isNotEmpty(recordDataDTO.getChildren())) {
                        for (NestedModuleDataDTO nestData : recordDataDTO.getChildren()) {
                            getParentToChildrenDataIdMapRecursion(
                                    nestData,
                                    moduleCacheMap,
                                    nestedModuleDataDTO.getModuleId(),
                                    dataId,
                                    parentToChildrenDataIdMap);
                        }
                    }
                }
            }
        }
    }

    private Map<String, Map<Long, Set<Long>>> combineParentToChildrenDataIdMap(
            Map<String, Map<Long, Set<Long>>> oldmap, Map<String, Map<Long, Set<Long>>> newmap) {
        for (Map.Entry<String, Map<Long, Set<Long>>> entry : newmap.entrySet()) {
            if (!newmap.containsKey(entry.getKey())) {
                newmap.computeIfAbsent(entry.getKey(), k -> new HashMap<>())
                        .putAll(entry.getValue());
            } else {
                for (Map.Entry<Long, Set<Long>> childEntry : entry.getValue().entrySet()) {
                    if (!newmap.get(entry.getKey()).containsKey(childEntry.getKey())) {
                        newmap.get(entry.getKey())
                                .computeIfAbsent(childEntry.getKey(), k -> new HashSet<>())
                                .addAll(childEntry.getValue());
                    } else {
                        newmap.get(entry.getKey())
                                .get(childEntry.getKey())
                                .addAll(childEntry.getValue());
                    }
                }
            }
        }
        return newmap;
    }

    private Map<Long, Map<Long, RowChangeDTO>> getRowChangeMap(
            Map<String, Map<String, Object>> oldRowMap,
            Map<String, Map<String, Object>> newRowMap,
            Map<Long, SysModuleCombineInfo> moduleCacheMap) {
        Map<Long, Map<Long, RowChangeDTO>> rowChangeMap = new HashMap<>();

        // 旧详情页中存在的
        for (Map.Entry<String, Map<String, Object>> entry : oldRowMap.entrySet()) {
            String[] keys = entry.getKey().split("_"); // Split the key by "_"
            Long moduleId = Long.parseLong(keys[0]);
            Long dataId = Long.parseLong(keys[1]);
            SysModuleCombineInfo sysModuleInfo = moduleCacheMap.get(moduleId);
            // 更新的行
            if (newRowMap.containsKey(entry.getKey())) {
                RowChangeDTO row =
                        getRowChangeDTO(
                                entry.getValue(),
                                newRowMap.get(entry.getKey()),
                                entry.getKey(),
                                sysModuleInfo,
                                "u");
                rowChangeMap.computeIfAbsent(moduleId, k -> new HashMap<>()).put(dataId, row);

            } else {
                // 删除的行
                RowChangeDTO row =
                        getRowChangeDTO(
                                entry.getValue(),
                                newRowMap.get(entry.getKey()),
                                entry.getKey(),
                                sysModuleInfo,
                                "d");
                rowChangeMap.computeIfAbsent(moduleId, k -> new HashMap<>()).put(dataId, row);
            }
        }
        // 新详情页中存在的
        for (Map.Entry<String, Map<String, Object>> entry : newRowMap.entrySet()) {
            String[] keys = entry.getKey().split("_"); // Split the key by "_"
            Long moduleId = Long.parseLong(keys[0]);
            Long dataId = Long.parseLong(keys[1]);
            SysModuleCombineInfo sysModuleInfo = moduleCacheMap.get(moduleId);
            // 新增的行
            if (!oldRowMap.containsKey(entry.getKey())) {
                RowChangeDTO row =
                        getRowChangeDTO(
                                null,
                                newRowMap.get(entry.getKey()),
                                entry.getKey(),
                                sysModuleInfo,
                                "i");
                rowChangeMap.computeIfAbsent(moduleId, k -> new HashMap<>()).put(dataId, row);
            }
        }
        return rowChangeMap;
    }

    private RowChangeDTO getRowChangeDTO(
            Map<String, Object> oldRowMap,
            Map<String, Object> newRowMap,
            String key,
            SysModuleCombineInfo sysModuleInfo,
            String action) {
        switch (action) {
            case "i" -> {
                RowChangeDTO rowChangeDTO =
                        DataPersistenceUtil.convertInsertRecordToRowChangeDTO(
                                DataPersistenceUtil.getTableRowMapFromWideRow(
                                        newRowMap, sysModuleInfo.getModuleInfo().getPrimaryTable()),
                                sysModuleInfo.getModuleInfo().getPrimaryTable(),
                                sysModuleInfo
                                        .getFieldMapByTable()
                                        .get(sysModuleInfo.getModuleInfo().getPrimaryTable()),
                                sysModuleInfo.getUniqueNameFields());
                List<RowChangeDTO> subTableChanges = new ArrayList<>();
                if (CollectionUtil.isNotEmpty(sysModuleInfo.getSubTables())) {
                    for (String table : sysModuleInfo.getSubTables()) {
                        RowChangeDTO rowChange =
                                DataPersistenceUtil.convertInsertRecordToRowChangeDTO(
                                        DataPersistenceUtil.getTableRowMapFromWideRow(
                                                newRowMap, table),
                                        table,
                                        sysModuleInfo.getFieldMapByTable().get(table),
                                        sysModuleInfo.getUniqueNameFields());
                        if (rowChange != null
                                && CollectionUtil.isNotEmpty(rowChange.getColumns())) {
                            subTableChanges.add(rowChangeDTO);
                        }
                    }
                }
                return DataPersistenceUtil.combineRowChanges(rowChangeDTO, subTableChanges);
            }
            case "u", "d" -> {
                RowChangeDTO rowChangeDTO =
                        DataPersistenceUtil.convertUpdateRecordToRowChangeDTO(
                                DataPersistenceUtil.getTableRowMapFromWideRow(
                                        oldRowMap, sysModuleInfo.getModuleInfo().getPrimaryTable()),
                                DataPersistenceUtil.getTableRowMapFromWideRow(
                                        newRowMap, sysModuleInfo.getModuleInfo().getPrimaryTable()),
                                sysModuleInfo.getModuleInfo().getPrimaryTable(),
                                sysModuleInfo
                                        .getFieldMapByTable()
                                        .get(sysModuleInfo.getModuleInfo().getPrimaryTable()),
                                sysModuleInfo.getUniqueNameFields(),
                                false);
                List<RowChangeDTO> subTableChanges = new ArrayList<>();
                if (CollectionUtil.isNotEmpty(sysModuleInfo.getSubTables())) {
                    for (String table : sysModuleInfo.getSubTables()) {
                        RowChangeDTO rowChange =
                                DataPersistenceUtil.convertUpdateRecordToRowChangeDTO(
                                        DataPersistenceUtil.getTableRowMapFromWideRow(
                                                oldRowMap, table),
                                        DataPersistenceUtil.getTableRowMapFromWideRow(
                                                newRowMap, table),
                                        table,
                                        sysModuleInfo.getFieldMapByTable().get(table),
                                        sysModuleInfo.getUniqueNameFields(),
                                        action.equals("d"));
                        if (rowChange != null
                                && CollectionUtil.isNotEmpty(rowChange.getColumns())) {
                            subTableChanges.add(rowChangeDTO);
                        }
                    }
                }
                return DataPersistenceUtil.combineRowChanges(rowChangeDTO, subTableChanges);
            }
        }
        return null;
    }
}
