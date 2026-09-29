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
                    "根据菜单ID查询该菜单及其子菜单/字段构成的表头节点树。菜单ID 先按统一规则解析表头归属菜单（页面类型取关联的详情菜单ID，表等其它类型取菜单ID 自身）；"
                            + "change=0 时与 sys_menu_header 已保存配置按 virId 合并"
                            + "（两边都有取表中记录、仅表中有丢弃、仅生成侧有取生成侧默认值）；change=1 时只按菜单当前绑定模块的字段生成，不读表。"
                            + "纯查询接口，不落库：递归子菜单生成的节点不带主键id")
    public ApiResponse<SysMenuHeaderResp> tree(
            @Parameter(description = "菜单ID", example = "1") @RequestParam Long menuId,
            @Parameter(description = "变更标记：0-菜单关联的详情菜单未变化，1-详情菜单已切换", example = "0")
                    @RequestParam(defaultValue = "0")
                    Integer change) {
        return ApiResponse.success(sysMenuHeaderApi.getMenuHeaderTree(menuId, change));
    }

    @GetMapping("/display")
    @Operation(
            summary = "查询菜单多级列头展示",
            description =
                    "根据菜单ID读取 sys_menu_header 已保存的配置（菜单ID 先按统一规则解析出表头归属菜单ID 再读取），读取前先取模块侧最新数据（菜单及其子菜单、绑定模块下的字段）构成节点池，"
                            + "仅返回节点池中仍存在的节点（模块侧已删除的菜单/字段不再返回）；按多级列头展示格式返回：只输出已勾选（checked=1）的列，"
                            + "其祖先菜单节点一并保留用于组装多级列头；无已勾选配置时 header 为空")
    public ApiResponse<MenuHeaderDisplayResp> display(
            @Parameter(description = "菜单ID", example = "1") @RequestParam Long menuId) {
        return ApiResponse.success(sysMenuHeaderApi.getMenuHeaderDisplay(menuId));
    }
}
