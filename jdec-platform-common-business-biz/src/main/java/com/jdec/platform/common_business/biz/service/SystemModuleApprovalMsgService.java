package com.jdec.platform.common_business.biz.service;

import com.jdec.platform.common_business.api.dto.common.ModuleFilterInfo;
import com.jdec.platform.common_business.api.dto.request.ModuleSaveReq;
import java.util.List;
import java.util.Map;

public interface SystemModuleApprovalMsgService {

    public Boolean processApprovalMsg(
            Long subjectId,
            List<Map<String, Object>> processResult,
            ModuleFilterInfo moduleFilterInfo,
            List<ModuleSaveReq> moduleSaveReqList);
}
