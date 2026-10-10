package com.jdec.platform.common.biz.controller;

import com.jdec.platform.common.api.dto.request.ApprovalReq;
import com.jdec.platform.common.biz.service.CommonApprovalService;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/** 公共审批业务聚合控制器 */
@Slf4j
@Tag(name = "公共审批业务聚合接口", description = "提供跨模块的审批聚合统计与统一查询能力")
@RestController("commonApprovalController")
@RequestMapping("/api/common/approval")
@RequiredArgsConstructor
public class CommonApprovalController {

    private final CommonApprovalService commonApprovalService;

    @GetMapping("/menu/{menuId}/approval-count")
    @Operation(
            summary = "基于菜单 ID 一次性统计待办、已办和全部的记录数量",
            description = "仅需传入 menuId，一次性返回 Map 映射：1(待办)、2(已办)、3(全部) 的全量数量")
    public ApiResponse<List<Map<String, Object>>> getApprovalCountByMenu(
            @PathVariable Long menuId) {
        try {
            List<Map<String, Object>> result = commonApprovalService.getApprovalCountByMenu(menuId);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error(
                    "Common approval getApprovalCountByMenu failed: menuId={}, err={}",
                    menuId,
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @GetMapping("/menu/{menuId}/nextId")
    @Operation(summary = "基于菜单 ID 查询待办下一个单据ID", description = "基于 menuId 查询待办下一个单据ID")
    public ApiResponse<Long> BusinessNextId(@PathVariable Long menuId) {
        try {
            Long result = commonApprovalService.BusinessNextId(menuId);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error(
                    "Common approval getApprovalCountByMenu failed: menuId={}, err={}",
                    menuId,
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @PostMapping("/menu/{menuId}/approval")
    @Operation(summary = "基于菜单 ID 调用审批事件", description = "仅需传入 menuId 调用审批事件")
    public ApiResponse<List<Map<String, Object>>> approvalBusiness(
            @PathVariable Long menuId, @RequestBody ApprovalReq req) {
        try {
            commonApprovalService.approvalBusiness(menuId, req);
            return ApiResponse.success();
        } catch (Exception ex) {
            log.error(
                    "Common approval getApprovalCountByMenu failed: menuId={}, err={}",
                    menuId,
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }
}
