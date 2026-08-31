package com.jdec.platform.config.biz.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jdec.platform.config.api.SysRoleApi;
import com.jdec.platform.config.api.dto.request.QueryRoleConfigPageReq;
import com.jdec.platform.config.api.dto.request.RoleSubjectReq;
import com.jdec.platform.config.api.dto.request.RoleUserReq;
import com.jdec.platform.config.api.dto.request.SaveRoleUserReq;
import com.jdec.platform.config.api.dto.response.*;
import com.jdec.platform.config.biz.audit.service.RoleApprovalLogService;
import com.jdec.platform.config.biz.check.CheckConstant;
import com.jdec.platform.config.biz.check.ReferenceCheckManager;
import com.jdec.platform.config.biz.check.ReferenceContext;
import com.jdec.platform.config.biz.entity.SysRole;
import com.jdec.platform.config.biz.entity.SysRoleSubject;
import com.jdec.platform.config.biz.entity.SysRoleUser;
import com.jdec.platform.config.biz.entity.SysUser;
import com.jdec.platform.config.biz.mapper.SysRoleMapper;
import com.jdec.platform.config.biz.mapper.SysRoleSubjectMapper;
import com.jdec.platform.config.biz.mapper.SysRoleUserMapper;
import com.jdec.platform.config.biz.mapper.SysUserMapper;
import com.jdec.platform.hr.api.DepartmentApi;
import com.jdec.platform.hr.api.SubjectApi;
import com.jdec.platform.hr.api.UserApi;
import com.jdec.platform.hr.api.bo.DepartmentBO;
import com.jdec.platform.hr.api.bo.SubjectBO;
import com.jdec.platform.hr.api.bo.UserBO;
import com.jdec.platform.hr.api.dto.response.UserTreeNodeResp;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.enums.*;
import com.jdec.platform.shared.exception.ApiCodeEnum;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.exception.PopException;
import com.jdec.platform.shared.model.PageResult;
import com.jdec.platform.shared.service.ConfigChangeMessagePublisher;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 角色 Service 实现 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_ENGINE)
public class SysRoleService implements SysRoleApi {
    private final SysRoleMapper sysRoleMapper;
    private final SysRoleUserMapper sysRoleUserMapper;
    private final SysRoleSubjectMapper sysRoleSubjectMapper;
    private final SysUserMapper sysUserMapper;
    private final UserApi userApi;
    private final SubjectApi subjectApi;
    private final DepartmentApi departmentApi;
    private final ConfigChangeMessagePublisher messagePublisher;
    private final ReferenceCheckManager referenceCheckManager;
    private final SysDataSnapshotService sysDataSnapshotService;
    private final RoleApprovalLogService roleApprovalLogService;

    @Override
    public Long getUserRoleCount(Long id, Long subjectId, String projectNo) {
        // 先更新已时效的关联状态
        updateExpiredRelations();

        long count =
                filterValidRoleUsers(
                                sysRoleUserMapper.selectList(
                                        Wrappers.<SysRoleUser>lambdaQuery()
                                                .eq(SysRoleUser::getUserId, id)
                                                .eq(SysRoleUser::getStatus, 0)))
                        .size();
        long count1 =
                filterValidRoleSubjects(
                                sysRoleSubjectMapper.selectList(
                                        Wrappers.<SysRoleSubject>lambdaQuery()
                                                .eq(SysRoleSubject::getSubjectId, subjectId)
                                                .eq(SysRoleSubject::getStatus, 0)))
                        .size();
        return count + count1;
    }

    /**
     * 更新关联状态：将已失效的置为1（失效），将已到期的待生效置为0（生效）
     *
     * <p>处理两种状态转换：
     *
     * <ul>
     *   <li>已失效：当前时间超出有效期，status 0→1
     *   <li>待生效→生效：当前时间符合生效条件，status 2→0
     * </ul>
     */
    private void updateExpiredRelations() {
        java.time.LocalDate today = java.time.LocalDate.now();

        // 1. 处理用户关联表
        // 1.1 查询生效和待生效的记录
        List<SysRoleUser> activeAndPendingUsers =
                sysRoleUserMapper.selectList(
                        Wrappers.<SysRoleUser>lambdaQuery().in(SysRoleUser::getStatus, 0, 2));

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
                        Wrappers.<SysRoleSubject>lambdaQuery().in(SysRoleSubject::getStatus, 0, 2));

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

    @Override
    public CheckRoleUserResp checkRoleUser(SaveRoleUserReq request) {
        // 先更新已时效的关联状态
        updateExpiredRelations();

        autoDetectEffectiveType(request);

        CheckRoleUserResp resp = new CheckRoleUserResp();
        resp.setNeedConfirm(false);

        Long roleId = request.getId();
        boolean isUpdate = roleId != null;
        Set<Long> allUserIds = collectUserIds(request);

        // ========== 校验1：选择主体后，再选择该主体下的员工 ==========
        // 新增和更新都得校验
        if (request.getSubjects() != null
                && !request.getSubjects().isEmpty()
                && !allUserIds.isEmpty()) {
            // 获取所有主体下的用户
            List<UserBO> allSubjectUsers = new ArrayList<>();
            for (RoleSubjectReq subjectReq : request.getSubjects()) {
                List<UserBO> users = userApi.listBySubjectId(subjectReq.getSubjectId());
                allSubjectUsers.addAll(users);
            }
            List<String> conflictNames = checkSubjectUserConflict(allSubjectUsers, allUserIds);
            if (!conflictNames.isEmpty()) {
                if (isUpdate) {
                    // 更新时：检查冲突用户是否和任何角色绑定过
                    List<String> previouslyBound = getUsersBoundToAnyRole(allUserIds);
                    if (previouslyBound.isEmpty()) {
                        // 之前都没有绑定过任何角色，不可强制保存
                        resp.setNeedConfirm(true);
                        resp.setConfirmType(3);
                        resp.setMessage(
                                String.format(
                                        "\"%s\"已在授权主体中，请重新设置", String.join("、", conflictNames)));
                        return resp;
                    }
                    // 有之前绑定过任何角色的员工，可强制保存（继续执行后续校验）
                } else {
                    // 新增时：之前不可能有绑定，不可强制保存
                    resp.setNeedConfirm(true);
                    resp.setConfirmType(3);
                    resp.setMessage(
                            String.format("\"%s\"已在授权主体中，请重新设置", String.join("、", conflictNames)));
                    return resp;
                }
            }
        }

        if (roleId == null) {
            return resp;
        }

        // ========== 校验2：更新时，授权主体后，是否需要覆盖已单独关联到该角色的主体下员工（可强制保存） ==========
        // 只有更新时需要校验，可强制保存
        if (request.getSubjects() != null && !request.getSubjects().isEmpty()) {
            for (RoleSubjectReq subjectReq : request.getSubjects()) {
                List<String> overlapNames =
                        checkRoleHasSubjectUsers(roleId, subjectReq.getSubjectId());
                if (!overlapNames.isEmpty()) {
                    resp.setNeedConfirm(true);
                    resp.setConfirmType(2); // 可强制保存，覆盖已关联的员工
                    resp.setMessage(
                            String.format("\"%s\"已在该角色中，是否覆盖？", String.join("、", overlapNames)));
                    resp.setConflictNames(overlapNames);
                    return resp;
                }
            }
        }

        // ========== 校验1：更新时，当前角色中的用户时效时间被修改，提示是否覆盖 ==========
        // 只有更新时需要校验：检查当前角色中已有用户的时效时间是否发生改变
        if (!allUserIds.isEmpty() && hasExistingUserTimeChanged(roleId, allUserIds, request)) {
            resp.setNeedConfirm(true);
            resp.setConfirmType(1);
            resp.setMessage("该角色中存在员工设置过权限时间，是否覆盖？");
            return resp;
        }

        return resp;
    }

    @Override
    public RoleConfigPageResp saveRoleUser(SaveRoleUserReq request) {
        // 先更新已时效的关联状态
        updateExpiredRelations();

        autoDetectEffectiveType(request);

        Set<Long> allUserIds = collectUserIds(request);

        // === 快照旧数据（仅更新时，必须在事务开启前执行） ===
        boolean isUpdate = request.getId() != null;
        boolean hasChanges = true; // 新增时默认需要记录日志
        Set<Long> oldUserIds = new HashSet<>();
        Set<Long> oldSubjectIds = new HashSet<>();
        String oldRoleName = "";
        List<SysRoleUser> oldRoleUsers = new ArrayList<>();
        List<SysRoleSubject> oldRoleSubjects = new ArrayList<>();

        if (isUpdate) {
            Long roleId = request.getId();
            // 查询旧角色名称
            SysRole oldRole = sysRoleMapper.selectById(roleId);
            if (oldRole != null) {
                oldRoleName = oldRole.getRoleName();
            }

            // 查询旧用户列表
            oldRoleUsers =
                    sysRoleUserMapper.selectList(
                            Wrappers.<SysRoleUser>lambdaQuery().eq(SysRoleUser::getRoleId, roleId));
            Set<Long> oldUserIdsTemp =
                    oldRoleUsers.stream().map(SysRoleUser::getUserId).collect(Collectors.toSet());

            // 查询旧主体列表
            oldRoleSubjects =
                    sysRoleSubjectMapper.selectList(
                            Wrappers.<SysRoleSubject>lambdaQuery()
                                    .eq(SysRoleSubject::getRoleId, roleId));
            Set<Long> oldSubjectIdsTemp =
                    oldRoleSubjects.stream()
                            .map(SysRoleSubject::getSubjectId)
                            .collect(Collectors.toSet());

            // 比较用户/主体及生效时间是否发生了变更
            hasChanges = hasRoleConfigChanged(request, oldRoleUsers, oldRoleSubjects, allUserIds);

            if (hasChanges) {
                oldUserIds = oldUserIdsTemp;
                oldSubjectIds = oldSubjectIdsTemp;
            }
        }

        // 提前完成所有跨库查询（hr 库），必须在事务开启前执行
        Map<Long, UserBO> hrUserMap = new HashMap<>();
        Map<Long, DepartmentBO> deptMap = new HashMap<>();
        Map<Long, SubjectBO> subjectMap = new HashMap<>();
        List<UserBO> allSubjectUsers = new ArrayList<>();

        // 合并新旧用户ID，确保删除的用户也能查到姓名
        Set<Long> allUserIdsForQuery = new HashSet<>(allUserIds);
        allUserIdsForQuery.addAll(oldUserIds);

        if (!allUserIdsForQuery.isEmpty()) {
            userApi.listByIds(new HashSet<>(allUserIdsForQuery))
                    .forEach(u -> hrUserMap.put(u.getId(), u));
            departmentApi.getDepartmentList().forEach(d -> deptMap.put(d.getId(), d));
            subjectApi.getSubjectList().forEach(s -> subjectMap.put(s.getId(), s));
        }
        // 查询所有主体下的用户
        if (request.getSubjects() != null && !request.getSubjects().isEmpty()) {
            for (RoleSubjectReq subjectReq : request.getSubjects()) {
                List<UserBO> users = userApi.listBySubjectId(subjectReq.getSubjectId());
                allSubjectUsers.addAll(users);
            }
        }

        Long roleId =
                doSaveRoleUser(
                        request,
                        allUserIds,
                        hrUserMap,
                        deptMap,
                        subjectMap,
                        allSubjectUsers,
                        oldSubjectIds);

        // === 有变更时：记录审批日志并发送变更消息 ===
        if (!isUpdate || hasChanges) {
            // 操作完成后查询关联表，收集受影响的用户/主体 ID
            List<Long> newUserIds =
                    sysRoleUserMapper
                            .selectList(
                                    Wrappers.<SysRoleUser>lambdaQuery()
                                            .eq(SysRoleUser::getRoleId, roleId))
                            .stream()
                            .map(SysRoleUser::getUserId)
                            .distinct()
                            .toList();
            List<Long> newSubjectIds =
                    sysRoleSubjectMapper
                            .selectList(
                                    Wrappers.<SysRoleSubject>lambdaQuery()
                                            .eq(SysRoleSubject::getRoleId, roleId))
                            .stream()
                            .map(SysRoleSubject::getSubjectId)
                            .distinct()
                            .toList();

            // 记录审批日志
            try {
                if (isUpdate) {
                    // 转换旧数据为 RoleUserReq 和 RoleSubjectReq
                    List<RoleUserReq> oldUserReqs = convertToRoleUserReqs(oldRoleUsers, hrUserMap);
                    List<RoleSubjectReq> oldSubjectReqs = convertToRoleSubjectReqs(oldRoleSubjects);

                    roleApprovalLogService.logRoleUpdate(
                            roleId,
                            oldRoleName,
                            request,
                            oldUserReqs,
                            oldSubjectReqs,
                            subjectMap,
                            hrUserMap);
                } else {
                    roleApprovalLogService.logRoleCreate(roleId, request, subjectMap, hrUserMap);
                }
            } catch (Exception e) {
                log.error("记录角色审批日志失败: roleId={}", roleId, e);
            }

            // 编程式发送细粒度角色变更消息
            publishRoleChangeMessages(
                    roleId, isUpdate, oldUserIds, oldSubjectIds, newUserIds, newSubjectIds);
        }

        // 查询并返回保存后的角色配置信息
        return getRoleConfigById(roleId);
    }

    /**
     * 比较角色配置是否发生了变更（用户/主体列表及生效时间）
     *
     * @return true-有变更，false-无变更
     */
    private boolean hasRoleConfigChanged(
            SaveRoleUserReq request,
            List<SysRoleUser> oldRoleUsers,
            List<SysRoleSubject> oldRoleSubjects,
            Set<Long> newAllUserIds) {

        // 1. 比较用户列表
        Set<Long> oldUserIds =
                oldRoleUsers.stream().map(SysRoleUser::getUserId).collect(Collectors.toSet());
        if (!oldUserIds.equals(newAllUserIds)) {
            return true;
        }

        // 用户列表相同，比较每个用户的生效时间
        Map<Long, SysRoleUser> oldUserMap =
                oldRoleUsers.stream().collect(Collectors.toMap(SysRoleUser::getUserId, ru -> ru));

        List<RoleUserReq> allUserReqs = new ArrayList<>();
        if (request.getUserInsideList() != null) {
            allUserReqs.addAll(request.getUserInsideList());
        }
        if (request.getUserExternalList() != null) {
            allUserReqs.addAll(request.getUserExternalList());
        }
        Map<Long, RoleUserReq> newUserMap = new HashMap<>();
        for (RoleUserReq req : allUserReqs) {
            if (req.getUserId() != null) {
                newUserMap.put(Long.parseLong(req.getUserId()), req);
            }
        }

        for (Map.Entry<Long, SysRoleUser> entry : oldUserMap.entrySet()) {
            RoleUserReq newReq = newUserMap.get(entry.getKey());
            if (newReq == null) {
                return true;
            }
            SysRoleUser old = entry.getValue();
            if (!Objects.equals(old.getEffectiveType(), newReq.getEffectiveType())) {
                return true;
            }
            if (!Objects.equals(old.getEffectiveStartDate(), newReq.getEffectiveStartDate())) {
                return true;
            }
            if (!Objects.equals(old.getEffectiveEndDate(), newReq.getEffectiveEndDate())) {
                return true;
            }
        }

        // 2. 比较主体列表
        Set<Long> oldSubjectIds =
                oldRoleSubjects.stream()
                        .map(SysRoleSubject::getSubjectId)
                        .collect(Collectors.toSet());

        Set<Long> newSubjectIds =
                request.getSubjectList() == null
                        ? Collections.emptySet()
                        : request.getSubjectList().stream()
                                .map(RoleSubjectReq::getSubjectId)
                                .collect(Collectors.toSet());

        if (!oldSubjectIds.equals(newSubjectIds)) {
            return true;
        }

        // 主体列表相同，比较每个主体的生效时间
        if (!oldSubjectIds.isEmpty()) {
            Map<Long, SysRoleSubject> oldSubjectMap =
                    oldRoleSubjects.stream()
                            .collect(Collectors.toMap(SysRoleSubject::getSubjectId, rs -> rs));

            Map<Long, RoleSubjectReq> newSubjectReqMap =
                    request.getSubjectList().stream()
                            .collect(Collectors.toMap(RoleSubjectReq::getSubjectId, rs -> rs));

            for (Map.Entry<Long, SysRoleSubject> entry : oldSubjectMap.entrySet()) {
                RoleSubjectReq newReq = newSubjectReqMap.get(entry.getKey());
                if (newReq == null) {
                    return true;
                }
                SysRoleSubject old = entry.getValue();
                if (!Objects.equals(old.getEffectiveType(), newReq.getEffectiveType())) {
                    return true;
                }
                if (!Objects.equals(old.getEffectiveStartDate(), newReq.getEffectiveStartDate())) {
                    return true;
                }
                if (!Objects.equals(old.getEffectiveEndDate(), newReq.getEffectiveEndDate())) {
                    return true;
                }
            }
        }

        return false;
    }

    /** 编程式发送角色人员/主体变更消息，区分新增和移除 */
    private void publishRoleChangeMessages(
            Long roleId,
            boolean isUpdate,
            Set<Long> oldUserIds,
            Set<Long> oldSubjectIds,
            List<Long> newUserIds,
            List<Long> newSubjectIds) {
        try {
            Long subjectId = AppContext.getSubjectId();
            String projectNo = AppContext.getProjectNo();

            if (isUpdate) {
                Set<Long> newUserSet = new HashSet<>(newUserIds);
                Set<Long> newSubjectSet = new HashSet<>(newSubjectIds);

                // 新增的用户：在新列表中但不在旧列表中
                List<Long> addedUsers =
                        newUserIds.stream().filter(id -> !oldUserIds.contains(id)).toList();
                // 移除的用户：在旧列表中但不在新列表中
                List<Long> removedUsers =
                        oldUserIds.stream().filter(id -> !newUserSet.contains(id)).toList();
                // 新增的主体：在新列表中但不在旧列表中
                List<Long> addedSubjects =
                        newSubjectIds.stream().filter(id -> !oldSubjectIds.contains(id)).toList();
                // 移除的主体：在旧列表中但不在新列表中
                List<Long> removedSubjects =
                        oldSubjectIds.stream().filter(id -> !newSubjectSet.contains(id)).toList();

                if (!addedUsers.isEmpty()) {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("roleId", roleId);
                    payload.put("affectedUserIds", addedUsers);
                    messagePublisher.publish(subjectId, projectNo, ChangeType.ROLE_ADD, payload);
                }
                if (!removedUsers.isEmpty()) {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("roleId", roleId);
                    payload.put("affectedUserIds", removedUsers);
                    messagePublisher.publish(subjectId, projectNo, ChangeType.ROLE_DELETE, payload);
                }
                if (!addedSubjects.isEmpty()) {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("roleId", roleId);
                    payload.put("affectedSubjectIds", addedSubjects);
                    messagePublisher.publish(subjectId, projectNo, ChangeType.ROLE_ADD, payload);
                }
                if (!removedSubjects.isEmpty()) {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("roleId", roleId);
                    payload.put("affectedSubjectIds", removedSubjects);
                    messagePublisher.publish(subjectId, projectNo, ChangeType.ROLE_DELETE, payload);
                }
            } else {
                // 新增角色：所有用户和主体都是新增
                if (!newUserIds.isEmpty()) {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("roleId", roleId);
                    payload.put("affectedUserIds", newUserIds);
                    messagePublisher.publish(subjectId, projectNo, ChangeType.ROLE_ADD, payload);
                }
                if (!newSubjectIds.isEmpty()) {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("roleId", roleId);
                    payload.put("affectedSubjectIds", newSubjectIds);
                    messagePublisher.publish(subjectId, projectNo, ChangeType.ROLE_ADD, payload);
                }
            }
        } catch (Exception e) {
            log.error("发送角色变更细粒度消息失败: roleId={}", roleId, e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Long doSaveRoleUser(
            SaveRoleUserReq request,
            Set<Long> allUserIds,
            Map<Long, UserBO> hrUserMap,
            Map<Long, DepartmentBO> deptMap,
            Map<Long, SubjectBO> subjectMap,
            List<UserBO> subjectUsers,
            Set<Long> oldSubjectIds) {
        Long roleId = request.getId();
        boolean isCreate = roleId == null;

        // ========== 校验1：选择主体后，再选择该主体下的员工 ==========
        // 新增和更新都得校验
        if (request.getSubjectIds() != null
                && !request.getSubjectIds().isEmpty()
                && !allUserIds.isEmpty()) {
            List<String> conflictNames = checkSubjectUserConflict(subjectUsers, allUserIds);
            if (!conflictNames.isEmpty()) {
                if (isCreate) {
                    // 新增时：之前不可能有绑定，不可强制保存
                    throw new BusinessException(
                            ApiCodeEnum.WARNING.getCode(),
                            String.format("\"%s\"已在授权主体中，请重新设置", String.join("、", conflictNames)));
                } else {
                    // 更新时：检查冲突用户是否和任何角色绑定过
                    List<String> previouslyBound = getUsersBoundToAnyRole(allUserIds);
                    if (previouslyBound.isEmpty()) {
                        // 之前都没有绑定过任何角色，不可强制保存
                        throw new BusinessException(
                                ApiCodeEnum.WARNING.getCode(),
                                String.format(
                                        "\"%s\"已在授权主体中，请重新设置", String.join("、", conflictNames)));
                    }
                    // 有之前绑定过任何角色的员工，可强制保存（跳过校验，继续执行）
                }
            }
        }

        // ========== 更新逻辑 ==========
        // 记录更新前该角色关联的用户ID（用于后续清理孤立用户）
        Set<Long> previousUserIds = new HashSet<>();
        if (!isCreate) {
            // 1. 获取该角色之前关联的用户ID（只获取单独用户关联，不包括主体关联的用户）
            List<SysRoleUser> previousRoleUsers =
                    sysRoleUserMapper.selectList(
                            new LambdaQueryWrapper<SysRoleUser>()
                                    .eq(SysRoleUser::getRoleId, roleId));
            previousUserIds =
                    previousRoleUsers.stream()
                            .map(SysRoleUser::getUserId)
                            .collect(Collectors.toSet());

            // ========== 校验2：更新时，授权主体后，是否需要覆盖已单独关联到该角色的主体下员工 ==========
            // 可强制保存，force=true时进行覆盖操作
            if (request.getSubjectIds() != null
                    && !request.getSubjectIds().isEmpty()
                    && !Boolean.TRUE.equals(request.getForce())) {
                for (Long subjectId : request.getSubjectIds()) {
                    List<String> overlapNames = checkRoleHasSubjectUsers(roleId, subjectId);
                    if (!overlapNames.isEmpty()) {
                        throw new BusinessException(
                                ApiCodeEnum.WARNING.getCode(),
                                String.format(
                                        "\"%s\"已在该角色中，是否覆盖？", String.join("、", overlapNames)));
                    }
                }
            }
        }

        // ========== 删除旧关联 ==========
        if (!isCreate) {
            // 删除该角色之前的所有关联数据（只删除关联表）
            // 删除角色-主体关联
            sysRoleSubjectMapper.delete(
                    new LambdaQueryWrapper<SysRoleSubject>().eq(SysRoleSubject::getRoleId, roleId));
            // 删除角色-用户关联
            sysRoleUserMapper.delete(
                    new LambdaQueryWrapper<SysRoleUser>().eq(SysRoleUser::getRoleId, roleId));
        }

        // 保存角色
        if (isCreate) {
            // 新增：校验角色名称唯一性（只检查有效的角色）
            long count =
                    sysRoleMapper.selectCount(
                            Wrappers.<SysRole>lambdaQuery()
                                    .eq(SysRole::getRoleName, request.getRoleName())
                                    .eq(SysRole::getSubjectId, AppContext.getSubjectId())
                                    .eq(SysRole::getProjectNo, AppContext.getProjectNo()));
            if (count > 0) {
                throw new BusinessException(ApiCodeEnum.WARNING.getCode(), "角色名称已存在");
            }
            SysRole role = new SysRole();
            role.setRoleName(request.getRoleName());
            role.setRoleSlug(request.getRoleName());
            role.setSubjectId(AppContext.getSubjectId());
            role.setProjectNo(AppContext.getProjectNo());
            sysRoleMapper.insert(role);
            roleId = role.getId();
            request.setId(roleId);
        } else {
            SysRole role = sysRoleMapper.selectById(roleId);
            if (role == null) {
                throw new BusinessException(ApiCodeEnum.WARNING.getCode(), "角色不存在");
            }
            // 编辑：校验角色名称唯一性（排除自己）
            long count =
                    sysRoleMapper.selectCount(
                            Wrappers.<SysRole>lambdaQuery()
                                    .eq(SysRole::getRoleName, request.getRoleName())
                                    .eq(SysRole::getSubjectId, AppContext.getSubjectId())
                                    .eq(SysRole::getProjectNo, AppContext.getProjectNo())
                                    .ne(SysRole::getId, roleId));
            if (count > 0) {
                throw new BusinessException(ApiCodeEnum.WARNING.getCode(), "角色名称已存在");
            }
            role.setRoleName(request.getRoleName());
            sysRoleMapper.updateById(role);
        }

        // 保存新的主体关联
        if (request.getSubjects() != null && !request.getSubjects().isEmpty()) {
            saveRoleSubjects(request, roleId);
        }

        // 需求1：同步主体下所有用户到 sys_user（有则更新，无则新增）
        syncSubjectUsers(subjectUsers, deptMap, subjectMap);

        // 保存新的单独用户关联（过滤掉属于该主体的用户）
        if (!allUserIds.isEmpty()) {
            // 收集内部用户ID用于判断用户类型
            Set<Long> insideUserIds = new HashSet<>();
            if (request.getUsers() != null) {
                insideUserIds =
                        request.getUsers().stream()
                                .filter(u -> UserTypeEnum.INSIDE.getCode().equals(u.getUserType()))
                                .map(u -> Long.parseLong(u.getUserId()))
                                .collect(Collectors.toSet());
            }
            batchEnsureUsersExist(allUserIds, insideUserIds, hrUserMap, deptMap, subjectMap);
            saveUserRelations(request, roleId);
        }

        // 需求2：清理孤立用户 —— 本次操作相关且既无有效角色绑定、也不属于有效角色绑定主体的用户，逻辑删除
        Set<Long> candidateUserIds = new HashSet<>(previousUserIds);
        candidateUserIds.addAll(allUserIds);
        if (subjectUsers != null) {
            candidateUserIds.addAll(
                    subjectUsers.stream().map(UserBO::getId).collect(Collectors.toSet()));
        }
        // 被移除的主体下，仍在 sys_user 中的用户也要纳入候选
        Set<Long> removedSubjectIds = new HashSet<>(oldSubjectIds);
        if (request.getSubjectIds() != null) {
            request.getSubjectIds().forEach(removedSubjectIds::remove);
        }
        if (!removedSubjectIds.isEmpty()) {
            List<SysUser> removedSubjectUsers =
                    sysUserMapper.selectList(
                            Wrappers.<SysUser>lambdaQuery()
                                    .in(SysUser::getSubjectId, removedSubjectIds)
                                    .eq(SysUser::getDeleted, 0));
            removedSubjectUsers.forEach(u -> candidateUserIds.add(u.getUserId()));
        }
        cleanupOrphanedUsers(candidateUserIds);

        return roleId;
    }

    @Override
    public RoleUserTimeResp getRoleUserTime(String userId, Long roleId) {
        Long userIdLong = Long.parseLong(userId);
        SysRoleUser user =
                sysRoleUserMapper.selectOne(
                        Wrappers.<SysRoleUser>lambdaQuery()
                                .eq(SysRoleUser::getUserId, userIdLong)
                                .eq(SysRoleUser::getRoleId, roleId));
        RoleUserTimeResp resp = new RoleUserTimeResp();
        resp.setUserId(userId);
        if (user != null) {
            resp.setEffectiveStartDate(user.getEffectiveStartDate());
            resp.setEffectiveEndDate(user.getEffectiveEndDate());
        } else {
            SysUser sysUser =
                    sysUserMapper.selectOne(
                            Wrappers.<SysUser>lambdaQuery()
                                    .eq(SysUser::getUserId, userIdLong)
                                    .eq(SysUser::getDeleted, 0));
            if (sysUser != null) {
                SysRoleSubject subject =
                        sysRoleSubjectMapper.selectOne(
                                Wrappers.<SysRoleSubject>lambdaQuery()
                                        .eq(SysRoleSubject::getRoleId, roleId)
                                        .eq(SysRoleSubject::getSubjectId, sysUser.getSubjectId()));

                resp.setEffectiveStartDate(subject.getEffectiveStartDate());
                resp.setEffectiveEndDate(subject.getEffectiveEndDate());
            }
        }
        return resp;
    }

    @Override
    public PageResult<RoleConfigPageResp> getRoleConfigPage(QueryRoleConfigPageReq query) {
        // 先更新已时效的关联状态
        updateExpiredRelations();

        Page<SysRole> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRole::getProjectNo, AppContext.getProjectNo());
        wrapper.eq(SysRole::getSubjectId, AppContext.getSubjectId());
        if (query.getRoleId() != null) {
            wrapper.eq(SysRole::getId, query.getRoleId());
        }
        if (query.getUserId() != null) {
            // 查询包含该用户的角色，且关联必须状态有效且在时效范围内
            List<Long> roleIds =
                    filterValidRoleUsers(
                                    sysRoleUserMapper.selectList(
                                            Wrappers.<SysRoleUser>lambdaQuery()
                                                    .eq(SysRoleUser::getUserId, query.getUserId())
                                                    .ne(SysRoleUser::getStatus, 1)))
                            .stream()
                            .map(SysRoleUser::getRoleId)
                            .distinct()
                            .toList();

            if (!roleIds.isEmpty()) {
                wrapper.in(SysRole::getId, roleIds);
            } else {
                // 如果没有找到任何角色，返回空结果
                return PageResult.of(
                        query.getPageNum(), query.getPageSize(), 0L, Collections.emptyList());
            }
        }
        IPage<SysRole> rolePage = sysRoleMapper.selectPage(page, wrapper);

        List<RoleConfigPageResp> respList =
                rolePage.getRecords().stream()
                        .map(
                                role -> {
                                    RoleConfigPageResp resp = new RoleConfigPageResp();
                                    resp.setId(role.getId());
                                    resp.setRoleName(role.getRoleName());

                                    // 查询该角色的用户关联（过滤掉失效的，保留生效和待生效的）
                                    List<SysRoleUser> roleUsers =
                                            sysRoleUserMapper.selectList(
                                                    Wrappers.<SysRoleUser>lambdaQuery()
                                                            .eq(
                                                                    SysRoleUser::getRoleId,
                                                                    role.getId())
                                                            .ne(SysRoleUser::getStatus, 1));

                                    // 填充用户列表（每个用户带时效信息）
                                    List<RoleUserResp> userList = buildUserList(roleUsers);
                                    resp.setUserList(userList);

                                    // 查询该角色的主体关联（过滤掉待生效的，保留生效和失效的）
                                    List<SysRoleSubject> subjects =
                                            sysRoleSubjectMapper.selectList(
                                                    Wrappers.<SysRoleSubject>lambdaQuery()
                                                            .eq(
                                                                    SysRoleSubject::getRoleId,
                                                                    role.getId())
                                                            .ne(SysRoleSubject::getStatus, 1));
                                    // 填充主体列表（每个主体带时效信息）
                                    List<RoleSubjectResp> subjectList = buildSubjectList(subjects);
                                    resp.setSubjectList(subjectList);

                                    return resp;
                                })
                        .collect(Collectors.toList());

        return PageResult.of(
                query.getPageNum(), query.getPageSize(), rolePage.getTotal(), respList);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RoleConfigDetailResp getRoleConfigDetail(Long roleId) {
        // 先更新已时效的关联状态
        updateExpiredRelations();

        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(ApiCodeEnum.WARNING.getCode(), "角色不存在");
        }
        RoleConfigDetailResp resp = new RoleConfigDetailResp();
        resp.setId(role.getId());
        resp.setRoleName(role.getRoleName());
        // 查询角色主体关联表（只查询有效且在时效内的）
        List<SysRoleSubject> subjectList =
                filterValidRoleSubjects(
                        sysRoleSubjectMapper.selectList(
                                Wrappers.<SysRoleSubject>lambdaQuery()
                                        .eq(SysRoleSubject::getRoleId, roleId)
                                        .eq(SysRoleSubject::getStatus, 0)));
        if (CollUtil.isNotEmpty(subjectList)) {
            List<Long> subjectIds = subjectList.stream().map(SysRoleSubject::getSubjectId).toList();
            resp.setSubjectIds(subjectIds);
            List<RoleSubjectReq> subjectReqs = new ArrayList<>();
            for (SysRoleSubject rs : subjectList) {
                RoleSubjectReq req = new RoleSubjectReq();
                req.setSubjectId(rs.getSubjectId());
                req.setEffectiveType(rs.getEffectiveType());
                req.setEffectiveStartDate(rs.getEffectiveStartDate());
                req.setEffectiveEndDate(rs.getEffectiveEndDate());
                subjectReqs.add(req);
            }
            resp.setSubjects(subjectReqs);
        }
        // 查询角色用户关联表（只查询有效且在时效内的）
        List<SysRoleUser> userList =
                filterValidRoleUsers(
                        sysRoleUserMapper.selectList(
                                Wrappers.<SysRoleUser>lambdaQuery()
                                        .eq(SysRoleUser::getRoleId, roleId)
                                        .eq(SysRoleUser::getStatus, 0)));
        if (CollUtil.isNotEmpty(userList)) {
            // 区分内部和外部用户，并携带各自时效
            List<String> insideIds = new ArrayList<>();
            List<String> externalIds = new ArrayList<>();
            List<RoleUserReq> insideUsers = new ArrayList<>();
            List<RoleUserReq> externalUsers = new ArrayList<>();

            // 批量查询所有用户信息，避免N+1查询
            List<Long> userIds =
                    userList.stream().map(SysRoleUser::getUserId).collect(Collectors.toList());
            List<SysUser> sysUsers =
                    sysUserMapper.selectList(
                            Wrappers.<SysUser>lambdaQuery()
                                    .in(SysUser::getUserId, userIds)
                                    .eq(SysUser::getDeleted, 0));
            Map<Long, SysUser> userMap =
                    sysUsers.stream().collect(Collectors.toMap(SysUser::getUserId, u -> u));

            for (SysRoleUser roleUser : userList) {
                SysUser user = userMap.get(roleUser.getUserId());
                if (user != null) {
                    String userId = String.valueOf(user.getUserId());
                    RoleUserReq userReq = new RoleUserReq();
                    userReq.setUserId(userId);
                    userReq.setUserName(user.getUserName());
                    userReq.setUserAvatar(user.getUserAvatar());
                    userReq.setWorkNumber(user.getWorkNumber());
                    userReq.setSex(user.getSex());
                    userReq.setEffectiveType(roleUser.getEffectiveType());
                    userReq.setEffectiveStartDate(roleUser.getEffectiveStartDate());
                    userReq.setEffectiveEndDate(roleUser.getEffectiveEndDate());
                    if (UserTypeEnum.INSIDE.getCode().equals(user.getUserType())) {
                        insideIds.add(userId);
                        userReq.setUserType(UserTypeEnum.INSIDE.getCode());
                        insideUsers.add(userReq);
                    } else if (UserTypeEnum.EXTERNAL.getCode().equals(user.getUserType())) {
                        externalIds.add(userId);
                        userReq.setUserType(UserTypeEnum.EXTERNAL.getCode());
                        externalUsers.add(userReq);
                    }
                }
            }

            resp.setUserInsideIds(insideIds);
            resp.setUserExternalIds(externalIds);
            resp.setInsideUsers(insideUsers);
            resp.setExternalUsers(externalUsers);
        }
        return resp;
    }

    @Override
    public RoleConfigDetailResp getRoleConfigDetailAll(Long roleId) {
        // 先更新已时效的关联状态
        updateExpiredRelations();

        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(ApiCodeEnum.WARNING.getCode(), "角色不存在");
        }
        RoleConfigDetailResp resp = new RoleConfigDetailResp();
        resp.setId(role.getId());
        resp.setRoleName(role.getRoleName());
        // 查询角色主体关联表（查询所有非删除状态，不过滤时效）
        List<SysRoleSubject> subjectList =
                sysRoleSubjectMapper.selectList(
                        Wrappers.<SysRoleSubject>lambdaQuery()
                                .eq(SysRoleSubject::getRoleId, roleId)
                                .ne(SysRoleSubject::getStatus, 1));
        if (CollUtil.isNotEmpty(subjectList)) {
            List<Long> subjectIds = subjectList.stream().map(SysRoleSubject::getSubjectId).toList();
            resp.setSubjectIds(subjectIds);
            List<RoleSubjectReq> subjectReqs = new ArrayList<>();
            for (SysRoleSubject rs : subjectList) {
                RoleSubjectReq req = new RoleSubjectReq();
                req.setSubjectId(rs.getSubjectId());
                req.setEffectiveType(rs.getEffectiveType());
                req.setEffectiveStartDate(rs.getEffectiveStartDate());
                req.setEffectiveEndDate(rs.getEffectiveEndDate());
                subjectReqs.add(req);
            }
            resp.setSubjects(subjectReqs);
        }
        // 查询角色用户关联表（查询所有非删除状态，不过滤时效）
        List<SysRoleUser> userList =
                sysRoleUserMapper.selectList(
                        Wrappers.<SysRoleUser>lambdaQuery()
                                .eq(SysRoleUser::getRoleId, roleId)
                                .ne(SysRoleUser::getStatus, 1));
        if (CollUtil.isNotEmpty(userList)) {
            // 区分内部和外部用户，并携带各自时效
            List<String> insideIds = new ArrayList<>();
            List<String> externalIds = new ArrayList<>();
            List<RoleUserReq> insideUsers = new ArrayList<>();
            List<RoleUserReq> externalUsers = new ArrayList<>();

            // 批量查询所有用户信息，避免N+1查询
            List<Long> userIds =
                    userList.stream().map(SysRoleUser::getUserId).collect(Collectors.toList());
            List<SysUser> sysUsers =
                    sysUserMapper.selectList(
                            Wrappers.<SysUser>lambdaQuery()
                                    .in(SysUser::getUserId, userIds)
                                    .eq(SysUser::getDeleted, 0));
            Map<Long, SysUser> userMap =
                    sysUsers.stream().collect(Collectors.toMap(SysUser::getUserId, u -> u));

            for (SysRoleUser roleUser : userList) {
                SysUser user = userMap.get(roleUser.getUserId());
                if (user != null) {
                    String userId = String.valueOf(user.getUserId());
                    RoleUserReq userReq = new RoleUserReq();
                    userReq.setUserId(userId);
                    userReq.setUserName(user.getUserName());
                    userReq.setUserAvatar(user.getUserAvatar());
                    userReq.setWorkNumber(user.getWorkNumber());
                    userReq.setSex(user.getSex());
                    userReq.setEffectiveType(roleUser.getEffectiveType());
                    userReq.setEffectiveStartDate(roleUser.getEffectiveStartDate());
                    userReq.setEffectiveEndDate(roleUser.getEffectiveEndDate());
                    if (UserTypeEnum.INSIDE.getCode().equals(user.getUserType())) {
                        insideIds.add(userId);
                        userReq.setUserType(UserTypeEnum.INSIDE.getCode());
                        insideUsers.add(userReq);
                    } else if (UserTypeEnum.EXTERNAL.getCode().equals(user.getUserType())) {
                        externalIds.add(userId);
                        userReq.setUserType(UserTypeEnum.EXTERNAL.getCode());
                        externalUsers.add(userReq);
                    }
                }
            }

            resp.setUserInsideIds(insideIds);
            resp.setUserExternalIds(externalIds);
            resp.setInsideUsers(insideUsers);
            resp.setExternalUsers(externalUsers);
        }
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long roleId) {
        // 检查关联
        SysRole sysRole = sysRoleMapper.selectById(roleId);
        ReferenceContext context =
                ReferenceContext.builder()
                        .targetType(CheckConstant.SYS_ROLE)
                        .targetId(roleId)
                        .targetName(sysRole.getRoleName())
                        .build();
        List<String> check = referenceCheckManager.check(context);
        if (CollUtil.isNotEmpty(check)) {
            throw new PopException(String.join("\n", check));
        }

        // === 审批日志：记录删除角色 ===
        try {
            roleApprovalLogService.logRoleDelete(roleId, sysRole.getRoleName());
        } catch (Exception e) {
            log.error("记录角色删除审批日志失败: roleId={}", roleId, e);
        }

        // 1. 删除前收集受影响的用户/主体 ID（删除后数据已不存在，无法再查）
        List<SysRoleUser> roleUsers =
                sysRoleUserMapper.selectList(
                        new LambdaQueryWrapper<SysRoleUser>().eq(SysRoleUser::getRoleId, roleId));
        Set<Long> userIds =
                roleUsers.stream().map(SysRoleUser::getUserId).collect(Collectors.toSet());

        List<Long> affectedSubjectIds =
                sysRoleSubjectMapper
                        .selectList(
                                new LambdaQueryWrapper<SysRoleSubject>()
                                        .eq(SysRoleSubject::getRoleId, roleId))
                        .stream()
                        .map(SysRoleSubject::getSubjectId)
                        .distinct()
                        .toList();

        // 2. 删除角色主体关联 (sys_role_subject)
        sysRoleSubjectMapper.delete(
                new LambdaQueryWrapper<SysRoleSubject>().eq(SysRoleSubject::getRoleId, roleId));

        // 3. 删除角色用户关联 (sys_role_user)
        sysRoleUserMapper.delete(
                new LambdaQueryWrapper<SysRoleUser>().eq(SysRoleUser::getRoleId, roleId));

        // 4. 删除角色快照 (sys_data_snapshot)
        sysDataSnapshotService.deleteSnapshot("sys_role", roleId);

        // 5. 删除角色本身
        sysRoleMapper.deleteById(roleId);

        // 6. 发送 ROLE_DELETE 消息
        try {
            Long subjectId = AppContext.getSubjectId();
            String projectNo = AppContext.getProjectNo();
            if (!userIds.isEmpty()) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("roleId", roleId);
                payload.put("affectedUserIds", new ArrayList<>(userIds));
                messagePublisher.publish(subjectId, projectNo, ChangeType.ROLE_DELETE, payload);
            }
            if (!affectedSubjectIds.isEmpty()) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("roleId", roleId);
                payload.put("affectedSubjectIds", affectedSubjectIds);
                messagePublisher.publish(subjectId, projectNo, ChangeType.ROLE_DELETE, payload);
            }
        } catch (Exception e) {
            log.error("发送角色删除消息失败: roleId={}", roleId, e);
        }
    }

    @Override
    public List<SubjectOptionResp> listSubjectOptions() {
        List<SubjectBO> subjectList = subjectApi.getSubjectList();
        return subjectList.stream()
                .map(
                        subject -> {
                            SubjectOptionResp resp = new SubjectOptionResp();
                            resp.setSubjectId(subject.getId());
                            resp.setSubjectName(subject.getSubjectName());
                            return resp;
                        })
                .toList();
    }

    /** 根据角色ID查询角色配置信息 */
    private RoleConfigPageResp getRoleConfigById(Long roleId) {
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(ApiCodeEnum.WARNING.getCode(), "角色不存在");
        }

        RoleConfigPageResp resp = new RoleConfigPageResp();
        resp.setId(role.getId());
        resp.setRoleName(role.getRoleName());

        // 查询该角色的用户关联（过滤掉待生效的，保留生效和失效的）
        List<SysRoleUser> roleUsers =
                sysRoleUserMapper.selectList(
                        Wrappers.<SysRoleUser>lambdaQuery()
                                .eq(SysRoleUser::getRoleId, role.getId())
                                .ne(SysRoleUser::getStatus, 2));

        // 填充用户列表（每个用户带时效信息）
        List<RoleUserResp> userList = buildUserList(roleUsers);
        resp.setUserList(userList);

        // 查询该角色的主体关联（过滤掉待生效的，保留生效和失效的）
        List<SysRoleSubject> subjects =
                sysRoleSubjectMapper.selectList(
                        Wrappers.<SysRoleSubject>lambdaQuery()
                                .eq(SysRoleSubject::getRoleId, role.getId())
                                .ne(SysRoleSubject::getStatus, 2));
        // 填充主体列表（每个主体带时效信息）
        List<RoleSubjectResp> subjectList = buildSubjectList(subjects);
        resp.setSubjectList(subjectList);

        return resp;
    }

    @Override
    public List<RoleListResp> listRole() {
        // 先更新已时效的关联状态
        updateExpiredRelations();

        List<SysRole> sysRoles =
                sysRoleMapper.selectList(
                        Wrappers.<SysRole>lambdaQuery()
                                .eq(SysRole::getProjectNo, AppContext.getProjectNo())
                                .eq(SysRole::getSubjectId, AppContext.getSubjectId()));
        List<RoleListResp> respList = BeanUtil.copyToList(sysRoles, RoleListResp.class);
        // 添加序号
        for (int i = 0; i < respList.size(); i++) {
            respList.get(i).setXh(i + 1);
        }
        return respList;
    }

    @Override
    public List<UserRoleListResp> getUserRolesByUserIdAndSubjectId(Long userId) {
        // 先更新已时效的关联状态
        updateExpiredRelations();
        Long currentSubjectId = AppContext.getSubjectId();
        List<UserRoleListResp> result = new ArrayList<>();

        // 步骤1: 根据当前主体ID查询该主体下的所有角色
        List<SysRole> currentSubjectRoles =
                sysRoleMapper.selectList(
                        Wrappers.<SysRole>lambdaQuery()
                                .eq(SysRole::getSubjectId, currentSubjectId)
                                .eq(SysRole::getProjectNo, AppContext.getProjectNo()));

        // 如果当前主体没有角色，直接返回空列表
        if (currentSubjectRoles.isEmpty()) {
            return result;
        }

        // 当前主体下的所有角色ID集合
        Set<Long> currentSubjectRoleIds =
                currentSubjectRoles.stream().map(SysRole::getId).collect(Collectors.toSet());

        // 构建角色ID到角色对象的映射
        Map<Long, SysRole> roleMap =
                currentSubjectRoles.stream().collect(Collectors.toMap(SysRole::getId, r -> r));

        // 路径1（步骤2）: 查询用户角色关系表（状态有效且在时效内），并与当前主体角色取交集
        List<SysRoleUser> validRoleUsers =
                filterValidRoleUsers(
                        sysRoleUserMapper.selectList(
                                Wrappers.<SysRoleUser>lambdaQuery()
                                        .eq(SysRoleUser::getUserId, userId)
                                        .eq(SysRoleUser::getStatus, 0)));

        if (!validRoleUsers.isEmpty()) {
            // 用户关联的角色ID
            Set<Long> userRoleIds =
                    validRoleUsers.stream().map(SysRoleUser::getRoleId).collect(Collectors.toSet());

            // 取交集：保留既在用户关联中，又在当前主体中的角色
            userRoleIds.retainAll(currentSubjectRoleIds);

            // 构建响应
            for (SysRoleUser roleUser : validRoleUsers) {
                // 只处理交集中的角色
                if (userRoleIds.contains(roleUser.getRoleId())) {
                    SysRole role = roleMap.get(roleUser.getRoleId());
                    if (role != null) {
                        UserRoleListResp resp = new UserRoleListResp();
                        resp.setRoleId(role.getId());
                        resp.setRoleName(role.getRoleName());
                        resp.setRoleSlug(role.getRoleSlug());
                        resp.setSubjectId(role.getSubjectId());
                        resp.setEffectiveType(roleUser.getEffectiveType());
                        resp.setEffectiveStartDate(roleUser.getEffectiveStartDate());
                        resp.setEffectiveEndDate(roleUser.getEffectiveEndDate());
                        resp.setRoleSource(1); // 用户直接关联
                        result.add(resp);
                    }
                }
            }
        }

        // 路径2（步骤4）: 从 sys_user 表获取用户所属主体ID（角色保存时已同步主体下用户，无需再调 HR）
        // 注意：这里需要获取用户自己所属的主体ID，不是当前请求的主体ID
        Long userSubjectId = null;
        SysUser userForSubject =
                sysUserMapper.selectOne(
                        Wrappers.<SysUser>lambdaQuery()
                                .eq(SysUser::getUserId, userId)
                                .eq(SysUser::getDeleted, 0));
        if (userForSubject != null) {
            userSubjectId = userForSubject.getSubjectId();
        }

        if (userSubjectId != null) {
            // 查询用户所属主体的角色关联（状态有效且在时效内）
            List<SysRoleSubject> validRoleSubjects =
                    filterValidRoleSubjects(
                            sysRoleSubjectMapper.selectList(
                                    Wrappers.<SysRoleSubject>lambdaQuery()
                                            .eq(SysRoleSubject::getSubjectId, userSubjectId)
                                            .eq(SysRoleSubject::getStatus, 0)));

            if (!validRoleSubjects.isEmpty()) {
                // 用户主体关联的角色ID
                Set<Long> subjectRoleIds =
                        validRoleSubjects.stream()
                                .map(SysRoleSubject::getRoleId)
                                .collect(Collectors.toSet());

                // 取交集：保留既在主体关联中，又在当前主体中的角色
                subjectRoleIds.retainAll(currentSubjectRoleIds);

                // 构建响应，合并到结果中
                for (SysRoleSubject roleSubject : validRoleSubjects) {
                    // 只处理交集中的角色
                    if (subjectRoleIds.contains(roleSubject.getRoleId())) {
                        SysRole role = roleMap.get(roleSubject.getRoleId());
                        if (role != null) {
                            UserRoleListResp resp = new UserRoleListResp();
                            resp.setRoleId(role.getId());
                            resp.setRoleName(role.getRoleName());
                            resp.setRoleSlug(role.getRoleSlug());
                            resp.setSubjectId(role.getSubjectId());
                            resp.setEffectiveType(roleSubject.getEffectiveType());
                            resp.setEffectiveStartDate(roleSubject.getEffectiveStartDate());
                            resp.setEffectiveEndDate(roleSubject.getEffectiveEndDate());
                            resp.setRoleSource(2); // 主体关联
                            result.add(resp);
                        }
                    }
                }
            }
        }

        // 返回合并后的结果（两个路径都为空时返回空列表，否则返回合并结果）
        return result;
    }

    /** 过滤出时效内的用户关联记录（永久有效，或当前时间在自定义时效范围内） */
    private List<SysRoleUser> filterValidRoleUsers(List<SysRoleUser> roleUsers) {
        java.time.LocalDate today = java.time.LocalDate.now();
        return roleUsers.stream()
                .filter(
                        ru ->
                                isEffectiveDateValid(
                                        ru.getEffectiveType(),
                                        ru.getEffectiveStartDate(),
                                        ru.getEffectiveEndDate(),
                                        today))
                .collect(Collectors.toList());
    }

    /** 过滤出时效内的主体关联记录（永久有效，或当前时间在自定义时效范围内） */
    private List<SysRoleSubject> filterValidRoleSubjects(List<SysRoleSubject> subjects) {
        java.time.LocalDate today = java.time.LocalDate.now();
        return subjects.stream()
                .filter(
                        s ->
                                isEffectiveDateValid(
                                        s.getEffectiveType(),
                                        s.getEffectiveStartDate(),
                                        s.getEffectiveEndDate(),
                                        today))
                .collect(Collectors.toList());
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
            Integer effectiveType,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate,
            java.time.LocalDate today) {
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
            Integer effectiveType,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate,
            java.time.LocalDate today) {
        Integer status = calculateCurrentStatus(effectiveType, startDate, endDate, today);
        return status == 0; // 只有生效状态才算有效
    }

    private List<RoleSubjectResp> buildSubjectList(List<SysRoleSubject> subjects) {
        if (subjects.isEmpty()) {
            return Collections.emptyList();
        }

        // 查询所有主体信息并构建 Map
        List<SubjectBO> allSubjects = subjectApi.getSubjectList();
        Map<Long, String> subjectNameMap =
                allSubjects.stream()
                        .collect(Collectors.toMap(SubjectBO::getId, SubjectBO::getSubjectName));

        List<RoleSubjectResp> respList = new ArrayList<>();
        for (SysRoleSubject sysRoleSubject : subjects) {
            RoleSubjectResp vo = new RoleSubjectResp();
            vo.setSubjectId(sysRoleSubject.getSubjectId());
            vo.setSubjectName(subjectNameMap.get(sysRoleSubject.getSubjectId()));
            vo.setEffectiveType(sysRoleSubject.getEffectiveType());
            vo.setEffectiveStartDate(sysRoleSubject.getEffectiveStartDate());
            vo.setEffectiveEndDate(sysRoleSubject.getEffectiveEndDate());
            vo.setStatus(sysRoleSubject.getStatus());
            respList.add(vo);
        }
        return respList;
    }

    /** 构建用户VO列表（每个用户携带各自的时效信息） */
    private List<RoleUserResp> buildUserList(List<SysRoleUser> roleUsers) {
        if (roleUsers.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> userIds =
                roleUsers.stream().map(SysRoleUser::getUserId).collect(Collectors.toList());

        // 查询用户基础信息
        Map<Long, SysUser> sysUserMap = new HashMap<>();
        List<SysUser> sysUsers =
                sysUserMapper.selectList(
                        Wrappers.<SysUser>lambdaQuery()
                                .in(SysUser::getUserId, userIds)
                                .eq(SysUser::getDeleted, 0));
        for (SysUser sysUser : sysUsers) {
            sysUserMap.put(sysUser.getUserId(), sysUser);
        }

        // 按 roleUsers 顺序构建，保留每个用户关联的时效信息
        List<RoleUserResp> respList = new ArrayList<>();
        for (SysRoleUser roleUser : roleUsers) {
            SysUser sysUser = sysUserMap.get(roleUser.getUserId());
            if (sysUser == null) {
                continue;
            }
            RoleUserResp vo = new RoleUserResp();
            vo.setUserName(sysUser.getUserName());
            vo.setUserId(String.valueOf(sysUser.getUserId()));
            vo.setUserAvatar(sysUser.getUserAvatar());
            vo.setWorkNumber(sysUser.getWorkNumber());
            vo.setSex(sysUser.getSex());
            vo.setEffectiveType(roleUser.getEffectiveType());
            vo.setEffectiveStartDate(roleUser.getEffectiveStartDate());
            vo.setEffectiveEndDate(roleUser.getEffectiveEndDate());
            vo.setStatus(roleUser.getStatus());
            respList.add(vo);
        }
        return respList;
    }

    /**
     * 清理孤立用户：候选用户中，既没有有效的角色绑定，也不属于任何有效角色绑定主体的用户， 将其 sys_user 记录逻辑删除（deleted=1）。判断基于时效（仅算有效期内）。
     *
     * @param userIds 候选用户ID（与本次角色操作相关的用户）
     */
    private void cleanupOrphanedUsers(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        // 候选用户在 sys_user 中的未删除记录（用于取 subjectId 判断主体覆盖）
        List<SysUser> candidates =
                sysUserMapper.selectList(
                        Wrappers.<SysUser>lambdaQuery()
                                .in(SysUser::getUserId, userIds)
                                .eq(SysUser::getDeleted, 0));
        if (candidates.isEmpty()) {
            return;
        }
        Map<Long, SysUser> candidateMap =
                candidates.stream().collect(Collectors.toMap(SysUser::getUserId, u -> u));

        // 有有效角色绑定的用户（时效有效）
        Set<Long> protectedUserIds =
                filterValidRoleUsers(
                                sysRoleUserMapper.selectList(
                                        Wrappers.<SysRoleUser>lambdaQuery()
                                                .in(SysRoleUser::getUserId, candidateMap.keySet())
                                                .eq(SysRoleUser::getStatus, 0)))
                        .stream()
                        .map(SysRoleUser::getUserId)
                        .collect(Collectors.toSet());

        // 有有效角色绑定的主体（时效有效），属于这些主体的用户视为被覆盖
        Set<Long> coveredSubjectIds =
                filterValidRoleSubjects(
                                sysRoleSubjectMapper.selectList(
                                        Wrappers.<SysRoleSubject>lambdaQuery()
                                                .eq(SysRoleSubject::getStatus, 0)))
                        .stream()
                        .map(SysRoleSubject::getSubjectId)
                        .collect(Collectors.toSet());

        // 孤儿用户 = 候选用户 - 有效角色绑定用户 - 属于有效角色绑定主体的用户
        Set<Long> orphanedUserIds = new HashSet<>(candidateMap.keySet());
        orphanedUserIds.removeAll(protectedUserIds);
        orphanedUserIds.removeIf(
                uid -> {
                    SysUser user = candidateMap.get(uid);
                    return user.getSubjectId() != null
                            && coveredSubjectIds.contains(user.getSubjectId());
                });

        // 批量逻辑删除孤立用户
        if (!orphanedUserIds.isEmpty()) {
            SysUser updateEntity = new SysUser();
            updateEntity.setDeleted(1);
            sysUserMapper.update(
                    updateEntity,
                    Wrappers.<SysUser>lambdaQuery()
                            .in(SysUser::getUserId, orphanedUserIds)
                            .eq(SysUser::getDeleted, 0));
        }
    }

    /** 保存单独用户授权关系 注意：只添加不属于该主体的用户，属于该主体的用户无需再和角色进行关联 */
    private void saveUserRelations(SaveRoleUserReq request, Long roleId) {
        Set<Long> allUserIds = collectUserIds(request);

        if (allUserIds.isEmpty()) {
            return;
        }

        List<SysUser> users =
                sysUserMapper.selectList(
                        Wrappers.<SysUser>lambdaQuery()
                                .in(SysUser::getUserId, allUserIds)
                                .eq(SysUser::getDeleted, 0));
        Map<Long, SysUser> userMap =
                users.stream().collect(Collectors.toMap(SysUser::getUserId, u -> u));

        List<SysRoleUser> list = new ArrayList<>();

        // 处理内部用户列表
        if (request.getUserInsideList() != null) {
            for (RoleUserReq userReq : request.getUserInsideList()) {
                Long userId = Long.parseLong(userReq.getUserId());
                // 过滤：只添加不属于任何主体的用户
                if (request.getSubjectIds() != null && !request.getSubjectIds().isEmpty()) {
                    boolean inAnySubject = false;
                    for (Long subjectId : request.getSubjectIds()) {
                        if (isUserInSubject(userId, subjectId, userMap)) {
                            inAnySubject = true;
                            break;
                        }
                    }
                    if (inAnySubject) {
                        continue;
                    }
                }
                list.add(
                        buildRoleUser(
                                userReq, roleId, userId, UserTypeEnum.INSIDE.getCode(), userMap));
            }
        }

        // 处理外部用户列表
        if (request.getUserExternalList() != null) {
            for (RoleUserReq userReq : request.getUserExternalList()) {
                Long userId = Long.parseLong(userReq.getUserId());
                list.add(
                        buildRoleUser(
                                userReq, roleId, userId, UserTypeEnum.EXTERNAL.getCode(), userMap));
            }
        }

        if (!list.isEmpty()) {
            sysRoleUserMapper.insert(list);
        }
    }

    private SysRoleUser buildRoleUser(
            RoleUserReq userReq,
            Long roleId,
            Long userId,
            Integer userType,
            Map<Long, SysUser> userMap) {
        SysUser user = userMap.get(userId);
        SysRoleUser ru = new SysRoleUser();
        ru.setRoleId(roleId);
        ru.setUserId(userId);
        ru.setUserType(userType);
        ru.setCurrentFlag(1);
        ru.setSubjectId(user.getSubjectId());
        ru.setOriginalUserId(user.getUserId());
        ru.setEffectiveStartDate(userReq.getEffectiveStartDate());
        ru.setEffectiveEndDate(userReq.getEffectiveEndDate());
        ru.setEffectiveType(userReq.getEffectiveType());
        // 根据时间计算初始状态
        java.time.LocalDate today = java.time.LocalDate.now();
        ru.setStatus(
                calculateCurrentStatus(
                        userReq.getEffectiveType(),
                        userReq.getEffectiveStartDate(),
                        userReq.getEffectiveEndDate(),
                        today));
        // 为字符串字段设置默认空字符串，避免 diff 时遗漏
        ru.setCreatedName("");
        ru.setUpdatedName("");
        return ru;
    }

    /** 判断用户是否属于指定主体 */
    private boolean isUserInSubject(Long userId, Long subjectId, Map<Long, SysUser> userMap) {
        SysUser user = userMap.get(userId);
        return user != null && user.getSubjectId() != null && user.getSubjectId().equals(subjectId);
    }

    /** 批量检查并创建不存在的用户 */
    private void batchEnsureUsersExist(
            Set<Long> allUserIds,
            Set<Long> insideIds,
            Map<Long, UserBO> userMap,
            Map<Long, DepartmentBO> deptMap,
            Map<Long, SubjectBO> subjectMap) {
        List<SysUser> existingUsers =
                sysUserMapper.selectList(
                        Wrappers.<SysUser>lambdaQuery()
                                .in(SysUser::getUserId, allUserIds)
                                .eq(SysUser::getDeleted, 0));
        Set<Long> existingUserIds =
                existingUsers.stream().map(SysUser::getUserId).collect(Collectors.toSet());

        if (insideIds == null) {
            insideIds = new HashSet<>();
        }
        List<SysUser> newUsers = new ArrayList<>();
        for (Long userId : allUserIds) {
            if (!existingUserIds.contains(userId)) {
                SysUser sysUser = new SysUser();
                sysUser.setUserId(userId);
                sysUser.setUserType(
                        insideIds.contains(userId)
                                ? UserTypeEnum.INSIDE.getCode()
                                : UserTypeEnum.EXTERNAL.getCode());
                sysUser.setStatusFlag(UserStatusFlagEnum.ENABLE.getCode());

                // 从User表获取数据填充
                UserBO u = userMap.get(userId);
                if (u != null) {
                    sysUser.setUserName(u.getUserName());
                    sysUser.setSex(u.getSex());
                    sysUser.setUserAvatar(u.getUserAvatar());
                    sysUser.setWorkNumber(u.getWorkNumber());
                    sysUser.setPhone(u.getPhone());
                    sysUser.setShortName(u.getShortName());
                    sysUser.setSubjectId(Long.valueOf(u.getSubjectId()));
                    SubjectBO subject = subjectMap.get(u.getSubjectId().longValue());
                    sysUser.setSubjectName(subject.getSubjectName());
                    sysUser.setCompanyName(subject.getCompanyName());
                    sysUser.setDepartIds(u.getDepartmentId());
                    sysUser.setDepartName(u.getDepartmentName());
                    String managerName = this.getManagerByDepartId(u.getDepartmentId(), deptMap);
                    sysUser.setLeaderName(managerName);
                    sysUser.setPositionName(u.getJobName());
                    sysUser.setStatusFlag(UserStatusFlagEnum.ENABLE.getCode());
                    sysUser.setDeleted(0);
                    sysUser.setEmployedStatus(EmployedStatusEnum.EMPLOYED_STATUS_ENABLED.getCode());
                } else {
                    sysUser.setUserName("用户" + userId);
                }
                newUsers.add(sysUser);
            }
        }
        if (!newUsers.isEmpty()) {
            sysUserMapper.insert(newUsers);
        }
    }

    /**
     * 同步主体下所有用户到 sys_user 表：按 user_id 对比，有则更新（含恢复被逻辑删除的记录），无则新增。
     *
     * @param subjectUsers 本次选中主体下的全部用户（来自 HR 模块）
     * @param deptMap 部门信息映射
     * @param subjectMap 主体信息映射
     */
    private void syncSubjectUsers(
            List<UserBO> subjectUsers,
            Map<Long, DepartmentBO> deptMap,
            Map<Long, SubjectBO> subjectMap) {
        if (CollUtil.isEmpty(subjectUsers)) {
            return;
        }

        Set<Long> userIds = subjectUsers.stream().map(UserBO::getId).collect(Collectors.toSet());
        // 查询已存在记录（不过滤 deleted，便于恢复被逻辑删除的用户）
        List<SysUser> existingUsers =
                sysUserMapper.selectList(
                        Wrappers.<SysUser>lambdaQuery().in(SysUser::getUserId, userIds));
        Map<Long, SysUser> existingMap =
                existingUsers.stream()
                        .collect(Collectors.toMap(SysUser::getUserId, u -> u, (a, b) -> a));

        List<SysUser> toInsert = new ArrayList<>();
        List<SysUser> toUpdate = new ArrayList<>();
        for (UserBO user : subjectUsers) {
            SysUser sysUser = existingMap.get(user.getId());
            if (sysUser != null) {
                fillSubjectUserFields(sysUser, user, deptMap, subjectMap);
                sysUser.setDeleted(0); // 恢复被逻辑删除的记录
                toUpdate.add(sysUser);
            } else {
                SysUser newUser = new SysUser();
                newUser.setUserId(user.getId());
                newUser.setUserType(UserTypeEnum.INSIDE.getCode());
                newUser.setStatusFlag(UserStatusFlagEnum.ENABLE.getCode());
                newUser.setEmployedStatus(EmployedStatusEnum.EMPLOYED_STATUS_ENABLED.getCode());
                newUser.setDeleted(0);
                fillSubjectUserFields(newUser, user, deptMap, subjectMap);
                toInsert.add(newUser);
            }
        }

        if (!toInsert.isEmpty()) {
            sysUserMapper.insert(toInsert);
        }
        if (!toUpdate.isEmpty()) {
            sysUserMapper.updateById(toUpdate);
        }
    }

    /** 将 HR 用户信息填充到 sys_user（不覆盖用户类型、启用状态等管理字段） */
    private void fillSubjectUserFields(
            SysUser sysUser,
            UserBO user,
            Map<Long, DepartmentBO> deptMap,
            Map<Long, SubjectBO> subjectMap) {
        sysUser.setUserName(user.getUserName());
        sysUser.setSex(user.getSex());
        sysUser.setUserAvatar(user.getUserAvatar());
        sysUser.setWorkNumber(user.getWorkNumber());
        sysUser.setPhone(user.getPhone());
        sysUser.setShortName(user.getShortName());
        if (user.getSubjectId() != null) {
            sysUser.setSubjectId(Long.valueOf(user.getSubjectId()));
            SubjectBO subject = subjectMap.get(user.getSubjectId().longValue());
            if (subject != null) {
                sysUser.setSubjectName(subject.getSubjectName());
                sysUser.setCompanyName(subject.getCompanyName());
            }
        }
        sysUser.setDepartIds(user.getDepartmentId());
        sysUser.setDepartName(user.getDepartmentName());
        sysUser.setLeaderName(getManagerByDepartId(user.getDepartmentId(), deptMap));
        sysUser.setPositionName(user.getJobName());
    }

    private String getManagerByDepartId(String departIds, Map<Long, DepartmentBO> departMap) {
        return getColumnInfoByDepartId(departIds, departMap, ",");
    }

    private String getColumnInfoByDepartId(
            Object departIds, Map<Long, DepartmentBO> departs, String glue) {
        List<Long> idList = parseDepartIds(departIds);
        if (idList.isEmpty()) {
            return "";
        }
        List<String> values =
                idList.stream()
                        .filter(departs::containsKey)
                        .map(departs::get)
                        .map(DepartmentBO::getManagerName)
                        .filter(Objects::nonNull)
                        .filter(v -> !v.trim().isEmpty())
                        .distinct()
                        .collect(Collectors.toList());

        return String.join(glue, values);
    }

    // 辅助方法：将部门ID参数转换为List<Integer>
    private List<Long> parseDepartIds(Object departIds) {
        if (departIds == null) {
            return Collections.emptyList();
        }
        if (departIds instanceof Collection) {
            return ((Collection<?>) departIds)
                    .stream()
                            .map(Object::toString)
                            .filter(s -> !s.trim().isEmpty())
                            .map(Long::parseLong)
                            .collect(Collectors.toList());
        }
        if (departIds instanceof String str) {
            if (str.trim().isEmpty()) {
                return Collections.emptyList();
            }
            return Arrays.stream(str.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
        }
        // 假设是单个数字
        try {
            return Collections.singletonList(Long.parseLong(departIds.toString()));
        } catch (NumberFormatException e) {
            return Collections.emptyList();
        }
    }

    /** 保存角色主体关联（批量） */
    private void saveRoleSubjects(SaveRoleUserReq request, Long roleId) {
        if (request.getSubjectList() == null || request.getSubjectList().isEmpty()) {
            return;
        }

        java.time.LocalDate today = java.time.LocalDate.now();
        List<SysRoleSubject> subjectsToInsert = new ArrayList<>();
        for (RoleSubjectReq subjectReq : request.getSubjectList()) {
            SysRoleSubject subject = new SysRoleSubject();
            subject.setRoleId(roleId);
            subject.setSubjectId(subjectReq.getSubjectId());
            subject.setEffectiveStartDate(subjectReq.getEffectiveStartDate());
            subject.setEffectiveEndDate(subjectReq.getEffectiveEndDate());
            subject.setEffectiveType(subjectReq.getEffectiveType());
            // 根据时间计算初始状态
            subject.setStatus(
                    calculateCurrentStatus(
                            subjectReq.getEffectiveType(),
                            subjectReq.getEffectiveStartDate(),
                            subjectReq.getEffectiveEndDate(),
                            today));
            subjectsToInsert.add(subject);
        }

        if (!subjectsToInsert.isEmpty()) {
            sysRoleSubjectMapper.insert(subjectsToInsert);
        }
    }

    /** 更新时，是否对已有用户记录进行覆盖（时间范围改变） */
    private boolean hasExistingUserTimeChanged(
            Long roleId, Set<Long> userIds, SaveRoleUserReq request) {
        List<SysRoleUser> existing =
                sysRoleUserMapper.selectList(
                        new LambdaQueryWrapper<SysRoleUser>()
                                .eq(SysRoleUser::getRoleId, roleId)
                                .in(SysRoleUser::getUserId, userIds)
                                .eq(SysRoleUser::getStatus, 0));

        if (existing.isEmpty()) {
            return false;
        }

        // 构建 userId -> RoleUserReq 的映射
        Map<Long, RoleUserReq> userReqMap = new HashMap<>();
        if (request.getUserInsideList() != null) {
            for (RoleUserReq userReq : request.getUserInsideList()) {
                if (userReq.getUserId() != null) {
                    userReqMap.put(Long.parseLong(userReq.getUserId()), userReq);
                }
            }
        }
        if (request.getUserExternalList() != null) {
            for (RoleUserReq userReq : request.getUserExternalList()) {
                if (userReq.getUserId() != null) {
                    userReqMap.put(Long.parseLong(userReq.getUserId()), userReq);
                }
            }
        }

        for (SysRoleUser ru : existing) {
            RoleUserReq userReq = userReqMap.get(ru.getUserId());
            if (userReq == null) {
                continue;
            }
            boolean typeChanged =
                    !Objects.equals(ru.getEffectiveType(), userReq.getEffectiveType());
            boolean startChanged =
                    !Objects.equals(ru.getEffectiveStartDate(), userReq.getEffectiveStartDate());
            boolean endChanged =
                    !Objects.equals(ru.getEffectiveEndDate(), userReq.getEffectiveEndDate());
            if (typeChanged || startChanged || endChanged) {
                return true;
            }
        }
        return false;
    }

    /** 当前角色中是否已有员工设置过自定义时间 */
    private boolean hasCustomTimeInRole(Long roleId) {
        boolean userCustom =
                filterValidRoleUsers(
                                sysRoleUserMapper.selectList(
                                        new LambdaQueryWrapper<SysRoleUser>()
                                                .eq(SysRoleUser::getRoleId, roleId)
                                                .eq(SysRoleUser::getStatus, 0)))
                        .stream()
                        .anyMatch(
                                ru ->
                                        EffectiveTypeEnum.CUSTOM
                                                .getCode()
                                                .equals(ru.getEffectiveType()));

        boolean subjectCustom =
                filterValidRoleSubjects(
                                sysRoleSubjectMapper.selectList(
                                        new LambdaQueryWrapper<SysRoleSubject>()
                                                .eq(SysRoleSubject::getRoleId, roleId)
                                                .eq(SysRoleSubject::getStatus, 0)))
                        .stream()
                        .anyMatch(
                                s ->
                                        EffectiveTypeEnum.CUSTOM
                                                .getCode()
                                                .equals(s.getEffectiveType()));

        return userCustom || subjectCustom;
    }

    /** 校验：更新时，授权主体后，角色是否已经单独关联了属于该主体的员工 返回这些员工的名称列表，用于提示"是否覆盖" */
    private List<String> checkRoleHasSubjectUsers(Long roleId, Long subjectId) {
        // 查询该角色下单独关联的用户（只查有效且在时效内的）
        List<SysRoleUser> roleUsers =
                filterValidRoleUsers(
                        sysRoleUserMapper.selectList(
                                new LambdaQueryWrapper<SysRoleUser>()
                                        .eq(SysRoleUser::getRoleId, roleId)
                                        .eq(SysRoleUser::getStatus, 0)));

        if (roleUsers.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> subjectUserIds =
                roleUsers.stream()
                        .filter(roleUser -> Objects.equals(roleUser.getSubjectId(), subjectId))
                        .map(SysRoleUser::getUserId)
                        .toList();
        // 查询这些用户中属于指定主体的用户
        List<SysUser> users = new ArrayList<>();
        if (CollUtil.isNotEmpty(subjectUserIds)) {
            users =
                    sysUserMapper.selectList(
                            new LambdaQueryWrapper<SysUser>()
                                    .in(SysUser::getUserId, subjectUserIds)
                                    .eq(SysUser::getDeleted, 0));
        }

        return users.stream().map(SysUser::getUserName).toList();
    }

    /** 获取用户中，之前已经和任何角色绑定的用户名称列表 */
    private List<String> getUsersBoundToAnyRole(Set<Long> userIds) {
        // 查询这些用户中，已经和任何角色绑定过的用户
        List<SysRoleUser> boundUsers =
                sysRoleUserMapper.selectList(
                        new LambdaQueryWrapper<SysRoleUser>().in(SysRoleUser::getUserId, userIds));

        if (boundUsers.isEmpty()) {
            return Collections.emptyList();
        }

        // 获取已绑定的用户ID
        Set<Long> boundUserIds =
                boundUsers.stream().map(SysRoleUser::getUserId).collect(Collectors.toSet());

        // 查询这些用户的名称
        List<SysUser> users =
                sysUserMapper.selectList(
                        Wrappers.<SysUser>lambdaQuery()
                                .in(SysUser::getUserId, boundUserIds)
                                .eq(SysUser::getDeleted, 0));
        return users.stream().map(SysUser::getUserName).toList();
    }

    /** 自动判断有效期类型：对每个 RoleUserReq 和 RoleSubjectReq 单独判断 */
    private void autoDetectEffectiveType(SaveRoleUserReq request) {
        if (request.getUserInsideList() != null) {
            for (RoleUserReq userReq : request.getUserInsideList()) {
                if (userReq.getEffectiveType() == null) {
                    if (userReq.getEffectiveStartDate() != null
                            || userReq.getEffectiveEndDate() != null) {
                        userReq.setEffectiveType(EffectiveTypeEnum.CUSTOM.getCode());
                    } else {
                        userReq.setEffectiveType(EffectiveTypeEnum.PERMANENT.getCode());
                    }
                }
            }
        }
        if (request.getUserExternalList() != null) {
            for (RoleUserReq userReq : request.getUserExternalList()) {
                if (userReq.getEffectiveType() == null) {
                    if (userReq.getEffectiveStartDate() != null
                            || userReq.getEffectiveEndDate() != null) {
                        userReq.setEffectiveType(EffectiveTypeEnum.CUSTOM.getCode());
                    } else {
                        userReq.setEffectiveType(EffectiveTypeEnum.PERMANENT.getCode());
                    }
                }
            }
        }
        if (request.getSubjectList() != null) {
            for (RoleSubjectReq subjectReq : request.getSubjectList()) {
                if (subjectReq.getEffectiveType() == null) {
                    if (subjectReq.getEffectiveStartDate() != null
                            || subjectReq.getEffectiveEndDate() != null) {
                        subjectReq.setEffectiveType(EffectiveTypeEnum.CUSTOM.getCode());
                    } else {
                        subjectReq.setEffectiveType(EffectiveTypeEnum.PERMANENT.getCode());
                    }
                }
            }
        }
    }

    /** 收集所有用户ID */
    private Set<Long> collectUserIds(SaveRoleUserReq request) {
        Set<Long> allUserIds = new HashSet<>();
        if (request.getUserInsideList() != null) {
            for (RoleUserReq userReq : request.getUserInsideList()) {
                if (userReq.getUserId() != null) {
                    allUserIds.add(Long.parseLong(userReq.getUserId()));
                }
            }
        }
        if (request.getUserExternalList() != null) {
            for (RoleUserReq userReq : request.getUserExternalList()) {
                if (userReq.getUserId() != null) {
                    allUserIds.add(Long.parseLong(userReq.getUserId()));
                }
            }
        }
        return allUserIds;
    }

    /** 校验：授权主体时，是否有员工已单独配置在该角色中 通过 user 表查询主体下用户，与前端传入 userId 做对比 */
    private List<String> checkSubjectUserConflict(List<UserBO> subjectUsers, Set<Long> userIds) {
        Set<Long> subjectUserIds =
                subjectUsers.stream().map(UserBO::getId).collect(Collectors.toSet());

        Set<Long> conflictIds = new HashSet<>(userIds);
        conflictIds.retainAll(subjectUserIds);

        if (conflictIds.isEmpty()) {
            return Collections.emptyList();
        }

        return subjectUsers.stream()
                .filter(user -> conflictIds.contains(user.getId()))
                .map(UserBO::getUserName)
                .toList();
    }

    /**
     * 获取人员架构树（仅包含 sys_user 表中存在的用户）
     *
     * @param userType 用户类型 0=全部 1=内部 2=外部
     * @return 人员架构树
     */
    public List<UserTreeNodeResp> getUserTreeListForSearch(Integer userType) {
        // 1. 通过 DepartmentApi 获取部门列表
        List<DepartmentBO> departments = departmentApi.getDepartmentList();
        if (departments.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 查询 sys_user 表中存在的用户
        List<SysUser> sysUsers =
                sysUserMapper.selectList(
                        Wrappers.<SysUser>lambdaQuery()
                                .eq(
                                        userType != null && userType != 0,
                                        SysUser::getUserType,
                                        userType)
                                .eq(SysUser::getSubjectId, AppContext.getSubjectId())
                                .eq(SysUser::getDeleted, 0));

        // 3. 将用户按部门分类
        Map<String, List<UserTreeNodeResp>> deptUsers = new LinkedHashMap<>();
        for (SysUser user : sysUsers) {
            String deptIds = user.getDepartIds();
            if (deptIds == null || deptIds.isEmpty()) {
                continue;
            }
            for (String deptId : deptIds.split(",")) {
                deptId = deptId.trim();
                if (deptId.isEmpty()) {
                    continue;
                }
                UserTreeNodeResp userNode =
                        UserTreeNodeResp.builder()
                                .id("U" + user.getUserId())
                                .sysId(user.getUserId())
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

        // 4. 构建部门节点并挂载用户
        List<UserTreeNodeResp> departListWithUsers = new ArrayList<>();
        for (DepartmentBO dept : departments) {
            String deptKey = "D" + dept.getId();
            List<UserTreeNodeResp> users = deptUsers.get(deptKey);
            // 只保留有用户的部门
            if (users != null && !users.isEmpty()) {
                String pid =
                        (dept.getPid() != null && dept.getPid() != 0) ? "D" + dept.getPid() : "D0";
                UserTreeNodeResp node =
                        UserTreeNodeResp.builder()
                                .id(deptKey)
                                .label(dept.getName())
                                .pid(pid)
                                .type(dept.getType() != null ? dept.getType() : 1)
                                .children(new ArrayList<>(users))
                                .build();
                departListWithUsers.add(node);
            }
        }

        // 5. 构建嵌套树
        return buildNestedTree(departListWithUsers, "D0");
    }

    /** 构建嵌套树结构 */
    private List<UserTreeNodeResp> buildNestedTree(List<UserTreeNodeResp> items, String rootPid) {
        Map<String, UserTreeNodeResp> nodeMap = new LinkedHashMap<>();
        for (UserTreeNodeResp item : items) {
            nodeMap.put(item.getId(), item);
        }

        List<UserTreeNodeResp> tree = new ArrayList<>();
        for (UserTreeNodeResp item : items) {
            String pid = item.getPid();
            UserTreeNodeResp parent = nodeMap.get(pid);
            boolean isRoot = (pid == null || pid.equals(rootPid) || parent == null);
            if (isRoot) {
                tree.add(item);
            } else {
                List<UserTreeNodeResp> children = parent.getChildren();
                if (children == null) {
                    children = new ArrayList<>();
                    parent.setChildren(children);
                }
                children.add(item);
            }
        }
        return tree;
    }

    /**
     * 查询用户切换所需的数据
     *
     * @param userName 用户名称（模糊查询）
     * @return 用户切换数据
     */
    @Override
    public UserSwitchDataResp getUserSwitchData(String userName) {
        java.time.LocalDate today = java.time.LocalDate.now();
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();

        // 1. 根据请求头中的主体ID和项目编码查询角色列表
        List<SysRole> roles =
                sysRoleMapper.selectList(
                        Wrappers.<SysRole>lambdaQuery()
                                .eq(SysRole::getSubjectId, subjectId)
                                .eq(SysRole::getProjectNo, projectNo)
                                .select(SysRole::getId));

        if (roles.isEmpty()) {
            return UserSwitchDataResp.builder()
                    .sysUsers(Collections.emptyList())
                    .subjectIds(Collections.emptyList())
                    .build();
        }

        List<Long> roleIds = roles.stream().map(SysRole::getId).toList();

        // 2. 通过角色用户关联表查询用户ID（有效期内）
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

        // 3. 通过角色主体关联表查询主体ID（有效期内）
        List<Long> subjectIds =
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

        // 4. 查询 sys_user 表的用户信息（角色直接绑定用户 + 主体下用户；角色保存时已同步主体下用户到 sys_user）
        boolean hasUserNameFilter = userName != null && !userName.trim().isEmpty();
        Map<Long, SysUser> sysUserMap = new LinkedHashMap<>();
        if (!userIds.isEmpty()) {
            sysUserMapper
                    .selectList(
                            Wrappers.<SysUser>lambdaQuery()
                                    .in(SysUser::getUserId, userIds)
                                    .eq(SysUser::getDeleted, Boolean.FALSE)
                                    .like(hasUserNameFilter, SysUser::getUserName, userName))
                    .forEach(u -> sysUserMap.putIfAbsent(u.getUserId(), u));
        }
        if (!subjectIds.isEmpty()) {
            sysUserMapper
                    .selectList(
                            Wrappers.<SysUser>lambdaQuery()
                                    .in(SysUser::getSubjectId, subjectIds)
                                    .eq(SysUser::getDeleted, Boolean.FALSE)
                                    .like(hasUserNameFilter, SysUser::getUserName, userName))
                    .forEach(u -> sysUserMap.putIfAbsent(u.getUserId(), u));
        }
        List<SysUser> sysUsers = new ArrayList<>(sysUserMap.values());

        // 5. 转换为响应对象
        List<UserSwitchDataResp.SysUserInfo> userInfoList =
                sysUsers.stream()
                        .map(
                                user ->
                                        UserSwitchDataResp.SysUserInfo.builder()
                                                .id(user.getId())
                                                .userId(user.getUserId())
                                                .userType(user.getUserType())
                                                .userName(user.getUserName())
                                                .workNumber(user.getWorkNumber())
                                                .userAvatar(user.getUserAvatar())
                                                .subjectId(user.getSubjectId())
                                                .sex(user.getSex())
                                                .departIds(user.getDepartIds())
                                                .departName(user.getDepartName())
                                                .build())
                        .toList();

        return UserSwitchDataResp.builder().sysUsers(userInfoList).subjectIds(subjectIds).build();
    }

    /** 将 SysRoleUser 列表转换为 RoleUserReq 列表 */
    private List<RoleUserReq> convertToRoleUserReqs(
            List<SysRoleUser> roleUsers, Map<Long, UserBO> hrUserMap) {
        if (roleUsers == null || roleUsers.isEmpty()) {
            return Collections.emptyList();
        }

        return roleUsers.stream()
                .map(
                        ru -> {
                            RoleUserReq req = new RoleUserReq();
                            req.setUserId(String.valueOf(ru.getUserId()));
                            req.setUserType(ru.getUserType());
                            req.setEffectiveType(ru.getEffectiveType());
                            req.setEffectiveStartDate(ru.getEffectiveStartDate());
                            req.setEffectiveEndDate(ru.getEffectiveEndDate());

                            // 从 hrUserMap 获取用户名
                            UserBO user = hrUserMap.get(ru.getUserId());
                            if (user != null) {
                                req.setUserName(user.getUserName());
                            }

                            return req;
                        })
                .toList();
    }

    /** 将 SysRoleSubject 列表转换为 RoleSubjectReq 列表 */
    private List<RoleSubjectReq> convertToRoleSubjectReqs(List<SysRoleSubject> roleSubjects) {
        if (roleSubjects == null || roleSubjects.isEmpty()) {
            return Collections.emptyList();
        }

        return roleSubjects.stream()
                .map(
                        rs -> {
                            RoleSubjectReq req = new RoleSubjectReq();
                            req.setSubjectId(rs.getSubjectId());
                            req.setEffectiveType(rs.getEffectiveType());
                            req.setEffectiveStartDate(rs.getEffectiveStartDate());
                            req.setEffectiveEndDate(rs.getEffectiveEndDate());
                            return req;
                        })
                .toList();
    }
}
