package com.jdec.platform.dataengine.biz.third.api.persistence.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.dataengine.biz.dto.ModuleDataStoreDTO;
import com.jdec.platform.dataengine.biz.dto.ModuleRowNodeDataDTO;
import com.jdec.platform.dataengine.biz.dto.ModuleTreeNodeDataDTO;
import com.jdec.platform.dataengine.biz.dto.SysModuleCombineInfo;
import com.jdec.platform.dataengine.biz.enums.SaveType;
import com.jdec.platform.dataengine.biz.third.api.persistence.ModuleDataPreStoreService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModuleDataPreStoreServiceImpl implements ModuleDataPreStoreService {
    @Override
    public Object preProcess(ModuleDataStoreDTO dataStoreDTO) {

        ModuleTreeNodeDataDTO moduleTreeNodeDataDTO = null;

        // 变更保存
        if (dataStoreDTO.getSaveType() == SaveType.APPROVAL_CHANGE) {

        } else {
            // 非变更保存

            // 全局插入数据
            if (dataStoreDTO.getIsOverallInsert()) {
                moduleTreeNodeDataDTO =
                        convertToModuleTreeNodeDataDTOByNew(dataStoreDTO.getParamData());

            } else {
                // 全局更新数据

            }
        }
        return null;
    }

    /**
     * 根据 新增数据 构建模块树节点数据
     *
     * @param paramData
     * @return
     */
    private ModuleTreeNodeDataDTO convertToModuleTreeNodeDataDTOByNew(
            NestedModuleDataDTO paramData) {
        ModuleTreeNodeDataDTO moduleTreeNodeDataDTO = new ModuleTreeNodeDataDTO();
        moduleTreeNodeDataDTO.setModuleId(paramData.getModuleId());

        List<ModuleRowNodeDataDTO> records = new ArrayList<>(paramData.getRecord().size());

        for (RecordDataDTO recordDataDTO : paramData.getRecord()) {
            ModuleRowNodeDataDTO moduleRowNodeDataDTO = new ModuleRowNodeDataDTO();
            Map<String, Object> row = new HashMap<>(recordDataDTO.getRow());
            if (CollectionUtil.isNotEmpty(recordDataDTO.getRow())) {
                row.putAll(recordDataDTO.getRow());
            }
            moduleRowNodeDataDTO.setRow(row);
            records.add(moduleRowNodeDataDTO);
            if (CollectionUtil.isNotEmpty(recordDataDTO.getChildren())) {
                List<ModuleTreeNodeDataDTO> children =
                        new ArrayList<>(recordDataDTO.getChildren().size());
                for (NestedModuleDataDTO child : recordDataDTO.getChildren()) {
                    ModuleTreeNodeDataDTO childModuleTreeNodeDataDTO =
                            convertToModuleTreeNodeDataDTOByNew(child);
                    children.add(childModuleTreeNodeDataDTO);
                }
                moduleRowNodeDataDTO.setChildren(children);
            }
        }
        moduleTreeNodeDataDTO.setRecords(records);
        return moduleTreeNodeDataDTO;
    }

    /**
     * 根据 更新数据 构建模块树节点数据
     *
     * @param paramData
     * @return
     */
    private ModuleTreeNodeDataDTO convertToModuleTreeNodeDataDTOByUpdate(
            NestedModuleDataDTO paramData,
            NestedModuleDataDTO detailData,
            Map<Long, SysModuleCombineInfo> moduleCacheMap) {
        ModuleTreeNodeDataDTO moduleTreeNodeDataDTO = new ModuleTreeNodeDataDTO();
        moduleTreeNodeDataDTO.setModuleId(detailData.getModuleId());

        List<ModuleRowNodeDataDTO> records = new ArrayList<>(detailData.getRecord().size());

        for (RecordDataDTO recordDataDTO : paramData.getRecord()) {
            ModuleRowNodeDataDTO moduleRowNodeDataDTO = new ModuleRowNodeDataDTO();
            Map<String, Object> row = new HashMap<>(recordDataDTO.getRow());
            if (CollectionUtil.isNotEmpty(recordDataDTO.getRow())) {
                row.putAll(recordDataDTO.getRow());
            }
            moduleRowNodeDataDTO.setRow(row);
            records.add(moduleRowNodeDataDTO);
            if (CollectionUtil.isNotEmpty(recordDataDTO.getChildren())) {
                List<ModuleTreeNodeDataDTO> children =
                        new ArrayList<>(recordDataDTO.getChildren().size());
                for (NestedModuleDataDTO child : recordDataDTO.getChildren()) {
                    ModuleTreeNodeDataDTO childModuleTreeNodeDataDTO =
                            convertToModuleTreeNodeDataDTOByNew(child);
                    children.add(childModuleTreeNodeDataDTO);
                }
                moduleRowNodeDataDTO.setChildren(children);
            }
        }
        moduleTreeNodeDataDTO.setRecords(records);
        return moduleTreeNodeDataDTO;
    }
}
