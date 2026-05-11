package com.jdec.platform.config.biz.service;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysRolePermissionApi;
import com.jdec.platform.config.api.dto.request.SaveRoleDataPermissionReq;
import com.jdec.platform.config.api.dto.request.SaveRoleInteractionPermissionReq;
import com.jdec.platform.config.api.dto.request.SaveRoleMenuReq;
import com.jdec.platform.config.api.dto.request.SaveRoleModuleFieldReq;
import com.jdec.platform.config.api.dto.request.SaveRoleSpecialPermissionReq;
import com.jdec.platform.config.api.dto.response.RoleDataPermissionResp;
import com.jdec.platform.config.api.dto.response.RoleInteractionPermissionResp;
import com.jdec.platform.config.api.dto.response.RoleMenuResp;
import com.jdec.platform.config.api.dto.response.RoleModuleFieldResp;
import com.jdec.platform.config.api.dto.response.RoleSpecialPermissionResp;
import com.jdec.platform.config.biz.entity.SysDataPermissionTarget;
import com.jdec.platform.config.biz.entity.SysDataPermissionType;
import com.jdec.platform.config.biz.entity.SysInteractionPermission;
import com.jdec.platform.config.biz.entity.SysMenu;
import com.jdec.platform.config.biz.entity.SysRoleDataPermission;
import com.jdec.platform.config.biz.entity.SysRoleInteractionPermission;
import com.jdec.platform.config.biz.entity.SysRoleMenu;
import com.jdec.platform.config.biz.entity.SysRoleModuleField;
import com.jdec.platform.config.biz.entity.SysRoleSpecialPermission;
import com.jdec.platform.config.biz.entity.SysSpecialPermission;
import com.jdec.platform.config.biz.mapper.SysDataPermissionTargetMapper;
import com.jdec.platform.config.biz.mapper.SysDataPermissionTypeMapper;
import com.jdec.platform.config.biz.mapper.SysInteractionPermissionMapper;
import com.jdec.platform.config.biz.mapper.SysMenuMapper;
import com.jdec.platform.config.biz.mapper.SysRoleDataPermissionMapper;
import com.jdec.platform.config.biz.mapper.SysRoleInteractionPermissionMapper;
import com.jdec.platform.config.biz.mapper.SysRoleMenuMapper;
import com.jdec.platform.config.biz.mapper.SysRoleModuleFieldMapper;
import com.jdec.platform.config.biz.mapper.SysRoleSpecialPermissionMapper;
import com.jdec.platform.config.biz.mapper.SysSpecialPermissionMapper;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.Collections;
import java.util.List;
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
    private final SysDataPermissionTargetMapper sysDataPermissionTargetMapper;
    private final SysDataPermissionTypeMapper sysDataPermissionTypeMapper;
    private final SysRoleSpecialPermissionMapper sysRoleSpecialPermissionMapper;
    private final SysSpecialPermissionMapper sysSpecialPermissionMapper;
    private final SysRoleModuleFieldMapper sysRoleModuleFieldMapper;

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
                                        roleMenu.setProjectNo(AppContext.getProjectNo());
                                        roleMenu.setSubjectId(AppContext.getSubjectId());
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
        Long dataPermissionTypeId = request.getDataPermissionTypeId();
        List<Long> userIds = request.getUserIds();

        // 1. 查询数据权限类型，判断 hasTarget
        SysDataPermissionType permissionType =
                sysDataPermissionTypeMapper.selectById(dataPermissionTypeId);
        if (permissionType == null) {
            throw new BusinessException("数据权限类型不存在");
        }
        if (permissionType.getHasTarget() != null && permissionType.getHasTarget() == 1) {
            List<SysRoleDataPermission> sysRoleDataPermissions =
                    sysRoleDataPermissionMapper.selectList(
                            Wrappers.<SysRoleDataPermission>lambdaQuery()
                                    .eq(SysRoleDataPermission::getRoleId, roleId));
            if (CollUtil.isNotEmpty(sysRoleDataPermissions)) {
                List<Long> dataPermissionIds =
                        sysRoleDataPermissions.stream().map(SysRoleDataPermission::getId).toList();
                sysDataPermissionTargetMapper.delete(
                        Wrappers.<SysDataPermissionTarget>lambdaQuery()
                                .in(
                                        SysDataPermissionTarget::getRoleDataPermissionId,
                                        dataPermissionIds));
            }
        }
        // 2. 清理旧数据：删除该角色与该数据权限类型的旧关联
        sysRoleDataPermissionMapper.delete(
                Wrappers.<SysRoleDataPermission>lambdaQuery()
                        .eq(SysRoleDataPermission::getRoleId, roleId)
                        .eq(SysRoleDataPermission::getDataPermissionTypeId, dataPermissionTypeId));

        // 3. 插入新的 role_data_permission 记录
        SysRoleDataPermission roleDataPermission = new SysRoleDataPermission();
        roleDataPermission.setRoleId(roleId);
        roleDataPermission.setDataPermissionTypeId(dataPermissionTypeId);
        sysRoleDataPermissionMapper.insert(roleDataPermission);

        // 4. 如果 hasTarget 为 1，还需要插入 data_permission_target 表
        if (permissionType.getHasTarget() != null && permissionType.getHasTarget() == 1) {
            // 清理旧的 data_permission_target 关联（通过 role_data_permission_id）
            // 先查询之前该角色和权限类型关联的 role_data_permission 记录对应的 target
            // 由于上面已经删除了旧的 role_data_permission，但我们需要获取刚插入的 id
            Long roleDataPermissionId = roleDataPermission.getId();

            if (CollUtil.isNotEmpty(userIds)) {
                List<SysDataPermissionTarget> targets =
                        userIds.stream()
                                .map(
                                        userId -> {
                                            SysDataPermissionTarget target =
                                                    new SysDataPermissionTarget();
                                            target.setRoleDataPermissionId(roleDataPermissionId);
                                            target.setTargetType("user");
                                            target.setTargetId(userId);
                                            return target;
                                        })
                                .toList();
                sysDataPermissionTargetMapper.insert(targets);
            }
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
        // 1. 查询角色数据权限关联
        List<SysRoleDataPermission> roleDataPermissions =
                sysRoleDataPermissionMapper.selectList(
                        Wrappers.<SysRoleDataPermission>lambdaQuery()
                                .eq(SysRoleDataPermission::getRoleId, roleId));

        if (roleDataPermissions.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 获取数据权限类型ID列表
        List<Long> dataPermissionTypeIds =
                roleDataPermissions.stream()
                        .map(SysRoleDataPermission::getDataPermissionTypeId)
                        .toList();

        // 3. 查询数据权限类型信息
        List<SysDataPermissionType> permissionTypes =
                sysDataPermissionTypeMapper.selectList(
                        Wrappers.<SysDataPermissionType>lambdaQuery()
                                .in(SysDataPermissionType::getId, dataPermissionTypeIds));

        // 4. 获取需要查询 target 的 role_data_permission ID 列表（hasTarget == 1）
        List<Long> needTargetIds =
                roleDataPermissions.stream()
                        .filter(
                                rdp -> {
                                    SysDataPermissionType type =
                                            permissionTypes.stream()
                                                    .filter(
                                                            pt ->
                                                                    pt.getId()
                                                                            .equals(
                                                                                    rdp
                                                                                            .getDataPermissionTypeId()))
                                                    .findFirst()
                                                    .orElse(null);
                                    return type != null
                                            && type.getHasTarget() != null
                                            && type.getHasTarget() == 1;
                                })
                        .map(SysRoleDataPermission::getId)
                        .toList();

        // 5. 查询 data_permission_target 表
        List<SysDataPermissionTarget> targets = Collections.emptyList();
        if (CollUtil.isNotEmpty(needTargetIds)) {
            targets =
                    sysDataPermissionTargetMapper.selectList(
                            Wrappers.<SysDataPermissionTarget>lambdaQuery()
                                    .in(
                                            SysDataPermissionTarget::getRoleDataPermissionId,
                                            needTargetIds));
        }

        // 6. 构建响应
        List<SysDataPermissionTarget> finalTargets = targets;
        return roleDataPermissions.stream()
                .map(
                        rdp -> {
                            RoleDataPermissionResp resp = new RoleDataPermissionResp();
                            resp.setId(rdp.getId());
                            resp.setRoleId(rdp.getRoleId());
                            resp.setDataPermissionTypeId(rdp.getDataPermissionTypeId());

                            SysDataPermissionType type =
                                    permissionTypes.stream()
                                            .filter(
                                                    pt ->
                                                            pt.getId()
                                                                    .equals(
                                                                            rdp
                                                                                    .getDataPermissionTypeId()))
                                            .findFirst()
                                            .orElse(null);
                            if (type != null) {
                                resp.setDataPermissionTypeName(type.getName());
                                resp.setHasTarget(type.getHasTarget());

                                // hasTarget 为 1 时，查询对应的用户ID列表
                                if (type.getHasTarget() != null && type.getHasTarget() == 1) {
                                    List<Long> userIds =
                                            finalTargets.stream()
                                                    .filter(
                                                            t ->
                                                                    t.getRoleDataPermissionId()
                                                                            .equals(rdp.getId()))
                                                    .map(SysDataPermissionTarget::getTargetId)
                                                    .toList();
                                    resp.setUserIds(userIds);
                                }
                            }
                            return resp;
                        })
                .toList();
    }

    @Override
    public List<RoleModuleFieldResp> getRoleModuleFieldPermission(Long roleId, Long moduleId) {
        List<SysRoleModuleField> list =
                sysRoleModuleFieldMapper.selectList(
                        Wrappers.<SysRoleModuleField>lambdaQuery()
                                .eq(SysRoleModuleField::getRoleId, roleId)
                                .eq(SysRoleModuleField::getModuleId, moduleId));

        return list.stream()
                .map(
                        e ->
                                RoleModuleFieldResp.builder()
                                        .id(e.getId())
                                        .fieldType(e.getFieldType())
                                        .fieldId(e.getFieldId())
                                        .readable(e.getReadable())
                                        .writable(e.getWritable())
                                        .updatable(e.getUpdatable())
                                        .build())
                .toList();
    }

    @Transactional
    @Override
    public void saveRoleModuleFieldPermission(SaveRoleModuleFieldReq request) {
        Long roleId = request.getRoleId();
        Long moduleId = request.getModuleId();

        // 1. 删除该角色在该模块下的旧权限
        sysRoleModuleFieldMapper.delete(
                Wrappers.<SysRoleModuleField>lambdaQuery()
                        .eq(SysRoleModuleField::getRoleId, roleId)
                        .eq(SysRoleModuleField::getModuleId, moduleId));

        // 2. 批量插入新权限
        if (CollUtil.isNotEmpty(request.getPermissions())) {
            List<SysRoleModuleField> entities =
                    request.getPermissions().stream()
                            .map(
                                    item ->
                                            SysRoleModuleField.builder()
                                                    .roleId(roleId)
                                                    .moduleId(moduleId)
                                                    .fieldType(item.getFieldType())
                                                    .fieldId(item.getFieldId())
                                                    .readable(item.getReadable())
                                                    .writable(item.getWritable())
                                                    .updatable(item.getUpdatable())
                                                    .build())
                            .toList();
            sysRoleModuleFieldMapper.insert(entities);
        }
    }
}
