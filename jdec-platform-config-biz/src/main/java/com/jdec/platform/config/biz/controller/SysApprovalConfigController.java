package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysApprovalChainConfigApi;
import com.jdec.platform.config.api.SysApprovalChainTypeApi;
import com.jdec.platform.config.api.dto.request.ApprovalChainConfigPageReq;
import com.jdec.platform.config.api.dto.request.ApprovalChainConfigSaveReq;
import com.jdec.platform.config.api.dto.request.ApprovalChainTypePageReq;
import com.jdec.platform.config.api.dto.request.ApprovalChainTypeSaveReq;
import com.jdec.platform.config.api.dto.response.*;
import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 审批配置 REST 控制器
 *
 * <p>提供审批链配置相关接口
 */
@RestController
@RequestMapping("/api/config/approval")
@RequiredArgsConstructor
@Tag(name = "审批配置", description = "审批链配置相关接口")
public class SysApprovalConfigController {

    private final SysApprovalChainConfigApi sysApprovalChainConfigApi;
    private final SysApprovalChainTypeApi sysApprovalChainTypeApi;

    @GetMapping("/cascade-options")
    @Operation(summary = "获取审批链级联下拉数据", description = "返回模块及其对应的审批链分类列表，用于级联下拉选择")
    public ApiResponse<List<ApprovalChainCascadeResp>> getCascadeOptions() {
        return ApiResponse.success(sysApprovalChainConfigApi.getCascadeOptions());
    }

    @PostMapping("/page")
    @Operation(summary = "分页查询审批链配置", description = "查询审批链配置列表，包含所有关联数据的中文名称")
    public ApiResponse<PageResult<ApprovalChainConfigPageResp>> getPage(
            @RequestBody ApprovalChainConfigPageReq request) {
        return ApiResponse.success(sysApprovalChainConfigApi.getPage(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询审批链配置详情", description = "根据ID查询同模块同类型的所有审批链配置（按步骤升序）")
    public ApiResponse<List<ApprovalChainConfigDetailResp>> getDetail(@PathVariable Long id) {
        return ApiResponse.success(sysApprovalChainConfigApi.getDetail(id));
    }

    @GetMapping("/chain-type/cascade-options")
    @Operation(summary = "获取审批链分类级联下拉数据", description = "返回审批链分类表中所有模块及其对应的审批链分类列表")
    public ApiResponse<List<ApprovalChainCascadeResp>> getChainTypeCascadeOptions() {
        return ApiResponse.success(sysApprovalChainTypeApi.getCascadeOptions());
    }

    @GetMapping("/chain-type/options")
    @Operation(summary = "获取审批链分类列表", description = "查询所有审批链分类")
    public ApiResponse<List<ApprovalChainTypeOptionResp>> chainTypeOptions(
            @Parameter(description = "模块ID，可选参数。传入时返回该模块下的审批链分类，不传则返回所有审批链分类")
                    @RequestParam(value = "moduleId", required = false)
                    Long moduleId) {
        return ApiResponse.success(sysApprovalChainTypeApi.options(moduleId));
    }

    @PostMapping("/chain-type/page")
    @Operation(summary = "分页查询审批链分类", description = "查询审批链分类列表，支持模块和分类名称搜索，支持分类多选")
    public ApiResponse<PageResult<ApprovalChainTypePageResp>> getChainTypePage(
            @RequestBody ApprovalChainTypePageReq request) {
        return ApiResponse.success(sysApprovalChainTypeApi.getPage(request));
    }

    @PostMapping("/chain-type/save")
    @Operation(summary = "新增/编辑审批链分类", description = "id为空则新增，不为空则修改")
    public ApiResponse<Void> saveChainType(@Valid @RequestBody ApprovalChainTypeSaveReq request) {
        sysApprovalChainTypeApi.save(request);
        return ApiResponse.success();
    }

    @PostMapping("/save")
    @Operation(summary = "新增/编辑审批链配置", description = "id为空则新增，不为空则修改")
    public ApiResponse<ApprovalChainConfigSaveResp> save(
            @Valid @RequestBody ApprovalChainConfigSaveReq request) {
        return ApiResponse.success(sysApprovalChainConfigApi.save(request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除审批链配置", description = "根据ID删除审批链配置，并重新编排步骤顺序")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        sysApprovalChainConfigApi.delete(id);
        return ApiResponse.success();
    }
}
