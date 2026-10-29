package com.jdec.platform.common.biz.interaction;

import com.jdec.platform.config.api.dto.response.RoleInteractionPermissionByModuleResp;
import java.util.Collections;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 互动操作上下文
 *
 * <p>封装当前请求的项目编号、根模块信息、当前用户信息以及从 SysRolePermissionApi 获取的模块交互权限列表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteractionActionContext {

    /** 项目编码 (如 "company") */
    private String projectNo;

    /** 根模块 ID */
    private Long rootModuleId;

    /** 模块编码 (如 "COMPANY-ROOT") */
    private String moduleCode;

    /** 主表物理表名 (如 "info") */
    private String primaryTable;

    /** 当前登录用户 ID */
    private Long userId;

    /** 当前登录角色 ID */
    private Long roleId;

    /** 是否具备编辑权限 */
    private boolean hasEditPermission;

    /** 模块原始交互权限响应 (来自 SysRolePermissionApi) */
    private RoleInteractionPermissionByModuleResp modulePermissionResp;

    /** 当前角色在该模块拥有的交互权限编码集合 */
    @Builder.Default private Set<String> assignedPermissionCodes = Collections.emptySet();

    /**
     * 判断当前角色是否被授予指定交互权限编码
     *
     * @param permissionCode 交互权限编码 (如 "enable_company", "disable_company")
     * @return true-已授权, false-未授权
     */
    public boolean hasPermission(String permissionCode) {
        return assignedPermissionCodes != null && assignedPermissionCodes.contains(permissionCode);
    }
}
