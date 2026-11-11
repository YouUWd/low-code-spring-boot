package com.jdec.platform.dataengine.api;

import com.jdec.platform.dataengine.api.dto.model.DataPostParamDTO;

public interface DataSavePostApi {

    Boolean isApplicableSystem(DataPostParamDTO dataPostParamDTO);

    void postProcess(DataPostParamDTO dataPostParamDTO);
}
