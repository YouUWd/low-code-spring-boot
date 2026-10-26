package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.response.MenuHeaderDisplayResp;
import com.jdec.platform.config.api.dto.response.SysMenuHeaderResp;

/** 列表表头节点 API 接口 */
public interface SysMenuHeaderApi {

    /**
     * 查询菜单表头树
     *
     * <p>以该菜单为根，递归其全部子级菜单，并按菜单绑定模块（sys_menu.module_id -> sys_module_field）取字段，得到生成侧节点： {@code
     * menuId} 统一取最顶层菜单ID，{@code virId} 为「类型_主键」（菜单 MENU_{id}、字段 Field_{id}）， {@code parentId} 为父节点
     * virId（顶层为 0），且 sorted=1、ellipsis=0、checked=0。
     *
     * <p><b>层级（{@code parentId}）、模块（{@code moduleId}）与展示名（{@code label}）均每次按模块侧动态生成</b>，不再从 {@code
     * sys_menu_header} 表记录读取，以实时反映前端对菜单与模块绑定的调整；表中仅保存前端改动的展示配置项。
     *
     * <p>该接口为纯查询，不产生任何落库行为；递归子菜单生成的节点直接返回，不带数据库主键 {@code id}，仅 {@code sys_menu_header} 已保存的节点才带
     * {@code id}。
     *
     * <p>生成侧与 {@code sys_menu_header} 已保存配置按 {@code virId}
     * 合并：两边都有的节点以表记录的展示配置覆写生成侧默认值（层级/模块/展示名仍取生成侧）； 仅表中有的节点丢弃不再返回；仅生成侧有的节点以生成侧默认值返回。菜单改绑模块后其旧字段的
     * {@code virId} 不再出现在生成侧，历史记录自然被丢弃，无需额外处理。
     *
     * @param menuId 菜单ID
     * @return 菜单表头树的根节点
     */
    SysMenuHeaderResp getMenuHeaderTree(Long menuId);

    /**
     * 查询菜单多级列头展示结构
     *
     * <p>先取「菜单/字段生成 + {@code sys_menu_header} 已保存展示配置」合并后的完整树（结构动态生成），再只保留已勾选（{@code
     * checked=1}）的列及其祖先菜单节点，按多级列头展示格式组装返回：菜单节点输出 {@code
     * sourceId/moduleId/label/colors/children}，字段节点输出 {@code
     * label/fieldId/moduleId/dataIndex/width/sorted/searchType/ellipsis/fixed/sortValue}。{@code
     * colors} 与 {@code searchType} 在库中均为 JSON 串，此处解析后输出为数组/对象。
     *
     * <p>入参 {@code menuId} 为列表菜单ID，内部先按统一规则解析表头归属菜单ID（「页面」类型取关联的详情菜单ID，「表」等其它类型取菜单ID 自身）再读取。
     *
     * <p>该接口不做落库；无已勾选配置时返回空（header 为 null）。
     *
     * @param menuId 菜单ID
     * @return 多级列头展示结构
     */
    MenuHeaderDisplayResp getMenuHeaderDisplay(Long menuId);
}
