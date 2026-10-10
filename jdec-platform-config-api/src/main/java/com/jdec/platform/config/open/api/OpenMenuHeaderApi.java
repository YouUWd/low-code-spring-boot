package com.jdec.platform.config.open.api;

import com.jdec.platform.config.api.dto.response.MenuHeaderDisplayResp;
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
     * 按字段权限查询列表表头树（供数据引擎等下游消费）
     *
     * <p>与 {@code SysMenuHeaderApi#getMenuHeaderTree} 同一套生成与合并逻辑，区别有两点：
     *
     * <ul>
     *   <li>入参 {@code menuId} 为「列表菜单ID」，内部按 {@link #resolveHeaderMenuId(Long)} 解析出表头归属菜单ID
     *       后再组树（「页面」类型 取关联的详情菜单ID，「表」等其它类型取菜单ID 自身）；
     *   <li>入参 {@code roleId} 非空时额外按 {@code sys_role_module_field_permission} 仅保留 {@code view=1}
     *       的字段（无记录即丢弃）；<b>不做角色菜单权限过滤，一律按最大权限返回全部菜单</b>；<b>既无字段节点也无子级菜单节点的空菜单节点一并丢弃</b>； {@code
     *       roleId} 为空时与 {@code SysMenuHeaderApi#getMenuHeaderTree} 完全一致。
     * </ul>
     *
     * @param menuId 列表菜单ID
     * @param change 变更标记：0-菜单关联的详情菜单未变化，1-详情菜单已切换
     * @param roleId 角色ID，为空时不做字段权限过滤
     * @return 菜单表头树的根节点；整树裁剪后为空时返回 {@code null}
     */
    SysMenuHeaderResp getMenuHeaderTree(Long menuId, Integer change, Long roleId);

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

    /**
     * 查询菜单默认多级列头展示结构（供数据引擎在用户未个性化时回退使用）
     *
     * <p>与 {@code SysMenuHeaderApi#getMenuHeaderDisplay} 同一套逻辑：纯读取 {@code sys_menu_header} 中该菜单（按
     * {@code subject_id} + {@code project_no} 隔离）已保存的节点，只输出已勾选（{@code
     * checked=1}）的列并保留其祖先菜单节点；无配置记录时返回空（{@code header} 为 null）。
     *
     * @param menuId 列表菜单ID，内部按 {@link #resolveHeaderMenuId(Long)} 解析出表头归属菜单ID 后读取
     * @return 多级列头展示结构
     */
    MenuHeaderDisplayResp getMenuHeaderDisplay(Long menuId);

    /**
     * 按字段权限查询菜单默认多级列头展示结构（供数据引擎在用户未个性化时回退使用）
     *
     * <p>与 {@link #getMenuHeaderDisplay(Long)} 同一套逻辑，区别是可用节点池额外按 {@code roleId} 做字段权限过滤： 非 {@code
     * view=1} 的字段节点不返回，且无字段的菜单节点一并被裁剪；菜单不做权限过滤。{@code roleId} 为空时等价于 {@link
     * #getMenuHeaderDisplay(Long)}。
     *
     * @param menuId 列表菜单ID，内部按 {@link #resolveHeaderMenuId(Long)} 解析出表头归属菜单ID 后读取
     * @param roleId 角色ID，为空时不做字段权限过滤
     * @return 多级列头展示结构
     */
    MenuHeaderDisplayResp getMenuHeaderDisplay(Long menuId, Long roleId);
}
