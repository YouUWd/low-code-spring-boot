package com.jdec.platform.config.open.api;

import com.jdec.platform.config.api.dto.response.SysMenuHeaderResp;
import java.util.Collection;
import java.util.Set;

/** 列表表头节点 API 接口 */
public interface OpenMenuHeaderApi {

    /**
     * 列表菜单ID → 表头归属菜单ID
     *
     * <p>表头记录（{@code sys_menu_header} / {@code user_menu_header}）按表头归属菜单ID 存放：
     *
     * <ul>
     *   <li>菜单类型为「页面」且关联了详情菜单 → 取关联的详情菜单ID；
     *   <li>菜单类型为「表」等其它情形 → 取菜单ID 自身，不做转换。
     * </ul>
     *
     * @param menuId 列表菜单ID
     * @return 表头归属菜单ID
     */
    Long resolveHeaderMenuId(Long menuId);

    /**
     * 按角色权限查询列表表头树（供数据引擎等下游消费）
     *
     * <p>与 {@code SysMenuHeaderApi#getMenuHeaderTree} 同一套生成与合并逻辑，区别有两点：
     *
     * <ul>
     *   <li>入参 {@code menuId} 为「列表菜单ID」，内部按 {@link #resolveHeaderMenuId(Long)} 解析出表头归属菜单ID
     *       后再组树（「页面」类型 取关联的详情菜单ID，「表」等其它类型取菜单ID 自身）；
     *   <li>入参 {@code roleId} 非空时额外做角色权限过滤：按 {@code sys_role_menu} 过滤无权限的子级菜单，按 {@code
     *       sys_role_module_field_permission} 仅保留 {@code view=1} 的字段；{@code roleId} 为空时与 {@code
     *       SysMenuHeaderApi#getMenuHeaderTree} 完全一致。
     * </ul>
     *
     * @param menuId 列表菜单ID
     * @param change 变更标记：0-菜单关联的详情菜单未变化，1-详情菜单已切换
     * @param roleId 角色ID，为空时不做角色权限过滤
     * @return 菜单表头树的根节点
     */
    SysMenuHeaderResp getMenuHeaderTree(Long menuId, Integer change, Long roleId);

    /**
     * 查询角色有权限的菜单ID集合
     *
     * <p>按 {@code sys_role_menu} 取该角色可见的菜单ID；{@code roleId} 为空时返回 {@code null}，表示不做菜单权限过滤。
     *
     * @param roleId 角色ID
     * @return 有权限的菜单ID集合，roleId 为空时返回 null
     */
    Set<Long> selectPermittedMenuIds(Long roleId);

    /**
     * 查询角色可查阅（{@code view=1}）的字段ID集合
     *
     * <p>按 {@code sys_role_module_field_permission} 取该角色可查阅的字段ID，无记录即无权限；{@code roleId} 为空时返回
     * {@code null}，表示不做字段权限过滤。
     *
     * @param roleId 角色ID
     * @param fieldIds 待校验的模块字段ID集合
     * @return 可查阅的字段ID集合，roleId 为空时返回 null
     */
    Set<Long> selectReadableFieldIds(Long roleId, Collection<Long> fieldIds);
}
