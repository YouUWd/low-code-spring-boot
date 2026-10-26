package com.jdec.platform.approval.api;

import com.jdec.platform.approval.api.dto.ApprovalIdButton;
import com.jdec.platform.approval.api.event.ApprovalEvent;
import com.jdec.platform.approval.api.req.ApprovalListReq;
import com.jdec.platform.approval.api.req.ApprovalProcessReq;
import com.jdec.platform.approval.api.resp.ApprovalIdButtonListResp;
import java.util.List;
import java.util.Map;

public interface IApprovalEngine {
    // 根据用户ID和角色ID查询用户指定业务的代办，已办，全部数量------------------------------------------------
    List<Map<String, Object>> getBusinessTypeList(ApprovalListReq approvalProcessDTO);

    // 根据用户ID和角色ID查询用户指定业务的代办，已办，全部列表------------------------------------------------
    List<ApprovalIdButton> getBusinessList(ApprovalListReq approvalListReq);

    // 根据模块ID查询用户指定业务的代办，已办，全部数量-------------------------------------------------
    List<Map<String, Object>> getListNumber(Long moduleId);

    // 根据模块ID及类型查询用户指定业务的代办，已办，全部列表ID------------------------------------------------
    List<ApprovalIdButton> getListButton(Long moduleId, Integer approvalType);

    // 根据模块ID及类型查询用户指定业务的代办，已办，全部列表ID
    ApprovalIdButtonListResp getApprovalListButton(Long moduleId);

    // 根据用户ID和角色ID查询用户指定业务的下一单ID------------------------------------------------
    Long getBusinessNextId(Long moduleId);

    // 查询单据审批详细数据-旧---------------------------------------------------------------------------
    Map<String, Object> getApprovalDetail(ApprovalProcessReq req);

    // 查询单据审批详细数据---------------------------------------------------------------------------
    Map<String, Object> getApprovalData(Long moduleId, Long recordId, String button);

    // 查询已经审批的步骤
    List<Map<String, Object>> getApprovalStep(Long moduleId, Long recordId);

    // 保存或者提交单据处理
    void handleApproval(ApprovalEvent event);

    // 查询当前步骤是否可编辑
    boolean getCanEdit(Long moduleId, Long recordId, String btnType);

    // 查询单据指定版本是否草稿状态
    boolean getApprovalChainStatus(Long moduleId, Long recordId);
}
