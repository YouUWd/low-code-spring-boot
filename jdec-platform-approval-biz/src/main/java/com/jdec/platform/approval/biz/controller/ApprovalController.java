package com.jdec.platform.approval.biz.controller;

import com.jdec.platform.approval.api.IApprovalEngine;
import com.jdec.platform.approval.api.req.ApprovalProcessReq;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    @Operation(summary = "查询单据数量", description = "查询单据数量")
    public ApiResponse<Map<String, Object>> getBusinessTypeList(
            @RequestBody ApprovalProcessReq req) {
        return ApiResponse.success(approvalService.getBusinessTypeList(req));
    }

    @PostMapping("/detail")
    @Operation(summary = "查询单据数据", description = "查询单据数据")
    public ApiResponse<Map<String, Object>> getApprovalDetail(@RequestBody ApprovalProcessReq req) {
        return ApiResponse.success(approvalService.getApprovalDetail(req));
    }

    @PostMapping("/save")
    @Operation(summary = "保存单据", description = "保存单据")
    public ApiResponse<Map<String, Object>> saveApproval(@RequestBody ApprovalProcessReq req) {
        return ApiResponse.success(approvalService.process(req));
    }
}
