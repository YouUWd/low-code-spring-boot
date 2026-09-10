package com.jdec.platform.approval.api;

import com.jdec.platform.approval.api.dto.ApprovalProcessDTO;

/** 异步任务服务接口 */
public interface AsyncTaskService {

    /**
     * 异步发送企业微信消息
     *
     * @param projectNo 项目编号（用于数据源切换）
     * @param approvalProcessDTO 审批流程内容
     * @param msgType 消息类型 （1 触发消息 2 模板消息）
     * @param triggerType 触发类型
     * @param templateId 模板ID
     */
    void sendWechatMessageAsync(
            String projectNo,
            ApprovalProcessDTO approvalProcessDTO,
            Integer msgType,
            String triggerType,
            Long templateId);

    /**
     * 异步记录审计日志（带事务）
     *
     * @param projectNo 项目编号
     * @param approvalProcessDTO 审批流程内容
     */
    void saveAuditLogAsync(String projectNo, ApprovalProcessDTO approvalProcessDTO);
}
