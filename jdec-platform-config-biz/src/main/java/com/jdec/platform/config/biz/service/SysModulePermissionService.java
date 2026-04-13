package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.config.biz.entity.SysModulePermission;
import com.jdec.platform.config.biz.mapper.SysModulePermissionMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 模块权限定义服务实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysModulePermissionService {

    private final SysModulePermissionMapper permissionMapper;

    /** 创建权限 */
    public SysModulePermission createPermission(SysModulePermission permission) {
        permissionMapper.insert(permission);
        return permission;
    }

    /** 根据 ID 查询权限 */
    public SysModulePermission getPermissionById(Long id) {
        return permissionMapper.selectById(id);
    }

    /** 根据权限编码查询权限 */
    public SysModulePermission getPermissionByCode(String permissionCode) {
        LambdaQueryWrapper<SysModulePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModulePermission::getPermissionCode, permissionCode);
        return permissionMapper.selectOne(wrapper);
    }

    /** 根据模块 ID 查询权限 */
    public List<SysModulePermission> getPermissionsByModuleId(Long moduleId) {
        LambdaQueryWrapper<SysModulePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModulePermission::getModuleId, moduleId)
                .orderByAsc(
                        SysModulePermission::getPermissionType, SysModulePermission::getFieldCode);
        return permissionMapper.selectList(wrapper);
    }

    /** 根据模块 ID 和权限类型查询权限 */
    public List<SysModulePermission> getPermissionsByModuleIdAndType(
            Long moduleId, String permissionType) {
        LambdaQueryWrapper<SysModulePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModulePermission::getModuleId, moduleId)
                .eq(SysModulePermission::getPermissionType, permissionType)
                .orderByAsc(SysModulePermission::getFieldCode);
        return permissionMapper.selectList(wrapper);
    }

    /** 根据模块 ID、字段编码和权限类型查询权限 */
    public SysModulePermission getPermissionByModuleIdFieldCodeAndType(
            Long moduleId, String fieldCode, String permissionType) {
        LambdaQueryWrapper<SysModulePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModulePermission::getModuleId, moduleId)
                .eq(SysModulePermission::getFieldCode, fieldCode)
                .eq(SysModulePermission::getPermissionType, permissionType);
        return permissionMapper.selectOne(wrapper);
    }

    /** 更新权限 */
    public SysModulePermission updatePermission(SysModulePermission permission) {
        permissionMapper.updateById(permission);
        return permission;
    }

    /** 删除权限 */
    public void deletePermission(Long id) {
        permissionMapper.deleteById(id);
    }

    /** 根据模块 ID 删除权限 */
    public void deletePermissionsByModuleId(Long moduleId) {
        LambdaQueryWrapper<SysModulePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModulePermission::getModuleId, moduleId);
        permissionMapper.delete(wrapper);
    }

    /** 查询所有权限 */
    public List<SysModulePermission> getAllPermissions() {
        return permissionMapper.selectList(null);
    }
}
