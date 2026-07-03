package com.jdec.platform.config.biz.service;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysRolePermissionApi;
import com.jdec.platform.config.api.dto.request.*;
import com.jdec.platform.config.api.dto.response.*;
import com.jdec.platform.config.biz.entity.*;
import com.jdec.platform.config.biz.mapper.*;
import com.jdec.platform.shared.annotation.ConfigChangeNotify;
import com.jdec.platform.shared.context.ConfigChangeContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.enums.ChangeType;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.*;
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
    private final SysRoleSpecialPermissionMapper sysRoleSpecialPermissionMapper;
    private final SysSpecialPermissionMapper sysSpecialPermissionMapper;
    private final SysRoleModuleFieldPermissionMapper sysRoleModuleFieldPermissionMapper;
    private final SysRoleUserMapper sysRoleUserMapper;
    private final SysRoleSubjectMapper sysRoleSubjectMapper;

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
                        .eq(SysMenu::getCategory, 2)
                        .eq(SysMenu::getSubjectId, subjectId)
                        .eq(SysMenu::getProjectNo, projectNo);
        List<SysMenu> menus = sysMenuMapper.selectList(menuWrapper);

        // 构建响应，包含所有菜单字段
        return menus.stream()
                .map(
                        menu -> {
                            RoleMenuResp resp = new RoleMenuResp();
                            resp.setMenuId(menu.getId());
                            resp.setMenuTitle(menu.getTitle());
                            resp.setPid(menu.getPid());
                            resp.setParam(menu.getParam());
                            resp.setMenuType(menu.getMenuType());
                            resp.setCategory(menu.getCategory());
                            resp.setImgDefault(menu.getImgDefault());
                            resp.setImgActive(menu.getImgActive());
                            resp.setModuleId(menu.getModuleId());
                            resp.setSortOrder(menu.getSortOrder());
                            resp.setShowed(menu.getShowed());
                            resp.setEnabled(menu.getEnabled());
                            resp.setDocUrl(menu.getDocUrl());
                            resp.setDocName(menu.getDocName());
                            resp.setVideoUrl(menu.getVideoUrl());
                            resp.setVideoName(menu.getVideoName());
                            return resp;
                        })
                .sorted(
                        Comparator.comparing(
                                RoleMenuResp::getSortOrder,
                                Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    @Override
    public List<RoleMenuResp> getRoleMenuByCategory(
            Long roleId, Integer category, Long subjectId, String projectNo) {
        // 查询角色菜单关联
        List<SysRoleMenu> roleMenus =
                sysRoleMenuMapper.selectList(
                        Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, roleId));

        if (roleMenus.isEmpty()) {
            return Collections.emptyList();
        }

        // 获取菜单ID列表
        List<Long> menuIds = roleMenus.stream().map(SysRoleMenu::getMenuId).toList();

        // 查询菜单信息，增加菜单分类过滤
        LambdaQueryWrapper<SysMenu> menuWrapper =
                new LambdaQueryWrapper<SysMenu>()
                        .in(SysMenu::getId, menuIds)
                        .eq(SysMenu::getSubjectId, subjectId)
                        .eq(SysMenu::getProjectNo, projectNo)
                        .eq(category != null, SysMenu::getCategory, category);
        List<SysMenu> menus = sysMenuMapper.selectList(menuWrapper);

        // 构建响应，包含所有菜单字段
        return menus.stream()
                .map(
                        menu -> {
                            RoleMenuResp resp = new RoleMenuResp();
                            resp.setMenuId(menu.getId());
                            resp.setMenuTitle(menu.getTitle());
                            resp.setPid(menu.getPid());
                            resp.setParam(menu.getParam());
                            resp.setMenuType(menu.getMenuType());
                            resp.setCategory(menu.getCategory());
                            resp.setImgDefault(menu.getImgDefault());
                            resp.setImgActive(menu.getImgActive());
                            resp.setModuleId(menu.getModuleId());
                            resp.setSortOrder(menu.getSortOrder());
                            resp.setShowed(menu.getShowed());
                            resp.setEnabled(menu.getEnabled());
                            resp.setDocUrl(menu.getDocUrl());
                            resp.setDocName(menu.getDocName());
                            resp.setVideoUrl(menu.getVideoUrl());
                            resp.setVideoName(menu.getVideoName());
                            return resp;
                        })
                .sorted(
                        Comparator.comparing(
                                RoleMenuResp::getSortOrder,
                                Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    @Override
    @ConfigChangeNotify(changeType = ChangeType.ROLE_MENU_CHANGED, roleIdExpr = "#request.roleId")
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

        setAffectedContext(roleId);
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
    public RoleInteractionPermissionByModuleResp getRoleInteractionPermissionByModule(
            Long roleId, Long moduleId) {
        // 1. 查询角色交互权限关联
        List<SysRoleInteractionPermission> rolePermissions =
                sysRoleInteractionPermissionMapper.selectList(
                        Wrappers.<SysRoleInteractionPermission>lambdaQuery()
                                .eq(SysRoleInteractionPermission::getRoleId, roleId));

        if (rolePermissions.isEmpty()) {
            return buildEmptyResponse();
        }

        // 2. 获取交互权限ID列表
        List<Long> permissionIds =
                rolePermissions.stream()
                        .map(SysRoleInteractionPermission::getPermissionId)
                        .toList();

        // 3. 查询交互权限信息，过滤指定模块ID
        List<SysInteractionPermission> permissions =
                sysInteractionPermissionMapper.selectList(
                        Wrappers.<SysInteractionPermission>lambdaQuery()
                                .in(SysInteractionPermission::getId, permissionIds)
                                .eq(SysInteractionPermission::getModuleId, moduleId)
                                .orderByAsc(SysInteractionPermission::getSort));

        if (permissions.isEmpty()) {
            return buildEmptyResponse();
        }

        // 4. 按类型分组
        RoleInteractionPermissionByModuleResp response =
                new RoleInteractionPermissionByModuleResp();

        List<RoleInteractionPermissionByModuleResp.InteractionPermissionItem> applyList =
                new ArrayList<>();
        List<RoleInteractionPermissionByModuleResp.InteractionPermissionItem> editList =
                new ArrayList<>();
        List<RoleInteractionPermissionByModuleResp.InteractionPermissionItem> viewList =
                new ArrayList<>();

        for (SysInteractionPermission permission : permissions) {
            RoleInteractionPermissionByModuleResp.InteractionPermissionItem item =
                    new RoleInteractionPermissionByModuleResp.InteractionPermissionItem();
            item.setPermissionId(permission.getId());
            item.setPermissionName(permission.getName());
            item.setPermissionCode(permission.getCode());
            item.setModuleId(permission.getModuleId());
            item.setType(permission.getType());
            item.setSort(permission.getSort());

            Integer type = permission.getType();
            if (type != null) {
                switch (type) {
                    case 1 -> applyList.add(item); // 申请
                    case 2 -> editList.add(item); // 编辑
                    case 3 -> viewList.add(item); // 查看
                }
            }
        }

        response.setApplyPermissions(applyList);
        response.setEditPermissions(editList);
        response.setViewPermissions(viewList);

        return response;
    }

    private RoleInteractionPermissionByModuleResp buildEmptyResponse() {
        RoleInteractionPermissionByModuleResp response =
                new RoleInteractionPermissionByModuleResp();
        response.setApplyPermissions(Collections.emptyList());
        response.setEditPermissions(Collections.emptyList());
        response.setViewPermissions(Collections.emptyList());
        return response;
    }

    @Override
    @ConfigChangeNotify(
            changeType = ChangeType.ROLE_INTERACTION_PERMISSION_CHANGED,
            roleIdExpr = "#request.roleId")
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

        setAffectedContext(roleId);
    }

    @Transactional
    @Override
    @ConfigChangeNotify(
            changeType = ChangeType.ROLE_DATA_PERMISSION_CHANGED,
            roleIdExpr = "#request.roleId")
    public void saveRoleDataPermission(SaveRoleDataPermissionReq request) {
        Long roleId = request.getRoleId();
        List<String> permissionIdList = request.getPermissionIds();

        // 1. 删除该角色的所有数据权限关联
        sysRoleDataPermissionMapper.delete(
                Wrappers.<SysRoleDataPermission>lambdaQuery()
                        .eq(SysRoleDataPermission::getRoleId, roleId));

        // 2. 解析并插入新的权限关联
        if (CollUtil.isNotEmpty(permissionIdList)) {
            List<SysRoleDataPermission> roleDataPermissions =
                    permissionIdList.stream()
                            .map(
                                    permissionIds -> {
                                        // 按 "-" 分割：permissionTypeId-dataPermissionId
                                        String[] parts = permissionIds.split("-");
                                        if (parts.length != 2) {
                                            throw new BusinessException(
                                                    "权限ID格式错误，应为：permissionTypeId-bizId");
                                        }
                                        Long permissionId = Long.parseLong(parts[0]);
                                        Long bizId = Long.parseLong(parts[1]);

                                        // 验证权限类型是否存在
                                        SysDataPermission permission =
                                                sysDataPermissionMapper.selectById(permissionId);
                                        if (permission == null) {
                                            throw new BusinessException("数据权限类型不存在");
                                        }

                                        SysRoleDataPermission rdp = new SysRoleDataPermission();
                                        rdp.setRoleId(roleId);
                                        rdp.setDataPermissionId(permissionId);
                                        rdp.setBizId(bizId);
                                        return rdp;
                                    })
                            .toList();
            sysRoleDataPermissionMapper.insert(roleDataPermissions);
        }

        setAffectedContext(roleId);
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
    @ConfigChangeNotify(
            changeType = ChangeType.ROLE_SPECIAL_PERMISSION_CHANGED,
            roleIdExpr = "#request.roleId")
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

        setAffectedContext(roleId);
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

        // 按 dataPermissionId 分组，收集拼接后的权限ID
        Map<Long, List<String>> groupedPermissions = new LinkedHashMap<>();
        for (SysRoleDataPermission rdp : roleDataPermissions) {
            if (rdp.getDataPermissionId() != null && rdp.getBizId() != null) {
                groupedPermissions
                        .computeIfAbsent(rdp.getDataPermissionId(), k -> new ArrayList<>())
                        .add(rdp.getDataPermissionId() + "-" + rdp.getBizId());
            }
        }

        // 构建响应
        return groupedPermissions.entrySet().stream()
                .map(
                        entry -> {
                            RoleDataPermissionResp resp = new RoleDataPermissionResp();
                            resp.setDataPermissionId(entry.getKey());
                            resp.setRoleId(roleId);
                            resp.setPermissionIds(entry.getValue());
                            return resp;
                        })
                .toList();
    }

    @Override
    public List<DataRightConfigResp> getRoleDataPermissionDetail(Long roleId) {
        // 1. 查询角色数据权限关联
        List<SysRoleDataPermission> roleDataPermissions =
                sysRoleDataPermissionMapper.selectList(
                        Wrappers.<SysRoleDataPermission>lambdaQuery()
                                .eq(SysRoleDataPermission::getRoleId, roleId));

        if (roleDataPermissions.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 获取所有涉及的数据权限ID
        List<Long> dataPermissionIds =
                roleDataPermissions.stream()
                        .map(SysRoleDataPermission::getDataPermissionId)
                        .distinct()
                        .toList();

        // 3. 查询数据权限类型信息
        List<SysDataPermission> dataPermissions =
                sysDataPermissionMapper.selectByIds(dataPermissionIds);
        if (dataPermissions.isEmpty()) {
            return Collections.emptyList();
        }

        // 5. 按 dataPermissionId 分组角色数据权限，收集 bizId
        Map<Long, List<Long>> permissionIdToBizIdsMap = new HashMap<>();
        for (SysRoleDataPermission rdp : roleDataPermissions) {
            permissionIdToBizIdsMap
                    .computeIfAbsent(rdp.getDataPermissionId(), k -> new ArrayList<>())
                    .add(rdp.getBizId());
        }

        // 6. 组装返回数据
        List<DataRightConfigResp> result = new ArrayList<>();
        for (SysDataPermission dataPermission : dataPermissions) {
            // 获取该权限对应的业务ID列表
            List<Long> bizIds =
                    permissionIdToBizIdsMap.getOrDefault(
                            dataPermission.getId(), Collections.emptyList());

            // 转换为 DataConfig 列表
            List<DataRightConfigResp.DataConfig> dataList =
                    bizIds.stream()
                            .map(
                                    bizId ->
                                            DataRightConfigResp.DataConfig.builder()
                                                    .bizId(bizId)
                                                    .build())
                            .toList();

            DataRightConfigResp resp =
                    DataRightConfigResp.builder()
                            .tableName(dataPermission.getCode().split("\\*")[0])
                            .dataList(dataList)
                            .build();

            result.add(resp);
        }

        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    @ConfigChangeNotify(
            changeType = ChangeType.ROLE_MODULE_PERMISSION_CHANGED,
            roleIdExpr = "#request.roleId",
            moduleIdExpr = "#request.fields[0].moduleId")
    public void saveRoleModuleFieldPermission(SaveRoleModuleFieldPermissionReq request) {
        Long roleId = request.getRoleId();

        // 1. 删除该角色下 request 中模块列表的旧字段权限记录
        if (CollUtil.isNotEmpty(request.getFields())) {
            List<Long> moduleIds =
                    request.getFields().stream()
                            .map(
                                    SaveRoleModuleFieldPermissionReq.ModuleFieldPermissionItem
                                            ::getModuleId)
                            .filter(java.util.Objects::nonNull)
                            .toList();
            if (CollUtil.isNotEmpty(moduleIds)) {
                sysRoleModuleFieldPermissionMapper.delete(
                        Wrappers.<SysRoleModuleFieldPermission>lambdaQuery()
                                .eq(SysRoleModuleFieldPermission::getRoleId, roleId)
                                .in(SysRoleModuleFieldPermission::getModuleId, moduleIds));
            }
        }

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

        setAffectedContext(request.getRoleId());
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

    @Override
    public boolean checkRoleHasPermission(Long roleId, Long subjectId, String projectNo) {
        // 查询该角色在 sys_role_module_field_permission 表中是否有记录
        long count =
                sysRoleModuleFieldPermissionMapper.selectCount(
                        Wrappers.<SysRoleModuleFieldPermission>lambdaQuery()
                                .eq(SysRoleModuleFieldPermission::getRoleId, roleId));

        return count > 0;
    }

    /**
     * 查询角色关联的用户/主体 ID，写入 ConfigChangeContext，供 Aspect 追加到 payload
     *
     * <p>所有权限变更方法（菜单、交互权限、数据权限、特殊权限、模块字段权限）在操作完成后统一调用此方法
     */
    private void setAffectedContext(Long roleId) {
        List<Long> affectedUserIds =
                sysRoleUserMapper
                        .selectList(
                                new LambdaQueryWrapper<SysRoleUser>()
                                        .eq(SysRoleUser::getRoleId, roleId))
                        .stream()
                        .map(SysRoleUser::getUserId)
                        .distinct()
                        .toList();
        List<Long> affectedSubjectIds =
                sysRoleSubjectMapper
                        .selectList(
                                new LambdaQueryWrapper<SysRoleSubject>()
                                        .eq(SysRoleSubject::getRoleId, roleId))
                        .stream()
                        .map(SysRoleSubject::getSubjectId)
                        .distinct()
                        .toList();
        ConfigChangeContext.set(affectedUserIds, affectedSubjectIds);
    }
}
