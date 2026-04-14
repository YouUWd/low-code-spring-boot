package com.jdec.platform.auth.biz.service;

import com.jdec.platform.shared.service.PermissionQueryService;
import com.jdec.platform.shared.service.UserPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 用户权限服务实现
 *
 * <p>查询用户权限信息
 * <p>支持直接权限和角色继承权限
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserPermissionServiceImpl implements UserPermissionService {

    private final PermissionQueryService permissionQueryService;

    /**
     * 检查用户是否拥有特定权限
     *
     * @param userId 用户ID
     * @param permissionCode 权限代码，格式: {moduleCode}:{fieldCode}:{permissionType}
     * @return 是否拥有权限
     */
    @Override
    public boolean hasPermission(Long userId, String permissionCode) {
        try {
            log.debug("检查用户权限: userId={}, permissionCode={}", userId, permissionCode);

            // 1. 检查权限是否存在且启用
            if (!permissionQueryService.isPermissionEnabled(permissionCode)) {
                log.debug("权限不存在或已禁用: {}", permissionCode);
                return false;
            }

            // 2. 获取权限ID
            Long permissionId = permissionQueryService.getPermissionId(permissionCode);
            if (permissionId == null) {
                log.debug("无法获取权限ID: {}", permissionCode);
                return false;
            }

            // 3. 查询用户是否拥有权限
            boolean hasPermission = permissionQueryService.userHasPermission(userId, permissionId);

            log.debug("权限检查完成: userId={}, permissionCode={}, result={}", userId, permissionCode, hasPermission);
            return hasPermission;
        } catch (Exception e) {
            log.error("权限查询异常: userId={}, permissionCode={}", userId, permissionCode, e);
            return false;
        }
    }
}
