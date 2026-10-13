package com.jdec.platform.dataengine.biz.third.api.persistence.impl;

import com.jdec.platform.dataengine.biz.dto.ModuleDataStoreDTO;
import com.jdec.platform.dataengine.biz.dto.ModuleTreeNodeDataDTO;
import com.jdec.platform.dataengine.biz.enums.SaveType;
import com.jdec.platform.dataengine.biz.third.api.persistence.ModuleDataPreStoreService;
import com.jdec.platform.dataengine.biz.third.api.persistence.ModuleTreeDataUtils;
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
            ModuleTreeNodeDataDTO detailData =
                    ModuleTreeDataUtils.copyToModuleTreeNodeData(dataStoreDTO.getDetailData());
            ModuleTreeNodeDataDTO paramData =
                    ModuleTreeDataUtils.copyToModuleTreeNodeData(dataStoreDTO.getParamData());
            moduleTreeNodeDataDTO =
                    ModuleTreeDataUtils.mergeModuleTreeNodeData(detailData, paramData);

        } else {
            // 非变更保存
            // 全局插入数据
            if (dataStoreDTO.getIsOverallInsert()) {
                moduleTreeNodeDataDTO =
                        ModuleTreeDataUtils.copyToModuleTreeNodeData(dataStoreDTO.getParamData());

            } else {
                // 全局更新数据
                moduleTreeNodeDataDTO =
                        ModuleTreeDataUtils.copyToModuleTreeNodeData(dataStoreDTO.getParamData());
            }
        }
        return moduleTreeNodeDataDTO;
    }
}
