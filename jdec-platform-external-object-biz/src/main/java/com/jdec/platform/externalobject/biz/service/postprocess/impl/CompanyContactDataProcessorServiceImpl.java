package com.jdec.platform.externalobject.biz.service.postprocess.impl;

import com.jdec.platform.dataengine.api.dto.model.DataPostParamDTO;
import com.jdec.platform.externalobject.biz.service.postprocess.CustomizedDataProcessorService;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CompanyContactDataProcessorServiceImpl implements CustomizedDataProcessorService {

    private final Set<String> ROOT_MODULE_CODES = Set.of("WBDX", "COMPANY-ROOT");

    @Override
    public void process(DataPostParamDTO dataPostParamDTO) {}

    @Override
    public Boolean isApplicable(DataPostParamDTO dataPostParamDTO) {
        return ROOT_MODULE_CODES.contains(dataPostParamDTO.getRootModuleCode());
    }
}
