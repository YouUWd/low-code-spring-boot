package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysModuleApi;
import com.jdec.platform.config.api.dto.request.MoveModuleReq;
import com.jdec.platform.config.api.dto.request.SaveModuleReq;
import com.jdec.platform.config.api.dto.response.*;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "模块管理", description = "模块完整信息管理（包括基本信息、关联表、字段配置、状态配置）")
@RestController
@RequestMapping("/api/config/modules")
@RequiredArgsConstructor
public class SysModuleController {

    private final SysModuleApi sysModuleService;

    /**
     * 获取模块列表 返回所有模块的基本信息，不包含树形结构，前端自行构建树
     *
     * <p>项目上下文通过 X-Project-No 请求头指定。
     *
     * @param category
     * @return 模块列表
     */
    @Operation(summary = "获取模块列表", description = "获取所有模块的基本信息（不包含树形结构），前端自行构建树")
    @GetMapping
    public ApiResponse<List<SysModuleListResp>> listModules(
            @Parameter(description = "模块类别 (1=业务模块, 2=系统模块)", example = "1")
                    @RequestParam(required = false)
                    Integer category) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        log.info("查询项目 {} (主体 {}) 的模块列表, category={}", projectNo, subjectId, category);
        List<SysModuleListResp> result =
                sysModuleService.listModules(projectNo, subjectId, category);
        return ApiResponse.success(result);
    }

    /**
     * 获取所有可用模块的模块树
     *
     * <p>项目上下文通过 X-Project-No 请求头指定。
     *
     * @return 模块树列表
     */
    @Operation(
            summary = "获取所有可用模块的模块树",
            description = "获取所有状态为active的模块，并组装为树形结构，节点仅包含id, moduleCode和moduleName")
    @GetMapping("/tree")
    public ApiResponse<List<SysModuleSimpleTreeResp>> getAvailableModuleTree(Integer category) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        log.info("查询项目 {} (主体 {}) 的可用模块树", projectNo, subjectId);
        List<SysModuleSimpleTreeResp> result =
                sysModuleService.getAvailableModuleTree(category, projectNo, subjectId);
        return ApiResponse.success(result);
    }

    /**
     * 根据模块ID查询模块完整信息 包含模块基本信息、关联表、字段配置和状态
     *
     * @param moduleId 模块ID
     * @return 模块完整信息
     */
    @Operation(summary = "根据模块ID查询模块元数据信息", description = "根据模块ID获取模块的所有配置信息，包括基本信息、关联表、字段和状态配置")
    @GetMapping("/complete/{moduleId}")
    public ApiResponse<SysModuleMetaResp> getModuleCompleteById(
            @Parameter(description = "模块ID", required = true, example = "1") @PathVariable
                    Long moduleId) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        log.info(
                "根据ID查询模块元数据信息: moduleId={}, projectNo={}, subjectId={}",
                moduleId,
                projectNo,
                subjectId);
        SysModuleMetaResp result =
                sysModuleService.getModuleCompleteById(projectNo, subjectId, moduleId);
        return ApiResponse.success(result);
    }

    /**
     * 查询模块下的状态组合的模块状态树
     *
     * <p>项目上下文通过 X-Project-No 请求头指定。
     *
     * @param moduleId 模块ID
     * @return 模块状态树列表
     */
    @Operation(summary = "查询模块下的状态树", description = "获取指定模块下关联的所有状态，并组合为状态树形结构")
    @GetMapping("/{moduleId}/status-tree")
    public ApiResponse<List<SysStatusTreeResp>> getModuleStatusTree(
            @Parameter(description = "模块ID", required = true, example = "1") @PathVariable
                    Long moduleId) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        log.info("查询项目 {} (主体 {}) 的模块状态树: moduleId={}", projectNo, subjectId, moduleId);
        List<SysStatusTreeResp> result =
                sysModuleService.getModuleStatusTree(projectNo, subjectId, moduleId);
        return ApiResponse.success(result);
    }

    /**
     * 保存或编辑模块完整信息 包含模块基本信息、关联表、简单字段配置和组合字段配置 如果 request.module.id 为空则创建新模块，否则编辑现有模块
     *
     * <p>项目上下文通过 X-Project-No 请求头指定。
     *
     * @param request 模块完整信息请求
     * @return 模块 ID
     */
    @Operation(
            summary = "保存或编辑模块完整信息",
            description = "一次性保存或编辑模块的所有配置信息，包括基本信息、关联表、简单字段和组合字段。若 module.id 为空则创建新模块，否则编辑现有模块")
    @PostMapping("/complete")
    public ApiResponse<Long> saveModuleComplete(@RequestBody SaveModuleReq request) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        log.info(
                "保存项目 {} (主体 {}) 的模块信息: moduleCode={}",
                projectNo,
                subjectId,
                request.getModule().getModuleCode());
        Long moduleId = sysModuleService.saveModule(projectNo, subjectId, request);
        return ApiResponse.success("保存成功", moduleId);
    }

    /**
     * 删除模块完整信息 删除模块及其所有关联的表、字段配置和状态
     *
     * <p>基于全局唯一 moduleId 进行操作。
     *
     * @param moduleId 模块ID
     * @return 删除成功消息
     */
    @Operation(summary = "删除模块完整信息", description = "删除模块及其所有关联的表、字段配置和状态信息")
    @DeleteMapping("/{moduleId}/complete")
    public ApiResponse<String> deleteModuleComplete(
            @Parameter(description = "模块ID", required = true, example = "1") @PathVariable
                    Long moduleId) {
        log.info("删除项目 {} 的模块: moduleId={}", AppContext.getProjectNo(), moduleId);
        sysModuleService.deleteModuleComplete(moduleId);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 移动模块 更新模块的父模块和排序顺序，用于处理拖拽移动场景
     *
     * <p>项目上下文通过 X-Project-No 请求头指定。
     *
     * @param request 移动模块请求（包含模块ID、目标父模块ID和排序顺序）
     * @return 移动后的模块变动信息
     */
    @Operation(summary = "移动模块", description = "移动模块到新的父模块下，并更新排序顺序。支持拖拽移动场景")
    @PostMapping("/move")
    public ApiResponse<MoveModuleResp> moveModule(@RequestBody MoveModuleReq request) {
        log.info(
                "移动项目 {} 的模块: moduleId={}, targetParentId={}, targetSortOrder={}",
                AppContext.getProjectNo(),
                request.getModuleId(),
                request.getTargetParentId(),
                request.getTargetSortOrder());
        MoveModuleResp result = sysModuleService.moveModule(request);
        return ApiResponse.success("移动成功", result);
    }
}
