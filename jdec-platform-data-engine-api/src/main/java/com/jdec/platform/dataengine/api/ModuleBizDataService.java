package com.jdec.platform.dataengine.api;

import com.jdec.platform.dataengine.api.dto.model.ModuleDetailQueryContext;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;

public interface ModuleBizDataService {

    NestedModuleDataDTO getModuleDetailDataAsync(ModuleDetailQueryContext queryContext);

    NestedModuleDataDTO getModuleDetailData(ModuleDetailQueryContext queryContext);
}
