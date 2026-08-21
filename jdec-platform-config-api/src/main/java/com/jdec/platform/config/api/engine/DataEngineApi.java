package com.jdec.platform.config.api.engine;

import com.jdec.platform.config.api.engine.dto.request.DataQueryReq;
import com.jdec.platform.config.api.engine.dto.request.DataUpdateReq;
import com.jdec.platform.config.api.engine.dto.response.DataDetailResp;
import com.jdec.platform.config.api.engine.dto.response.DataPageResp;

public interface DataEngineApi {
    DataPageResp listData(String moduleCode, DataQueryReq request);

    DataDetailResp getDataDetail(String moduleCode, Long id);

    void updateData(String moduleCode, Long id, DataUpdateReq request);
}
