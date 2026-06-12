package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysConfigCategoryApi;
import com.jdec.platform.config.api.dto.request.SysConfigCategorySaveReq;
import com.jdec.platform.config.api.dto.request.SysConfigItemDragReq;
import com.jdec.platform.config.api.dto.request.SysConfigItemSaveReq;
import com.jdec.platform.config.api.dto.response.AllConfigResp;
import com.jdec.platform.config.api.dto.response.SysConfigCategoryResp;
import com.jdec.platform.config.api.dto.response.SysConfigItemTreeResp;
import com.jdec.platform.config.api.dto.response.SysConfigTreeNodeResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 配置管理 REST 控制器
 *
 * <p>提供配置分类和配置项的统一管理接口。
 */
@RestController
@RequestMapping("/api/config/category")
@RequiredArgsConstructor
@Tag(name = "通用配置管理", description = "配置分类和配置项的统一管理接口")
public class SysConfigCategoryController {

    private final SysConfigCategoryApi sysConfigCategoryApi;

    // ==================== 配置分类接口 ====================

    @GetMapping("/list")
    @Operation(summary = "查询配置分类列表", description = "查询当前应用的所有配置分类")
    public ApiResponse<List<SysConfigCategoryResp>> list() {
        return ApiResponse.success(sysConfigCategoryApi.list());
    }

    @GetMapping("/allConfigs")
    @Operation(summary = "查询所有通用配置", description = "查询所有配置分类及其配置项，按分类分组返回")
    public ApiResponse<List<AllConfigResp>> allConfigs() {
        return ApiResponse.success(sysConfigCategoryApi.getAllConfigs());
    }

    @GetMapping("/mixedTree")
    @Operation(summary = "查询混合树", description = "查询配置分类和配置项的混合树结构")
    public ApiResponse<List<SysConfigTreeNodeResp>> mixedTree() {
        return ApiResponse.success(sysConfigCategoryApi.mixedTree());
    }

    @GetMapping("/getByCategoryAlias")
    @Operation(summary = "根据categoryAlias查询配置分类", description = "根据分类英文标识查询配置分类")
    public ApiResponse<SysConfigCategoryResp> getByCategoryAlias(
            @Parameter(description = "分类英文标识", example = "employee_status") @RequestParam
                    String categoryAlias) {
        return ApiResponse.success(sysConfigCategoryApi.getByCategoryAlias(categoryAlias));
    }

    @PostMapping("/save")
    @Operation(summary = "新增/编辑配置分类", description = "id为空则新增，不为空则修改；新增时返回新记录ID")
    public ApiResponse<Long> save(@RequestBody SysConfigCategorySaveReq request) {
        return ApiResponse.success(sysConfigCategoryApi.saveCategory(request));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "删除配置分类",
            description = "删除指定配置分类及其所有配置项。force=false 时若有配置项则返回提示；force=true 时级联删除")
    public ApiResponse<Void> delete(
            @Parameter(description = "分类ID", example = "1") @PathVariable Long id,
            @Parameter(description = "是否强制删除", example = "false")
                    @RequestParam(defaultValue = "false")
                    boolean force) {
        sysConfigCategoryApi.deleteCategory(id, force);
        return ApiResponse.success();
    }

    // ==================== 配置项接口 ====================

    @GetMapping("/item/byCategory")
    @Operation(summary = "查询配置项（按分类ID）", description = "按分类ID查询配置项，根据分类format自动返回树形或列表结构")
    public ApiResponse<List<SysConfigItemTreeResp>> itemByCategory(
            @Parameter(description = "分类ID", example = "1") @RequestParam Long categoryId) {
        return ApiResponse.success(sysConfigCategoryApi.getItemsByCategory(categoryId));
    }

    @GetMapping("/item/byCategoryAlias")
    @Operation(summary = "查询配置项（按分类alias）", description = "按分类alias查询配置项，根据分类format自动返回树形或列表结构")
    public ApiResponse<List<SysConfigItemTreeResp>> itemByCategoryAlias(
            @Parameter(description = "分类英文标识", example = "employee_status") @RequestParam
                    String categoryAlias) {
        return ApiResponse.success(sysConfigCategoryApi.getItemsByCategoryAlias(categoryAlias));
    }

    @PostMapping("/item/save")
    @Operation(summary = "新增/编辑配置项", description = "id为空则新增，不为空则修改；新增时返回新记录ID")
    public ApiResponse<Long> saveItem(@RequestBody SysConfigItemSaveReq request) {
        return ApiResponse.success(sysConfigCategoryApi.saveItem(request));
    }

    @DeleteMapping("/item/{id}")
    @Operation(
            summary = "删除配置项",
            description = "删除指定配置项及其所有子项。force=false 时若有子项则返回提示；force=true 时级联删除")
    public ApiResponse<Void> deleteItem(
            @Parameter(description = "配置项ID", example = "1") @PathVariable Long id,
            @Parameter(description = "是否强制删除", example = "false")
                    @RequestParam(defaultValue = "false")
                    boolean force) {
        sysConfigCategoryApi.deleteItem(id, force);
        return ApiResponse.success();
    }

    @PostMapping("/item/drag")
    @Operation(
            summary = "拖拽配置项",
            description = "支持将配置项拖拽到其它层级。限制：item 不能拖到 category 层级，只能在 item 之间拖拽")
    public ApiResponse<Void> dragItem(@RequestBody SysConfigItemDragReq request) {
        sysConfigCategoryApi.dragItem(request);
        return ApiResponse.success();
    }
}
