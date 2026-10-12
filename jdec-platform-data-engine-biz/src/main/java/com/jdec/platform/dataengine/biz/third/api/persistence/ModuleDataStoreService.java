package com.jdec.platform.dataengine.biz.third.api.persistence;

import com.jdec.platform.dataengine.api.dto.request.DynamicSaveReq;

// 数据存储服务
public interface ModuleDataStoreService {

    /**
     * 保存前端提交过来的数据
     *
     * @param req
     * @return 更模块数据ID
     */
    Long save(DynamicSaveReq req);
}
