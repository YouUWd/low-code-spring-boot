package com.jdec.platform.common.api;

import com.jdec.platform.common.api.dto.request.SaveUserMenuHeaderReq;
import com.jdec.platform.common.api.dto.request.SaveUserMenuHeaderWidthReq;
import com.jdec.platform.config.api.dto.response.MenuHeaderDisplayResp;
import com.jdec.platform.config.api.dto.response.SysMenuHeaderResp;

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
     * <p>与 {@code SysMenuHeaderApi#getMenuHeaderDisplay} 同一套展示组装逻辑，区别在于叠加了业务库 {@code
     * user_menu_header} 中该用户（用户 + 菜单（详情菜单ID）+ {@code subject_id} + {@code project_no}）的个性化配置： 以「菜单
     * + 字段 + {@code sys_menu_header} + 角色权限」生成的默认表头树为骨架，按 {@code virId} 逐节点合并，用户有记录取用户配置，
     * 用户没有记录的节点（模块侧新增、或 {@code sys_menu_header} 后来才勾选的列）回落到系统默认值； 合并后只输出已勾选（{@code
     * checked=1}）的列并保留其祖先菜单节点，无勾选列时 header 为 null。
     *
     * <p>字段可见性按角色权限过滤，满足哪些权限由入参共同决定（{@code roleId} 为空时不做任何权限过滤）：
     *
     * <ul>
     *   <li>{@code type=list}（列表）：查看、申请、编辑任一权限为 1 即可见；
     *   <li>{@code type=detail} 且 {@code operate=apply}（申请阶段）：查看、申请任一权限为 1 即可见；
     *   <li>{@code type=detail} 且 {@code operate=edit}（编辑阶段）：
     *       <ul>
     *         <li>{@code hasApprove=0}（无审批链）：查看、编辑任一权限为 1 即可见；
     *         <li>{@code hasApprove=1}（有审批链）：{@code roleId} 与 {@code currentRoleId} 相等时查看、编辑任一权限为 1
     *             即可见，不相等时仅查看为 1 才可见。
     *       </ul>
     * </ul>
     *
     * <p>未知的 {@code type}/{@code operate} 一律回退为「仅查看」过滤范围。
     *
     * @param userId 用户ID
     * @param menuId 列表菜单ID
     * @param roleId 登录角色ID，为空时不做角色权限过滤
     * @param type 查询场景：{@code list}-列表 / {@code detail}-详情
     * @param operate 操作场景（仅 {@code detail} 有效）：{@code edit}-编辑 / {@code apply}-申请
     * @param hasApprove 是否有审批链：0-否 / 1-是
     * @param currentRoleId 前端当前角色ID（与 {@code roleId} 比对决定编辑阶段的过滤范围）
     * @return 多级列头展示结构
     */
    MenuHeaderDisplayResp getUserMenuHeaderDisplay(
            Long userId,
            Long menuId,
            Long roleId,
            String type,
            String operate,
            Integer hasApprove,
            Long currentRoleId);

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

    /**
     * 保存单列列宽（列表拖动列宽）
     *
     * <p>定位列：入参 {@code nodeType} + {@code sourceId} 按生成侧同一规则拼出 {@code virId}（菜单 {@code MENU_菜单ID} /
     * 字段 {@code Field_字段ID}）去 {@code user_menu_header} 命中记录，未命中时用 {@code dataIndex}
     * 兜底比对；用户与角色取当前请求上下文。
     *
     * <ul>
     *   <li>该用户在此菜单下没有任何记录（数据隔离 {@code user_id + menu_id + subject_id + project_no}）：先按 {@code
     *       getUserMenuHeaderTree} 的口径生成用户表头树，校验目标列为字段且已勾选后，将整棵树落库（首次个性化）， 目标列宽度一并写入；
     *   <li>已有记录且命中目标列记录：以库中记录为准校验（节点类型为字段、{@code checked=1}），只更新该记录的 {@code width}；
     *   <li>已有记录但目标列无记录（模块侧新增、或系统表头后来才勾选的列）：按合并后的用户表头树取值校验，仅补插该列记录并写入宽度， 其余节点保持原有个性化记录不变。
     * </ul>
     *
     * @param req 列宽请求（列表菜单ID + 节点类型 + 来源ID + 字段索引 + 列宽）
     */
    void saveUserMenuHeaderWidth(SaveUserMenuHeaderWidthReq req);

    /**
     * 恢复默认表头：删除该用户在指定列表菜单下的个性化表头配置
     *
     * <p>按 {@code user_id + menu_id + subject_id + project_no} 删除业务库 {@code user_menu_header}
     * 中该用户的整棵表头节点树， 删除后查询接口自然回退到系统默认表头配置（{@code sys_menu_header}）。{@code menu_id} 同样先转换为表头归属菜单ID，
     * 与保存/查询保持一致。主体与项目编码取当前上下文。
     *
     * @param userId 用户ID
     * @param menuId 列表菜单ID
     */
    void resetUserMenuHeader(Long userId, Long menuId);
}
