package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysModuleApi;
import com.jdec.platform.config.api.dto.request.MoveModuleReq;
import com.jdec.platform.config.api.dto.request.SaveModuleReq;
import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp;
import com.jdec.platform.config.api.dto.response.SysModuleDetailResp;
import com.jdec.platform.config.api.dto.response.SysModuleListResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模块管理 Controller
 * 提供模块完整信息的 CRUD 操作
 */
@Tag(name = "模块管理", description = "模块完整信息管理（包括基本信息、关联表、字段配置、状态配置）")
@RestController
@RequestMapping("/api/config/modules")
@RequiredArgsConstructor
public class SysModuleController {

    private final SysModuleApi sysModuleService;

    /**
     * 获取模块列表
     * 返回所有模块的基本信息，不包含树形结构，前端自行构建树
     */
    @Operation(summary = "获取模块列表", description = "获取所有模块的基本信息（不包含树形结构），前端自行构建树")
    @GetMapping
    public ApiResponse<List<SysModuleListResp>> listModules() {
        List<SysModuleListResp> result = sysModuleService.listModules();
        return ApiResponse.success(result);
    }

    /**
     * 查询模块完整信息
     * 包含模块基本信息、关联表、字段配置和状态
     */
    @Operation(summary = "查询模块完整信息", description = "获取模块的所有配置信息，包括基本信息、关联表、简单字段、组合字段和状态配置")
    @GetMapping("/{moduleId}/complete")
    public ApiResponse<SysModuleCompleteResp> getModuleComplete(
            @Parameter(description = "模块ID", required = true, example = "1")
            @PathVariable Long moduleId) {
        SysModuleCompleteResp result = sysModuleService.getModuleComplete(moduleId);
        return ApiResponse.success(result);
    }

    /**
     * 保存或编辑模块完整信息
     * 包含模块基本信息、关联表、简单字段配置和组合字段配置
     * 如果 request.module.id 为空则创建新模块，否则编辑现有模块
     */
    @Operation(summary = "保存或编辑模块完整信息", description = "一次性保存或编辑模块的所有配置信息，包括基本信息、关联表、简单字段和组合字段。若 module.id 为空则创建新模块，否则编辑现有模块")
    @PostMapping("/complete")
    public ApiResponse<SysModuleDetailResp> saveModuleComplete(@RequestBody SaveModuleReq request) {
        SysModuleDetailResp result = sysModuleService.saveModule(request);
        return ApiResponse.success("保存成功", result);
    }

    /**
     * 删除模块完整信息
     * 删除模块及其所有关联的表、字段配置和状态
     */
    @Operation(summary = "删除模块完整信息", description = "删除模块及其所有关联的表、字段配置和状态信息")
    @DeleteMapping("/{moduleId}/complete")
    public ApiResponse<Void> deleteModuleComplete(
            @Parameter(description = "模块ID", required = true, example = "1")
            @PathVariable Long moduleId) {
        sysModuleService.deleteModuleComplete(moduleId);
        return ApiResponse.success("删除成功");
    }

    /**
     * 移动模块
     * 更新模块的父模块和排序顺序，用于处理拖拽移动场景
     */
    @Operation(summary = "移动模块", description = "移动模块到新的父模块下，并更新排序顺序。支持拖拽移动场景")
    @PostMapping("/{moduleId}/move")
    public ApiResponse<SysModuleDetailResp> moveModule(
            @Parameter(description = "模块ID", required = true, example = "5")
            @PathVariable Long moduleId,
            @RequestBody MoveModuleReq request) {
        request.setModuleId(moduleId);
        SysModuleDetailResp result = sysModuleService.moveModule(request);
        return ApiResponse.success("移动成功", result);
    }
}
