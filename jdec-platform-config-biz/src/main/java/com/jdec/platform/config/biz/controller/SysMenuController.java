package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysMenuApi;
import com.jdec.platform.config.api.dto.request.SysMenuDragReq;
import com.jdec.platform.config.api.dto.request.SysMenuSaveReq;
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

    @GetMapping("/tree")
    @Operation(summary = "查询菜单树", description = "按菜单分类查询所有菜单的树形结构")
    public ApiResponse<List<SysMenuTreeResp>> tree(
            @Parameter(description = "菜单分类.1:业务菜单 2:系统菜单", example = "1") @RequestParam
                    Integer category) {
        return ApiResponse.success(sysMenuApi.tree(category));
    }

    @PostMapping("/save")
    @Operation(summary = "新增/编辑菜单", description = "id为空则新增，不为空则修改")
    public ApiResponse<Void> save(@RequestBody SysMenuSaveReq request) {
        sysMenuApi.saveMenu(request);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "删除菜单",
            description = "删除指定菜单及其所有子菜单。force=false 时若有子菜单或角色授权则返回提示；force=true 时级联删除")
    public ApiResponse<Void> delete(
            @Parameter(description = "菜单ID", example = "1") @PathVariable Long id,
            @Parameter(description = "是否强制删除", example = "false")
                    @RequestParam(defaultValue = "false")
                    boolean force) {
        sysMenuApi.deleteMenu(id, force);
        return ApiResponse.success();
    }

    @PostMapping("/drag")
    @Operation(summary = "拖拽菜单", description = "将菜单拖拽到指定父菜单下，拖到顶层时targetPid传0")
    public ApiResponse<Void> drag(@RequestBody SysMenuDragReq request) {
        sysMenuApi.dragMenu(request);
        return ApiResponse.success();
    }
}
