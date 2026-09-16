package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysMenuHeaderApi;
import com.jdec.platform.config.api.dto.response.MenuHeaderDisplayResp;
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
    @Operation(
            summary = "查询菜单表头树",
            description =
                    "根据菜单ID查询该菜单及其子菜单/字段构成的表头节点树。change=0 时与 sys_menu_header 已保存配置按 virId 合并"
                            + "（两边都有取表中记录、仅表中有丢弃、仅生成侧有取生成侧默认值）；change=1 时只按菜单当前绑定模块的字段生成，不读表。"
                            + "纯查询接口，不落库：递归子菜单生成的节点不带主键id")
    public ApiResponse<SysMenuHeaderResp> tree(
            @Parameter(description = "菜单ID", example = "1") @RequestParam Long menuId,
            @Parameter(description = "变更标记：0-菜单关联模块未变化，1-菜单关联模块已切换", example = "0")
                    @RequestParam(defaultValue = "0")
                    Integer change) {
        return ApiResponse.success(sysMenuHeaderApi.getMenuHeaderTree(menuId, change));
    }

    @GetMapping("/display")
    @Operation(
            summary = "查询菜单多级列头展示",
            description = "根据菜单ID纯读取 sys_menu_header 已保存的配置，按多级列头展示格式返回；无配置时 header 为空")
    public ApiResponse<MenuHeaderDisplayResp> display(
            @Parameter(description = "菜单ID", example = "1") @RequestParam Long menuId) {
        return ApiResponse.success(sysMenuHeaderApi.getMenuHeaderDisplay(menuId));
    }
}
