package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.config.api.SysUserRoleApi;
import com.jdec.platform.config.api.dto.response.UserRoleEntryResp;
import com.jdec.platform.config.biz.converter.PermissionConverter;
import com.jdec.platform.config.biz.entity.SysUserRole;
import com.jdec.platform.config.biz.mapper.SysUserRoleMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 用户角色关联服务实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysUserRoleService implements SysUserRoleApi {

    private final SysUserRoleMapper userRoleMapper;
    private final PermissionConverter permissionConverter;

    /** 为用户添加角色 */
    public UserRoleEntryResp addRoleToUser(Long userId, Long roleId) {
        SysUserRole userRole = SysUserRole.builder().userId(userId).roleId(roleId).build();
        userRoleMapper.insert(userRole);
        return permissionConverter.toUserRoleEntryResp(userRole);
    }

    /** 根据用户 ID 查询角色 */
    public List<UserRoleEntryResp> getRolesByUserId(Long userId) {
        LambdaQueryWrapper<SysUserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserRole::getUserId, userId);
        List<SysUserRole> userRoles = userRoleMapper.selectList(wrapper);
        return permissionConverter.toUserRoleEntryResps(userRoles);
    }

    /** 根据角色 ID 查询用户 */
    public List<UserRoleEntryResp> getUsersByRoleId(Long roleId) {
        LambdaQueryWrapper<SysUserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserRole::getRoleId, roleId);
        List<SysUserRole> userRoles = userRoleMapper.selectList(wrapper);
        return permissionConverter.toUserRoleEntryResps(userRoles);
    }

    /** 检查用户是否拥有角色 */
    public boolean hasRole(Long userId, Long roleId) {
        LambdaQueryWrapper<SysUserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserRole::getUserId, userId).eq(SysUserRole::getRoleId, roleId);
        return userRoleMapper.selectOne(wrapper) != null;
    }

    /** 从用户移除角色 */
    public void removeRoleFromUser(Long userId, Long roleId) {
        LambdaQueryWrapper<SysUserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserRole::getUserId, userId).eq(SysUserRole::getRoleId, roleId);
        userRoleMapper.delete(wrapper);
    }

    /** 删除用户的所有角色 */
    public void deleteRolesByUserId(Long userId) {
        LambdaQueryWrapper<SysUserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserRole::getUserId, userId);
        userRoleMapper.delete(wrapper);
    }

    /** 删除角色的所有用户关联 */
    public void deleteUsersByRoleId(Long roleId) {
        LambdaQueryWrapper<SysUserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserRole::getRoleId, roleId);
        userRoleMapper.delete(wrapper);
    }
}
