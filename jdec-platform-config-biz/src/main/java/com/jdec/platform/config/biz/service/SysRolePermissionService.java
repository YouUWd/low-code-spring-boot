package com.jdec.platform.config.biz.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysRolePermissionApi;
import com.jdec.platform.config.api.dto.request.*;
import com.jdec.platform.config.api.dto.response.*;
import com.jdec.platform.config.biz.check.CheckConstant;
import com.jdec.platform.config.biz.check.ReferenceCheckResult;
import com.jdec.platform.config.biz.check.ReferenceChecker;
import com.jdec.platform.config.biz.check.ReferenceContext;
import com.jdec.platform.config.biz.entity.*;
import com.jdec.platform.config.biz.entity.SysModuleHeader;
import com.jdec.platform.config.biz.mapper.*;
import com.jdec.platform.config.biz.mapper.SysModuleHeaderMapper;
import com.jdec.platform.shared.annotation.ConfigChangeNotify;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.ConfigChangeContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.datasource.DataSourceResolver;
import com.jdec.platform.shared.enums.ChangeType;
import com.jdec.platform.shared.enums.EffectiveTypeEnum;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_ENGINE)
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
    private final SysModuleHeaderMapper sysModuleHeaderMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final DataSourceResolver dataSourceResolver;
    private final com.jdec.platform.config.biz.util.AuditLogHelper auditLogHelper;
    private final SysRoleMapper sysRoleMapper;

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
                        .eq(SysMenu::getEnabled, true)
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

        // 编程式审计日志：记录菜单权限变更
        logMenuPermissionAudit(
                roleId,
                request.getCheckFlag(),
                request.getPermissionPaths(),
                request.getPermissionTypeName());

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

        // 4. 记录审计日志（编程式）
        logInteractionPermissionAudit(
                roleId,
                request.getCheckFlag(),
                request.getPermissionPaths(),
                request.getPermissionTypeName());

        // 5. 设置受影响的上下文（用户、主体、模块）
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

        // 3. 记录审计日志（编程式）
        logDataPermissionAudit(
                roleId,
                request.getCheckFlag(),
                request.getPermissionPaths(),
                request.getPermissionTypeName());

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

        // 记录审计日志（编程式）
        logSpecialPermissionAudit(
                roleId,
                request.getCheckFlag(),
                request.getPermissionPaths(),
                request.getPermissionTypeName());

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

        // 4. 审计日志记录（编程式）
        logModuleFieldPermissionAudit(
                roleId,
                request.getCheckFlag(),
                request.getPermissionPaths(),
                request.getPermissionTypeName());

        // 5. 设置受影响的上下文（用户、主体、模块）
        setAffectedContext(request.getRoleId(), affectedModuleIds);
    }

    @Override
    public List<RoleModuleFieldPermissionResp> getRoleModuleFieldPermission(
            Long roleId, Integer category) {
        // 0. 根据角色有效期更新已失效的关联状态，若角色已失效则直接返回空集合
        updateExpiredRelationsByRoleId(roleId);
        if (isRoleExpired(roleId)) {
            return Collections.emptyList();
        }

        // 1. 直接查询该角色已分配的模块字段权限配置列表
        LambdaQueryWrapper<SysRoleModuleFieldPermission> query = Wrappers.lambdaQuery();
        query.eq(SysRoleModuleFieldPermission::getRoleId, roleId);

        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        List<SysModule> modules =
                sysModuleMapper.selectList(
                        Wrappers.<SysModule>lambdaQuery()
                                .eq(SysModule::getProjectNo, projectNo)
                                .eq(SysModule::getSubjectId, subjectId));
        if (modules.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> moduleIds = modules.stream().map(SysModule::getId).toList();
        query.in(SysRoleModuleFieldPermission::getModuleId, moduleIds);

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

    /**
     * 根据角色ID更新关联状态：将已失效的置为1（失效），将已到期的待生效置为0（生效）
     *
     * <p>处理两种状态转换：
     *
     * <ul>
     *   <li>已失效：当前时间超出有效期，status 0→1
     *   <li>待生效→生效：当前时间符合生效条件，status 2→0
     * </ul>
     *
     * @param roleId 角色ID
     */
    private void updateExpiredRelationsByRoleId(Long roleId) {
        LocalDate today = LocalDate.now();

        // 1. 处理用户关联表
        // 1.1 查询生效和待生效的记录
        List<SysRoleUser> activeAndPendingUsers =
                sysRoleUserMapper.selectList(
                        Wrappers.<SysRoleUser>lambdaQuery()
                                .eq(SysRoleUser::getRoleId, roleId)
                                .in(SysRoleUser::getStatus, 0, 2));

        List<Long> expiredUserIds = new ArrayList<>();
        List<Long> activatedUserIds = new ArrayList<>();

        for (SysRoleUser roleUser : activeAndPendingUsers) {
            Integer currentStatus = roleUser.getStatus();
            Integer newStatus =
                    calculateCurrentStatus(
                            roleUser.getEffectiveType(),
                            roleUser.getEffectiveStartDate(),
                            roleUser.getEffectiveEndDate(),
                            today);

            // 状态 0→1：已失效
            if (currentStatus == 0 && newStatus == 1) {
                expiredUserIds.add(roleUser.getId());
            }
            // 状态 2→0：待生效→生效
            else if (currentStatus == 2 && newStatus == 0) {
                activatedUserIds.add(roleUser.getId());
            }
        }

        // 批量更新为失效
        if (!expiredUserIds.isEmpty()) {
            SysRoleUser updateEntity = new SysRoleUser();
            updateEntity.setStatus(1);
            sysRoleUserMapper.update(
                    updateEntity,
                    Wrappers.<SysRoleUser>lambdaQuery().in(SysRoleUser::getId, expiredUserIds));
        }

        // 批量更新为生效
        if (!activatedUserIds.isEmpty()) {
            SysRoleUser updateEntity = new SysRoleUser();
            updateEntity.setStatus(0);
            sysRoleUserMapper.update(
                    updateEntity,
                    Wrappers.<SysRoleUser>lambdaQuery().in(SysRoleUser::getId, activatedUserIds));
        }

        // 2. 处理主体关联表
        // 2.1 查询生效和待生效的记录
        List<SysRoleSubject> activeAndPendingSubjects =
                sysRoleSubjectMapper.selectList(
                        Wrappers.<SysRoleSubject>lambdaQuery()
                                .eq(SysRoleSubject::getRoleId, roleId)
                                .in(SysRoleSubject::getStatus, 0, 2));

        List<Long> expiredSubjectIds = new ArrayList<>();
        List<Long> activatedSubjectIds = new ArrayList<>();

        for (SysRoleSubject roleSubject : activeAndPendingSubjects) {
            Integer currentStatus = roleSubject.getStatus();
            Integer newStatus =
                    calculateCurrentStatus(
                            roleSubject.getEffectiveType(),
                            roleSubject.getEffectiveStartDate(),
                            roleSubject.getEffectiveEndDate(),
                            today);

            // 状态 0→1：已失效
            if (currentStatus == 0 && newStatus == 1) {
                expiredSubjectIds.add(roleSubject.getId());
            }
            // 状态 2→0：待生效→生效
            else if (currentStatus == 2 && newStatus == 0) {
                activatedSubjectIds.add(roleSubject.getId());
            }
        }

        // 批量更新为失效
        if (!expiredSubjectIds.isEmpty()) {
            SysRoleSubject updateEntity = new SysRoleSubject();
            updateEntity.setStatus(1);
            sysRoleSubjectMapper.update(
                    updateEntity,
                    Wrappers.<SysRoleSubject>lambdaQuery()
                            .in(SysRoleSubject::getId, expiredSubjectIds));
        }

        // 批量更新为生效
        if (!activatedSubjectIds.isEmpty()) {
            SysRoleSubject updateEntity = new SysRoleSubject();
            updateEntity.setStatus(0);
            sysRoleSubjectMapper.update(
                    updateEntity,
                    Wrappers.<SysRoleSubject>lambdaQuery()
                            .in(SysRoleSubject::getId, activatedSubjectIds));
        }
    }

    /**
     * 判断角色是否已失效
     *
     * <p>角色在用户关联和主体关联中均无有效（status=0）记录时视为失效
     *
     * @param roleId 角色ID
     * @return true-角色已失效
     */
    private boolean isRoleExpired(Long roleId) {
        long validUserCount =
                sysRoleUserMapper.selectCount(
                        Wrappers.<SysRoleUser>lambdaQuery()
                                .eq(SysRoleUser::getRoleId, roleId)
                                .eq(SysRoleUser::getStatus, 0));
        long validSubjectCount =
                sysRoleSubjectMapper.selectCount(
                        Wrappers.<SysRoleSubject>lambdaQuery()
                                .eq(SysRoleSubject::getRoleId, roleId)
                                .eq(SysRoleSubject::getStatus, 0));
        return validUserCount == 0 && validSubjectCount == 0;
    }

    /**
     * 计算当前应该处于的状态
     *
     * @param effectiveType 有效期类型：1-永久，2-自定义
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param today 当前日期
     * @return 0-生效，1-失效，2-待生效
     */
    private Integer calculateCurrentStatus(
            Integer effectiveType, LocalDate startDate, LocalDate endDate, LocalDate today) {
        // 永久有效，直接生效
        if (EffectiveTypeEnum.PERMANENT.getCode().equals(effectiveType)) {
            return 0;
        }

        // 自定义时间
        if (EffectiveTypeEnum.CUSTOM.getCode().equals(effectiveType)) {
            // 开始时间和结束时间都为空，视为永久有效
            if (startDate == null && endDate == null) {
                return 0;
            }

            // 只有开始时间：今天 < 开始时间 → 待生效，否则 → 生效
            if (startDate != null && endDate == null) {
                return today.isBefore(startDate) ? 2 : 0;
            }

            // 只有结束时间：今天 > 结束时间 → 失效，否则 → 生效
            if (startDate == null && endDate != null) {
                return today.isAfter(endDate) ? 1 : 0;
            }

            // 开始时间和结束时间都有
            if (today.isBefore(startDate)) {
                return 2; // 待生效
            } else if (today.isAfter(endDate)) {
                return 1; // 失效
            } else {
                return 0; // 生效
            }
        }

        // 其他情况视为失效
        return 1;
    }

    /**
     * 校验时间有效期（仅判断是否在有效期内，不区分待生效）
     *
     * @param effectiveType 有效期类型：1-永久，2-自定义
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param today 当前日期
     * @return true-有效，false-无效
     */
    private boolean isEffectiveDateValid(
            Integer effectiveType, LocalDate startDate, LocalDate endDate, LocalDate today) {
        Integer status = calculateCurrentStatus(effectiveType, startDate, endDate, today);
        return status == 0; // 只有生效状态才算有效
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

    @Override
    public boolean checkRoleHasModuleWrite(Long roleId, Long moduleId) {
        // 查询该角色在 sys_role_module_field_permission 表中是否有写入权限记录
        long count =
                sysRoleModuleFieldPermissionMapper.selectCount(
                        Wrappers.<SysRoleModuleFieldPermission>lambdaQuery()
                                .eq(SysRoleModuleFieldPermission::getRoleId, roleId)
                                .eq(SysRoleModuleFieldPermission::getModuleId, moduleId)
                                .eq(SysRoleModuleFieldPermission::getWritable, 1));

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
            case CheckConstant.SYS_MENU -> {
                return checkMenu(referenceContext);
            }
            default -> {
                return ReferenceCheckResult.empty();
            }
        }
    }

    private ReferenceCheckResult checkMenu(ReferenceContext referenceContext) {
        Long count =
                sysRoleMenuMapper.selectCount(
                        Wrappers.<SysRoleMenu>lambdaQuery()
                                .eq(SysRoleMenu::getMenuId, referenceContext.getTargetId()));
        if (count == 0) {
            return ReferenceCheckResult.empty();
        }
        return ReferenceCheckResult.builder()
                .referenced(true)
                .count(count)
                .message("菜单【" + referenceContext.getTargetName() + "】绑定了" + count + "个角色菜单权限")
                .build();
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
    public List<PersonalSettingResp> getPersonalSettingByRole(Long roleId, Long moduleId) {
        // 1. 根据 roleId 查询 sys_role_module_field_permission
        LambdaQueryWrapper<SysRoleModuleFieldPermission> query =
                Wrappers.<SysRoleModuleFieldPermission>lambdaQuery()
                        .eq(SysRoleModuleFieldPermission::getRoleId, roleId)
                        .eq(moduleId != null, SysRoleModuleFieldPermission::getModuleId, moduleId);

        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        List<SysModule> modules =
                sysModuleMapper.selectList(
                        Wrappers.<SysModule>lambdaQuery()
                                .eq(SysModule::getProjectNo, projectNo)
                                .eq(SysModule::getSubjectId, subjectId));
        if (modules.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> moduleIds = modules.stream().map(SysModule::getId).toList();
        query.in(SysRoleModuleFieldPermission::getModuleId, moduleIds);

        List<SysRoleModuleFieldPermission> roleFieldPermissions =
                sysRoleModuleFieldPermissionMapper.selectList(query);

        if (CollUtil.isEmpty(roleFieldPermissions)) {
            return Collections.emptyList();
        }

        // 2. 获取模块ID列表
        List<Long> activeModuleIds =
                roleFieldPermissions.stream()
                        .map(SysRoleModuleFieldPermission::getModuleId)
                        .distinct()
                        .toList();

        // 3. 查询模块表
        List<SysModule> listModules =
                sysModuleMapper.selectList(
                        Wrappers.<SysModule>lambdaQuery()
                                .in(SysModule::getId, activeModuleIds)
                                .eq(SysModule::getSubjectId, AppContext.getSubjectId())
                                .eq(SysModule::getProjectNo, AppContext.getProjectNo()));

        if (CollUtil.isEmpty(listModules)) {
            return Collections.emptyList();
        }

        // 4. 获取模块ID集合
        Set<Long> listModuleIds =
                listModules.stream().map(SysModule::getId).collect(Collectors.toSet());

        // 5. 过滤出字段权限
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

        // 8. 构造 (moduleId + table + column) -> SysModuleField 的映射，便于快速查找
        Map<String, SysModuleField> fieldMap =
                moduleFields.stream()
                        .collect(
                                Collectors.toMap(
                                        f ->
                                                f.getModuleId()
                                                        + "_"
                                                        + f.getTableName()
                                                        + "_"
                                                        + f.getColumnName(),
                                        f -> f));

        // 9. 批量查询表头配置 sys_module_header
        List<SysModuleHeader> allHeaders =
                sysModuleHeaderMapper.selectList(
                        Wrappers.<SysModuleHeader>lambdaQuery()
                                .in(SysModuleHeader::getModuleId, listModuleIds)
                                .orderByAsc(SysModuleHeader::getSortOrder));
        Map<Long, List<SysModuleHeader>> headersByModule =
                allHeaders.stream().collect(Collectors.groupingBy(SysModuleHeader::getModuleId));

        // 10. 批量查询表注释
        Map<String, String> tableCommentMap = batchQueryTableComments(allHeaders);

        // 11. 构造响应体
        List<PersonalSettingResp> result = new ArrayList<>();

        for (SysModule module : listModules) {
            List<SysModuleHeader> tableHeaders =
                    headersByModule.getOrDefault(module.getId(), Collections.emptyList());

            // 获取该模块有权限的 fieldId 集合
            Set<Long> permittedFieldIds =
                    modulePermittedFieldIds.getOrDefault(module.getId(), Collections.emptySet());
            if (permittedFieldIds.isEmpty()) {
                continue; // 该模块没有字段权限，跳过
            }

            // 构造 personalSettingColumns
            List<PersonalSettingResp.PersonalSettingColumn> columns = new ArrayList<>();

            for (SysModuleHeader header : tableHeaders) {
                // 使用复合 key 快速查找字段
                String fieldKey =
                        module.getId() + "_" + header.getTableName() + "_" + header.getColumnName();
                SysModuleField moduleField = fieldMap.get(fieldKey);

                // 检查该字段是否存在且有权限
                if (moduleField != null && permittedFieldIds.contains(moduleField.getId())) {
                    PersonalSettingResp.PersonalSettingColumn column =
                            new PersonalSettingResp.PersonalSettingColumn();
                    column.setId(moduleField.getId());
                    column.setName(header.getHeaderName());
                    column.setTable(header.getTableName());
                    // 设置表中文名：优先从 tableCommentMap 获取，否则使用英文表名
                    String tableName =
                            tableCommentMap.getOrDefault(
                                    header.getTableName().toLowerCase(), header.getTableName());
                    column.setTableName(tableName);
                    column.setField(header.getColumnName());
                    column.setWidth(header.getWidth());
                    column.setFixed(header.getFixed());
                    column.setSearchType(header.getSearchType());
                    column.setEllipsis(header.getEllipsis());
                    column.setSortable(header.getSortable());
                    column.setSortOrder(header.getSortOrder());

                    columns.add(column);
                }
            }

            // 只有当有权限的字段不为空时，才添加到结果列表
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

    /**
     * 批量查询表注释
     *
     * <p>根据项目编码从配置中获取对应的数据源，然后查询 information_schema 获取表注释
     *
     * @param allHeaders 表头列表
     * @return 表名 -> 表注释的映射（表名统一转小写）
     */
    private Map<String, String> batchQueryTableComments(List<SysModuleHeader> allHeaders) {
        Map<String, String> tableCommentMap = new HashMap<>();

        if (allHeaders == null || allHeaders.isEmpty()) {
            return tableCommentMap;
        }

        // 1. 收集所有表名
        Set<String> allTableNames =
                allHeaders.stream()
                        .map(SysModuleHeader::getTableName)
                        .filter(t -> t != null && !t.trim().isEmpty() && !"*".equals(t))
                        .collect(Collectors.toSet());

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

    /**
     * 通用权限审计日志记录
     *
     * <p>构建标准审计日志格式：
     *
     * <pre>
     * {"change":[{"name":"角色名","mId":"","actions":[{"i/d":[{"name":"前缀-权限类型","columns":[{"name":"","old":"","newer":"permissionPaths拼接","field":""}]}]}]}]}
     * </pre>
     *
     * @param roleId 角色ID
     * @param checkFlag 是否勾选（1=勾选新增，0=取消勾选）
     * @param permissionPaths 权限路径列表
     * @param permissionTypeName 权限类型后缀（如："业务菜单权限"、"系统菜单权限"）
     * @param subModuleStr 子模块字符串（如："角色菜单权限"）
     * @param tableName 表名（如："sys_role_menu"）
     * @param recordNamePrefix 记录名称前缀（如："菜单显示"、"模块权限"），硬编码
     * @param suffixFromPath 是否从permissionPaths取后缀（特殊权限用true，此时permissionTypeName传null）
     */
    private void logPermissionAudit(
            Long roleId,
            Integer checkFlag,
            List<String> permissionPaths,
            String permissionTypeName,
            String subModuleStr,
            String tableName,
            String recordNamePrefix,
            boolean suffixFromPath) {
        if (checkFlag == null || CollUtil.isEmpty(permissionPaths)) {
            return;
        }

        try {
            // 1. 查询角色名称
            SysRole role = sysRoleMapper.selectById(roleId);
            if (role == null) {
                log.warn("角色不存在，无法记录审计日志: roleId={}", roleId);
                return;
            }
            String roleName = role.getRoleName();

            // 2. 构建记录名称：前缀-权限类型
            String suffix;
            if (suffixFromPath) {
                // 其它权限从permissionPaths取
                suffix = permissionPaths.getFirst();
            } else {
                suffix = permissionTypeName != null ? permissionTypeName : "";
            }
            String recordName = recordNamePrefix + "-" + suffix;

            // 3. 构建permissionPath字符串（用-拼接）
            String permissionPathStr = String.join("-", permissionPaths);

            // 4. 构建审计日志结构
            // RecordChange
            Map<String, Object> recordChange = new LinkedHashMap<>();
            recordChange.put("name", recordName);

            // 其它权限不需要 columns
            if (!suffixFromPath) {
                // ColumnChange: 新增放newer，删除放old
                Map<String, Object> column = new LinkedHashMap<>();
                column.put("name", "");
                if (checkFlag == 1) {
                    column.put("old", "");
                    column.put("newer", permissionPathStr);
                } else {
                    column.put("old", permissionPathStr);
                    column.put("newer", "");
                }
                column.put("field", "");

                recordChange.put("columns", Collections.singletonList(column));
            }

            // ActionDetail: checkFlag=1 -> i(新增), checkFlag=0 -> d(取消)
            Map<String, Object> actionDetail = new LinkedHashMap<>();
            String actionKey = (checkFlag == 1) ? "i" : "d";
            actionDetail.put(actionKey, Collections.singletonList(recordChange));

            // ModuleAction: name=角色名
            Map<String, Object> moduleAction = new LinkedHashMap<>();
            moduleAction.put("name", roleName);
            moduleAction.put("mId", "");
            moduleAction.put("actions", actionDetail);

            // 构建完整的 logRemark
            Map<String, Object> remarkMap = new LinkedHashMap<>();
            remarkMap.put("change", Collections.singletonList(moduleAction));
            String remarkJson = JSONUtil.toJsonStr(remarkMap);

            // 5. 发送审计日志
            String operation = (checkFlag == 1) ? "新增权限" : "取消权限";
            auditLogHelper.logWithCustomRemark(
                    subModuleStr, tableName, operation, roleId, remarkJson);

        } catch (Exception e) {
            log.error("记录{}审计日志失败: roleId={}, checkFlag={}", subModuleStr, roleId, checkFlag, e);
        }
    }

    /**
     * 处理菜单权限审计日志
     *
     * @param roleId 角色ID
     * @param checkFlag 是否勾选（1=勾选新增，0=取消勾选）
     * @param permissionPaths 权限路径列表
     * @param permissionTypeName 权限类型后缀（如："业务菜单权限"）
     */
    private void logMenuPermissionAudit(
            Long roleId,
            Integer checkFlag,
            List<String> permissionPaths,
            String permissionTypeName) {
        logPermissionAudit(
                roleId,
                checkFlag,
                permissionPaths,
                permissionTypeName,
                "角色菜单权限",
                "sys_role_menu",
                "菜单显示",
                false);
    }

    /**
     * 处理交互权限审计日志
     *
     * @param roleId 角色ID
     * @param checkFlag 是否勾选（1=勾选新增，0=取消勾选）
     * @param permissionPaths 权限路径列表
     * @param permissionTypeName 权限类型后缀（如："业务菜单权限"）
     */
    private void logInteractionPermissionAudit(
            Long roleId,
            Integer checkFlag,
            List<String> permissionPaths,
            String permissionTypeName) {
        logPermissionAudit(
                roleId,
                checkFlag,
                permissionPaths,
                permissionTypeName,
                "角色交互权限",
                "sys_role_interaction_permission",
                "互动权限",
                false);
    }

    /**
     * 处理数据权限审计日志
     *
     * @param roleId 角色ID
     * @param checkFlag 是否勾选（1=勾选新增，0=取消勾选）
     * @param permissionPaths 权限路径列表
     * @param permissionTypeName 权限类型后缀（如："业务菜单权限"）
     */
    private void logDataPermissionAudit(
            Long roleId,
            Integer checkFlag,
            List<String> permissionPaths,
            String permissionTypeName) {
        logPermissionAudit(
                roleId,
                checkFlag,
                permissionPaths,
                permissionTypeName,
                "角色数据权限",
                "sys_role_data_permission",
                "数据权限",
                false);
    }

    /**
     * 处理模块字段权限审计日志
     *
     * @param roleId 角色ID
     * @param checkFlag 是否勾选（1=勾选新增，0=取消勾选）
     * @param permissionPaths 权限路径列表
     * @param permissionTypeName 权限类型后缀（如："业务菜单权限"）
     */
    private void logModuleFieldPermissionAudit(
            Long roleId,
            Integer checkFlag,
            List<String> permissionPaths,
            String permissionTypeName) {
        logPermissionAudit(
                roleId,
                checkFlag,
                permissionPaths,
                permissionTypeName,
                "角色模块字段权限",
                "sys_role_module_field_permission",
                "模块权限",
                false);
    }

    /**
     * 处理特殊权限审计日志
     *
     * @param roleId 角色ID
     * @param checkFlag 是否勾选（1=勾选新增，0=取消勾选）
     * @param permissionPaths 权限路径列表
     * @param permissionTypeName 权限类型名称（传null，取permissionPaths[0]）
     */
    private void logSpecialPermissionAudit(
            Long roleId,
            Integer checkFlag,
            List<String> permissionPaths,
            String permissionTypeName) {
        logPermissionAudit(
                roleId,
                checkFlag,
                permissionPaths,
                null,
                "角色特殊权限",
                "sys_role_special_permission",
                "其它权限",
                true);
    }
}
