package com.jdec.platform.shared.enums;

/**
 * 权限类型枚举
 *
 * <p>定义系统中支持的所有权限类型，用于模块权限管理和字段级权限控制。
 */
public enum PermissionType {
    /**
     * 读取权限 - 用于 GET 请求和 Response 对象
     *
     * <p>允许用户查看和读取数据
     */
    READ("read", "读取权限"),

    /**
     * 写入权限 - 用于 POST 请求和 Request 对象
     *
     * <p>允许用户创建新数据
     */
    WRITE("write", "写入权限"),

    /**
     * 更新权限 - 用于 PUT 请求和 Request 对象
     *
     * <p>允许用户修改现有数据
     */
    UPDATE("update", "更新权限"),

    /**
     * 删除权限 - 用于 DELETE 请求
     *
     * <p>允许用户删除数据
     */
    DELETE("delete", "删除权限");

    private final String code;
    private final String description;

    PermissionType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 获取权限类型代码
     *
     * @return 权限类型代码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取权限类型描述
     *
     * @return 权限类型描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 根据代码获取权限类型
     *
     * @param code 权限类型代码
     * @return 对应的权限类型，如果不存在则返回 null
     */
    public static PermissionType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (PermissionType type : PermissionType.values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 检查代码是否为有效的权限类型
     *
     * @param code 权限类型代码
     * @return 如果是有效的权限类型则返回 true，否则返回 false
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }
}
