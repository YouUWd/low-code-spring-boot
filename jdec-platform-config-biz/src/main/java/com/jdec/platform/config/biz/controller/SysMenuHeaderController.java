package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysMenuHeaderApi;
import com.jdec.platform.config.api.dto.request.SysMenuHeaderReq;
import com.jdec.platform.config.api.dto.request.UpdateSysMenuHeaderReq;
import com.jdec.platform.config.api.dto.response.SysMenuHeaderResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
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
    @Operation(
            summary = "查询菜单表头树",
            description = "根据菜单ID查询该菜单及其子菜单/字段构成的表头节点树，无配置时按菜单绑定模块的字段生成默认树并落库后返回")
    public ApiResponse<SysMenuHeaderResp> tree(
            @Parameter(description = "菜单ID", example = "1") @RequestParam Long menuId) {
        return ApiResponse.success(sysMenuHeaderApi.getMenuHeaderTree(menuId));
    }

    @PostMapping("/save")
    @Operation(
            summary = "保存菜单表头配置",
            description =
                    "批量更新表头节点，按 menuId + 虚拟id(id) 定位记录，更新 parentId、sortOrder、fixed、checked；顶层节点 parentId 传 0")
    public ApiResponse<Void> save(@RequestBody List<SysMenuHeaderReq> items) {
        sysMenuHeaderApi.saveMenuHeader(items);
        return ApiResponse.success();
    }

    @PutMapping("/edit")
    @Operation(
            summary = "编辑菜单表头节点展示配置",
            description = "按主键 id 更新表头节点的 colors/width/sorted/searchType/ellipsis，字段均非必填，未传字段保持原值")
    public ApiResponse<Void> edit(@RequestBody UpdateSysMenuHeaderReq req) {
        sysMenuHeaderApi.updateMenuHeader(req);
        return ApiResponse.success();
    }
}
