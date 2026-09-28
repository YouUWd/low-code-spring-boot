package com.jdec.platform.dataengine.biz.third.api.module;

import com.jdec.platform.dataengine.biz.dto.MenuListResp;
import java.util.List;

/** 菜单ID和模块之间的映射以及菜单ID获取所有子模块相关信息 */
public interface MenuModuleMappingService {

    /**
     * 调用组件: 系统配置： /api/config/menu/list 获取menuId对应的业务菜单信息
     *
     * @param menuId
     * @return
     */
    MenuListResp findTargetMenu(Long menuId);

    /**
     * 调用组件: 系统配置： /api/config/menu/list 获取顶层菜单ID及其子模块的菜单列表，List按层级顺序add
     *
     * @param menus 不带角色过滤，按sortOrder排序后的menu平铺列表
     * @param menuId
     * @return
     */
    List<MenuListResp> findTargetAndAllChildren(List<MenuListResp> menus, Long menuId);

    /**
     * 调用组件: 系统配置： /api/config/menu/list 获取顶层菜单ID及其子模块的菜单列表，List按层级顺序add
     *
     * <p>不带角色过滤，按sortOrder排序后的menu平铺列表
     *
     * @param menuId
     * @return
     */
    List<MenuListResp> findTargetAndAllChildrenByMenuId(Long menuId);
}
