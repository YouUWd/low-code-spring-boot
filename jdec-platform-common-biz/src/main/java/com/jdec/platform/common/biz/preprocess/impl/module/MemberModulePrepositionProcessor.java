package com.jdec.platform.common.biz.preprocess.impl.module;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.common.biz.preprocess.ModuleDataPrepositionProcessor;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.dataengine.api.DataEngineApi;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class MemberModulePrepositionProcessor implements ModuleDataPrepositionProcessor {

    private final DataEngineApi dataEngineApi;

    private final Set<String> MODULE_CODE = Set.of("COMPANY-MEMBER", "W_RYGC");

    @Override
    public void preProcess(NestedModuleDataDTO nestedModuleDataDTO, ModuleTreeDTO moduleTreeDTO) {
        log.info("preProcess member module  param data");

        List<RecordDataDTO> recordDataDTOList =
                nestedModuleDataDTO.getRecord() != null
                        ? nestedModuleDataDTO.getRecord()
                        : Collections.emptyList();
        for (RecordDataDTO recordDataDTO : recordDataDTOList) {
            if (CollectionUtil.isEmpty(recordDataDTO.getRow())) {
                continue;
            }
            Object id = recordDataDTO.getRow().get("member.id");
            Object code = recordDataDTO.getRow().get("member.code");
            // 新增数据
            if ((id == null || (id instanceof Number && ((Number) id).longValue() <= 0))
                    && (code == null || StringUtils.isEmpty((String) code))) {
                recordDataDTO.getRow().put("member.code", dataEngineApi.getNextDeptNo());
            }
        }
    }

    @Override
    public Boolean isApplicable(Long moduleId, ModuleTreeDTO moduleTreeDTO) {
        return MODULE_CODE.contains(moduleTreeDTO.getModuleMap().get(moduleId).getModuleCode());
    }

    @Override
    public Set<String> getModuleCode() {
        return MODULE_CODE;
    }
}
