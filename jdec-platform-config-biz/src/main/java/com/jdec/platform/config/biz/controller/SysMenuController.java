package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysMenuApi;
import com.jdec.platform.config.api.dto.request.SaveMenuReq;
import com.jdec.platform.config.api.dto.request.SysMenuDragReq;
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
    @Operation(
            summary = "新增/编辑菜单",
            description =
                    "menu.id 为空则新增，不为空则修改，返回保存后的菜单信息。支持前端将整棵菜单树一并提交，treeNodes 非空时差量同步整棵树的父子关系与同级排序（排序由前端决定），treeNodes 中缺失的节点不做删除。"
                            + "同时支持多级表头配置随菜单一并保存，表头记录按「详情菜单ID」归属（菜单未关联详情菜单时不允许保存表头）：变更标记由后端比对库中记录的详情菜单ID 自行判定，"
                            + "详情菜单已变动时先删除变更前归属菜单下的表头配置再按 header 落库，未变动时按 header 节点树批量更新/新增表头记录；"
                            + "两种情形下库中已保存、但本次 header 未提交的节点均一并删除（整棵树以本次提交为准）")
    public ApiResponse<SysMenuResp> save(@RequestBody SaveMenuReq request) {
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
