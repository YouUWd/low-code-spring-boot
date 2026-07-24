package com.jdec.platform.approval.api;

import com.jdec.platform.approval.api.req.ApprovalDataReq;
import com.jdec.platform.approval.api.req.ApprovalListReq;
import com.jdec.platform.approval.api.req.ApprovalProcessReq;
import com.jdec.platform.approval.api.req.MessageReceiverReq;
import com.jdec.platform.approval.api.req.SaveBusinessIdReq;
import com.jdec.platform.approval.api.resp.ActionLogResp;
import java.util.List;
import java.util.Map;

public interface IApprovalEngine {
    // 根据用户ID和角色ID查询用户指定业务的代办，已办，全部数量------------------------------------------------
    Map<String, Object> getBusinessTypeList(ApprovalProcessReq approvalProcessDTO);

    // 根据用户ID和角色ID查询用户指定业务的代办，已办，全部列表------------------------------------------------
    List<Map<String, Object>> getBusinessList(ApprovalListReq approvalListReq);

    // 处理业务数据的审批流转---------------------------------------------------------------------
    Map<String, Object> process(ApprovalProcessReq approvalProcessDTO);

    // 更新单据真实业务ID---------------------------------------------------------------------------
    Map<String, Object> saveBusinessId(SaveBusinessIdReq req);

    // 查询单据审批详细数据---------------------------------------------------------------------------
    Map<String, Object> getApprovalDetail(ApprovalProcessReq req);

    // 查询单据审批操作日志
    List<ActionLogResp> getActionLog(ApprovalDataReq req);

    // 查询单据对应版本号和数据ID
    Map<String, Object> getApprovalChangeData(ApprovalDataReq req);

    // 查询消息接收人
    List<Map<String, Object>> getMessageReceiver(MessageReceiverReq req);

    // 查询已经审批的步骤
    List<Map<String, Object>> getApprovalStep(ApprovalDataReq req);
}
