package com.jdec.platform.shared.permission;

/**
 * 权限拒绝异常
 *
 * <p>当用户权限不足时抛出
 */
public class PermissionDeniedException extends RuntimeException {

    private final String permissionCode;
    private final String permissionType;

    /**
     * 构造函数
     *
     * @param message 错误消息
     * @param permissionCode 权限代码
     * @param permissionType 权限类型
     */
    public PermissionDeniedException(String message, String permissionCode, String permissionType) {
        super(message);
        this.permissionCode = permissionCode;
        this.permissionType = permissionType;
    }

    /**
     * 获取权限代码
     *
     * @return 权限代码
     */
    public String getPermissionCode() {
        return permissionCode;
    }

    /**
     * 获取权限类型
     *
     * @return 权限类型
     */
    public String getPermissionType() {
        return permissionType;
    }
}
