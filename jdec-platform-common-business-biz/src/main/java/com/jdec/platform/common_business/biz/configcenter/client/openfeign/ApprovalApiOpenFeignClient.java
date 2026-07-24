package com.jdec.platform.common_business.biz.configcenter.client.openfeign;

import com.jdec.platform.approval.api.req.*;
import com.jdec.platform.approval.api.resp.ActionLogResp;
import com.jdec.platform.common_business.biz.dto.configcenter.response.ConfigApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(
        name = "approval-biz",
        url = "http://127.0.0.1:8080/api/data/approval",
        configuration = FeignConfig.class)
public interface ApprovalApiOpenFeignClient {

    // 根据用户ID和角色ID查询用户指定业务的代办，已办，全部数量------------------------------------------------
    @PostMapping("/BusinessNum")
    ConfigApiResponse<Map<String, Object>> getBusinessTypeList(
            ApprovalProcessReq approvalProcessDTO);

    // 根据用户ID和角色ID查询用户指定业务的代办，已办，全部列表------------------------------------------------
    @PostMapping("/BusinessList")
    ConfigApiResponse<List<Map<String, Object>>> getBusinessList(ApprovalListReq approvalListReq);

    // 处理业务数据的审批流转---------------------------------------------------------------------
    @PostMapping("/save")
    ConfigApiResponse<Map<String, Object>> process(ApprovalProcessReq approvalProcessDTO);

    // 更新单据真实业务ID---------------------------------------------------------------------------
    @GetMapping("/businessId")
    ConfigApiResponse<Map<String, Object>> saveBusinessId(SaveBusinessIdReq req);

    // 查询单据审批详细数据---------------------------------------------------------------------------
    @PostMapping("/detail")
    ConfigApiResponse<Map<String, Object>> getApprovalDetail(ApprovalProcessReq req);

    // 查询单据审批操作日志
    @PostMapping("/Log")
    ConfigApiResponse<List<ActionLogResp>> getActionLog(ApprovalDataReq req);

    // 查询单据对应版本号和数据ID
    @PostMapping("/VersionId")
    ConfigApiResponse<Map<String, Object>> getApprovalChangeData(ApprovalDataReq req);

    // 查询消息接收人
    @PostMapping("/Receiver")
    ConfigApiResponse<List<Map<String, Object>>> getMessageReceiver(MessageReceiverReq req);
}
