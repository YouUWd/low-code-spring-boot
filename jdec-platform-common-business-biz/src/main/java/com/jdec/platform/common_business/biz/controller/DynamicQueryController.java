package com.jdec.platform.common_business.biz.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.jdec.platform.common_business.api.DynamicDataAccessServiceApi;
import com.jdec.platform.common_business.api.DynamicDataSaveServiceApi;
import com.jdec.platform.common_business.api.dto.common.DataRightValue;
import com.jdec.platform.common_business.api.dto.request.*;
import com.jdec.platform.common_business.biz.util.JsonUtil;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/student/query")
@ResponseBody
@RequiredArgsConstructor
@Tag(
        name = "动态数据查询", // 分组名称（文档中显示的分组标签）
        description = "列表和详情动态查询接口" // 分组描述
        )
public class DynamicQueryController {

    private final DynamicDataAccessServiceApi dynamicDataAccessService;

    private final DynamicDataSaveServiceApi dynamicDataSaveService;

    @GetMapping("/detail/menu")
    @Operation(summary = "获取详情数据", description = "获取详情数据")
    public ApiResponse<Map<Object, Object>> dynamicQueryAllMenuDetail(
            @Schema(description = "主体ID") @RequestHeader("Subject-Id") Long subjectId,
            @Schema(description = "角色ID") @RequestHeader(name = "Role-Id") Long roleId,
            @Schema(description = "菜单ID") @RequestParam Long menuId,
            @Schema(description = "主表主键ID") @RequestParam Long id,
            @Schema(description = "关联筛选ID") @RequestParam(required = false) Long relateSearchId) {

        return ApiResponse.success(
                dynamicDataAccessService.dynamicQueryAllModuleDetailData(
                        subjectId, roleId, menuId, id, relateSearchId));
    }

    @PostMapping("/edit/menu")
    @Operation(summary = "编辑module数据", description = "编辑module数据")
    public ApiResponse<Object> editMenuModule(
            @Schema(description = "主体ID") @RequestHeader("Subject-Id") Long subjectId,
            @Schema(description = "角色ID") @RequestHeader(name = "Role-Id") Long roleId,
            @RequestBody List<MenuSaveReq> req) {
        return ApiResponse.success(
                dynamicDataSaveService.editMultiMenuModuleData(subjectId, roleId, req));
    }

    @PostMapping("/new/menu")
    @Operation(summary = "创建module数据", description = "创建module数据")
    public ApiResponse<Object> createMenuModule(
            @Schema(description = "主体ID") @RequestHeader("Subject-Id") Long subjectId,
            @Schema(description = "角色ID") @RequestHeader(name = "Role-Id") Long roleId,
            @RequestBody List<MenuSaveReq> req) {
        return dynamicDataSaveService.createMultiMenuModuleData(subjectId, roleId, req);
    }

    @PostMapping("/get-data-right")
    @Operation(summary = "系统设置获取数据权限显示数据", description = "系统设置获取数据权限显示数据")
    public ApiResponse<List<DataRightValue>> getDataRightData(
            @Schema(description = "主体ID") @RequestHeader("Subject-Id") Long subjectId,
            @RequestBody List<DataRightAccessReq> req) {
        try {
            log.debug("系统配置调用获取数据权限节点数据，主体id:{} , 请求参数:{}", subjectId, JsonUtil.obj2Json(req));
        } catch (JsonProcessingException e) {
            log.debug("json object serialize error", e);
        }
        return ApiResponse.success(dynamicDataAccessService.getDataRightData(subjectId, req));
    }

    @PostMapping("/list/menu/search")
    @Operation(summary = "获取列表数据", description = "获取列表数据")
    public ApiResponse<Map<String, Object>> getDynamicQueryResultWithSearchByMenu(
            @Schema(description = "主体ID") @RequestHeader("Subject-Id") Long subjectId,
            @Schema(description = "角色ID") @RequestHeader(name = "Role-Id") Long roleId,
            @RequestBody ListQueryReq queryReq) {
        Page pageObj = null;
        if (Objects.nonNull(queryReq.getPage()) && Objects.nonNull(queryReq.getPageSize())) {
            pageObj = Page.of(queryReq.getPage(), queryReq.getPageSize());
        }
        return ApiResponse.success(
                dynamicDataAccessService.dynamicQueryListDataWithSearchByMenuId(
                        subjectId, roleId, queryReq, pageObj, null));
    }

    @PostMapping("/approval/list/menu/search")
    @Operation(summary = "获取列表数据", description = "获取列表数据")
    public ApiResponse<Map<String, Object>> getApprovalDynamicQueryResultWithSearchByMenu(
            @Schema(description = "主体ID") @RequestHeader("Subject-Id") Long subjectId,
            @Schema(description = "角色ID") @RequestHeader(name = "Role-Id") Long roleId,
            @RequestBody ApprovalListQueryReq queryReq) {
        Page pageObj = null;
        if (Objects.nonNull(queryReq.getPage()) && Objects.nonNull(queryReq.getPageSize())) {
            pageObj = Page.of(queryReq.getPage(), queryReq.getPageSize());
        }
        return ApiResponse.success(
                dynamicDataAccessService.dynamicQueryListDataWithSearchByMenuId(
                        subjectId, roleId, queryReq, pageObj, null));
    }
}
