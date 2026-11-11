package com.jdec.platform.externalobject.biz.service.postprocess;

import com.jdec.platform.dataengine.api.DataSavePostApi;
import com.jdec.platform.dataengine.api.dto.model.DataPostParamDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ExternalObjectDataPostProcessService implements DataSavePostApi {

    private static final String PROJECT_NO = "company";

    @Override
    public Boolean isApplicableSystem(DataPostParamDTO dataPostParamDTO) {
        return PROJECT_NO.equals(dataPostParamDTO.getProjectNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void postProcess(DataPostParamDTO dataPostParamDTO) {}
}
