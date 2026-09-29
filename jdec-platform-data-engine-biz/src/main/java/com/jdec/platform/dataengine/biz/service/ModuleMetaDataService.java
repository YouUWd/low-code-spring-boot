package com.jdec.platform.dataengine.biz.service;

import com.jdec.platform.dataengine.api.dto.model.SysModuleInfo;
import com.jdec.platform.dataengine.biz.dto.context.ModuleQueryContext;
import java.util.Map;

public interface ModuleMetaDataService {
    Map<Long, SysModuleInfo> getModuleMetaDataMap(ModuleQueryContext moduleQueryContext);
}
