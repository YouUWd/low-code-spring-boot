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
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 模块管理 Controller 提供模块完整信息的 CRUD 操作
 *
 * <p>数据源通过 HTTP 请求头 X-Tenant-Id 指定，由 TenantInterceptor 自动设置。
 *
 * <p>使用示例：
 *
 * <pre>
 * # 获取模块列表
 * curl -H "X-Tenant-Id: config_center" http://localhost:8080/api/config/modules
 *
 * # 查询模块完整信息
 * curl -H "X-Tenant-Id: config_center" http://localhost:8080/api/config/modules/1/complete
 *
 * # 保存模块
 * curl -X POST -H "X-Tenant-Id: config_center" -H "Content-Type: application/json" \
 *   -d '{"module": {...}}' http://localhost:8080/api/config/modules/complete
 * </pre>
 */
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
     * <p>数据源通过 X-Tenant-Id 请求头指定。
     *
     * @param tenantId 租户/数据源 ID（通过 X-Tenant-Id 请求头指定）
     * @return 模块列表
     */
    @Operation(summary = "获取模块列表", description = "获取所有模块的基本信息（不包含树形结构），前端自行构建树")
    @GetMapping
    public ApiResponse<List<SysModuleListResp>> listModules(
            @Parameter(description = "租户/数据源 ID", example = "config_center", required = true)
                    @RequestHeader("X-Tenant-Id")
                    String tenantId) {
        log.info("查询数据源 {} 的模块列表", tenantId);
        List<SysModuleListResp> result = sysModuleService.listModules(tenantId);
        return ApiResponse.success(result);
    }

    /**
     * 查询模块完整信息 包含模块基本信息、关联表、字段配置和状态
     *
     * <p>数据源通过 X-Tenant-Id 请求头指定。
     *
     * @param moduleCode 模块编码
     * @param tenantId 租户/数据源 ID（通过 X-Tenant-Id 请求头指定）
     * @return 模块完整信息
     */
    @Operation(summary = "查询模块完整信息", description = "获取模块的所有配置信息，包括基本信息、关联表、简单字段、组合字段和状态配置")
    @GetMapping("/{moduleCode}/complete")
    public ApiResponse<SysModuleCompleteResp> getModuleComplete(
            @Parameter(description = "模块编码", required = true, example = "1") @PathVariable
                    String moduleCode,
            @Parameter(description = "租户/数据源 ID", example = "config_center", required = true)
                    @RequestHeader("X-Tenant-Id")
                    String tenantId) {
        log.info("查询数据源 {} 的模块完整信息: moduleId={}", tenantId, moduleCode);
        SysModuleCompleteResp result = sysModuleService.getModuleComplete(tenantId, moduleCode);
        return ApiResponse.success(result);
    }

    /**
     * 保存或编辑模块完整信息 包含模块基本信息、关联表、简单字段配置和组合字段配置 如果 request.module.id 为空则创建新模块，否则编辑现有模块
     *
     * <p>数据源通过 X-Tenant-Id 请求头指定。
     *
     * @param request 模块完整信息请求
     * @param tenantId 租户/数据源 ID（通过 X-Tenant-Id 请求头指定）
     * @return 保存后的模块详细信息
     */
    @Operation(
            summary = "保存或编辑模块完整信息",
            description = "一次性保存或编辑模块的所有配置信息，包括基本信息、关联表、简单字段和组合字段。若 module.id 为空则创建新模块，否则编辑现有模块")
    @PostMapping("/complete")
    public ApiResponse<SysModuleDetailResp> saveModuleComplete(
            @RequestBody SaveModuleReq request,
            @Parameter(description = "租户/数据源 ID", example = "config_center", required = true)
                    @RequestHeader("X-Tenant-Id")
                    String tenantId) {
        log.info("保存数据源 {} 的模块信息: moduleCode={}", tenantId, request.getModule().getModuleCode());
        SysModuleDetailResp result = sysModuleService.saveModule(request);
        return ApiResponse.success("保存成功", result);
    }

    /**
     * 删除模块完整信息 删除模块及其所有关联的表、字段配置和状态
     *
     * <p>数据源通过 X-Tenant-Id 请求头指定。
     *
     * @param moduleId 模块ID
     * @param tenantId 租户/数据源 ID（通过 X-Tenant-Id 请求头指定）
     * @return 删除成功消息
     */
    @Operation(summary = "删除模块完整信息", description = "删除模块及其所有关联的表、字段配置和状态信息")
    @DeleteMapping("/{moduleId}/complete")
    public ApiResponse<String> deleteModuleComplete(
            @Parameter(description = "模块ID", required = true, example = "1") @PathVariable
                    Long moduleId,
            @Parameter(description = "租户/数据源 ID", example = "config_center", required = true)
                    @RequestHeader("X-Tenant-Id")
                    String tenantId) {
        log.info("删除数据源 {} 的模块: moduleId={}", tenantId, moduleId);
        sysModuleService.deleteModuleComplete(moduleId);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 移动模块 更新模块的父模块和排序顺序，用于处理拖拽移动场景
     *
     * <p>数据源通过 X-Tenant-Id 请求头指定。
     *
     * @param request 移动模块请求（包含模块ID、目标父模块ID和排序顺序）
     * @param tenantId 租户/数据源 ID（通过 X-Tenant-Id 请求头指定）
     * @return 移动后的模块详细信息
     */
    @Operation(summary = "移动模块", description = "移动模块到新的父模块下，并更新排序顺序。支持拖拽移动场景")
    @PostMapping("/move")
    public ApiResponse<SysModuleDetailResp> moveModule(
            @RequestBody MoveModuleReq request,
            @Parameter(description = "租户/数据源 ID", example = "config_center", required = true)
                    @RequestHeader("X-Tenant-Id")
                    String tenantId) {
        log.info(
                "移动数据源 {} 的模块: moduleId={}, targetParentId={}, targetSortOrder={}",
                tenantId,
                request.getModuleId(),
                request.getTargetParentId(),
                request.getTargetSortOrder());
        SysModuleDetailResp result = sysModuleService.moveModule(request);
        return ApiResponse.success("移动成功", result);
    }
}
