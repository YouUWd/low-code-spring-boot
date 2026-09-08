package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysMenuHeaderApi;
import com.jdec.platform.config.api.dto.response.SysMenuHeaderResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 列表表头节点 REST 控制器
 *
 * <p>提供菜单表头节点的管理接口。
 */
@RestController
@RequestMapping("/api/config/menu-header")
@RequiredArgsConstructor
@Tag(name = "菜单表头节点", description = "菜单表头节点管理接口")
public class SysMenuHeaderController {

    private final SysMenuHeaderApi sysMenuHeaderApi;

    @GetMapping("/tree")
    @Operation(summary = "查询菜单表头树", description = "根据菜单ID查询该菜单及其子菜单/字段构成的表头节点树，无配置时按菜单绑定模块的字段生成默认树")
    public ApiResponse<SysMenuHeaderResp> tree(
            @Parameter(description = "菜单ID", example = "1") @RequestParam Long menuId) {
        return ApiResponse.success(sysMenuHeaderApi.getMenuHeaderTree(menuId));
    }
}
