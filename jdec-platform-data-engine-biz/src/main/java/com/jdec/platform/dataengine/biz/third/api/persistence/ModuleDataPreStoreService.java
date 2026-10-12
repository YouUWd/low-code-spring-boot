package com.jdec.platform.dataengine.biz.third.api.persistence;

import com.jdec.platform.dataengine.biz.dto.ModuleDataStoreDTO;

public interface ModuleDataPreStoreService {

    Object preProcess(ModuleDataStoreDTO dataStoreDTO);
}
