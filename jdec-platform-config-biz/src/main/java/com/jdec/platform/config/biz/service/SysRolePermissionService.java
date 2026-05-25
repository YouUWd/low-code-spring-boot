package com.jdec.platform.config.biz.service;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysRolePermissionApi;
import com.jdec.platform.config.api.dto.request.*;
import com.jdec.platform.config.api.dto.response.*;
import com.jdec.platform.config.biz.entity.*;
import com.jdec.platform.config.biz.mapper.*;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysRolePermissionService implements SysRolePermissionApi {
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysMenuMapper sysMenuMapper;
    private final SysRoleInteractionPermissionMapper sysRoleInteractionPermissionMapper;
    private final SysInteractionPermissionMapper sysInteractionPermissionMapper;
    private final SysRoleDataPermissionMapper sysRoleDataPermissionMapper;
    private final SysDataPermissionMapper sysDataPermissionMapper;
    private final SysDataPermissionTypeMapper sysDataPermissionTypeMapper;
    private final SysRoleSpecialPermissionMapper sysRoleSpecialPermissionMapper;
    private final SysSpecialPermissionMapper sysSpecialPermissionMapper;
    private final SysRoleModuleFieldPermissionMapper sysRoleModuleFieldPermissionMapper;

    @Override
    public List<RoleMenuResp> getRoleMenu(Long roleId, Long subjectId, String projectNo) {
        // 查询角色菜单关联
        List<SysRoleMenu> roleMenus =
                sysRoleMenuMapper.selectList(
                        Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, roleId));

        if (roleMenus.isEmpty()) {
            return Collections.emptyList();
        }

        // 获取菜单ID列表
        List<Long> menuIds = roleMenus.stream().map(SysRoleMenu::getMenuId).toList();

        // 查询菜单信息
        LambdaQueryWrapper<SysMenu> menuWrapper =
                new LambdaQueryWrapper<SysMenu>()
                        .in(SysMenu::getId, menuIds)
                        .eq(SysMenu::getSubjectId, subjectId)
                        .eq(SysMenu::getProjectNo, projectNo);
        List<SysMenu> menus = sysMenuMapper.selectList(menuWrapper);

        // 构建响应
        return menus.stream()
                .map(
                        menu -> {
                            RoleMenuResp resp = new RoleMenuResp();
                            resp.setMenuId(menu.getId());
                            resp.setMenuTitle(menu.getTitle());
                            return resp;
                        })
                .toList();
    }

    @Override
    public void saveRoleMenu(SaveRoleMenuReq request) {
        Long roleId = request.getRoleId();
        List<Long> menuIds = request.getMenuIds();

        // 删除旧的角色菜单关联
        sysRoleMenuMapper.delete(
                Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, roleId));

        // 批量插入新的角色菜单关联
        if (CollUtil.isNotEmpty(menuIds)) {
            List<SysRoleMenu> roleMenus =
                    menuIds.stream()
                            .map(
                                    menuId -> {
                                        SysRoleMenu roleMenu = new SysRoleMenu();
                                        roleMenu.setRoleId(roleId);
                                        roleMenu.setMenuId(menuId);
                                        return roleMenu;
                                    })
                            .toList();
            sysRoleMenuMapper.insert(roleMenus);
        }
    }

    @Override
    public List<RoleInteractionPermissionResp> getRoleInteractionPermission(Long roleId) {
        // 查询角色交互权限关联
        List<SysRoleInteractionPermission> rolePermissions =
                sysRoleInteractionPermissionMapper.selectList(
                        Wrappers.<SysRoleInteractionPermission>lambdaQuery()
                                .eq(SysRoleInteractionPermission::getRoleId, roleId));

        if (rolePermissions.isEmpty()) {
            return Collections.emptyList();
        }

        // 获取交互权限ID列表
        List<Long> permissionIds =
                rolePermissions.stream()
                        .map(SysRoleInteractionPermission::getPermissionId)
                        .toList();

        // 查询交互权限信息
        List<SysInteractionPermission> permissions =
                sysInteractionPermissionMapper.selectList(
                        Wrappers.<SysInteractionPermission>lambdaQuery()
                                .in(SysInteractionPermission::getId, permissionIds));

        // 构建响应
        return permissions.stream()
                .map(
                        permission -> {
                            RoleInteractionPermissionResp resp =
                                    new RoleInteractionPermissionResp();
                            resp.setPermissionId(permission.getId());
                            resp.setPermissionName(permission.getName());
                            resp.setPermissionCode(permission.getCode());
                            resp.setModuleId(permission.getModuleId());
                            resp.setType(permission.getType());
                            return resp;
                        })
                .toList();
    }

    @Override
    public void saveRoleInteractionPermission(SaveRoleInteractionPermissionReq request) {
        Long roleId = request.getRoleId();
        List<Long> permissionIds = request.getPermissionIds();

        // 删除旧的角色交互权限关联
        sysRoleInteractionPermissionMapper.delete(
                Wrappers.<SysRoleInteractionPermission>lambdaQuery()
                        .eq(SysRoleInteractionPermission::getRoleId, roleId));

        // 批量插入新的角色交互权限关联
        if (CollUtil.isNotEmpty(permissionIds)) {
            List<SysRoleInteractionPermission> rolePermissions =
                    permissionIds.stream()
                            .map(
                                    permissionId -> {
                                        SysRoleInteractionPermission rp =
                                                new SysRoleInteractionPermission();
                                        rp.setRoleId(roleId);
                                        rp.setPermissionId(permissionId);
                                        return rp;
                                    })
                            .toList();
            sysRoleInteractionPermissionMapper.insert(rolePermissions);
        }
    }

    @Transactional
    @Override
    public void saveRoleDataPermission(SaveRoleDataPermissionReq request) {
        Long roleId = request.getRoleId();
        List<String> permissionIds = request.getPermissionIds();

        // 1. 删除该角色的所有数据权限关联
        sysRoleDataPermissionMapper.delete(
                Wrappers.<SysRoleDataPermission>lambdaQuery()
                        .eq(SysRoleDataPermission::getRoleId, roleId));

        // 2. 解析并插入新的权限关联
        if (CollUtil.isNotEmpty(permissionIds)) {
            List<SysRoleDataPermission> roleDataPermissions =
                    permissionIds.stream()
                            .map(
                                    permissionId -> {
                                        // 按 "-" 分割：permissionTypeId-dataPermissionId
                                        String[] parts = permissionId.split("-");
                                        if (parts.length != 2) {
                                            throw new BusinessException(
                                                    "权限ID格式错误，应为：permissionTypeId-bizId");
                                        }
                                        Long permissionTypeId = Long.parseLong(parts[0]);
                                        Long bizId = Long.parseLong(parts[1]);

                                        // 验证权限类型是否存在
                                        SysDataPermissionType permissionType =
                                                sysDataPermissionTypeMapper.selectById(
                                                        permissionTypeId);
                                        if (permissionType == null) {
                                            throw new BusinessException("数据权限类型不存在");
                                        }

                                        SysRoleDataPermission rdp = new SysRoleDataPermission();
                                        rdp.setRoleId(roleId);
                                        rdp.setDataPermissionTypeId(permissionTypeId);
                                        rdp.setBizId(bizId);
                                        return rdp;
                                    })
                            .toList();
            sysRoleDataPermissionMapper.insert(roleDataPermissions);
        }
    }

    @Override
    public List<RoleSpecialPermissionResp> getRoleSpecialPermission(Long roleId) {
        // 1. 查询角色特殊权限关联
        List<SysRoleSpecialPermission> roleSpecialPermissions =
                sysRoleSpecialPermissionMapper.selectList(
                        Wrappers.<SysRoleSpecialPermission>lambdaQuery()
                                .eq(SysRoleSpecialPermission::getRoleId, roleId));

        if (roleSpecialPermissions.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 获取特殊权限ID列表
        List<Long> specialPermissionIds =
                roleSpecialPermissions.stream()
                        .map(SysRoleSpecialPermission::getSpecialPermissionId)
                        .toList();

        // 3. 查询特殊权限信息
        List<SysSpecialPermission> specialPermissions =
                sysSpecialPermissionMapper.selectList(
                        Wrappers.<SysSpecialPermission>lambdaQuery()
                                .in(SysSpecialPermission::getId, specialPermissionIds));

        // 4. 构建响应
        return specialPermissions.stream()
                .map(
                        permission -> {
                            RoleSpecialPermissionResp resp = new RoleSpecialPermissionResp();
                            resp.setSpecialPermissionId(permission.getId());
                            resp.setCode(permission.getCode());
                            resp.setName(permission.getName());
                            resp.setDescription(permission.getDescription());
                            return resp;
                        })
                .toList();
    }

    @Override
    public void saveRoleSpecialPermission(SaveRoleSpecialPermissionReq request) {
        Long roleId = request.getRoleId();
        List<Long> specialPermissionIds = request.getSpecialPermissionIds();

        // 删除旧的角色特殊权限关联
        sysRoleSpecialPermissionMapper.delete(
                Wrappers.<SysRoleSpecialPermission>lambdaQuery()
                        .eq(SysRoleSpecialPermission::getRoleId, roleId));

        // 批量插入新的角色特殊权限关联
        if (CollUtil.isNotEmpty(specialPermissionIds)) {
            List<SysRoleSpecialPermission> roleSpecialPermissions =
                    specialPermissionIds.stream()
                            .map(
                                    specialPermissionId -> {
                                        SysRoleSpecialPermission rsp =
                                                new SysRoleSpecialPermission();
                                        rsp.setRoleId(roleId);
                                        rsp.setSpecialPermissionId(specialPermissionId);
                                        return rsp;
                                    })
                            .toList();
            sysRoleSpecialPermissionMapper.insert(roleSpecialPermissions);
        }
    }

    @Override
    public List<RoleDataPermissionResp> getRoleDataPermission(Long roleId) {
        // 查询角色数据权限关联
        List<SysRoleDataPermission> roleDataPermissions =
                sysRoleDataPermissionMapper.selectList(
                        Wrappers.<SysRoleDataPermission>lambdaQuery()
                                .eq(SysRoleDataPermission::getRoleId, roleId));

        if (roleDataPermissions.isEmpty()) {
            return Collections.emptyList();
        }

        // 构建响应，拼接权限ID
        return roleDataPermissions.stream()
                .map(
                        rdp -> {
                            RoleDataPermissionResp resp = new RoleDataPermissionResp();
                            resp.setId(rdp.getId());
                            resp.setRoleId(rdp.getRoleId());

                            // 拼接权限ID：permissionTypeId-dataPermissionId
                            if (rdp.getDataPermissionTypeId() != null && rdp.getBizId() != null) {
                                resp.setPermissionId(
                                        rdp.getDataPermissionTypeId() + "-" + rdp.getBizId());
                            }
                            return resp;
                        })
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveRoleModuleFieldPermission(SaveRoleModuleFieldPermissionReq request) {
        Long roleId = request.getRoleId();

        // 1. 删除该角色下的所有旧字段权限记录
        sysRoleModuleFieldPermissionMapper.delete(
                Wrappers.<SysRoleModuleFieldPermission>lambdaQuery()
                        .eq(SysRoleModuleFieldPermission::getRoleId, roleId));

        // 2. 解析并批量插入新数据
        if (CollUtil.isNotEmpty(request.getFields())) {
            List<SysRoleModuleFieldPermission> allEntities = new ArrayList<>();
            for (SaveRoleModuleFieldPermissionReq.ModuleFieldPermissionItem item :
                    request.getFields()) {
                Long moduleId = item.getModuleId();
                if (moduleId == null) {
                    continue;
                }

                // 收集当前模块下所有涉及的 fieldId
                Set<Long> fieldIds = new HashSet<>();
                if (CollUtil.isNotEmpty(item.getReadableFields())) {
                    fieldIds.addAll(item.getReadableFields());
                }
                if (CollUtil.isNotEmpty(item.getWritableFields())) {
                    fieldIds.addAll(item.getWritableFields());
                }
                if (CollUtil.isNotEmpty(item.getUpdatableFields())) {
                    fieldIds.addAll(item.getUpdatableFields());
                }

                if (fieldIds.isEmpty()) {
                    continue;
                }

                // 为每个 fieldId 构建一条权限记录
                List<SysRoleModuleFieldPermission> entities =
                        fieldIds.stream()
                                .map(
                                        fieldId -> {
                                            Integer readable =
                                                    (item.getReadableFields() != null
                                                                    && item.getReadableFields()
                                                                            .contains(fieldId))
                                                            ? 1
                                                            : 0;
                                            Integer writable =
                                                    (item.getWritableFields() != null
                                                                    && item.getWritableFields()
                                                                            .contains(fieldId))
                                                            ? 1
                                                            : 0;
                                            Integer updatable =
                                                    (item.getUpdatableFields() != null
                                                                    && item.getUpdatableFields()
                                                                            .contains(fieldId))
                                                            ? 1
                                                            : 0;

                                            return SysRoleModuleFieldPermission.builder()
                                                    .roleId(roleId)
                                                    .moduleId(moduleId)
                                                    .fieldId(fieldId)
                                                    .readable(readable)
                                                    .writable(writable)
                                                    .updatable(updatable)
                                                    .build();
                                        })
                                .toList();

                allEntities.addAll(entities);
            }

            if (CollUtil.isNotEmpty(allEntities)) {
                sysRoleModuleFieldPermissionMapper.insert(allEntities);
            }
        }
    }

    @Override
    public List<RoleModuleFieldPermissionResp> getRoleModuleFieldPermission(Long roleId) {
        // 1. 直接查询该角色已分配的模块字段权限配置列表
        List<SysRoleModuleFieldPermission> allocatedList =
                sysRoleModuleFieldPermissionMapper.selectList(
                        Wrappers.<SysRoleModuleFieldPermission>lambdaQuery()
                                .eq(SysRoleModuleFieldPermission::getRoleId, roleId));

        if (CollUtil.isEmpty(allocatedList)) {
            return Collections.emptyList();
        }

        // 2. 转换为 DTO 扁平列表
        return allocatedList.stream()
                .map(
                        p -> {
                            RoleModuleFieldPermissionResp resp =
                                    new RoleModuleFieldPermissionResp();
                            resp.setId(p.getId());
                            resp.setRoleId(p.getRoleId());
                            resp.setModuleId(p.getModuleId());
                            resp.setFieldId(p.getFieldId());
                            resp.setReadable(p.getReadable());
                            resp.setWritable(p.getWritable());
                            resp.setUpdatable(p.getUpdatable());
                            return resp;
                        })
                .toList();
    }
}
