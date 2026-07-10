package com.jdec.platform.approval.api;

import com.jdec.platform.approval.api.req.ApprovalProcessReq;
import java.util.Map;

public interface IApprovalEngine {
    // 根据用户ID和角色ID查询用户指定业务的代办，已办，全部数量------------------------------------------------
    Map<String, Object> getBusinessTypeList(ApprovalProcessReq approvalProcessDTO);

    // 处理业务数据的审批流转---------------------------------------------------------------------
    Map<String, Object> process(ApprovalProcessReq approvalProcessDTO);

    // 查询单据审批详细数据---------------------------------------------------------------------------
    Map<String, Object> getApprovalDetail(ApprovalProcessReq req);
}
