package com.jdec.platform.data.api;

import com.jdec.platform.config.api.dto.response.MenuHeaderDisplayResp;
import com.jdec.platform.config.api.dto.response.SysMenuHeaderResp;
import com.jdec.platform.data.api.dto.request.SaveUserMenuHeaderReq;

/** 用户自定义表头配置 API 接口 */
public interface UserMenuHeaderApi {

    /**
     * 查询用户在指定列表菜单下的个性化表头树
     *
     * <p>以「菜单 + 字段 + {@code sys_menu_header} + 角色权限」生成的默认表头树为骨架，按 {@code vir_id} 叠加该用户在 {@code
     * user_menu_header} 中的个性化配置：两边都有取用户记录，用户没有的取默认节点，用户有而默认树没有的记录被丢弃。
     *
     * <p>{@code user_menu_header.menu_id} 存的是「详情菜单ID」，入参 {@code menuId} 为前端传入的列表菜单ID， 内部先转换为详情菜单ID
     * 再查询用户配置。
     *
     * <p>返回节点的主键 {@code id} 仅当该节点命中 {@code user_menu_header} 记录时才有值，来自默认表头 / {@code
     * sys_menu_header} 的节点不带 id。
     *
     * @param userId 用户ID
     * @param menuId 列表菜单ID
     * @param roleId 角色ID，为空时不做角色权限过滤
     * @return 用户表头配置树的根节点
     */
    SysMenuHeaderResp getUserMenuHeaderTree(Long userId, Long menuId, Long roleId);

    /**
     * 查询用户在指定列表菜单下的多级列头展示结构
     *
     * <p>与 {@code SysMenuHeaderApi#getMenuHeaderDisplay} 同一套展示组装逻辑，区别在于数据来源为业务库的 {@code
     * user_menu_header}：按用户 + 菜单（详情菜单ID）+ {@code subject_id} + {@code project_no} 纯读取该用户已保存的个性化表头
     * 节点，只输出已勾选（{@code checked=1}）的列并保留其祖先菜单节点，无配置时返回空（header 为 null）。
     *
     * @param userId 用户ID
     * @param menuId 列表菜单ID
     * @param roleId 角色ID，为空时不做角色权限过滤
     * @return 多级列头展示结构
     */
    MenuHeaderDisplayResp getUserMenuHeaderDisplay(Long userId, Long menuId, Long roleId);

    /**
     * 保存用户个性化表头配置（整棵树提交）
     *
     * <p>入参节点树结构与 {@code SaveMenuReq.MenuHeaderNode} 一致，额外携带用户id；树按深度优先压平后逐节点处理：自带主键 {@code
     * id}（或库中已存在同 {@code vir_id} 记录）的节点按主键更新，其余节点新增。落库归属菜单为入参 {@code menuId} 对应的详情菜单ID，数据隔离按 {@code
     * user_id + menu_id + subject_id + project_no}。
     *
     * @param req 用户个性化表头配置节点树（根节点需带 userId 与 menuId）
     */
    void saveUserMenuHeader(SaveUserMenuHeaderReq req);
}
