package com.jdec.platform.common_business.biz.service;

import com.jdec.platform.common_business.api.dto.common.ModuleDataChangeDTO;
import com.jdec.platform.common_business.biz.change.ModuleDataChangeContext;
import java.util.List;

public interface SystemModuleDataChangeLogService {

    public Boolean saveLog(ModuleDataChangeContext context);

    public List<ModuleDataChangeDTO> compareOutChangeLog(ModuleDataChangeContext context);
}
