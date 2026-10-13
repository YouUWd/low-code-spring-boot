package com.jdec.platform.dataengine.biz.third.api.persistence;

import com.jdec.platform.dataengine.api.dto.request.DynamicDeleteReq;
import com.jdec.platform.dataengine.api.dto.request.DynamicInsertReq;
import com.jdec.platform.dataengine.biz.dto.ModuleDataStoreDTO;

public interface ModuleDataPersistenceService {

    public Long save(ModuleDataStoreDTO req);

    Long insertAndGetId(DynamicInsertReq req);

    Long deleteById(DynamicDeleteReq req);
}
