package com.jdec.platform.config.biz.service;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysRolePermissionApi;
import com.jdec.platform.config.api.dto.request.*;
import com.jdec.platform.config.api.dto.response.*;
import com.jdec.platform.config.api.enums.ModuleTypeEnum;
import com.jdec.platform.config.biz.check.CheckConstant;
import com.jdec.platform.config.biz.check.ReferenceCheckResult;
import com.jdec.platform.config.biz.check.ReferenceChecker;
import com.jdec.platform.config.biz.check.ReferenceContext;
import com.jdec.platform.config.biz.entity.*;
import com.jdec.platform.config.biz.mapper.*;
import com.jdec.platform.shared.annotation.ConfigChangeNotify;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.ConfigChangeContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.datasource.DataSourceResolver;
import com.jdec.platform.shared.enums.ChangeType;
import java.sql.Connection;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysRolePermissionService implements SysRolePermissionApi, ReferenceChecker {
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
    private final SysModuleMapper sysModuleMapper;
    private final SysModuleFieldMapper sysModuleFieldMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final DataSourceResolver dataSourceResolver;

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
        List<SaveRoleInteractionPermissionReq.PermissionItem> permissions =
                request.getPermissions();

        // 1. 提取受影响的模块 ID 列表
        List<Long> affectedModuleIds = Collections.emptyList();
        if (CollUtil.isNotEmpty(permissions)) {
            affectedModuleIds =
                    permissions.stream()
                            .map(SaveRoleInteractionPermissionReq.PermissionItem::getModuleId)
                            .filter(java.util.Objects::nonNull)
                            .distinct()
                            .toList();
        }

        // 2. 删除该角色下 request 中模块列表的旧交互权限记录
        if (CollUtil.isNotEmpty(affectedModuleIds)) {
            // 查询这些模块下的所有交互权限ID
            List<SysInteractionPermission> interactionPermissions =
                    sysInteractionPermissionMapper.selectList(
                            Wrappers.<SysInteractionPermission>lambdaQuery()
                                    .in(SysInteractionPermission::getModuleId, affectedModuleIds));

            if (CollUtil.isNotEmpty(interactionPermissions)) {
                List<Long> permissionIdsToDelete =
                        interactionPermissions.stream()
                                .map(SysInteractionPermission::getId)
                                .toList();

                // 只删除这些模块范围内的角色交互权限
                sysRoleInteractionPermissionMapper.delete(
                        Wrappers.<SysRoleInteractionPermission>lambdaQuery()
                                .eq(SysRoleInteractionPermission::getRoleId, roleId)
                                .in(
                                        SysRoleInteractionPermission::getPermissionId,
                                        permissionIdsToDelete));
            }
        }

        // 3. 批量插入新的角色交互权限关联
        if (CollUtil.isNotEmpty(permissions)) {
            List<SysRoleInteractionPermission> rolePermissions = new ArrayList<>();
            for (SaveRoleInteractionPermissionReq.PermissionItem permission : permissions) {
                if (CollUtil.isNotEmpty(permission.getPermissionIds())) {
                    for (Long permissionId : permission.getPermissionIds()) {
                        SysRoleInteractionPermission rp = new SysRoleInteractionPermission();
                        rp.setRoleId(roleId);
                        rp.setPermissionId(permissionId);
                        rolePermissions.add(rp);
                    }
                }
            }
            if (CollUtil.isNotEmpty(rolePermissions)) {
                sysRoleInteractionPermissionMapper.insert(rolePermissions);
            }
        }

        // 4. 设置受影响的上下文（用户、主体、模块）
        setAffectedContext(roleId, affectedModuleIds);
    }

    @Transactional
    @Override
    @ConfigChangeNotify(
            changeType = ChangeType.ROLE_DATA_PERMISSION_CHANGED,
            roleIdExpr = "#request.roleId")
    public void saveRoleDataPermission(SaveRoleDataPermissionReq request) {
        Long roleId = request.getRoleId();
        List<SaveRoleDataPermissionReq.SaveDataPermissionReq> saveDataPermissions =
                request.getDataPermissions();

        // 1. 删除该角色的所有数据权限关联
        sysRoleDataPermissionMapper.delete(
                Wrappers.<SysRoleDataPermission>lambdaQuery()
                        .eq(SysRoleDataPermission::getRoleId, roleId));

        // 2. 解析并插入新的权限关联
        if (CollUtil.isNotEmpty(saveDataPermissions)) {
            List<SysRoleDataPermission> roleDataPermissions =
                    saveDataPermissions.stream()
                            .flatMap(
                                    saveDataPermission ->
                                            saveDataPermission.getBizNames().stream()
                                                    .map(
                                                            bizName -> {
                                                                SysRoleDataPermission permission =
                                                                        new SysRoleDataPermission();
                                                                permission.setRoleId(
                                                                        request.getRoleId());
                                                                permission.setDataPermissionId(
                                                                        saveDataPermission
                                                                                .getPermissionId());
                                                                permission.setBizName(bizName);
                                                                return permission;
                                                            }))
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
    public RoleDataPermissionResp getRoleDataPermission(Long roleId) {
        // 查询角色数据权限关联
        List<SysRoleDataPermission> roleDataPermissions =
                sysRoleDataPermissionMapper.selectList(
                        Wrappers.<SysRoleDataPermission>lambdaQuery()
                                .eq(SysRoleDataPermission::getRoleId, roleId));

        if (roleDataPermissions.isEmpty()) {
            return new RoleDataPermissionResp();
        }

        // 按 dataPermissionId 分组，收集拼接后的权限ID
        Map<Long, List<String>> groupedPermissions =
                roleDataPermissions.stream()
                        .collect(
                                Collectors.groupingBy(
                                        SysRoleDataPermission::getDataPermissionId,
                                        Collectors.mapping(
                                                SysRoleDataPermission::getBizName,
                                                Collectors.toList())));
        RoleDataPermissionResp permissionResp = new RoleDataPermissionResp();
        permissionResp.setRoleId(roleId);
        // 构建响应
        List<RoleDataPermissionResp.DataPermissionListReq> dataPermissionListReqs =
                groupedPermissions.entrySet().stream()
                        .map(
                                entry -> {
                                    RoleDataPermissionResp.DataPermissionListReq permissionListReq =
                                            new RoleDataPermissionResp.DataPermissionListReq();
                                    permissionListReq.setPermissionId(entry.getKey());
                                    permissionListReq.setBizNames(entry.getValue());
                                    return permissionListReq;
                                })
                        .toList();
        permissionResp.setDataPermission(dataPermissionListReqs);
        return permissionResp;
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

        // 4. 构建 permissionId -> bizNames 的映射
        Map<Long, List<String>> permissionIdToBizNamesMap =
                roleDataPermissions.stream()
                        .collect(
                                Collectors.groupingBy(
                                        SysRoleDataPermission::getDataPermissionId,
                                        Collectors.mapping(
                                                SysRoleDataPermission::getBizName,
                                                Collectors.toList())));

        // 5. 构建临时数据结构：tableName -> (columnName -> bizNames)
        Map<String, Map<String, List<String>>> tableColumnBizNamesMap = new HashMap<>();

        for (SysDataPermission dataPermission : dataPermissions) {
            String code = dataPermission.getCode();
            String[] parts = code.split("\\*");
            if (parts.length != 2) {
                continue; // 跳过格式不正确的 code
            }

            String tableName = parts[0];
            String columnName = parts[1];

            List<String> bizNames =
                    permissionIdToBizNamesMap.getOrDefault(
                            dataPermission.getId(), Collections.emptyList());

            tableColumnBizNamesMap
                    .computeIfAbsent(tableName, k -> new HashMap<>())
                    .computeIfAbsent(columnName, k -> new ArrayList<>())
                    .addAll(bizNames);
        }

        // 6. 转换成最终结果：按表名分组，每个表名下按列名分组
        List<DataRightConfigResp> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<String>>> tableEntry :
                tableColumnBizNamesMap.entrySet()) {
            String tableName = tableEntry.getKey();
            Map<String, List<String>> columnBizNamesMap = tableEntry.getValue();

            List<DataRightConfigResp.DataConfig> dataList = new ArrayList<>();
            for (Map.Entry<String, List<String>> columnEntry : columnBizNamesMap.entrySet()) {
                String columnName = columnEntry.getKey();
                List<String> bizNames = columnEntry.getValue();

                DataRightConfigResp.DataConfig dataConfig =
                        DataRightConfigResp.DataConfig.builder()
                                .columnName(columnName)
                                .bizNames(bizNames)
                                .build();
                dataList.add(dataConfig);
            }

            DataRightConfigResp resp =
                    DataRightConfigResp.builder().tableName(tableName).dataList(dataList).build();
            result.add(resp);
        }

        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    @ConfigChangeNotify(
            changeType = ChangeType.ROLE_MODULE_PERMISSION_CHANGED,
            roleIdExpr = "#request.roleId")
    public void saveRoleModuleFieldPermission(SaveRoleModuleFieldPermissionReq request) {
        Long roleId = request.getRoleId();

        // 1. 提取受影响的模块 ID 列表
        List<Long> affectedModuleIds = Collections.emptyList();
        if (CollUtil.isNotEmpty(request.getFields())) {
            affectedModuleIds =
                    request.getFields().stream()
                            .map(
                                    SaveRoleModuleFieldPermissionReq.ModuleFieldPermissionItem
                                            ::getModuleId)
                            .filter(java.util.Objects::nonNull)
                            .distinct()
                            .toList();
        }

        // 2. 删除该角色下 request 中模块列表的旧字段权限记录
        if (CollUtil.isNotEmpty(affectedModuleIds)) {
            sysRoleModuleFieldPermissionMapper.delete(
                    Wrappers.<SysRoleModuleFieldPermission>lambdaQuery()
                            .eq(SysRoleModuleFieldPermission::getRoleId, roleId)
                            .in(SysRoleModuleFieldPermission::getModuleId, affectedModuleIds));
        }

        // 3. 解析并批量插入新数据
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

        // 4. 设置受影响的上下文（用户、主体、模块）
        setAffectedContext(request.getRoleId(), affectedModuleIds);
    }

    @Override
    public List<RoleModuleFieldPermissionResp> getRoleModuleFieldPermission(
            Long roleId, Integer category) {
        // 1. 直接查询该角色已分配的模块字段权限配置列表
        LambdaQueryWrapper<SysRoleModuleFieldPermission> query = Wrappers.lambdaQuery();
        query.eq(SysRoleModuleFieldPermission::getRoleId, roleId);

        if (category != null) {
            String projectNo = AppContext.getProjectNo();
            Long subjectId = AppContext.getSubjectId();
            List<SysModule> modules =
                    sysModuleMapper.selectList(
                            Wrappers.<SysModule>lambdaQuery()
                                    .eq(SysModule::getProjectNo, projectNo)
                                    .eq(SysModule::getSubjectId, subjectId)
                                    .eq(SysModule::getCategory, category));
            if (modules.isEmpty()) {
                return Collections.emptyList();
            }
            List<Long> moduleIds = modules.stream().map(SysModule::getId).toList();
            query.in(SysRoleModuleFieldPermission::getModuleId, moduleIds);
        }

        List<SysRoleModuleFieldPermission> allocatedList =
                sysRoleModuleFieldPermissionMapper.selectList(query);

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

    /**
     * 查询角色关联的用户/主体 ID，并携带自定义 payload，写入 ConfigChangeContext
     *
     * @param roleId 角色ID
     * @param customPayload 自定义 payload 数据
     */
    private void setAffectedContext(Long roleId, Map<String, Object> customPayload) {
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
        ConfigChangeContext.set(affectedUserIds, affectedSubjectIds, customPayload);
    }

    /**
     * 查询角色关联的用户/主体 ID，并携带受影响的模块 ID 列表，写入 ConfigChangeContext
     *
     * @param roleId 角色ID
     * @param affectedModuleIds 受影响的模块 ID 列表
     */
    private void setAffectedContext(Long roleId, List<Long> affectedModuleIds) {
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
        ConfigChangeContext.set(affectedUserIds, affectedSubjectIds, affectedModuleIds);
    }

    @Override
    public List<String> targetType() {
        return List.of(
                CheckConstant.SYS_ROLE,
                CheckConstant.SYS_INTERACTION_PERMISSION,
                CheckConstant.SYS_SPECIAL_PERMISSION,
                CheckConstant.SYS_DATA_PERMISSION);
    }

    @Override
    public ReferenceCheckResult check(ReferenceContext referenceContext) {
        switch (referenceContext.getTargetType()) {
            case CheckConstant.SYS_ROLE -> {
                return checkRole(referenceContext);
            }
            case CheckConstant.SYS_INTERACTION_PERMISSION -> {
                return checkInteractionPermission(referenceContext);
            }
            case CheckConstant.SYS_SPECIAL_PERMISSION -> {
                return checkSpecialPermission(referenceContext);
            }
            case CheckConstant.SYS_DATA_PERMISSION -> {
                return checkDataPermission(referenceContext);
            }
            default -> {
                return ReferenceCheckResult.empty();
            }
        }
    }

    private ReferenceCheckResult checkDataPermission(ReferenceContext referenceContext) {
        Long count =
                sysRoleDataPermissionMapper.selectCount(
                        Wrappers.<SysRoleDataPermission>lambdaQuery()
                                .eq(
                                        SysRoleDataPermission::getDataPermissionId,
                                        referenceContext.getTargetId()));
        if (count == 0) {
            return ReferenceCheckResult.empty();
        }
        return ReferenceCheckResult.builder()
                .referenced(true)
                .count(count)
                .message("数据权限节点【" + referenceContext.getTargetName() + "】绑定了" + count + "个角色数据权限")
                .build();
    }

    private ReferenceCheckResult checkSpecialPermission(ReferenceContext referenceContext) {
        Long count =
                sysRoleSpecialPermissionMapper.selectCount(
                        Wrappers.<SysRoleSpecialPermission>lambdaQuery()
                                .eq(
                                        SysRoleSpecialPermission::getSpecialPermissionId,
                                        referenceContext.getTargetId()));
        if (count == 0) {
            return ReferenceCheckResult.empty();
        }
        return ReferenceCheckResult.builder()
                .referenced(true)
                .count(count)
                .message("其它权限节点【" + referenceContext.getTargetName() + "】绑定了" + count + "个角色其它权限")
                .build();
    }

    private ReferenceCheckResult checkInteractionPermission(ReferenceContext referenceContext) {
        Long count =
                sysRoleInteractionPermissionMapper.selectCount(
                        Wrappers.<SysRoleInteractionPermission>lambdaQuery()
                                .eq(
                                        SysRoleInteractionPermission::getPermissionId,
                                        referenceContext.getTargetId()));
        if (count == 0) {
            return ReferenceCheckResult.empty();
        }
        return ReferenceCheckResult.builder()
                .referenced(true)
                .count(count)
                .message("交互权限节点【" + referenceContext.getTargetName() + "】绑定了" + count + "个角色交互权限")
                .build();
    }

    private ReferenceCheckResult checkRole(ReferenceContext referenceContext) {
        StringBuilder sb = new StringBuilder();
        Long countSum = 0L;
        Long count =
                sysRoleMenuMapper.selectCount(
                        Wrappers.<SysRoleMenu>lambdaQuery()
                                .eq(SysRoleMenu::getRoleId, referenceContext.getTargetId()));
        if (count > 0) {
            countSum += count;
            sb.append("角色【")
                    .append(referenceContext.getTargetName())
                    .append("】绑定了")
                    .append(count)
                    .append("个菜单权限\n");
        }
        count =
                sysRoleModuleFieldPermissionMapper.selectCount(
                        Wrappers.<SysRoleModuleFieldPermission>lambdaQuery()
                                .eq(
                                        SysRoleModuleFieldPermission::getRoleId,
                                        referenceContext.getTargetId()));
        if (count > 0) {
            countSum += count;
            sb.append("角色【")
                    .append(referenceContext.getTargetName())
                    .append("】绑定了")
                    .append(count)
                    .append("个字段权限\n");
        }
        count =
                sysRoleInteractionPermissionMapper.selectCount(
                        Wrappers.<SysRoleInteractionPermission>lambdaQuery()
                                .eq(
                                        SysRoleInteractionPermission::getRoleId,
                                        referenceContext.getTargetId()));
        if (count > 0) {
            countSum += count;
            sb.append("角色【")
                    .append(referenceContext.getTargetName())
                    .append("】绑定了")
                    .append(count)
                    .append("个交互权限\n");
        }
        count =
                sysRoleDataPermissionMapper.selectCount(
                        Wrappers.<SysRoleDataPermission>lambdaQuery()
                                .eq(
                                        SysRoleDataPermission::getRoleId,
                                        referenceContext.getTargetId()));
        if (count > 0) {
            countSum += count;
            sb.append("角色【")
                    .append(referenceContext.getTargetName())
                    .append("】绑定了")
                    .append(count)
                    .append("个数据权限\n");
        }
        count =
                sysRoleSpecialPermissionMapper.selectCount(
                        Wrappers.<SysRoleSpecialPermission>lambdaQuery()
                                .eq(
                                        SysRoleSpecialPermission::getRoleId,
                                        referenceContext.getTargetId()));
        if (count > 0) {
            countSum += count;
            sb.append("角色【")
                    .append(referenceContext.getTargetName())
                    .append("】绑定了")
                    .append(count)
                    .append("个其它权限");
        }
        if (countSum == 0) {
            return ReferenceCheckResult.empty();
        }
        return ReferenceCheckResult.builder()
                .referenced(true)
                .count(countSum)
                .message(sb.toString())
                .build();
    }

    @Override
    public List<PersonalSettingResp> getPersonalSettingByRole(Long roleId) {
        // 1. 根据 roleId 查询 sys_role_module_field_permission
        List<SysRoleModuleFieldPermission> roleFieldPermissions =
                sysRoleModuleFieldPermissionMapper.selectList(
                        Wrappers.<SysRoleModuleFieldPermission>lambdaQuery()
                                .eq(SysRoleModuleFieldPermission::getRoleId, roleId));

        if (CollUtil.isEmpty(roleFieldPermissions)) {
            return Collections.emptyList();
        }

        // 2. 获取模块ID列表
        List<Long> moduleIds =
                roleFieldPermissions.stream()
                        .map(SysRoleModuleFieldPermission::getModuleId)
                        .distinct()
                        .toList();

        // 3. 查询模块表，筛选出 moduleType 为 LIST 的模块
        List<SysModule> listModules =
                sysModuleMapper.selectList(
                        Wrappers.<SysModule>lambdaQuery()
                                .in(SysModule::getId, moduleIds)
                                .eq(SysModule::getModuleType, ModuleTypeEnum.LIST)
                                .eq(SysModule::getSubjectId, AppContext.getSubjectId())
                                .eq(SysModule::getProjectNo, AppContext.getProjectNo()));

        if (CollUtil.isEmpty(listModules)) {
            return Collections.emptyList();
        }

        // 4. 获取 LIST 类型的模块ID集合
        Set<Long> listModuleIds =
                listModules.stream().map(SysModule::getId).collect(Collectors.toSet());

        // 5. 过滤出属于 LIST 类型模块的字段权限
        List<SysRoleModuleFieldPermission> filteredPermissions =
                roleFieldPermissions.stream()
                        .filter(p -> listModuleIds.contains(p.getModuleId()))
                        .toList();

        // 6. 构造 moduleId -> 角色有权限的 fieldId 集合
        Map<Long, Set<Long>> modulePermittedFieldIds =
                filteredPermissions.stream()
                        .collect(
                                Collectors.groupingBy(
                                        SysRoleModuleFieldPermission::getModuleId,
                                        Collectors.mapping(
                                                SysRoleModuleFieldPermission::getFieldId,
                                                Collectors.toSet())));

        // 7. 直接用 moduleId 查询字段表（sys_module_field）
        List<SysModuleField> moduleFields =
                sysModuleFieldMapper.selectList(
                        Wrappers.<SysModuleField>lambdaQuery()
                                .in(SysModuleField::getModuleId, listModuleIds));

        // 8. 构造 (moduleId + table + field) -> SysModuleField 的映射，便于快速查找
        Map<String, SysModuleField> fieldMap =
                moduleFields.stream()
                        .collect(
                                Collectors.toMap(
                                        f ->
                                                f.getModuleId()
                                                        + "_"
                                                        + f.getTableName()
                                                        + "_"
                                                        + f.getFieldCode(),
                                        f -> f));

        // 9. 批量查询表注释
        Map<String, String> tableCommentMap = batchQueryTableComments(listModules);

        // 11. 构造响应体
        List<PersonalSettingResp> result = new ArrayList<>();

        for (SysModule module : listModules) {
            // 解析 tableHeader
            String tableHeaderJson = module.getTableHeader();
            List<com.jdec.platform.config.api.dto.common.ModuleTableHeaderDTO> tableHeaders =
                    parseTableHeader(tableHeaderJson);

            // 获取该模块有权限的 fieldId 集合
            Set<Long> permittedFieldIds =
                    modulePermittedFieldIds.getOrDefault(module.getId(), Collections.emptySet());
            if (permittedFieldIds.isEmpty()) {
                continue; // 该模块没有字段权限，跳过
            }

            // 构造 personalSettingColumns
            List<PersonalSettingResp.PersonalSettingColumn> columns = new ArrayList<>();

            for (com.jdec.platform.config.api.dto.common.ModuleTableHeaderDTO header :
                    tableHeaders) {
                // 使用复合 key 快速查找字段
                String fieldKey =
                        module.getId() + "_" + header.getTable() + "_" + header.getField();
                SysModuleField moduleField = fieldMap.get(fieldKey);

                // 检查该字段是否存在且有权限
                if (moduleField != null && permittedFieldIds.contains(moduleField.getId())) {
                    PersonalSettingResp.PersonalSettingColumn column =
                            new PersonalSettingResp.PersonalSettingColumn();
                    column.setId(moduleField.getId());
                    column.setName(header.getName());
                    column.setTable(header.getTable());
                    // 设置表中文名：优先从 tableCommentMap 获取，否则使用英文表名
                    String tableName =
                            tableCommentMap.getOrDefault(
                                    header.getTable().toLowerCase(), header.getTable());
                    column.setTableName(tableName);
                    column.setField(header.getField());
                    column.setWidth(header.getWidth());
                    column.setFixed(header.getFixed());
                    column.setSearchType(header.getSearchType());
                    column.setEllipsis(
                            header.getEllipsis() != null && header.getEllipsis() ? 1 : 0);
                    column.setSortable(
                            header.getSortable() != null && header.getSortable() ? 1 : 0);
                    column.setSortOrder(header.getSortOrder());

                    columns.add(column);
                }
            }

            // 只有有字段权限的模块才加入结果集
            if (!columns.isEmpty()) {
                PersonalSettingResp resp = new PersonalSettingResp();
                resp.setId(module.getId());
                resp.setModuleName(module.getModuleName());
                resp.setPersonalSettingColumns(columns);
                result.add(resp);
            }
        }

        return result;
    }

    /** 解析表头 JSON 字符串 */
    private List<com.jdec.platform.config.api.dto.common.ModuleTableHeaderDTO> parseTableHeader(
            String tableHeaderJson) {
        if (tableHeaderJson == null || tableHeaderJson.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper objectMapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            return objectMapper.readValue(
                    tableHeaderJson,
                    new com.fasterxml.jackson.core.type.TypeReference<
                            List<
                                    com.jdec.platform.config.api.dto.common
                                            .ModuleTableHeaderDTO>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 批量查询表注释
     *
     * <p>根据项目编码从配置中获取对应的数据源，然后查询 information_schema 获取表注释
     *
     * @param listModules 模块列表
     * @return 表名 -> 表注释的映射（表名统一转小写）
     */
    private Map<String, String> batchQueryTableComments(List<SysModule> listModules) {
        Map<String, String> tableCommentMap = new HashMap<>();

        if (listModules == null || listModules.isEmpty()) {
            return tableCommentMap;
        }

        // 1. 收集所有表名
        Set<String> allTableNames = new HashSet<>();
        for (SysModule module : listModules) {
            String tableHeaderJson = module.getTableHeader();
            List<com.jdec.platform.config.api.dto.common.ModuleTableHeaderDTO> tableHeaders =
                    parseTableHeader(tableHeaderJson);
            for (com.jdec.platform.config.api.dto.common.ModuleTableHeaderDTO header :
                    tableHeaders) {
                if (header.getTable() != null && !header.getTable().trim().isEmpty()) {
                    allTableNames.add(header.getTable());
                }
            }
        }

        // 移除通配符
        allTableNames.remove("*");
        if (allTableNames.isEmpty()) {
            return tableCommentMap;
        }

        try {
            // 1. 获取项目编码
            String projectNo = AppContext.getProjectNo();
            if (projectNo == null || projectNo.trim().isEmpty()) {
                log.warn("项目编码为空，无法查询表注释");
                return tableCommentMap;
            }

            // 2. 根据项目编码解析对应的数据源
            javax.sql.DataSource targetDataSource = dataSourceResolver.resolve(projectNo);

            // 3. 使用目标数据源创建 JdbcTemplate
            org.springframework.jdbc.core.JdbcTemplate targetJdbcTemplate =
                    new org.springframework.jdbc.core.JdbcTemplate(targetDataSource);

            // 4. 获取数据库 schema 名称（优先从连接获取，降级使用 projectNo）
            String tableSchema =
                    targetJdbcTemplate.execute(
                            (Connection conn) -> {
                                String catalog = conn.getCatalog();
                                return (catalog != null && !catalog.trim().isEmpty())
                                        ? catalog
                                        : projectNo;
                            });

            // 5. 构造 SQL 查询表注释
            String tableInSql =
                    allTableNames.stream().map(t -> "?").collect(Collectors.joining(","));
            String tableCommentSql =
                    String.format(
                            "SELECT TABLE_NAME, TABLE_COMMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME IN (%s)",
                            tableInSql);

            // 6. 准备参数
            List<Object> tableParams = new ArrayList<>();
            tableParams.add(tableSchema);
            tableParams.addAll(allTableNames);

            // 7. 执行查询
            List<Map<String, Object>> tableRows =
                    targetJdbcTemplate.queryForList(tableCommentSql, tableParams.toArray());

            // 8. 构造返回结果（表名统一转小写）
            for (Map<String, Object> row : tableRows) {
                String tName = (String) row.get("TABLE_NAME");
                String tComment = (String) row.get("TABLE_COMMENT");
                if (tName != null) {
                    tableCommentMap.put(tName.toLowerCase(), tComment);
                }
            }

            log.debug(
                    "批量查询表注释成功: projectNo={}, schema={}, 查询{}个表，返回{}条结果",
                    projectNo,
                    tableSchema,
                    allTableNames.size(),
                    tableCommentMap.size());

        } catch (Exception e) {
            log.warn("批量获取表注释失败，将使用表英文名", e);
        }

        return tableCommentMap;
    }
}
