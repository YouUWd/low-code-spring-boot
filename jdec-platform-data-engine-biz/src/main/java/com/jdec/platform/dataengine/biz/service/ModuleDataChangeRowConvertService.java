package com.jdec.platform.dataengine.biz.service;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.dataengine.api.dto.model.*;
import com.jdec.platform.dataengine.biz.third.api.persistence.DataPersistenceUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModuleDataChangeRowConvertService {

    public ModuleChangeDTO covertByModuleDataChangeRow(
            NestedModuleDataDTO nestedModuleDataDTO,
            Map<Long, Map<Long, RowChangeDTO>> changeMap,
            Map<Long, SysModuleInfo> moduleCacheMap) {
        return null;
    }

    private ModuleChangeDTO buildModuleChangeDTOList(
            NestedModuleDataDTO nestedModuleDataDTO,
            Map<Long, Map<Long, RowChangeDTO>> changeMap,
            Map<Long, SysModuleInfo> moduleCacheMap) {

        if (nestedModuleDataDTO == null || nestedModuleDataDTO.getModuleId() == null) {
            return null;
        }
        SysModuleInfo sysModuleInfo = moduleCacheMap.get(nestedModuleDataDTO.getModuleId());
        ModuleChangeDTO moduleChangeDTO = ModuleChangeDTO.builder().build();
        moduleChangeDTO.setMId(nestedModuleDataDTO.getModuleId());
        moduleChangeDTO.setName(sysModuleInfo != null ? sysModuleInfo.getModuleName() : null);

        // 模块没有数据 直接返回结构
        if (CollectionUtil.isEmpty(nestedModuleDataDTO.getRecord())) {
            return moduleChangeDTO;
        }

        ActionChangeDTO actionChangeDTO = ActionChangeDTO.builder().build();

        // 模块的多行数据
        for (RecordDataDTO recordDataDTO : nestedModuleDataDTO.getRecord()) {
            Long dataId = DataPersistenceUtil.getTableRowId(recordDataDTO.getRow());
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
            Map<Long, SysModuleInfo> moduleCacheMap) {

        List<ModuleChangeDTO> moduleChangeDTOList = new ArrayList<>();
        for (NestedModuleDataDTO nestedModuleDataDTO : nestedModuleDataDTOList) {
            ModuleChangeDTO moduleChangeDTO =
                    buildModuleChangeDTOList(nestedModuleDataDTO, changeMap, moduleCacheMap);
            if (moduleChangeDTO != null) {
                moduleChangeDTOList.add(moduleChangeDTO);
            }
        }
        return moduleChangeDTOList;
    }

    public ModuleChangeDTO covertByModuleDataDiff(
            NestedModuleDataDTO oldData, NestedModuleDataDTO newData) {
        return null;
    }
}
