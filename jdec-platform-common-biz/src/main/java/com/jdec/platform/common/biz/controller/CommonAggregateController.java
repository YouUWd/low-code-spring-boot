package com.jdec.platform.common.biz.controller;

import com.jdec.platform.common.api.dto.request.MenuListReq;
import com.jdec.platform.common.api.dto.request.MenuOptionReq;
import com.jdec.platform.common.api.dto.request.MenuQueryReq;
import com.jdec.platform.common.api.dto.response.DataRecordItem;
import com.jdec.platform.common.api.dto.response.MenuApprovalCountResp;
import com.jdec.platform.common.api.dto.response.MenuDataResp;
import com.jdec.platform.common.api.dto.response.MenuDetailResp;
import com.jdec.platform.common.biz.service.CommonAggregateService;
import com.jdec.platform.data.api.dto.response.DataOptionResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
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

    @PostMapping("/list")
    @Operation(
            summary = "动态数据列表全量查询 (不分页)",
            description =
                    "根据传入物理表 specs 中携带的 menuId 解析对应的 moduleId，在项目的全量模块树中定位公共根模块；"
                            + "根据传入的物理表与列名进行数据投影，基于平铺条件进行过滤与排序，"
                            + "定位匹配记录并获取其最新启用 (business_status=99) 的最大版本，"
                            + "只返回记录列表 (List<DataRecordItem>)，不包含元数据及分页信息")
    public ApiResponse<List<DataRecordItem>> queryList(
            @RequestBody(required = false) MenuListReq req) {
        try {
            List<DataRecordItem> result = commonAggregateService.queryList(req);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error("Common aggregate queryList failed: err={}", ex.getMessage(), ex);
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

    @PostMapping("/menu/{menuId}/options")
    @Operation(
            summary = "基于菜单 ID 的字段搜索下拉候选项",
            description =
                    "根据菜单 ID 自动解析模块上下文，根据 approvalType(1-待办, 2-已办, 3-全部) 进行审批流状态过滤，"
                            + "支持传入单层 AND 组合过滤条件与可选排序方向，获取去重候选值列表")
    public ApiResponse<List<DataOptionResp>> getOptionsByMenu(
            @PathVariable Long menuId, @RequestBody MenuOptionReq req) {
        try {
            List<DataOptionResp> result = commonAggregateService.getOptionsByMenu(menuId, req);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error(
                    "Common aggregate getOptionsByMenu failed: menuId={}, fieldId={}, err={}",
                    menuId,
                    req != null ? req.getFieldId() : null,
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @GetMapping("/menu/{menuId}/approval-count")
    @Operation(
            summary = "基于菜单 ID 一次性统计待办、已办和全部的记录数量",
            description = "仅需传入 menuId，一次性返回 1(待办)、2(已办)、3(全部) 的统计数量列表")
    public ApiResponse<List<MenuApprovalCountResp>> getApprovalCountByMenu(
            @PathVariable Long menuId) {
        try {
            List<MenuApprovalCountResp> result =
                    commonAggregateService.getApprovalCountByMenu(menuId);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error(
                    "Common aggregate getApprovalCountByMenu failed: menuId={}, err={}",
                    menuId,
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }
}
