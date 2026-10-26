package com.jdec.platform.common.api;

import com.jdec.platform.dataengine.api.dto.request.DynamicSaveReq;

/** 公共业务保存 API */
public interface CommonBusinessApi {

    /**
     * 保存业务数据（含审批链处理）。
     *
     * @param req 动态主子表保存请求
     * @return 业务数据 ID
     */
    Long businessSave(DynamicSaveReq req);
}
