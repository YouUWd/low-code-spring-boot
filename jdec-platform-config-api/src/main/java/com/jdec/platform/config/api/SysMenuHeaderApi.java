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
     * <p>该接口为纯查询，不产生任何落库行为；递归子菜单生成的节点直接返回，不带数据库主键 {@code id}，仅 {@code sys_menu_header} 已保存的节点才带
     * {@code id}。
     *
     * <p>按 {@code change} 决定生成侧与 {@code sys_menu_header} 已保存配置的合并方式：
     *
     * <ul>
     *   <li>{@code change=0}（菜单关联模块未变化）：两边按 {@code virId} 合并 —— 两边都有的节点取 {@code sys_menu_header}
     *       中的记录（保留用户配置）；仅表中有的节点丢弃不再返回；仅生成侧有的节点以生成侧默认值返回。
     *   <li>{@code change=1}（菜单关联模块已切换、表头尚未更新入库）：只用生成侧，不读 {@code sys_menu_header}。
     * </ul>
     *
     * @param menuId 菜单ID
     * @param change 变更标记：0-菜单关联模块未变化，1-菜单关联模块已切换
     * @return 菜单表头树的根节点
     */
    SysMenuHeaderResp getMenuHeaderTree(Long menuId, Integer change);

    /**
     * 查询菜单多级列头展示结构
     *
     * <p>纯读取 {@code sys_menu_header} 中该菜单（按 {@code subject_id} + {@code project_no} 隔离）已保存的节点，
     * 按多级列头展示格式组装返回：菜单节点输出 {@code sourceId/moduleId/label/colors/children}，字段节点输出 {@code
     * label/fieldId/moduleId/dataIndex/width/sorted/searchType/ellipsis/fixed/sortValue}。{@code
     * colors} 与 {@code searchType} 在库中均为 JSON 串，此处解析后输出为数组/对象。
     *
     * <p>入参 {@code menuId} 为列表菜单ID，内部先按统一规则解析表头归属菜单ID（「页面」类型取关联的详情菜单ID，「表」等其它类型取菜单ID 自身）再读取。
     *
     * <p>该接口不做默认树生成与落库，无配置记录时返回空（header 为 null）。
     *
     * @param menuId 菜单ID
     * @return 多级列头展示结构
     */
    MenuHeaderDisplayResp getMenuHeaderDisplay(Long menuId);
}
