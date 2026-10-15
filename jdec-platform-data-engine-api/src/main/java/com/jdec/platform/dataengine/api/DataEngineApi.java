package com.jdec.platform.dataengine.api;

import com.jdec.platform.dataengine.api.dto.request.DynamicQueryReq;
import com.jdec.platform.dataengine.api.dto.request.DynamicSaveReq;
import com.jdec.platform.dataengine.api.dto.response.DynamicDetailResp;
import com.jdec.platform.dataengine.api.dto.response.DynamicQueryResp;

public interface DataEngineApi {
    DynamicQueryResp query(DynamicQueryReq req);

    DynamicDetailResp getDetail(Long moduleId, Long id);

    Long save(DynamicSaveReq req);
}
