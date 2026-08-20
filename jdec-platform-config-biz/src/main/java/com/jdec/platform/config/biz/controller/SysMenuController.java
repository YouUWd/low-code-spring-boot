package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysMenuApi;
import com.jdec.platform.config.api.dto.request.SysMenuDragReq;
import com.jdec.platform.config.api.dto.request.SysMenuSaveReq;
import com.jdec.platform.config.api.dto.response.SysMenuResp;
import com.jdec.platform.config.api.dto.response.SysMenuTreeResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 菜单配置 REST 控制器
 *
 * <p>提供菜单的树形展示、增删改、拖拽接口。
 */
@RestController
@RequestMapping("/api/config/menu")
@RequiredArgsConstructor
@Tag(name = "菜单配置", description = "菜单的树形展示、增删改、拖拽接口")
public class SysMenuController {

    private final SysMenuApi sysMenuApi;

    @GetMapping("/list")
    @Operation(summary = "查询菜单列表", description = "按菜单分类查询菜单平铺列表，不传分类则返回所有菜单")
    public ApiResponse<List<SysMenuResp>> list(
            @Parameter(description = "菜单分类.1:业务菜单 2:系统菜单（可选）", example = "1")
                    @RequestParam(required = false)
                    Integer category) {
        return ApiResponse.success(sysMenuApi.list(category));
    }

    @GetMapping("/tree")
    @Operation(summary = "查询菜单树", description = "按菜单分类查询所有菜单的树形结构，不传分类则返回所有菜单")
    public ApiResponse<List<SysMenuTreeResp>> tree(
            @Parameter(description = "菜单分类.1:业务菜单 2:系统菜单（可选）", example = "1")
                    @RequestParam(required = false)
                    Integer category) {
        return ApiResponse.success(sysMenuApi.tree(category));
    }

    @GetMapping("/param")
    @Operation(summary = "根据参数查询菜单详情", description = "根据菜单参数查询菜单详情")
    public ApiResponse<SysMenuResp> getByParam(
            @Parameter(description = "菜单参数", example = "param") @RequestParam String param) {
        return ApiResponse.success(sysMenuApi.getMenuByParam(param));
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询菜单详情", description = "根据菜单ID查询菜单详情")
    public ApiResponse<SysMenuResp> getById(
            @Parameter(description = "菜单ID", example = "1") @PathVariable Long id) {
        return ApiResponse.success(sysMenuApi.getMenuById(id));
    }

    @PostMapping("/save")
    @Operation(summary = "新增/编辑菜单", description = "id为空则新增，不为空则修改，返回保存后的菜单信息")
    public ApiResponse<SysMenuResp> save(@RequestBody SysMenuSaveReq request) {
        return ApiResponse.success(sysMenuApi.saveMenu(request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除菜单", description = "删除指定菜单及其所有子菜单")
    public ApiResponse<Void> delete(
            @Parameter(description = "菜单ID", example = "1") @PathVariable Long id,
            @Parameter(description = "菜单分类（如：业务菜单/系统菜单），用于审计日志", example = "业务菜单") @RequestParam
                    String category) {
        sysMenuApi.deleteMenu(id, category);
        return ApiResponse.success();
    }

    @PostMapping("/drag")
    @Operation(summary = "拖拽菜单", description = "将菜单拖拽到指定父菜单下，拖到顶层时targetPid传0")
    public ApiResponse<Void> drag(@RequestBody SysMenuDragReq request) {
        sysMenuApi.dragMenu(request);
        return ApiResponse.success();
    }
}
