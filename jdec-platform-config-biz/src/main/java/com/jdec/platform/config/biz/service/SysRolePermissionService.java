package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.config.biz.entity.SysRolePermission;
import com.jdec.platform.config.biz.mapper.SysRolePermissionMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 角色权限关联服务实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysRolePermissionService {

    private final SysRolePermissionMapper rolePermissionMapper;

    /** 为角色添加权限 */
    public SysRolePermission addPermissionToRole(Long roleId, Long permissionId) {
        SysRolePermission rolePermission =
                SysRolePermission.builder().roleId(roleId).permissionId(permissionId).build();
        rolePermissionMapper.insert(rolePermission);
        return rolePermission;
    }

    /** 根据角色 ID 查询权限 */
    public List<SysRolePermission> getPermissionsByRoleId(Long roleId) {
        LambdaQueryWrapper<SysRolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRolePermission::getRoleId, roleId);
        return rolePermissionMapper.selectList(wrapper);
    }

    /** 根据权限 ID 查询角色 */
    public List<SysRolePermission> getRolesByPermissionId(Long permissionId) {
        LambdaQueryWrapper<SysRolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRolePermission::getPermissionId, permissionId);
        return rolePermissionMapper.selectList(wrapper);
    }

    /** 检查角色是否拥有权限 */
    public boolean hasPermission(Long roleId, Long permissionId) {
        LambdaQueryWrapper<SysRolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRolePermission::getRoleId, roleId)
                .eq(SysRolePermission::getPermissionId, permissionId);
        return rolePermissionMapper.selectOne(wrapper) != null;
    }

    /** 从角色移除权限 */
    public void removePermissionFromRole(Long roleId, Long permissionId) {
        LambdaQueryWrapper<SysRolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRolePermission::getRoleId, roleId)
                .eq(SysRolePermission::getPermissionId, permissionId);
        rolePermissionMapper.delete(wrapper);
    }

    /** 删除角色的所有权限 */
    public void deletePermissionsByRoleId(Long roleId) {
        LambdaQueryWrapper<SysRolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRolePermission::getRoleId, roleId);
        rolePermissionMapper.delete(wrapper);
    }

    /** 删除权限的所有角色关联 */
    public void deleteRolesByPermissionId(Long permissionId) {
        LambdaQueryWrapper<SysRolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRolePermission::getPermissionId, permissionId);
        rolePermissionMapper.delete(wrapper);
    }
}
