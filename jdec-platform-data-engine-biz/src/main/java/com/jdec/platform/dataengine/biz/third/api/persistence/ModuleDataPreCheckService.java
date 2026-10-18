package com.jdec.platform.dataengine.biz.third.api.persistence;

import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.biz.dto.SysModuleCombineInfo;
import java.util.Map;

public interface ModuleDataPreCheckService {

    Boolean prePermissionCheck(
            NestedModuleDataDTO param,
            NestedModuleDataDTO base,
            Boolean isOverAllInsert,
            Map<Long, SysModuleCombineInfo> moduleCacheMap);
}
