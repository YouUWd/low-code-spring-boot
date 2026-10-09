package com.jdec.platform.dataengine.biz.service;

import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.biz.dto.context.ModuleDetailQueryContext;

public interface ModuleBizDataService {

    NestedModuleDataDTO getModuleDetailDataAsync(ModuleDetailQueryContext queryContext);

    NestedModuleDataDTO getModuleDetailData(ModuleDetailQueryContext queryContext);
}
