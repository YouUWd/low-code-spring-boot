package com.jdec.platform.approval.api;

import com.jdec.platform.approval.api.req.ApprovalDataReq;
import com.jdec.platform.approval.api.req.ApprovalListReq;
import com.jdec.platform.approval.api.req.ApprovalProcessReq;
import java.util.List;
import java.util.Map;

public interface IApprovalEngine {
    // 根据用户ID和角色ID查询用户指定业务的代办，已办，全部数量------------------------------------------------
    List<Map<String, Object>> getBusinessTypeList(ApprovalListReq approvalProcessDTO);

    // 根据用户ID和角色ID查询用户指定业务的代办，已办，全部列表------------------------------------------------
    List<Map<String, Object>> getBusinessList(ApprovalListReq approvalListReq);

    // 根据菜单ID查询用户指定业务的代办，已办，全部数量-------------------------------------------------
    List<Map<String, Object>> getListNumber(Long moduleId);

    // 根据菜单ID及类型查询用户指定业务的代办，已办，全部列表ID------------------------------------------------
    List<Map<String, Object>> getListButton(Long moduleId, Integer approvalType);

    // 根据用户ID和角色ID查询用户指定业务的下一单ID------------------------------------------------
    Long getBusinessNextId(Long moduleId);

    // 查询单据审批详细数据-旧---------------------------------------------------------------------------
    Map<String, Object> getApprovalDetail(ApprovalProcessReq req);

    // 查询单据审批详细数据---------------------------------------------------------------------------
    Map<String, Object> getApprovalData(Long moduleId, Long recordId, String button);

    // 查询已经审批的步骤
    List<Map<String, Object>> getApprovalStep(ApprovalDataReq req);
}
