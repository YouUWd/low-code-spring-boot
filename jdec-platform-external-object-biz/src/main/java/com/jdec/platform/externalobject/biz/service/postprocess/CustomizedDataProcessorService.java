package com.jdec.platform.externalobject.biz.service.postprocess;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.dataengine.api.dto.model.DataPostParamDTO;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import java.util.HashSet;
import java.util.Set;

public interface CustomizedDataProcessorService {

    void process(DataPostParamDTO dataPostParamDTO);

    Boolean isApplicable(DataPostParamDTO dataPostParamDTO);

    default Set<Long> collectModifyModuleIds(NestedModuleDataDTO nestedModuleDataDTO) {
        Set<Long> set = new HashSet<>();
        collectModifyModuleIdsRecursive(nestedModuleDataDTO, set);
        return set;
    }

    default void collectModifyModuleIdsRecursive(NestedModuleDataDTO dataDTO, Set<Long> set) {
        if (dataDTO == null) {
            return;
        }
        if (dataDTO.getModuleId() != null) {
            set.add(dataDTO.getModuleId());
        }
        if (CollectionUtil.isNotEmpty(dataDTO.getRecord())) {
            for (RecordDataDTO recordDataDTO : dataDTO.getRecord()) {
                if (recordDataDTO.getChildren() != null) {
                    for (NestedModuleDataDTO child : recordDataDTO.getChildren()) {
                        collectModifyModuleIdsRecursive(child, set);
                    }
                }
            }
        }
    }
}
