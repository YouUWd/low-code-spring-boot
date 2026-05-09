package com.jdec.platform.shared.service;

/**
 * 用户权限服务接口
 *
 * <p>查询用户权限信息
 *
 * <p>支持直接权限和角色继承权限
 */
public interface UserPermissionService {

    /**
     * 检查用户是否拥有特定权限
     *
     * @param userId 用户ID
     * @param permissionCode 权限代码，格式: {moduleCode}:{fieldCode}:{permissionType}
     * @return 是否拥有权限
     */
    boolean hasPermission(Long userId, String permissionCode);
}
