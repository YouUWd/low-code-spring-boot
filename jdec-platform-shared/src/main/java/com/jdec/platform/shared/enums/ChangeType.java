package com.jdec.platform.shared.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * 配置变更类型枚举
 *
 * @author JDEC Platform
 * @since 2026-06-22
 */
public enum ChangeType {
    /** 模块变更 */
    MODULE_CHANGED("MODULE_CHANGED", "模块变更"),

    /** 角色菜单变更 */
    ROLE_MENU_CHANGED("ROLE_MENU_CHANGED", "角色菜单变更"),

    /** 角色模块权限变更 */
    ROLE_MODULE_PERMISSION_CHANGED("ROLE_MODULE_PERMISSION_CHANGED", "角色模块权限变更"),

    /** 角色配置变更 */
    ROLE_CONFIG_CHANGED("ROLE_CONFIG_CHANGED", "角色配置变更"),

    /** 角色交互权限变更 */
    ROLE_INTERACTION_PERMISSION_CHANGED("ROLE_INTERACTION_PERMISSION_CHANGED", "角色交互权限变更"),

    /** 角色数据权限变更 */
    ROLE_DATA_PERMISSION_CHANGED("ROLE_DATA_PERMISSION_CHANGED", "角色数据权限变更"),

    /** 角色特殊权限变更 */
    ROLE_SPECIAL_PERMISSION_CHANGED("ROLE_SPECIAL_PERMISSION_CHANGED", "角色特殊权限变更"),

    /** 角色新增（新增角色，或编辑角色时新增了用户/主体） */
    ROLE_ADD("ROLE_ADD", "角色新增"),

    /** 角色删除（删除角色，或编辑角色时移除了用户/主体） */
    ROLE_DELETE("ROLE_DELETE", "角色删除"),

    /** 审批流程更新（审批链新增，提交，通过，驳回，取消） */
    APPROVAL_UPDATE("APPROVAL_CHAIN_CHANGE", "审批流程更新");

    private final String code;
    private final String description;

    private static final Map<String, ChangeType> CODE_MAP = new HashMap<>();

    static {
        for (ChangeType type : values()) {
            CODE_MAP.put(type.code, type);
        }
    }

    ChangeType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据 code 反查枚举（发布侧使用 {@link #getCode()} 写入 Redis channel）
     *
     * @param code 变更类型编码
     * @return 对应的枚举，未匹配时返回 null
     */
    public static ChangeType fromCode(String code) {
        return code == null ? null : CODE_MAP.get(code);
    }
}
