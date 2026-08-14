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
@RequestMapping("/api/student/common/query")
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
            @Schema(description = "主体ID") @RequestHeader("X-Subject-Id") Long subjectId,
            @Schema(description = "角色ID") @RequestHeader(name = "X-Role-Id") Long roleId,
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
            @Schema(description = "主体ID") @RequestHeader("X-Subject-Id") Long subjectId,
            @Schema(description = "角色ID") @RequestHeader(name = "X-Role-Id") Long roleId,
            @RequestBody List<MenuSaveReq> req) {
        return ApiResponse.success(
                dynamicDataSaveService.editMultiMenuModuleData(subjectId, roleId, req));
    }

    @PostMapping("/new/menu")
    @Operation(summary = "创建module数据", description = "创建module数据")
    public ApiResponse<Object> createMenuModule(
            @Schema(description = "主体ID") @RequestHeader("X-Subject-Id") Long subjectId,
            @Schema(description = "角色ID") @RequestHeader(name = "X-Role-Id") Long roleId,
            @RequestBody List<MenuSaveReq> req) {
        return ApiResponse.success(
                dynamicDataSaveService.createMultiMenuModuleData(subjectId, roleId, req));
    }

    @PostMapping("/get-data-right")
    @Operation(summary = "系统设置获取数据权限显示数据", description = "系统设置获取数据权限显示数据")
    public ApiResponse<List<DataRightValue>> getDataRightData(
            @Schema(description = "主体ID") @RequestHeader("X-Subject-Id") Long subjectId,
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
            @Schema(description = "主体ID") @RequestHeader("X-Subject-Id") Long subjectId,
            @Schema(description = "角色ID") @RequestHeader(name = "X-Role-Id") Long roleId,
            @RequestBody ListQueryReq queryReq) {
        Page pageObj = null;
        if (Objects.nonNull(queryReq.getPageNum()) && Objects.nonNull(queryReq.getPageSize())) {
            pageObj =
                    Page.of(
                            queryReq.getPageNum() <= 0 ? 1 : queryReq.getPageNum(),
                            queryReq.getPageSize() <= 0 ? 10 : queryReq.getPageSize());
        }
        return ApiResponse.success(
                dynamicDataAccessService.dynamicQueryListDataWithSearchByMenuId(
                        subjectId, roleId, queryReq, pageObj, null));
    }

    @PostMapping("/list/menu/search/getSelectData")
    @Operation(summary = "获取表头筛选项候选数据", description = "获取表头筛选项候选数据")
    public ApiResponse<Map<String, Object>> dynamicQuerySearchByMenuForSelectData(
            @Schema(description = "主体ID") @RequestHeader("X-Subject-Id") Long subjectId,
            @Schema(description = "角色ID") @RequestHeader(name = "X-Role-Id") Long roleId,
            @RequestBody ListSelectQueryReq queryReq) {
        return ApiResponse.success(
                dynamicDataAccessService.dynamicQueryListSelectDataWithSearchByMenuId(
                        subjectId, roleId, queryReq, null));
    }

    @PostMapping("/list/menu/search/getFilterSelectedData")
    @Operation(summary = "获取表头筛选后其他筛选项已选中的候选数据", description = "获取表头筛选后其他筛选项已选中的候选数据")
    public ApiResponse<List<SearchItemReq>> dynamicQuerySearchByMenuFilterSelectedData(
            @Schema(description = "主体ID") @RequestHeader("X-Subject-Id") Long subjectId,
            @Schema(description = "角色ID") @RequestHeader(name = "X-Role-Id") Long roleId,
            @RequestBody ListFilterSelectedDataReq queryReq) {
        return ApiResponse.success(
                dynamicDataAccessService.dynamicQueryFilterSelectedDataWithSearchByMenuId(
                        subjectId, roleId, queryReq));
    }

    @GetMapping("/approval/detail/menu")
    @Operation(summary = "获取带审批的详情数据", description = "获取带审批的详情数据")
    public ApiResponse<Map<Object, Object>> getApprovalDynamicQueryDetailResultWithSearchByMenu(
            @Schema(description = "主体ID") @RequestHeader("X-Subject-Id") Long subjectId,
            @Schema(description = "角色ID") @RequestHeader(name = "X-Role-Id") Long roleId,
            @Schema(description = "菜单ID") @RequestParam Long menuId,
            @Schema(description = "主表主键ID") @RequestParam Long id,
            @Schema(description = "关联筛选ID") @RequestParam(required = false) Long relateSearchId,
            @Schema(description = "按钮状态") @RequestParam(required = false, defaultValue = "view")
                    String btnType) {
        return ApiResponse.success(
                dynamicDataAccessService.dynamicQueryAllModuleDetailDataWithApproval(
                        subjectId, roleId, menuId, id, relateSearchId, btnType));
    }

    @PostMapping("/approval/list/menu/search")
    @Operation(summary = "获取带审批的列表数据", description = "获取带审批的列表数据")
    public ApiResponse<Map<String, Object>> getApprovalDynamicQueryListResultWithSearchByMenu(
            @Schema(description = "主体ID") @RequestHeader("X-Subject-Id") Long subjectId,
            @Schema(description = "角色ID") @RequestHeader(name = "X-Role-Id") Long roleId,
            @RequestBody ApprovalListQueryReq queryReq) {
        Page pageObj = null;
        if (Objects.nonNull(queryReq.getPageNum()) && Objects.nonNull(queryReq.getPageSize())) {
            pageObj =
                    Page.of(
                            queryReq.getPageNum() <= 0 ? 1 : queryReq.getPageNum(),
                            queryReq.getPageSize() <= 0 ? 10 : queryReq.getPageSize());
        }
        return ApiResponse.success(
                dynamicDataAccessService.dynamicQueryListDataWithSearchByMenuId(
                        subjectId, roleId, queryReq, pageObj, null));
    }

    @PostMapping("/approval/new/menu")
    @Operation(summary = "带审批链的变更新建提交", description = "带审批链的变更新建提交")
    public ApiResponse<Object> createApproval(
            @Schema(description = "主体ID") @RequestHeader("X-Subject-Id") Long subjectId,
            @Schema(description = "角色ID") @RequestHeader(name = "X-Role-Id") Long roleId,
            @RequestBody ApprovalModuleSaveReq req) {
        return ApiResponse.success(
                dynamicDataSaveService.createMultiMenuModuleDataWithApproval(
                        subjectId, roleId, req));
    }

    @PostMapping("/approval/edit/menu")
    @Operation(summary = "带审批链的办理、驳回后提交", description = "带审批链的办理、驳回后提交")
    public ApiResponse<Object> editApproval(
            @Schema(description = "主体ID") @RequestHeader("X-Subject-Id") Long subjectId,
            @Schema(description = "角色ID") @RequestHeader(name = "X-Role-Id") Long roleId,
            @RequestBody ApprovalModuleSaveReq req) {
        return ApiResponse.success(
                dynamicDataSaveService.editMultiMenuModuleDataWithApproval(
                        subjectId, roleId, req.getModuleSaveData(), req.getApprovalOperation()));
    }

    @GetMapping("/approval/list/num/menu")
    @Operation(summary = "获取带审批的模块待办、已办、全部数量", description = "获取带审批的模块待办、已办、全部数量")
    public ApiResponse<Map<String, Object>> getApprovalDynamicQueryResultWithSearchByMenu(
            @Schema(description = "主体ID") @RequestHeader("X-Subject-Id") Long subjectId,
            @Schema(description = "角色ID") @RequestHeader(name = "X-Role-Id") Long roleId,
            @Schema(description = "菜单ID") @RequestParam Long menuId) {
        return ApiResponse.success(
                dynamicDataAccessService.dynamicQuerygetListNumWithApproval(
                        subjectId, roleId, menuId));
    }

    @GetMapping("/approval/getToDoId")
    @Operation(summary = "获取带审批的模块待办、已办、全部数量", description = "获取带审批的模块待办、已办、全部数量")
    public ApiResponse<Long> getApprovalToDoId(
            @Schema(description = "主体ID") @RequestHeader("X-Subject-Id") Long subjectId,
            @Schema(description = "角色ID") @RequestHeader(name = "X-Role-Id") Long roleId,
            @Schema(description = "菜单ID") @RequestParam Long menuId) {
        return ApiResponse.success(
                dynamicDataAccessService.getApprovalToDoId(subjectId, roleId, menuId));
    }

    @GetMapping("/approval/log")
    @Operation(summary = "获取单据操作日志", description = "获取单据操作日志")
    public ApiResponse<Object> getApprovalOperationLogs(
            @Schema(description = "主体ID") @RequestHeader("X-Subject-Id") Long subjectId,
            @Schema(description = "角色ID") @RequestHeader(name = "X-Role-Id") Long roleId,
            @Schema(description = "菜单ID") @RequestParam Long menuId,
            @Schema(description = "单据ID") @RequestParam Long id) {
        return ApiResponse.success(
                dynamicDataAccessService.dynamicQuerygetApprovalLogs(
                        subjectId, roleId, menuId, id));
    }

    @GetMapping("/approval/getApprovalStep")
    @Operation(summary = "查询已经审批的步骤状态", description = "查询已经审批的步骤状态")
    public ApiResponse<List<Map<String, Object>>> getApprovalStep(
            @Schema(description = "主体ID") @RequestHeader("X-Subject-Id") Long subjectId,
            @Schema(description = "角色ID") @RequestHeader(name = "X-Role-Id") Long roleId,
            @Schema(description = "菜单ID") @RequestParam Long menuId,
            @Schema(description = "单据ID") @RequestParam Long id) {
        return ApiResponse.success(
                dynamicDataAccessService.dynamicQuerygetApprovalStep(
                        subjectId, roleId, menuId, id));
    }
}
