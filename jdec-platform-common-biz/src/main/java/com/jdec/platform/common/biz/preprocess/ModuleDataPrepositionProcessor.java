package com.jdec.platform.common.biz.preprocess;

import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import java.util.Set;

public interface ModuleDataPrepositionProcessor {

    public void preProcess(NestedModuleDataDTO nestedModuleDataDTO, ModuleTreeDTO moduleTreeDTO);

    public Boolean isApplicable(Long moduleId, ModuleTreeDTO moduleTreeDTO);

    public Set<String> getModuleCode();
}
