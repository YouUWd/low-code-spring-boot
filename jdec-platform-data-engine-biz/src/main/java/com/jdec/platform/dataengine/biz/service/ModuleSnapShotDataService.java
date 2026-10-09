package com.jdec.platform.dataengine.biz.service;

import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.biz.dto.context.ModuleSnapShotDataContext;

public interface ModuleSnapShotDataService {

    public Long saveModuleDataPermanentSnapshot(ModuleSnapShotDataContext context);

    public NestedModuleDataDTO getLastStatusModuleDataTempSnapshot(
            ModuleSnapShotDataContext context);

    public Long saveModuleDataTempSnapshot(ModuleSnapShotDataContext context);

    public Long saveModuleFieldDataSnapshot(ModuleSnapShotDataContext context);
}
