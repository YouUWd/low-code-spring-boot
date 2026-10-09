package com.jdec.platform.dataengine.biz.service;

import com.jdec.platform.dataengine.biz.dto.SysModuleCombineInfo;
import com.jdec.platform.dataengine.biz.dto.context.ModuleQueryContext;
import java.util.Map;

public interface ModuleMetaDataService {
    Map<Long, SysModuleCombineInfo> getModuleMetaDataMap(ModuleQueryContext moduleQueryContext);

    Map<Long, SysModuleCombineInfo> getModuleMetaDataMapAsync(
            ModuleQueryContext moduleQueryContext);
}
