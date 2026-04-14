package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.config.biz.entity.SysModule;
import com.jdec.platform.config.biz.entity.SysModulePermission;
import com.jdec.platform.config.biz.mapper.SysModuleMapper;
import com.jdec.platform.config.biz.mapper.SysModulePermissionMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.service.ModuleFieldQueryService;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 模块权限定义服务实现 */
@Service
@Slf4j
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysModulePermissionService implements ModuleFieldQueryService {

    private final SysModulePermissionMapper permissionMapper;
    private final SysModuleMapper moduleMapper;

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

    /** 根据模块编码查询权限 */
    public List<SysModulePermission> getPermissionsByModuleCode(String moduleCode) {
        // 1. 根据模块编码查询模块
        LambdaQueryWrapper<SysModule> moduleWrapper = new LambdaQueryWrapper<>();
        moduleWrapper.eq(SysModule::getModuleCode, moduleCode);
        SysModule module = moduleMapper.selectOne(moduleWrapper);

        if (module == null) {
            log.debug("模块不存在: {}", moduleCode);
            return new ArrayList<>();
        }

        // 2. 根据模块 ID 查询权限
        return getPermissionsByModuleId(module.getId());
    }


    @Override
    public List<String> getFieldCodesByModuleCode(String moduleCode) {
        // 获取模块的所有权限
        List<SysModulePermission> permissions = getPermissionsByModuleCode(moduleCode);
        
        // 提取字段编码，排除模块级权限（fieldCode = "*"）
        return permissions.stream()
                .map(SysModulePermission::getFieldCode)
                .filter(fieldCode -> fieldCode != null && !fieldCode.equals("*"))
                .distinct()
                .collect(Collectors.toList());
    }
}
