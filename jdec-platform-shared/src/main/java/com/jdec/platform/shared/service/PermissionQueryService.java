package com.jdec.platform.shared.service;

/**
 * 权限查询服务接口
 *
 * <p>提供权限定义和用户权限的查询能力
 * <p>由config-biz模块实现
 */
public interface PermissionQueryService {

    /**
     * 根据权限代码查询权限是否存在且启用
     *
     * @param permissionCode 权限代码
     * @return 权限是否存在且启用
     */
    boolean isPermissionEnabled(String permissionCode);

    /**
     * 获取权限ID
     *
     * @param permissionCode 权限代码
     * @return 权限ID，如果不存在则返回null
     */
    Long getPermissionId(String permissionCode);

    /**
     * 查询用户是否拥有权限
     *
     * @param userId 用户ID
     * @param permissionId 权限ID
     * @return 是否拥有权限
     */
    boolean userHasPermission(Long userId, Long permissionId);

    /**
     * 查询用户对模块下所有字段的READ权限
     *
     * <p>一次查询获取用户对指定模块所有字段的READ权限
     * <p>性能优化：避免逐个字段查询权限
     *
     * @param userId 用户ID
     * @param moduleCode 模块代码
     * @return 用户有READ权限的字段编码集合
     */
    java.util.Set<String> getUserReadableFields(Long userId, String moduleCode);
}
