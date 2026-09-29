package com.jdec.platform.dataengine.biz.third.api.persistence;

import com.jdec.platform.dataengine.api.dto.request.ApprovalStatusCallBackReq;
import com.jdec.platform.dataengine.api.dto.response.ApprovalProcessResultResp;

public interface ModuleDataApprovalBizService {

    ApprovalProcessResultResp processApprovalStatus(ApprovalStatusCallBackReq req);
}
