package com.jdec.platform.common.biz.controller;

import com.jdec.platform.common.biz.dto.request.MenuQueryReq;
import com.jdec.platform.common.biz.dto.response.MenuDataResp;
import com.jdec.platform.common.biz.dto.response.MenuDetailResp;
import com.jdec.platform.common.biz.service.CommonAggregateService;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 公共业务数据聚合控制器 (基于菜单的数据隔离、动态列表与详情聚合) */
@Slf4j
@Tag(name = "公共业务数据聚合接口", description = "提供基于菜单维度的动态列头、列表分页、单据详情与审批流按钮聚合")
@RestController("commonAggregateController")
@RequestMapping("/api/common/aggregate")
@RequiredArgsConstructor
public class CommonAggregateController {

    private final CommonAggregateService commonAggregateService;

    @PostMapping("/menu/{menuId}")
    @Operation(
            summary = "基于菜单 ID 的动态数据列表查询",
            description =
                    "自动通过 UserMenuHeaderApi 解析勾选的 fieldId 列表与 rootModuleId，"
                            + "根据 approvalType(1-待办, 2-已办, 3-全部) 进行审批流状态过滤，"
                            + "返回包含多级列头、权限汇总、审批流操作按钮及同构树形结构的数据分页")
    public ApiResponse<MenuDataResp> queryByMenu(
            @PathVariable Long menuId, @RequestBody(required = false) MenuQueryReq req) {
        try {
            MenuDataResp result = commonAggregateService.queryByMenu(menuId, req);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error(
                    "Common aggregate queryByMenu failed: menuId={}, err={}",
                    menuId,
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @GetMapping("/menu/{menuId}/{id}")
    @Operation(
            summary = "基于菜单 ID 的单据详情及元数据聚合查询",
            description = "仅需传入 menuId 和单据 id，内部自动解析根模块 ID 及勾选字段，返回单据元数据定义与同构详情树")
    public ApiResponse<MenuDetailResp> getDetailByMenu(
            @PathVariable Long menuId, @PathVariable Long id) {
        try {
            MenuDetailResp result = commonAggregateService.getDetailByMenu(menuId, id);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error(
                    "Common aggregate getDetailByMenu failed: menuId={}, id={}, err={}",
                    menuId,
                    id,
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }
}
