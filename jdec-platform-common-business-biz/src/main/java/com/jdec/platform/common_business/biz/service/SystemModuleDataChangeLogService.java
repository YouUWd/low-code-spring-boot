package com.jdec.platform.common_business.biz.service;

import com.jdec.platform.common_business.api.dto.common.ModuleDataChangeDTO;
import com.jdec.platform.common_business.biz.change.ModuleDataChangeContext;
import com.jdec.platform.common_business.biz.entity.TableChangeLog;
import java.util.List;

public interface SystemModuleDataChangeLogService {

    public TableChangeLog saveLog(ModuleDataChangeContext context);

    public List<ModuleDataChangeDTO> compareOutChangeLog(ModuleDataChangeContext context);
}
