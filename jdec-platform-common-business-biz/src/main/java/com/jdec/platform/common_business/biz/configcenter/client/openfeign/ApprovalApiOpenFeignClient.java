package com.jdec.platform.common_business.biz.configcenter.client.openfeign;

import com.jdec.platform.approval.api.req.*;
import com.jdec.platform.approval.api.resp.ActionLogResp;
import com.jdec.platform.common_business.biz.dto.configcenter.response.ConfigApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "approval-biz",
        url = "${config.center.base}",
        path = "/api/data/approval",
        configuration = FeignConfig.class)
public interface ApprovalApiOpenFeignClient {

    // 根据用户ID和角色ID查询用户指定业务的代办，已办，全部数量------------------------------------------------
    @PostMapping("/BusinessNum")
    ConfigApiResponse<Map<String, Object>> getBusinessTypeList(
            @RequestHeader("X-Subject-Id") Long subjectId,
            @RequestBody ApprovalProcessReq approvalProcessDTO);

    // 根据用户ID和角色ID查询用户指定业务的代办，已办，全部列表------------------------------------------------
    @PostMapping("/BusinessList")
    ConfigApiResponse<List<Map<String, Object>>> getBusinessList(
            @RequestHeader("X-Subject-Id") Long subjectId,
            @RequestBody ApprovalListReq approvalListReq);

    // 处理业务数据的审批流转---------------------------------------------------------------------
    @PostMapping("/save")
    ConfigApiResponse<Map<String, Object>> process(
            @RequestHeader("X-Subject-Id") Long subjectId,
            @RequestBody ApprovalProcessReq approvalProcessDTO);

    // 更新单据真实业务ID---------------------------------------------------------------------------
    @PostMapping("/businessId")
    ConfigApiResponse<Map<String, Object>> saveBusinessId(
            @RequestHeader("X-Subject-Id") Long subjectId, @RequestBody SaveBusinessIdReq req);

    // 查询单据审批详细数据---------------------------------------------------------------------------
    @PostMapping("/detail")
    ConfigApiResponse<Map<String, Object>> getApprovalDetail(
            @RequestHeader("X-Subject-Id") Long subjectId, @RequestBody ApprovalProcessReq req);

    // 查询单据审批操作日志
    @PostMapping("/Log")
    ConfigApiResponse<List<ActionLogResp>> getActionLog(
            @RequestHeader("X-Subject-Id") Long subjectId, @RequestBody ApprovalDataReq req);

    // 查询单据对应版本号和数据ID
    @PostMapping("/VersionId")
    ConfigApiResponse<Map<String, Object>> getApprovalChangeData(
            @RequestHeader("X-Subject-Id") Long subjectId, @RequestBody ApprovalDataReq req);

    // 查询消息接收人
    @PostMapping("/Receiver")
    ConfigApiResponse<List<Map<String, Object>>> getMessageReceiver(
            @RequestHeader("X-Subject-Id") Long subjectId, @RequestBody MessageReceiverReq req);

    // 查询已经审批的步骤状态
    @PostMapping("/ApprovalStep")
    ConfigApiResponse<List<Map<String, Object>>> getApprovalStep(
            @RequestHeader("X-Subject-Id") Long subjectId, @RequestBody ApprovalDataReq req);
}
