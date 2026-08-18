package com.jdec.platform.common_business.biz.service;

import com.jdec.platform.common_business.biz.dto.ApprovalMsgDTO;

public interface SystemModuleApprovalMsgService {

    public Boolean processApprovalMsg(ApprovalMsgDTO approvalMsgDTO);
}
