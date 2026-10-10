package com.jdec.platform.config.api.enums;

/**
 * 表头字段权限过滤范围
 *
 * <p>决定按角色过滤 {@code sys_role_module_field_permission} 字段节点时，满足哪些权限（{@code view}/{@code
 * apply}/{@code edit} 的任意组合）才保留该字段。
 */
public enum MenuFieldPermissionScope {

    /** 查看、申请、编辑任一为 1 即可见（列表场景） */
    ANY,

    /** 查看或申请任一为 1 即可见（详情-申请阶段） */
    VIEW_OR_APPLY,

    /** 查看或编辑任一为 1 即可见（详情-编辑阶段，无审批链或同角色） */
    VIEW_OR_EDIT,

    /** 仅查看为 1 才可见（详情-编辑阶段且跨角色审批） */
    VIEW_ONLY,
}
