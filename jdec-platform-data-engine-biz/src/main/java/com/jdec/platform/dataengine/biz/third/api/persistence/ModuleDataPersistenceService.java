package com.jdec.platform.dataengine.biz.third.api.persistence;

import com.jdec.platform.dataengine.api.dto.request.DynamicDeleteReq;
import com.jdec.platform.dataengine.api.dto.request.DynamicInsertReq;
import com.jdec.platform.dataengine.api.dto.request.DynamicSaveReq;

public interface ModuleDataPersistenceService {

    public Long save(DynamicSaveReq req);

    Long insertAndGetId(DynamicInsertReq req);

    Long deleteById(DynamicDeleteReq req);
}
