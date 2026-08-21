package com.jdec.platform.engine.api;

import com.jdec.platform.engine.api.dto.request.DataQueryReq;
import com.jdec.platform.engine.api.dto.request.DataUpdateReq;
import com.jdec.platform.engine.api.dto.response.DataDetailResp;
import com.jdec.platform.engine.api.dto.response.DataPageResp;

public interface DataEngineApi {
    DataPageResp listData(String moduleCode, DataQueryReq request);

    DataDetailResp getDataDetail(String moduleCode, Long id);

    void updateData(String moduleCode, Long id, DataUpdateReq request);
}
