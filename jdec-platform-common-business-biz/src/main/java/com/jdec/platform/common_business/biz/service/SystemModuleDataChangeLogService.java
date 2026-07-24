package com.jdec.platform.common_business.biz.service;

import com.jdec.platform.common_business.biz.change.ModuleDataChangeContext;
import com.jdec.platform.common_business.biz.change.OperationChangeDTO;
import com.jdec.platform.common_business.biz.entity.TableChangeLog;

public interface SystemModuleDataChangeLogService {

    public TableChangeLog saveLog(ModuleDataChangeContext context);

    public String getLogContentHtml(OperationChangeDTO operationChangeDTO);
}
