package com.jdec.platform.approval.biz.controller;

import com.jdec.platform.approval.api.IApprovalEngine;
import com.jdec.platform.approval.api.event.ApprovalEvent;
import com.jdec.platform.approval.api.req.*;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/data/approval")
@RequiredArgsConstructor
@Tag(name = "审批流程控制", description = "审批流程控制，单据审批数据查询")
public class ApprovalController {
    private final IApprovalEngine approvalService;
    private final ApplicationEventPublisher eventPublisher;

    /* @PostMapping("/BusinessNum")
    @Operation(summary = "查询单据数量", description = "查询待办、已办、全部单据数量")
    public ApiResponse<Map<String, Object>> getBusinessTypeList(@RequestBody ApprovalListReq req) {
        return ApiResponse.success(approvalService.getBusinessTypeList(req));
    }

    @PostMapping("/BusinessList")
    @Operation(summary = "查询单据列表", description = "查询待办、已办、全部单据列表")
    public ApiResponse<List<Map<String, Object>>> getBusinessList(
            @RequestBody ApprovalListReq req) {
        return ApiResponse.success(approvalService.getBusinessList(req));
    }*/

    @PostMapping("/BusinessNextId")
    @Operation(summary = "查询待办下一个单据ID", description = "查询待办下一个单据ID")
    public ApiResponse<Long> BusinessNextId(@RequestBody ApprovalProcessReq req) {
        return ApiResponse.success(approvalService.getBusinessNextId(req));
    }

    //    @PostMapping("/detail")
    //    @Operation(summary = "查询单据数据", description = "查询单据数据")
    //    public ApiResponse<Map<String, Object>> getApprovalDetail(@RequestBody ApprovalProcessReq
    // req) {
    //        return ApiResponse.success(approvalService.getApprovalDetail(req));
    //    }

    @PostMapping("/ApprovalStep")
    @Operation(summary = "查询已经审批的步骤状态", description = "查询已经审批的步骤状态")
    public ApiResponse<List<Map<String, Object>>> getApprovalStep(
            @RequestBody ApprovalDataReq req) {
        return ApiResponse.success(approvalService.getApprovalStep(req));
    }

    @PostMapping("/ApprovalEvent")
    @Operation(summary = "测试审批事件", description = "测试审批事件")
    public ApiResponse<Long> approvalProcess(@RequestBody ApprovalEvent approvalEvent) {
        eventPublisher.publishEvent(approvalEvent);
        return ApiResponse.success();
    }

    @GetMapping("/getListNumber/{moduleId}")
    @Operation(summary = "查询单据数量", description = "查询待办、已办、全部单据数量")
    public ApiResponse<List<Map<String, Object>>> getListNumber(@PathVariable Long menuId) {
        return ApiResponse.success(approvalService.getListNumber(menuId));
    }

    @GetMapping("/getListButton/{moduleId}")
    @Operation(summary = "查询单据列表按钮", description = "查询查询单据列表按钮")
    public ApiResponse<List<Map<String, Object>>> getListButton(
            @PathVariable Long moduleId, @RequestParam Integer approvalType) {
        return ApiResponse.success(approvalService.getListButton(moduleId, approvalType));
    }

    @GetMapping("/detail/{moduleId}/{recordId}")
    @Operation(summary = "查询单据审批详情数据", description = "查询单据审批详情数据")
    public ApiResponse<Map<String, Object>> getApprovalData(
            @PathVariable Long moduleId,
            @PathVariable Long recordId,
            @RequestParam String btnType) {
        return ApiResponse.success(approvalService.getApprovalData(moduleId, recordId, btnType));
    }
}
