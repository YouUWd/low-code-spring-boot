package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.response.SysMenuHeaderResp;

/** 列表表头节点 API 接口 */
public interface SysMenuHeaderApi {

    /**
     * 查询菜单表头树
     *
     * <p>根据菜单ID查询该菜单及其子菜单/字段构成的表头节点树。若 {@code sys_menu_header} 已存在该菜单的配置记录，
     * 则按已保存的节点递归组装返回；否则视为首次查询，以菜单本身为根节点，依据菜单绑定的模块（sys_menu.module_id -> sys_module_field）生成默认表头树。
     *
     * @param menuId 菜单ID
     * @return 菜单表头树的根节点
     */
    SysMenuHeaderResp getMenuHeaderTree(Long menuId);
}
