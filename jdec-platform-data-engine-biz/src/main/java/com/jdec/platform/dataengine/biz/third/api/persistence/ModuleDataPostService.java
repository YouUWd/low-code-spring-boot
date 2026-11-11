package com.jdec.platform.dataengine.biz.third.api.persistence;

import com.jdec.platform.dataengine.api.dto.model.DataPostParamDTO;

public interface ModuleDataPostService {
    void postProcessData(DataPostParamDTO dataPostParamDTO);
}
