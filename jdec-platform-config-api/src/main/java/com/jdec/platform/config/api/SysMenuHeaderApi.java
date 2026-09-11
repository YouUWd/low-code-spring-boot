package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.SysMenuHeaderReq;
import com.jdec.platform.config.api.dto.request.UpdateSysMenuHeaderReq;
import com.jdec.platform.config.api.dto.response.SysMenuHeaderResp;
import java.util.List;

/** 列表表头节点 API 接口 */
public interface SysMenuHeaderApi {

    /**
     * 查询菜单表头树
     *
     * <p>根据菜单ID查询该菜单及其子菜单/字段构成的表头节点树。若 {@code sys_menu_header} 已存在该菜单的配置记录，
     * 则按已保存的节点递归组装返回；否则视为首次查询，以菜单本身为根节点，依据菜单绑定的模块（sys_menu.module_id -> sys_module_field）生成默认表头树，
     * 并将生成的默认树落库到 {@code sys_menu_header} 后再返回。
     *
     * <p>首次查询生成的节点：{@code menuId} 统一取最顶层菜单ID，{@code virId} 为「类型_主键」（菜单 MENU_{id}、字段
     * Field_{id}），{@code parentId} 为父节点 virId（顶层为 0），且 sorted=1、ellipsis=0、checked=0。落库后节点带有数据库主键
     * {@code id}。
     *
     * @param menuId 菜单ID
     * @return 菜单表头树的根节点
     */
    SysMenuHeaderResp getMenuHeaderTree(Long menuId);

    /**
     * 保存菜单表头配置（批量更新父节点引用与排序）
     *
     * <p>按 {@code menu_id + vir_id} 定位记录，更新 {@code parent_id}（parentId）、{@code sort_value}
     * （sortOrder）、{@code fixed}、{@code checked} 字段。请求项中 {@code id} 为节点虚拟id（对应 {@code vir_id}），顶层节点
     * parentId 传 0。
     *
     * @param items 待更新的表头节点列表
     */
    void saveMenuHeader(List<SysMenuHeaderReq> items);

    /**
     * 更新菜单表头节点展示配置
     *
     * <p>按主键 {@code id} 定位记录，仅更新传入的非空字段：{@code colors}、{@code width}、{@code sorted}、 {@code
     * searchType}、{@code ellipsis}，未传字段保持原值。所有字段均非必填，不做校验。
     *
     * @param req 更新请求
     */
    void updateMenuHeader(UpdateSysMenuHeaderReq req);
}
