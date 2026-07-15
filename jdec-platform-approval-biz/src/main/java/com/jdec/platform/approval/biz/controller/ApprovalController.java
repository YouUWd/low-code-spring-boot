package com.jdec.platform.approval.biz.controller;

import com.jdec.platform.approval.api.IApprovalEngine;
import com.jdec.platform.approval.api.req.ApprovalDataReq;
import com.jdec.platform.approval.api.req.ApprovalListReq;
import com.jdec.platform.approval.api.req.ApprovalProcessReq;
import com.jdec.platform.approval.api.req.SaveBusinessIdReq;
import com.jdec.platform.approval.api.resp.ActionLogResp;
import com.jdec.platform.approval.api.resp.ApprovalListResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/data/approval")
@RequiredArgsConstructor
@Tag(name = "审批流程控制", description = "审批流程控制，单据审批数据查询")
public class ApprovalController {
    private final IApprovalEngine approvalService;

    @PostMapping("/BusinessNum")
    @Operation(summary = "查询单据数量", description = "查询待办、已办、全部单据数量")
    public ApiResponse<Map<String, Object>> getBusinessTypeList(
            @RequestBody ApprovalProcessReq req) {
        return ApiResponse.success(approvalService.getBusinessTypeList(req));
    }

    @PostMapping("/BusinessList")
    @Operation(summary = "查询单据列表", description = "查询待办、已办、全部单据列表")
    public ApiResponse<List<ApprovalListResp>> getBusinessList(@RequestBody ApprovalListReq req) {
        return ApiResponse.success(approvalService.getBusinessList(req));
    }

    @PostMapping("/detail")
    @Operation(summary = "查询单据数据", description = "查询单据数据")
    public ApiResponse<Map<String, Object>> getApprovalDetail(@RequestBody ApprovalProcessReq req) {
        return ApiResponse.success(approvalService.getApprovalDetail(req));
    }

    @PostMapping("/Log")
    @Operation(summary = "查询单据操作日志", description = "查询单据操作日志")
    public ApiResponse<List<ActionLogResp>> getApprovalLog(@RequestBody ApprovalDataReq req) {
        return ApiResponse.success(approvalService.getActionLog(req));
    }

    @PostMapping("/VersionId")
    @Operation(summary = "查询单据对比版本数据", description = "查询单据对比版本数据ID,版本号")
    public ApiResponse<Map<String, Object>> getApprovalVersionId(@RequestBody ApprovalDataReq req) {
        return ApiResponse.success(approvalService.getApprovalChangeData(req));
    }

    @PostMapping("/save")
    @Operation(summary = "保存单据", description = "保存单据")
    public ApiResponse<Map<String, Object>> saveApproval(@RequestBody ApprovalProcessReq req) {
        return ApiResponse.success(approvalService.process(req));
    }

    @PostMapping("/businessId")
    @Operation(summary = "保存真实业务ID", description = "保存单据真实业务ID")
    public ApiResponse<Map<String, Object>> saveBusinessId(@RequestBody SaveBusinessIdReq req) {
        return ApiResponse.success(approvalService.saveBusinessId(req));
    }
}
