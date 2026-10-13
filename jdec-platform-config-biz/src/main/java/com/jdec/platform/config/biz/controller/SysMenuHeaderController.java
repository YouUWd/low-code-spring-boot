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
                            + "层级(parentId)/模块(moduleId)/展示名(label) 均按模块侧动态生成，实时反映菜单与模块绑定，菜单改绑模块后历史节点自然被丢弃；"
                            + "再与 sys_menu_header 已保存的展示配置按 virId 合并"
                            + "（两边都有以表记录覆写展示项、仅表中有丢弃、仅生成侧有取生成侧默认值）。"
                            + "纯查询接口，不落库：递归子菜单生成的节点不带主键id")
    public ApiResponse<SysMenuHeaderResp> tree(
            @Parameter(description = "菜单ID", example = "1") @RequestParam Long menuId) {
        return ApiResponse.success(sysMenuHeaderApi.getMenuHeaderTree(menuId));
    }

    @GetMapping("/display")
    @Operation(
            summary = "查询菜单多级列头展示",
            description =
                    "根据菜单ID先取「菜单/字段生成 + sys_menu_header 已保存展示配置」合并后的完整树（菜单ID 先按统一规则解析出表头归属菜单ID 再读取），"
                            + "结构性内容（层级/模块/展示名）按模块侧动态生成，模块侧已删除的菜单/字段不再返回；"
                            + "按多级列头展示格式返回：只输出已勾选（checked=1）的列，其祖先菜单节点一并保留用于组装多级列头；无已勾选配置时 header 为空")
    public ApiResponse<MenuHeaderDisplayResp> display(
            @Parameter(description = "菜单ID", example = "1") @RequestParam Long menuId) {
        return ApiResponse.success(sysMenuHeaderApi.getMenuHeaderDisplay(menuId));
    }
}
