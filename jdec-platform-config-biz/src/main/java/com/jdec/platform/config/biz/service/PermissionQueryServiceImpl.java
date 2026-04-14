package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.config.biz.entity.SysModule;
import com.jdec.platform.config.biz.entity.SysModulePermission;
import com.jdec.platform.config.biz.entity.SysRolePermission;
import com.jdec.platform.config.biz.entity.SysUserRole;
import com.jdec.platform.config.biz.mapper.SysModuleMapper;
import com.jdec.platform.config.biz.mapper.SysModulePermissionMapper;
import com.jdec.platform.config.biz.mapper.SysRolePermissionMapper;
import com.jdec.platform.config.biz.mapper.SysUserRoleMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.service.PermissionQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 权限查询服务实现
 *
 * <p>提供权限定义和用户权限的查询能力
 */
@Service
@Slf4j
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class PermissionQueryServiceImpl implements PermissionQueryService {

    private final SysModulePermissionMapper permissionMapper;
    private final SysModuleMapper moduleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;

    /**
     * 根据权限代码查询权限是否存在且启用
     *
     * @param permissionCode 权限代码
     * @return 权限是否存在且启用
     */
    @Override
    public boolean isPermissionEnabled(String permissionCode) {
        try {
            SysModulePermission permission = queryPermissionByCode(permissionCode);
            return permission != null && permission.getEnabled();
        } catch (Exception e) {
            log.error("查询权限启用状态异常: {}", permissionCode, e);
            return false;
        }
    }

    /**
     * 获取权限ID
     *
     * @param permissionCode 权限代码
     * @return 权限ID，如果不存在则返回null
     */
    @Override
    public Long getPermissionId(String permissionCode) {
        try {
            SysModulePermission permission = queryPermissionByCode(permissionCode);
            return permission != null ? permission.getId() : null;
        } catch (Exception e) {
            log.error("查询权限ID异常: {}", permissionCode, e);
            return null;
        }
    }

    /**
     * 查询用户是否拥有权限
     *
     * @param userId 用户ID
     * @param permissionId 权限ID
     * @return 是否拥有权限
     */
    @Override
    public boolean userHasPermission(Long userId, Long permissionId) {
        try {
            // 1. 查询用户的所有角色
            List<SysUserRole> userRoles = queryUserRoles(userId);
            if (userRoles.isEmpty()) {
                log.debug("用户无角色分配: userId={}", userId);
                return false;
            }

            // 2. 提取角色ID集合
            Set<Long> roleIds = new HashSet<>();
            for (SysUserRole userRole : userRoles) {
                roleIds.add(userRole.getRoleId());
            }

            // 3. 查询这些角色是否拥有该权限
            return checkRolePermissions(roleIds, permissionId);
        } catch (Exception e) {
            log.error("查询用户权限异常: userId={}, permissionId={}", userId, permissionId, e);
            return false;
        }
    }

    /**
     * 查询用户对模块下所有字段的READ权限
     *
     * <p>一次查询获取用户对指定模块所有字段的READ权限
     * <p>性能优化：避免逐个字段查询权限
     * <p>查询流程：
     * 1. 通过moduleCode查询moduleId
     * 2. 通过moduleId查询所有READ权限
     * 3. 查询用户角色对应的权限节点
     * 4. 提取用户有权限的字段编码
     *
     * @param userId 用户ID
     * @param moduleCode 模块代码
     * @return 用户有READ权限的字段编码集合
     */
    @Override
    public Set<String> getUserReadableFields(Long userId, String moduleCode) {
        try {
            Set<String> readableFields = new HashSet<>();

            // 1. 通过moduleCode查询moduleId
            LambdaQueryWrapper<SysModule> moduleWrapper = new LambdaQueryWrapper<>();
            moduleWrapper.eq(SysModule::getModuleCode, moduleCode);
            SysModule module = moduleMapper.selectOne(moduleWrapper);
            
            if (module == null) {
                log.debug("模块不存在: moduleCode={}", moduleCode);
                return readableFields;
            }

            Long moduleId = module.getId();
            log.debug("查询到模块: moduleCode={}, moduleId={}", moduleCode, moduleId);

            // 2. 查询用户的所有角色
            List<SysUserRole> userRoles = queryUserRoles(userId);
            if (userRoles.isEmpty()) {
                log.debug("用户无角色分配: userId={}", userId);
                return readableFields;
            }

            // 3. 提取角色ID集合
            Set<Long> roleIds = new HashSet<>();
            for (SysUserRole userRole : userRoles) {
                roleIds.add(userRole.getRoleId());
            }

            // 4. 通过moduleId查询所有READ权限
            LambdaQueryWrapper<SysModulePermission> permissionWrapper = new LambdaQueryWrapper<>();
            permissionWrapper.eq(SysModulePermission::getModuleId, moduleId)
                    .eq(SysModulePermission::getPermissionType, "READ")
                    .eq(SysModulePermission::getEnabled, true);
            List<SysModulePermission> permissions = permissionMapper.selectList(permissionWrapper);

            if (permissions.isEmpty()) {
                log.debug("模块无READ权限: moduleCode={}, moduleId={}", moduleCode, moduleId);
                return readableFields;
            }

            log.debug("查询到权限数: moduleId={}, count={}", moduleId, permissions.size());

            // 5. 查询用户角色对应的权限ID集合
            LambdaQueryWrapper<SysRolePermission> rolePermissionWrapper = new LambdaQueryWrapper<>();
            rolePermissionWrapper.in(SysRolePermission::getRoleId, roleIds);
            List<SysRolePermission> rolePermissions = rolePermissionMapper.selectList(rolePermissionWrapper);

            // 6. 构建用户有权限的权限ID集合
            Set<Long> userPermissionIds = new HashSet<>();
            for (SysRolePermission rolePermission : rolePermissions) {
                userPermissionIds.add(rolePermission.getPermissionId());
            }

            // 7. 提取用户有权限的字段编码
            for (SysModulePermission permission : permissions) {
                if (userPermissionIds.contains(permission.getId())) {
                    readableFields.add(permission.getFieldCode());
                }
            }

            log.debug("用户可读字段: userId={}, moduleCode={}, fields={}", userId, moduleCode, readableFields);
            return readableFields;
        } catch (Exception e) {
            log.error("查询用户可读字段异常: userId={}, moduleCode={}", userId, moduleCode, e);
            return new HashSet<>();
        }
    }

    /**
     * 根据权限代码查询权限定义
     *
     * @param permissionCode 权限代码
     * @return 权限定义，如果不存在则返回null
     */
    private SysModulePermission queryPermissionByCode(String permissionCode) {
        LambdaQueryWrapper<SysModulePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModulePermission::getPermissionCode, permissionCode);
        return permissionMapper.selectOne(wrapper);
    }

    /**
     * 查询用户的所有角色
     *
     * @param userId 用户ID
     * @return 用户角色列表
     */
    private List<SysUserRole> queryUserRoles(Long userId) {
        LambdaQueryWrapper<SysUserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserRole::getUserId, userId);
        return userRoleMapper.selectList(wrapper);
    }

    /**
     * 检查角色是否拥有权限
     *
     * @param roleIds 角色ID集合
     * @param permissionId 权限ID
     * @return 是否拥有权限
     */
    private boolean checkRolePermissions(Set<Long> roleIds, Long permissionId) {
        if (roleIds.isEmpty()) {
            return false;
        }

        LambdaQueryWrapper<SysRolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysRolePermission::getRoleId, roleIds)
               .eq(SysRolePermission::getPermissionId, permissionId);

        long count = rolePermissionMapper.selectCount(wrapper);
        return count > 0;
    }
}
