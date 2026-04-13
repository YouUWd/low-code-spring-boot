package com.jdec.platform.config.api.enums;

/**
 * 权限类型枚举
 *
 * <p>定义系统中支持的权限类型。
 */
public enum PermissionType {
    /** 读取权限 */
    READ("read", "读取权限"),

    /** 写入权限 */
    WRITE("write", "写入权限"),

    /** 更新权限 */
    UPDATE("update", "更新权限"),

    /** 删除权限 */
    DELETE("delete", "删除权限");

    private final String code;
    private final String description;

    PermissionType(String code, String description) {
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
     * 根据编码获取权限类型
     *
     * @param code 权限编码
     * @return 权限类型，如果不存在则返回 null
     */
    public static PermissionType fromCode(String code) {
        for (PermissionType type : PermissionType.values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
