package com.jdec.platform.shared.permission;

import com.jdec.platform.shared.security.UserContext;
import com.jdec.platform.shared.service.PermissionQueryService;
import com.jdec.platform.shared.service.UserPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 权限验证器
 *
 * <p>验证用户是否拥有特定权限
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PermissionValidator {

    private final UserPermissionService userPermissionService;
    private final PermissionQueryService permissionQueryService;

    /**
     * 检查用户是否拥有特定权限
     *
     * @param permissionCode 权限代码，格式: {moduleCode}:{fieldCode}:{permissionType}
     * @return 是否拥有权限
     */
    public boolean hasPermission(String permissionCode) {
        try {
            // 1. 获取当前用户
            Long userId = UserContext.getCurrentUserId();
            if (userId == null) {
                log.debug("未认证用户，拒绝权限: {}", permissionCode);
                return false;
            }

            // 2. 查询数据库
            boolean hasPermission = userPermissionService.hasPermission(userId, permissionCode);

            log.debug("权限检查完成: {} -> {}", permissionCode, hasPermission);
            return hasPermission;
        } catch (Exception e) {
            log.error("权限检查异常: {}", permissionCode, e);
            // 权限检查失败时，默认拒绝
            return false;
        }
    }

    /**
     * 批量查询用户对模块下所有字段的READ权限
     *
     * <p>一次查询获取用户对指定模块所有字段的READ权限
     * <p>性能优化：避免逐个字段查询权限
     *
     * @param moduleCode 模块代码
     * @return 用户有READ权限的字段编码集合
     */
    public Set<String> getReadableFields(String moduleCode) {
        try {
            // 1. 获取当前用户
            Long userId = UserContext.getCurrentUserId();
            if (userId == null) {
                log.debug("未认证用户，无可读字段: {}", moduleCode);
                return Set.of();
            }

            // 2. 查询用户对模块的所有可读字段
            Set<String> readableFields = permissionQueryService.getUserReadableFields(userId, moduleCode);

            log.debug("用户可读字段查询完成: moduleCode={}, fields={}", moduleCode, readableFields);
            return readableFields;
        } catch (Exception e) {
            log.error("查询可读字段异常: moduleCode={}", moduleCode, e);
            return Set.of();
        }
    }
}
