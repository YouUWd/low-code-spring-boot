package com.jdec.platform.common.biz.controller;

import com.jdec.platform.common.biz.service.CommonApprovalService;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ApiResponse<Map<Integer, Long>> getApprovalCountByMenu(@PathVariable Long menuId) {
        try {
            Map<Integer, Long> result = commonApprovalService.getApprovalCountByMenu(menuId);
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
}
