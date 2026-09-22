package com.jdec.platform.config.open.api;

/**
 * 菜单开放接口（供数据引擎、流程引擎等下游核心消费）
 *
 * <p>提供菜单维度的只读查询能力，调用方无需关心菜单与详情菜单、模块之间的绑定关系。
 */
public interface OpenMenuApi {

    /**
     * 根据菜单ID 查询模块ID
     *
     * <p>解析规则（只取一层，与表头归属菜单的解析规则一致）：
     *
     * <ul>
     *   <li>菜单类型为「页面」且记录了详情菜单ID（{@code detail_id}）→ 取详情菜单绑定的模块ID；
     *   <li>「表」等其它类型（含未关联详情菜单的页面）→ 取该菜单自身绑定的模块ID。
     * </ul>
     *
     * <p>菜单不存在、详情菜单不存在或均未绑定模块时返回 {@code null}。
     *
     * @param menuId 菜单ID
     * @return 模块ID，无法解析时返回 null
     */
    Long getModuleIdByMenuId(Long menuId);
}
