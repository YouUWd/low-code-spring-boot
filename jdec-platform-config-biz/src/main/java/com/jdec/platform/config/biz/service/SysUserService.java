package com.jdec.platform.config.biz.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jdec.platform.config.api.SysUserApi;
import com.jdec.platform.config.api.bo.SysUserBO;
import com.jdec.platform.config.api.dto.request.QuerySysUserPageReq;
import com.jdec.platform.config.api.dto.request.QueryUsersByIdsReq;
import com.jdec.platform.config.api.dto.request.UpdateSysUserReq;
import com.jdec.platform.config.api.dto.response.CheckUserPermissionByPhoneResp;
import com.jdec.platform.config.api.dto.response.SysUserPageResp;
import com.jdec.platform.config.api.dto.response.UserDetailResp;
import com.jdec.platform.config.api.dto.response.UserSearchTreeNodeResp;
import com.jdec.platform.config.biz.entity.SysRole;
import com.jdec.platform.config.biz.entity.SysRoleSubject;
import com.jdec.platform.config.biz.entity.SysRoleUser;
import com.jdec.platform.config.biz.entity.SysUser;
import com.jdec.platform.config.biz.mapper.SysRoleMapper;
import com.jdec.platform.config.biz.mapper.SysRoleSubjectMapper;
import com.jdec.platform.config.biz.mapper.SysRoleUserMapper;
import com.jdec.platform.config.biz.mapper.SysUserMapper;
import com.jdec.platform.hr.api.DepartmentApi;
import com.jdec.platform.hr.api.bo.DepartmentBO;
import com.jdec.platform.shared.constant.VirtualUserConstants;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.enums.UserStatusFlagEnum;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.model.PageResult;
import com.jdec.platform.shared.utils.PageResultUtils;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 用户 Service 实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysUserService implements SysUserApi {
    private final SysUserMapper sysUserMapper;
    private final SysRoleUserMapper sysRoleUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysRoleSubjectMapper sysRoleSubjectMapper;
    private final DepartmentApi departmentApi;

    @Override
    public PageResult<SysUserPageResp> getUserPage(QuerySysUserPageReq query) {
        // 1. 根据 projectNo + subjectId 查询角色列表
        List<SysRole> roles =
                sysRoleMapper.selectList(
                        Wrappers.<SysRole>lambdaQuery()
                                .eq(SysRole::getProjectNo, AppContext.getProjectNo())
                                .eq(SysRole::getSubjectId, AppContext.getSubjectId())
                                .select(SysRole::getId));
        if (roles.isEmpty()) {
            return PageResult.of(
                    query.getPageNum(), query.getPageSize(), 0L, Collections.emptyList());
        }

        // 2. 根据角色 ID 列表查询关联的用户 ID（过滤状态有效且在时效范围内的记录）
        List<Long> roleIds = roles.stream().map(SysRole::getId).toList();
        java.time.LocalDate today = java.time.LocalDate.now();
        List<Long> userIds =
                sysRoleUserMapper
                        .selectList(
                                Wrappers.<SysRoleUser>lambdaQuery()
                                        .in(SysRoleUser::getRoleId, roleIds)
                                        .eq(SysRoleUser::getStatus, 0))
                        .stream()
                        .filter(
                                ru ->
                                        isEffectiveDateValid(
                                                ru.getEffectiveType(),
                                                ru.getEffectiveStartDate(),
                                                ru.getEffectiveEndDate(),
                                                today))
                        .map(SysRoleUser::getUserId)
                        .distinct()
                        .toList();
        if (userIds.isEmpty()) {
            return PageResult.of(
                    query.getPageNum(), query.getPageSize(), 0L, Collections.emptyList());
        }
        Page<SysUser> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysUser> wrapper =
                new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getUserId, userIds)
                        .like(
                                StrUtil.isNotBlank(query.getUserName()),
                                SysUser::getUserName,
                                query.getUserName())
                        .like(
                                StrUtil.isNotBlank(query.getWorkNumber()),
                                SysUser::getWorkNumber,
                                query.getWorkNumber())
                        .like(
                                StrUtil.isNotBlank(query.getPhone()),
                                SysUser::getPhone,
                                query.getPhone())
                        .eq(query.getUserType() != null, SysUser::getUserType, query.getUserType())
                        .eq(
                                query.getEmployedStatus() != null,
                                SysUser::getEmployedStatus,
                                query.getEmployedStatus())
                        .eq(
                                query.getStatusFlag() != null,
                                SysUser::getStatusFlag,
                                query.getStatusFlag())
                        .like(
                                StrUtil.isNotBlank(query.getCompanyName()),
                                SysUser::getCompanyName,
                                query.getCompanyName())
                        .like(
                                StrUtil.isNotBlank(query.getDepartName()),
                                SysUser::getDepartName,
                                query.getDepartName())
                        .like(
                                StrUtil.isNotBlank(query.getSubjectName()),
                                SysUser::getSubjectName,
                                query.getSubjectName())
                        .eq(SysUser::getDeleted, Boolean.FALSE)
                        .orderByDesc(SysUser::getCreatedDate);
        Page<SysUser> result = sysUserMapper.selectPage(page, wrapper);
        return PageResultUtils.of(result.convert(this::toUserPageResp));
    }

    private SysUserPageResp toUserPageResp(SysUser user) {
        SysUserPageResp resp = new SysUserPageResp();
        BeanUtils.copyProperties(user, resp);
        return resp;
    }

    @Override
    public void editUser(UpdateSysUserReq req) {
        SysUser user = sysUserMapper.selectById(req.getId());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setUserName(req.getUserName());
        if (req.getWorkNumber() != null) {
            user.setWorkNumber(req.getWorkNumber());
        }
        user.setSex(req.getSex());
        user.setPhone(req.getPhone());
        user.setCompanyId(req.getCompanyId());
        if (req.getCompanyName() != null) {
            user.setCompanyName(req.getCompanyName());
        }
        if (req.getDepartIds() != null) {
            user.setDepartIds(req.getDepartIds());
        }
        if (req.getDepartName() != null) {
            user.setDepartName(req.getDepartName());
        }
        if (req.getPositionName() != null) {
            user.setPositionName(req.getPositionName());
        }
        if (req.getLeaderName() != null) {
            user.setLeaderName(req.getLeaderName());
        }
        if (req.getEmployedStatus() != null) {
            user.setEmployedStatus(req.getEmployedStatus());
        }
        if (req.getUserAvatar() != null) {
            user.setUserAvatar(req.getUserAvatar());
        }
        sysUserMapper.updateById(user);
    }

    @Override
    public void disableUser(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setStatusFlag(UserStatusFlagEnum.DISABLE.getCode());
        sysUserMapper.updateById(user);
    }

    @Override
    public SysUserBO getUserByPhone(String phone) {
        SysUser sysUser =
                sysUserMapper.selectOne(
                        Wrappers.<SysUser>lambdaQuery().eq(SysUser::getPhone, phone));
        return BeanUtil.toBean(sysUser, SysUserBO.class);
    }

    @Override
    public List<SysUserPageResp> getUserList() {
        // 1. 根据 projectNo + subjectId 查询角色列表
        List<SysRole> roles =
                sysRoleMapper.selectList(
                        Wrappers.<SysRole>lambdaQuery()
                                .eq(SysRole::getProjectNo, AppContext.getProjectNo())
                                .eq(SysRole::getSubjectId, AppContext.getSubjectId())
                                .select(SysRole::getId));
        if (roles.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 根据角色 ID 查询中间表，过滤状态有效且在时效范围内的记录
        List<Long> roleIds = roles.stream().map(SysRole::getId).toList();
        java.time.LocalDate today = java.time.LocalDate.now();
        List<SysRoleUser> roleUsers =
                sysRoleUserMapper
                        .selectList(
                                Wrappers.<SysRoleUser>lambdaQuery()
                                        .in(SysRoleUser::getRoleId, roleIds)
                                        .eq(SysRoleUser::getStatus, 0))
                        .stream()
                        .filter(
                                ru ->
                                        isEffectiveDateValid(
                                                ru.getEffectiveType(),
                                                ru.getEffectiveStartDate(),
                                                ru.getEffectiveEndDate(),
                                                today))
                        .toList();
        if (roleUsers.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. 根据用户 ID 列表查询用户表
        List<Long> userIds = roleUsers.stream().map(SysRoleUser::getUserId).distinct().toList();
        List<SysUser> users =
                sysUserMapper.selectList(
                        Wrappers.<SysUser>lambdaQuery()
                                .in(SysUser::getUserId, userIds)
                                .eq(SysUser::getDeleted, Boolean.FALSE)
                                .orderByDesc(SysUser::getCreatedDate));
        return users.stream().map(this::toUserPageResp).toList();
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<SysUserPageResp> getUsersByRoleId(Long roleId) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();

        // 验证角色属于当前项目和主体
        SysRole role =
                sysRoleMapper.selectOne(
                        Wrappers.<SysRole>lambdaQuery()
                                .eq(SysRole::getId, roleId)
                                .eq(SysRole::getProjectNo, projectNo)
                                .eq(SysRole::getSubjectId, subjectId));
        if (role == null) {
            return Collections.emptyList();
        }

        java.time.LocalDate today = java.time.LocalDate.now();
        List<Long> userIds =
                sysRoleUserMapper
                        .selectList(
                                Wrappers.<SysRoleUser>lambdaQuery()
                                        .eq(SysRoleUser::getRoleId, roleId)
                                        .eq(SysRoleUser::getStatus, 0))
                        .stream()
                        .filter(
                                ru ->
                                        isEffectiveDateValid(
                                                ru.getEffectiveType(),
                                                ru.getEffectiveStartDate(),
                                                ru.getEffectiveEndDate(),
                                                today))
                        .map(SysRoleUser::getUserId)
                        .distinct()
                        .toList();
        if (userIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<SysUser> users =
                sysUserMapper.selectList(
                        Wrappers.<SysUser>lambdaQuery()
                                .in(SysUser::getUserId, userIds)
                                .eq(SysUser::getDeleted, Boolean.FALSE)
                                .orderByDesc(SysUser::getCreatedDate));
        return users.stream().map(this::toUserPageResp).toList();
    }

    @Override
    public List<SysUserPageResp> getUsersByUserIds(QueryUsersByIdsReq req) {
        if (req.getUserIds() == null || req.getUserIds().isEmpty()) {
            return Collections.emptyList();
        }
        List<SysUser> users =
                sysUserMapper.selectList(
                        Wrappers.<SysUser>lambdaQuery()
                                .in(SysUser::getUserId, req.getUserIds())
                                .eq(SysUser::getDeleted, Boolean.FALSE)
                                .orderByDesc(SysUser::getCreatedDate));
        return users.stream().map(this::toUserPageResp).toList();
    }

    private boolean isEffectiveDateValid(
            Integer effectiveType,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate,
            java.time.LocalDate today) {
        if (effectiveType == null) {
            return true;
        }
        // 1-永久有效
        if (effectiveType == 1) {
            return true;
        }
        // 2-自定义时效
        if (startDate == null && endDate == null) {
            return true;
        }
        if (startDate != null && endDate == null) {
            return !today.isBefore(startDate);
        }
        if (startDate == null) {
            return !today.isAfter(endDate);
        }
        return !today.isBefore(startDate) && !today.isAfter(endDate);
    }

    @Override
    public List<UserSearchTreeNodeResp> getUserTreeListForSearch(Integer userType) {
        // userType: 0=全部, 1=内部, 2=外部。当前项目仅支持内部用户树
        if (userType != null && userType == 2) {
            return Collections.emptyList();
        }

        // 1. 根据当前主体和项目编码查询对应的角色
        List<SysRole> roles =
                sysRoleMapper.selectList(
                        Wrappers.<SysRole>lambdaQuery()
                                .eq(SysRole::getProjectNo, AppContext.getProjectNo())
                                .eq(SysRole::getSubjectId, AppContext.getSubjectId())
                                .select(SysRole::getId));
        if (roles.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> roleIds = roles.stream().map(SysRole::getId).toList();
        LocalDate today = LocalDate.now();

        // 2.1 根据角色查询关联的用户ID（状态有效+时效有效）
        List<Long> userIdsFromRoleUser =
                sysRoleUserMapper
                        .selectList(
                                Wrappers.<SysRoleUser>lambdaQuery()
                                        .in(SysRoleUser::getRoleId, roleIds)
                                        .eq(SysRoleUser::getStatus, 0))
                        .stream()
                        .filter(
                                ru ->
                                        isEffectiveDateValid(
                                                ru.getEffectiveType(),
                                                ru.getEffectiveStartDate(),
                                                ru.getEffectiveEndDate(),
                                                today))
                        .map(SysRoleUser::getUserId)
                        .distinct()
                        .toList();

        // 2.2 根据角色查询关联的主体ID（状态有效+时效有效）
        List<Long> subjectIdsFromRoleSubject =
                sysRoleSubjectMapper
                        .selectList(
                                Wrappers.<SysRoleSubject>lambdaQuery()
                                        .in(SysRoleSubject::getRoleId, roleIds)
                                        .eq(SysRoleSubject::getStatus, 0))
                        .stream()
                        .filter(
                                rs ->
                                        isEffectiveDateValid(
                                                rs.getEffectiveType(),
                                                rs.getEffectiveStartDate(),
                                                rs.getEffectiveEndDate(),
                                                today))
                        .map(SysRoleSubject::getSubjectId)
                        .distinct()
                        .toList();

        // 2.3 通过用户ID查询一批用户
        List<SysUser> usersByUserId = new ArrayList<>();
        if (!userIdsFromRoleUser.isEmpty()) {
            LambdaQueryWrapper<SysUser> userIdWrapper =
                    new LambdaQueryWrapper<SysUser>()
                            .in(SysUser::getUserId, userIdsFromRoleUser)
                            .eq(userType != null && userType != 0, SysUser::getUserType, userType)
                            .eq(SysUser::getDeleted, Boolean.FALSE);
            usersByUserId = sysUserMapper.selectList(userIdWrapper);
        }

        // 2.4 通过主体ID查询一批用户
        List<SysUser> usersBySubjectId = new ArrayList<>();
        if (!subjectIdsFromRoleSubject.isEmpty()) {
            LambdaQueryWrapper<SysUser> subjectIdWrapper =
                    new LambdaQueryWrapper<SysUser>()
                            .in(SysUser::getSubjectId, subjectIdsFromRoleSubject)
                            .eq(userType != null && userType != 0, SysUser::getUserType, userType)
                            .eq(SysUser::getDeleted, Boolean.FALSE);
            usersBySubjectId = sysUserMapper.selectList(subjectIdWrapper);
        }

        // 2.5 合并两批用户并按userId去重
        Map<Long, SysUser> userMap = new LinkedHashMap<>();
        for (SysUser user : usersByUserId) {
            userMap.put(user.getUserId(), user);
        }
        for (SysUser user : usersBySubjectId) {
            userMap.putIfAbsent(user.getUserId(), user);
        }
        List<SysUser> users = new ArrayList<>(userMap.values());

        // 3. 将用户按部门分类
        Map<String, List<UserSearchTreeNodeResp>> deptUsers = new LinkedHashMap<>();
        for (SysUser user : users) {
            String deptIds = user.getDepartIds();
            if (deptIds == null || deptIds.isEmpty()) {
                continue;
            }
            for (String deptId : deptIds.split(",")) {
                deptId = deptId.trim();
                if (deptId.isEmpty()) {
                    continue;
                }
                UserSearchTreeNodeResp userNode =
                        UserSearchTreeNodeResp.builder()
                                .id("U" + user.getUserId())
                                .sysId(user.getId())
                                .label(user.getUserName())
                                .departId("D" + deptId)
                                .sex(user.getSex() != null ? user.getSex() : 1)
                                .avatar(user.getUserAvatar())
                                .workNumber(
                                        user.getWorkNumber() != null ? user.getWorkNumber() : "")
                                .type(3)
                                .departName(
                                        user.getDepartName() != null ? user.getDepartName() : "")
                                .build();
                deptUsers.computeIfAbsent("D" + deptId, k -> new ArrayList<>()).add(userNode);
            }
        }
        // 1. 从人力获取部门列表
        List<DepartmentBO> departmentList = departmentApi.getDepartmentList();
        // 4. 构建部门节点并挂载用户
        List<UserSearchTreeNodeResp> departListWithUsers = new ArrayList<>();
        for (DepartmentBO dept : departmentList) {
            String pid = (dept.getPid() != null && dept.getPid() != 0) ? "D" + dept.getPid() : "D0";
            UserSearchTreeNodeResp node =
                    UserSearchTreeNodeResp.builder()
                            .id("D" + dept.getId())
                            .label(dept.getName())
                            .pid(pid)
                            .type(dept.getType() != null ? dept.getType() : 1)
                            .children(
                                    new ArrayList<>(
                                            deptUsers.getOrDefault(
                                                    "D" + dept.getId(), new ArrayList<>())))
                            .build();
            departListWithUsers.add(node);
        }

        // 5. 构建嵌套树
        List<UserSearchTreeNodeResp> tree = buildNestedTree(departListWithUsers, "D0");
        return tree;
        // 6. 过滤掉没有用户的部门
        //        return filterEmptyDepartments(tree);
    }

    private List<UserSearchTreeNodeResp> buildNestedTree(
            List<UserSearchTreeNodeResp> items, String rootPid) {
        Map<String, UserSearchTreeNodeResp> nodeMap = new LinkedHashMap<>();
        for (UserSearchTreeNodeResp item : items) {
            nodeMap.put(item.getId(), item);
        }

        List<UserSearchTreeNodeResp> tree = new ArrayList<>();
        for (UserSearchTreeNodeResp item : items) {
            String pid = item.getPid();
            UserSearchTreeNodeResp parent = nodeMap.get(pid);
            boolean isRoot = (pid == null || pid.equals(rootPid) || parent == null);
            if (isRoot) {
                tree.add(item);
            } else {
                List<UserSearchTreeNodeResp> children = parent.getChildren();
                if (children == null) {
                    children = new ArrayList<>();
                    parent.setChildren(children);
                }
                children.add(item);
            }
        }
        return tree;
    }

    private List<UserSearchTreeNodeResp> filterEmptyDepartments(
            List<UserSearchTreeNodeResp> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return nodes;
        }

        List<UserSearchTreeNodeResp> filtered = new ArrayList<>();
        for (UserSearchTreeNodeResp node : nodes) {
            if (hasUsers(node)) {
                // 递归过滤子节点
                if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                    node.setChildren(filterEmptyDepartments(node.getChildren()));
                }
                filtered.add(node);
            }
        }
        return filtered;
    }

    @Override
    public List<Long> getSubjectIdsByUserId(Long userId) {
        java.time.LocalDate today = java.time.LocalDate.now();
        Set<Long> resultSubjectIds = new HashSet<>();

        // 路径1: userId -> sys_role_user -> roleIds -> sys_role -> 收集 subjectIds
        List<Long> roleIds =
                sysRoleUserMapper
                        .selectList(
                                Wrappers.<SysRoleUser>lambdaQuery()
                                        .eq(SysRoleUser::getUserId, userId)
                                        .eq(SysRoleUser::getStatus, 0))
                        .stream()
                        .filter(
                                ru ->
                                        isEffectiveDateValid(
                                                ru.getEffectiveType(),
                                                ru.getEffectiveStartDate(),
                                                ru.getEffectiveEndDate(),
                                                today))
                        .map(SysRoleUser::getRoleId)
                        .distinct()
                        .toList();

        if (!roleIds.isEmpty()) {
            // 根据角色ID查询角色记录，收集主体ID
            List<Long> subjectIdsFromRoles =
                    sysRoleMapper
                            .selectList(
                                    Wrappers.<SysRole>lambdaQuery()
                                            .in(SysRole::getId, roleIds)
                                            .eq(SysRole::getProjectNo, AppContext.getProjectNo())
                                            .select(SysRole::getId, SysRole::getSubjectId))
                            .stream()
                            .map(SysRole::getSubjectId)
                            .filter(Objects::nonNull)
                            .toList();
            resultSubjectIds.addAll(subjectIdsFromRoles);
        }

        // 路径2: 请求头 subjectId -> sys_role_subject -> roleIds -> sys_role -> 收集 subjectIds
        Long headerSubjectId = AppContext.getSubjectId();
        if (headerSubjectId != null) {
            // 查询该主体绑定的角色ID（且状态有效、时效范围内）
            List<Long> subjectRoleIds =
                    sysRoleSubjectMapper
                            .selectList(
                                    Wrappers.<SysRoleSubject>lambdaQuery()
                                            .eq(SysRoleSubject::getSubjectId, headerSubjectId)
                                            .eq(SysRoleSubject::getStatus, 0))
                            .stream()
                            .filter(
                                    rs ->
                                            isEffectiveDateValid(
                                                    rs.getEffectiveType(),
                                                    rs.getEffectiveStartDate(),
                                                    rs.getEffectiveEndDate(),
                                                    today))
                            .map(SysRoleSubject::getRoleId)
                            .distinct()
                            .toList();

            if (!subjectRoleIds.isEmpty()) {
                // 根据角色ID查询角色表，收集这些角色的主体ID
                List<Long> subjectIdsFromSubjectRoles =
                        sysRoleMapper
                                .selectList(
                                        Wrappers.<SysRole>lambdaQuery()
                                                .in(SysRole::getId, subjectRoleIds)
                                                .eq(
                                                        SysRole::getProjectNo,
                                                        AppContext.getProjectNo())
                                                .select(SysRole::getId, SysRole::getSubjectId))
                                .stream()
                                .map(SysRole::getSubjectId)
                                .filter(Objects::nonNull)
                                .toList();
                resultSubjectIds.addAll(subjectIdsFromSubjectRoles);
            }
        }

        // 返回去重后的主体ID列表
        return new ArrayList<>(resultSubjectIds);
    }

    private boolean hasUsers(UserSearchTreeNodeResp node) {
        // type=3 表示用户节点
        if (node.getType() == 3) {
            return true;
        }

        // 检查子节点
        List<UserSearchTreeNodeResp> children = node.getChildren();
        if (children == null || children.isEmpty()) {
            return false;
        }

        // 递归检查子节点是否有用户
        for (UserSearchTreeNodeResp child : children) {
            if (hasUsers(child)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public CheckUserPermissionByPhoneResp checkUserPermissionByPhone(String phone) {
        // 0. 检查是否为虚拟用户（定时任务系统用户）
        if (VirtualUserConstants.SCHEDULED_TASK_PHONE.equals(phone)) {
            return CheckUserPermissionByPhoneResp.builder()
                    .permissionFlag(true)
                    .id(VirtualUserConstants.SCHEDULED_TASK_USER_ID)
                    .userId(VirtualUserConstants.SCHEDULED_TASK_USER_ID)
                    .userName(VirtualUserConstants.SCHEDULED_TASK_USERNAME)
                    .phone(VirtualUserConstants.SCHEDULED_TASK_PHONE)
                    .subjectId(3L)
                    .build();
        }

        String projectNo = AppContext.getProjectNo();
        LocalDate today = LocalDate.now();

        // 1. 从 sys_user 表查询用户（角色保存时已同步主体下用户，无需再调 HR）
        SysUser sysUser =
                sysUserMapper.selectOne(
                        Wrappers.<SysUser>lambdaQuery()
                                .eq(SysUser::getPhone, phone)
                                .eq(SysUser::getDeleted, Boolean.FALSE));

        if (sysUser == null) {
            // sys_user 中不存在该用户，视为无权限
            return CheckUserPermissionByPhoneResp.builder().permissionFlag(false).build();
        }
        Long userId = sysUser.getUserId();
        Long subjectId = sysUser.getSubjectId();

        // 2. 检查系统设置：userId -> sys_role_user -> sys_role
        boolean hasSystemPermission = false;
        if (userId != null) {
            List<Long> systemRoleIds =
                    sysRoleUserMapper
                            .selectList(
                                    Wrappers.<SysRoleUser>lambdaQuery()
                                            .eq(SysRoleUser::getUserId, userId)
                                            .eq(SysRoleUser::getStatus, 0))
                            .stream()
                            .filter(
                                    ru ->
                                            isEffectiveDateValid(
                                                    ru.getEffectiveType(),
                                                    ru.getEffectiveStartDate(),
                                                    ru.getEffectiveEndDate(),
                                                    today))
                            .map(SysRoleUser::getRoleId)
                            .distinct()
                            .toList();

            if (!systemRoleIds.isEmpty()) {
                long validRoleCount =
                        sysRoleMapper.selectCount(
                                Wrappers.<SysRole>lambdaQuery()
                                        .in(SysRole::getId, systemRoleIds)
                                        .eq(SysRole::getProjectNo, projectNo));
                hasSystemPermission = validRoleCount > 0;
            }
        }

        // 3. 检查主体设置：subjectId -> sys_role_subject -> sys_role
        boolean hasSubjectPermission = false;
        if (subjectId != null) {
            List<Long> subjectRoleIds =
                    sysRoleSubjectMapper
                            .selectList(
                                    Wrappers.<SysRoleSubject>lambdaQuery()
                                            .eq(SysRoleSubject::getSubjectId, subjectId)
                                            .eq(SysRoleSubject::getStatus, 0))
                            .stream()
                            .filter(
                                    rs ->
                                            isEffectiveDateValid(
                                                    rs.getEffectiveType(),
                                                    rs.getEffectiveStartDate(),
                                                    rs.getEffectiveEndDate(),
                                                    today))
                            .map(SysRoleSubject::getRoleId)
                            .distinct()
                            .toList();

            if (!subjectRoleIds.isEmpty()) {
                long validRoleCount =
                        sysRoleMapper.selectCount(
                                Wrappers.<SysRole>lambdaQuery()
                                        .in(SysRole::getId, subjectRoleIds)
                                        .eq(SysRole::getProjectNo, projectNo));
                hasSubjectPermission = validRoleCount > 0;
            }
        }

        // 4. 判断是否有权限
        boolean hasPermission = hasSystemPermission || hasSubjectPermission;

        // 5. 构建响应，只有有权限时才填充用户信息
        if (!hasPermission) {
            return CheckUserPermissionByPhoneResp.builder().permissionFlag(false).build();
        }

        return buildCheckUserPermissionResp(sysUser, true);
    }

    /**
     * 格式化部门ID，在每个ID前添加"D"前缀
     *
     * @param deptIds 逗号分隔的部门ID字符串，例如 "1,2,3"
     * @return 格式化后的部门ID字符串，例如 "D1,D2,D3"
     */
    private String formatDeptIds(String deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return deptIds;
        }
        return Arrays.stream(deptIds.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .map(id -> "D" + id)
                .collect(Collectors.joining(","));
    }

    private CheckUserPermissionByPhoneResp buildCheckUserPermissionResp(
            SysUser sysUser, boolean hasPermission) {
        CheckUserPermissionByPhoneResp.CheckUserPermissionByPhoneRespBuilder builder =
                CheckUserPermissionByPhoneResp.builder().permissionFlag(hasPermission);

        builder.id(sysUser.getId())
                .userId(sysUser.getUserId())
                .companyId(sysUser.getCompanyId())
                .subjectId(sysUser.getSubjectId())
                .userType(sysUser.getUserType())
                .userName(sysUser.getUserName())
                .workNumber(sysUser.getWorkNumber())
                .userAvatar(sysUser.getUserAvatar())
                .phone(sysUser.getPhone())
                .sex(sysUser.getSex())
                .companyName(sysUser.getCompanyName())
                .deptIds(formatDeptIds(sysUser.getDepartIds()))
                .departName(sysUser.getDepartName())
                .leaderName(sysUser.getLeaderName())
                .positionName(sysUser.getPositionName())
                .subjectName(sysUser.getSubjectName())
                .statusFlag(sysUser.getStatusFlag())
                .employedStatus(sysUser.getEmployedStatus())
                .superFlag(sysUser.getSuperFlag())
                .createdDate(sysUser.getCreatedDate());

        return builder.build();
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UserDetailResp getUserDetailByUserId(Long userId) {
        String projectNo = AppContext.getProjectNo();
        LocalDate today = LocalDate.now();

        // 1. 从 sys_user 表根据 userId 查询用户（角色保存时已同步主体下用户，无需再调 HR）
        SysUser sysUser =
                sysUserMapper.selectOne(
                        Wrappers.<SysUser>lambdaQuery()
                                .eq(SysUser::getUserId, userId)
                                .eq(SysUser::getDeleted, Boolean.FALSE));

        if (sysUser == null) {
            throw new BusinessException("用户不存在");
        }
        Long subjectId = sysUser.getSubjectId();

        // 2. 检查系统设置：userId -> sys_role_user -> sys_role
        boolean hasSystemPermission = false;
        List<Long> systemRoleIds =
                sysRoleUserMapper
                        .selectList(
                                Wrappers.<SysRoleUser>lambdaQuery()
                                        .eq(SysRoleUser::getUserId, userId)
                                        .eq(SysRoleUser::getStatus, 0))
                        .stream()
                        .filter(
                                ru ->
                                        isEffectiveDateValid(
                                                ru.getEffectiveType(),
                                                ru.getEffectiveStartDate(),
                                                ru.getEffectiveEndDate(),
                                                today))
                        .map(SysRoleUser::getRoleId)
                        .distinct()
                        .toList();

        if (!systemRoleIds.isEmpty()) {
            long validRoleCount =
                    sysRoleMapper.selectCount(
                            Wrappers.<SysRole>lambdaQuery()
                                    .in(SysRole::getId, systemRoleIds)
                                    .eq(SysRole::getProjectNo, projectNo));
            hasSystemPermission = validRoleCount > 0;
        }

        // 3. 检查主体设置：subjectId -> sys_role_subject -> sys_role
        boolean hasSubjectPermission = false;
        if (subjectId != null) {
            List<Long> subjectRoleIds =
                    sysRoleSubjectMapper
                            .selectList(
                                    Wrappers.<SysRoleSubject>lambdaQuery()
                                            .eq(SysRoleSubject::getSubjectId, subjectId)
                                            .eq(SysRoleSubject::getStatus, 0))
                            .stream()
                            .filter(
                                    rs ->
                                            isEffectiveDateValid(
                                                    rs.getEffectiveType(),
                                                    rs.getEffectiveStartDate(),
                                                    rs.getEffectiveEndDate(),
                                                    today))
                            .map(SysRoleSubject::getRoleId)
                            .distinct()
                            .toList();

            if (!subjectRoleIds.isEmpty()) {
                long validRoleCount =
                        sysRoleMapper.selectCount(
                                Wrappers.<SysRole>lambdaQuery()
                                        .in(SysRole::getId, subjectRoleIds)
                                        .eq(SysRole::getProjectNo, projectNo));
                hasSubjectPermission = validRoleCount > 0;
            }
        }

        // 4. 判断是否有权限
        boolean hasPermission = hasSystemPermission || hasSubjectPermission;
        if (!hasPermission) {
            throw new BusinessException("用户不存在");
        }

        // 5. 返回用户详情
        return buildUserDetailResp(sysUser);
    }

    private UserDetailResp buildUserDetailResp(SysUser sysUser) {
        UserDetailResp.UserDetailRespBuilder builder = UserDetailResp.builder();

        builder.id(sysUser.getId())
                .userId(sysUser.getUserId())
                .companyId(sysUser.getCompanyId())
                .subjectId(sysUser.getSubjectId())
                .userType(sysUser.getUserType())
                .userName(sysUser.getUserName())
                .workNumber(sysUser.getWorkNumber())
                .userAvatar(sysUser.getUserAvatar())
                .phone(sysUser.getPhone())
                .sex(sysUser.getSex())
                .companyName(sysUser.getCompanyName())
                .departName(sysUser.getDepartName())
                .leaderName(sysUser.getLeaderName())
                .positionName(sysUser.getPositionName())
                .subjectName(sysUser.getSubjectName())
                .statusFlag(sysUser.getStatusFlag())
                .employedStatus(sysUser.getEmployedStatus())
                .superFlag(sysUser.getSuperFlag())
                .createdDate(sysUser.getCreatedDate());

        return builder.build();
    }
}
