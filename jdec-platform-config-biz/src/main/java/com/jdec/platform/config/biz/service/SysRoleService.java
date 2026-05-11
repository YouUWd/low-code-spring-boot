package com.jdec.platform.config.biz.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jdec.platform.config.api.SysRoleApi;
import com.jdec.platform.config.api.dto.request.QueryRoleConfigPageReq;
import com.jdec.platform.config.api.dto.request.SaveRoleUserReq;
import com.jdec.platform.config.api.dto.response.*;
import com.jdec.platform.config.biz.entity.SysRole;
import com.jdec.platform.config.biz.entity.SysRoleSubject;
import com.jdec.platform.config.biz.entity.SysRoleUser;
import com.jdec.platform.config.biz.entity.SysUser;
import com.jdec.platform.config.biz.mapper.*;
import com.jdec.platform.hr.api.DepartmentApi;
import com.jdec.platform.hr.api.SubjectApi;
import com.jdec.platform.hr.api.UserApi;
import com.jdec.platform.hr.api.bo.DepartmentBO;
import com.jdec.platform.hr.api.bo.SubjectBO;
import com.jdec.platform.hr.api.bo.UserBO;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.enums.EffectiveTypeEnum;
import com.jdec.platform.shared.enums.EmployedStatusEnum;
import com.jdec.platform.shared.enums.UserStatusFlagEnum;
import com.jdec.platform.shared.enums.UserTypeEnum;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.model.PageResult;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 角色 Service 实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysRoleService implements SysRoleApi {
    private final SysRoleMapper sysRoleMapper;
    private final SysRoleUserMapper sysRoleUserMapper;
    private final SysRoleSubjectMapper sysRoleSubjectMapper;
    private final SysUserMapper sysUserMapper;
    private final UserApi userApi;
    private final SubjectApi subjectApi;
    private final DepartmentApi departmentApi;

    @Override
    public Long getUserRoleCount(Long id, Long subjectId, String projectNo) {
        Long count =
                sysRoleUserMapper.selectCount(
                        Wrappers.<SysRoleUser>lambdaQuery()
                                .eq(SysRoleUser::getUserId, id)
                                .eq(SysRoleUser::getProjectNo, projectNo));
        Long count1 =
                sysRoleSubjectMapper.selectCount(
                        Wrappers.<SysRoleSubject>lambdaQuery()
                                .eq(SysRoleSubject::getSubjectId, subjectId)
                                .eq(SysRoleSubject::getProjectNo, projectNo));
        return count + count1;
    }

    @Override
    public CheckRoleUserResp checkRoleUser(SaveRoleUserReq request) {
        autoDetectEffectiveType(request);

        CheckRoleUserResp resp = new CheckRoleUserResp();
        resp.setNeedConfirm(false);

        Long roleId = request.getId();
        boolean isUpdate = roleId != null;
        Set<Long> allUserIds = collectUserIds(request);

        // ========== 校验1：选择主体后，再选择该主体下的员工 ==========
        // 新增和更新都得校验
        if (request.getSubjectId() != null && !allUserIds.isEmpty()) {
            // 获取冲突的用户（主体下的用户）
            List<String> conflictNames =
                    checkSubjectUserConflict(request.getSubjectId(), allUserIds);
            if (!conflictNames.isEmpty()) {
                if (isUpdate) {
                    // 更新时：检查冲突用户是否和任何角色绑定过
                    List<String> previouslyBound = getUsersBoundToAnyRole(allUserIds);
                    if (previouslyBound.isEmpty()) {
                        // 之前都没有绑定过任何角色，不可强制保存
                        resp.setNeedConfirm(true);
                        resp.setConfirmType(3);
                        resp.setMessage("\"" + String.join("、", conflictNames) + "\"已在授权主体中，请重新设置");
                        return resp;
                    }
                    // 有之前绑定过任何角色的员工，可强制保存（继续执行后续校验）
                } else {
                    // 新增时：之前不可能有绑定，不可强制保存
                    resp.setNeedConfirm(true);
                    resp.setConfirmType(3);
                    resp.setMessage("\"" + String.join("、", conflictNames) + "\"已在授权主体中，请重新设置");
                    return resp;
                }
            }
        }

        // 校验4：当前选定的角色中绑定的用户是否在别的角色中已经设置过自定义时间（新增和更新都需要）
        if (!allUserIds.isEmpty() && hasCustomTimeInOtherRoles(roleId, allUserIds)) {
            resp.setNeedConfirm(true);
            resp.setConfirmType(1);
            resp.setMessage("该角色中存在员工设置过权限时间，是否覆盖？");
            return resp;
        }

        if (roleId == null) {
            return resp;
        }

        // ========== 校验2：更新时，授权主体后，是否需要覆盖已单独关联到该角色的主体下员工（可强制保存） ==========
        // 只有更新时需要校验，可强制保存
        if (request.getSubjectId() != null) {
            List<String> overlapNames = checkRoleHasSubjectUsers(roleId, request.getSubjectId());
            if (!overlapNames.isEmpty()) {
                resp.setNeedConfirm(true);
                resp.setConfirmType(2); // 可强制保存，覆盖已关联的员工
                resp.setMessage("\"" + String.join("、", overlapNames) + "\"已在该角色中，是否覆盖？");
                resp.setConflictNames(overlapNames);
                return resp;
            }
        }

        // 校验3：该角色中是否已有员工设置过自定义时间
        if (hasCustomTimeInRole(roleId)) {
            resp.setNeedConfirm(true);
            resp.setConfirmType(1);
            resp.setMessage("该角色中存在员工设置过权限时间，是否覆盖？");
            return resp;
        }

        // 校验5：更新时，是否对已有用户记录进行覆盖（时间改变）
        if (!allUserIds.isEmpty() && hasExistingUserTimeChanged(roleId, allUserIds, request)) {
            resp.setNeedConfirm(true);
            resp.setConfirmType(1);
            resp.setMessage("该角色中存在员工设置过权限时间，是否覆盖？");
            return resp;
        }

        return resp;
    }

    @Override
    public void saveRoleUser(SaveRoleUserReq request) {
        autoDetectEffectiveType(request);

        Long roleId = request.getId();
        boolean isCreate = roleId == null;
        Set<Long> allUserIds = collectUserIds(request);

        // ========== 校验1：选择主体后，再选择该主体下的员工 ==========
        // 新增和更新都得校验
        if (request.getSubjectId() != null && !allUserIds.isEmpty()) {
            List<String> conflictNames =
                    checkSubjectUserConflict(request.getSubjectId(), allUserIds);
            if (!conflictNames.isEmpty()) {
                if (isCreate) {
                    // 新增时：之前不可能有绑定，不可强制保存
                    throw new BusinessException(
                            "\"" + String.join("、", conflictNames) + "\"已在授权主体中，请重新设置");
                } else {
                    // 更新时：检查冲突用户是否和任何角色绑定过
                    List<String> previouslyBound = getUsersBoundToAnyRole(allUserIds);
                    if (previouslyBound.isEmpty()) {
                        // 之前都没有绑定过任何角色，不可强制保存
                        throw new BusinessException(
                                "\"" + String.join("、", conflictNames) + "\"已在授权主体中，请重新设置");
                    }
                    // 有之前绑定过任何角色的员工，可强制保存（跳过校验，继续执行）
                }
            }
        }

        // 校验4：当前选定的角色中绑定的用户是否在别的角色中已经设置过自定义时间（新增和更新都需要）
        if (!Boolean.TRUE.equals(request.getForce())
                && !allUserIds.isEmpty()
                && hasCustomTimeInOtherRoles(roleId, allUserIds)) {
            throw new BusinessException("该角色中存在员工设置过权限时间，是否覆盖？");
        }

        // ========== 更新逻辑 ==========
        // 记录更新前该角色关联的用户ID（用于后续清理孤立用户）
        Set<Long> previousUserIds = new HashSet<>();
        if (!isCreate) {
            // 1. 获取该角色之前关联的用户ID（只获取单独用户关联，不包括主体关联的用户）
            List<SysRoleUser> previousRoleUsers =
                    sysRoleUserMapper.selectList(
                            new LambdaQueryWrapper<SysRoleUser>()
                                    .eq(SysRoleUser::getRoleId, roleId)
                                    .eq(SysRoleUser::getProjectNo, AppContext.getProjectNo()));
            previousUserIds =
                    previousRoleUsers.stream()
                            .map(SysRoleUser::getUserId)
                            .collect(Collectors.toSet());

            // ========== 校验2：更新时，授权主体后，是否需要覆盖已单独关联到该角色的主体下员工 ==========
            // 可强制保存，force=true时进行覆盖操作
            if (request.getSubjectId() != null && !Boolean.TRUE.equals(request.getForce())) {
                List<String> overlapNames =
                        checkRoleHasSubjectUsers(roleId, request.getSubjectId());
                if (!overlapNames.isEmpty()) {
                    throw new BusinessException(
                            "\"" + String.join("、", overlapNames) + "\"已在该角色中，是否覆盖？");
                }
            }
        }

        // ========== 删除旧关联 ==========
        if (!isCreate) {
            // 删除该角色之前的所有关联数据（只删除关联表）
            // 删除角色-主体关联
            sysRoleSubjectMapper.delete(
                    new LambdaQueryWrapper<SysRoleSubject>()
                            .eq(SysRoleSubject::getRoleId, roleId)
                            .eq(SysRoleSubject::getProjectNo, AppContext.getProjectNo()));
            // 删除角色-用户关联
            sysRoleUserMapper.delete(
                    new LambdaQueryWrapper<SysRoleUser>()
                            .eq(SysRoleUser::getRoleId, roleId)
                            .eq(SysRoleUser::getProjectNo, AppContext.getProjectNo()));
        }

        // 保存角色
        if (isCreate) {
            SysRole role = new SysRole();
            role.setRoleName(request.getRoleName());
            role.setRoleSlug(request.getRoleName());
            role.setProjectNo(AppContext.getProjectNo());
            sysRoleMapper.insert(role);
            roleId = role.getId();
            request.setId(roleId);
        } else {
            SysRole role = sysRoleMapper.selectById(roleId);
            if (role == null) {
                throw new IllegalArgumentException("角色不存在");
            }
            role.setRoleName(request.getRoleName());
            sysRoleMapper.updateById(role);
        }

        // 保存新的主体关联
        if (request.getSubjectId() != null) {
            saveRoleSubject(request, roleId);
        }

        // 保存新的单独用户关联（过滤掉属于该主体的用户）
        if (!allUserIds.isEmpty()) {
            batchEnsureUsersExist(allUserIds, request.getUserInsideIds());
            saveUserRelations(request, roleId);
        }

        // 清理孤立数据：检查之前关联的用户是否现在没有任何角色绑定，删除这些用户的sys_user记录
        if (!previousUserIds.isEmpty()) {
            // 计算哪些旧用户不在新关联中
            Set<Long> currentUserIds = collectUserIds(request);
            Set<Long> removedUserIds = new HashSet<>(previousUserIds);
            removedUserIds.removeAll(currentUserIds);

            // 对每个被移除的用户，检查是否还有其他角色绑定
            if (!removedUserIds.isEmpty()) {
                cleanupOrphanedUsers(removedUserIds);
            }
        }
    }

    @Override
    public RoleUserTimeResp getRoleUserTime(String userId, Long roleId) {
        SysRoleUser user =
                sysRoleUserMapper.selectOne(
                        Wrappers.<SysRoleUser>lambdaQuery()
                                .eq(SysRoleUser::getUserId, removeUPrefix(userId))
                                .eq(SysRoleUser::getRoleId, roleId)
                                .eq(SysRoleUser::getProjectNo, AppContext.getProjectNo()));
        RoleUserTimeResp resp = new RoleUserTimeResp();
        resp.setUserId(userId);
        if (user != null) {
            resp.setEffectiveStartDate(user.getEffectiveStartDate());
            resp.setEffectiveEndDate(user.getEffectiveEndDate());
        } else {
            SysUser sysUser =
                    sysUserMapper.selectOne(
                            Wrappers.<SysUser>lambdaQuery()
                                    .eq(SysUser::getUserId, removeUPrefix(userId)));
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
        Page<SysRole> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRole::getProjectNo, AppContext.getProjectNo());
        IPage<SysRole> rolePage = sysRoleMapper.selectPage(page, wrapper);

        List<RoleConfigPageResp> respList =
                rolePage.getRecords().stream()
                        .map(
                                role -> {
                                    RoleConfigPageResp resp = new RoleConfigPageResp();
                                    resp.setRoleId(role.getId());
                                    resp.setRoleName(role.getRoleName());

                                    // 查询该角色的用户关联
                                    List<SysRoleUser> roleUsers =
                                            sysRoleUserMapper.selectList(
                                                    Wrappers.<SysRoleUser>lambdaQuery()
                                                            .eq(
                                                                    SysRoleUser::getRoleId,
                                                                    role.getId())
                                                            .eq(
                                                                    SysRoleUser::getProjectNo,
                                                                    AppContext.getProjectNo()));

                                    // 填充用户列表
                                    List<RoleUserResp> userList = buildUserList(roleUsers);
                                    resp.setUserList(userList);

                                    // 查询该角色的主体关联
                                    List<SysRoleSubject> subjects =
                                            sysRoleSubjectMapper.selectList(
                                                    Wrappers.<SysRoleSubject>lambdaQuery()
                                                            .eq(
                                                                    SysRoleSubject::getRoleId,
                                                                    role.getId())
                                                            .eq(
                                                                    SysRoleSubject::getProjectNo,
                                                                    AppContext.getProjectNo()));
                                    // 填充主体列表
                                    List<RoleSubjectResp> subjectList = buildSubjectList(subjects);
                                    resp.setSubjectList(subjectList);

                                    // 取用户关联或主体关联的时效信息（优先取用户关联的）
                                    if (!roleUsers.isEmpty()) {
                                        SysRoleUser firstUser = roleUsers.getFirst();
                                        resp.setEffectiveType(firstUser.getEffectiveType());
                                        resp.setEffectiveStartDate(
                                                firstUser.getEffectiveStartDate());
                                        resp.setEffectiveEndDate(firstUser.getEffectiveEndDate());
                                    } else if (!subjects.isEmpty()) {
                                        SysRoleSubject firstSubject = subjects.getFirst();
                                        resp.setEffectiveType(firstSubject.getEffectiveType());
                                        resp.setEffectiveStartDate(
                                                firstSubject.getEffectiveStartDate());
                                        resp.setEffectiveEndDate(
                                                firstSubject.getEffectiveEndDate());
                                    } else {
                                        resp.setEffectiveType(
                                                EffectiveTypeEnum.PERMANENT.getCode());
                                    }
                                    return resp;
                                })
                        .collect(Collectors.toList());

        return PageResult.of(
                query.getPageNum(), query.getPageSize(), rolePage.getTotal(), respList);
    }

    @Override
    public RoleConfigDetailResp getRoleConfigDetail(Long roleId, Integer userType) {
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        RoleConfigDetailResp resp = new RoleConfigDetailResp();
        resp.setId(role.getId());
        resp.setRoleName(role.getRoleName());
        // 查询角色主体关联表
        List<SysRoleSubject> subjectList =
                sysRoleSubjectMapper.selectList(
                        Wrappers.<SysRoleSubject>lambdaQuery()
                                .eq(SysRoleSubject::getRoleId, roleId)
                                .eq(SysRoleSubject::getProjectNo, AppContext.getProjectNo()));
        if (CollUtil.isNotEmpty(subjectList)) {
            List<Long> subjectIds = subjectList.stream().map(SysRoleSubject::getSubjectId).toList();
            resp.setEffectiveStartDate(subjectList.getFirst().getEffectiveStartDate());
            resp.setEffectiveEndDate(subjectList.getFirst().getEffectiveEndDate());
            resp.setSubjectIds(subjectIds);
        }
        // 查询角色用户关联表
        List<SysRoleUser> userList =
                sysRoleUserMapper.selectList(
                        Wrappers.<SysRoleUser>lambdaQuery()
                                .eq(SysRoleUser::getRoleId, roleId)
                                .eq(SysRoleUser::getProjectNo, AppContext.getProjectNo()));
        if (CollUtil.isNotEmpty(userList)) {
            List<Long> userIds = userList.stream().map(SysRoleUser::getUserId).toList();
            List<SysUser> users =
                    sysUserMapper.selectList(
                            Wrappers.<SysUser>lambdaQuery()
                                    .in(SysUser::getUserId, userIds)
                                    .eq(userType != null, SysUser::getUserType, userType));
            List<String> userIdList = users.stream().map(user -> "U" + user.getUserId()).toList();
            resp.setUserIds(userIdList);
            resp.setEffectiveStartDate(userList.getFirst().getEffectiveStartDate());
            resp.setEffectiveEndDate(userList.getFirst().getEffectiveEndDate());
        }
        return resp;
    }

    @Override
    public void deleteRole(Long roleId) {
        // 1. 获取该角色关联的所有用户ID（用于后续检查是否需要删除sys_user记录）
        List<SysRoleUser> roleUsers =
                sysRoleUserMapper.selectList(
                        new LambdaQueryWrapper<SysRoleUser>()
                                .eq(SysRoleUser::getRoleId, roleId)
                                .eq(SysRoleUser::getProjectNo, AppContext.getProjectNo()));
        Set<Long> userIds =
                roleUsers.stream().map(SysRoleUser::getUserId).collect(Collectors.toSet());

        // 2. 删除角色主体关联 (sys_role_subject)
        sysRoleSubjectMapper.delete(
                new LambdaQueryWrapper<SysRoleSubject>()
                        .eq(SysRoleSubject::getRoleId, roleId)
                        .eq(SysRoleSubject::getProjectNo, AppContext.getProjectNo()));

        // 3. 删除角色用户关联 (sys_role_user)
        sysRoleUserMapper.delete(
                new LambdaQueryWrapper<SysRoleUser>()
                        .eq(SysRoleUser::getRoleId, roleId)
                        .eq(SysRoleUser::getProjectNo, AppContext.getProjectNo()));

        // 4. 对于每个用户，检查是否还关联其他角色，如果没有则删除sys_user记录
        if (!userIds.isEmpty()) {
            cleanupOrphanedUsers(userIds);
        }

        // 5. 删除角色本身
        sysRoleMapper.deleteById(roleId);
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

    @Override
    public List<RoleListResp> listRole() {
        List<SysRole> sysRoles =
                sysRoleMapper.selectList(
                        Wrappers.<SysRole>lambdaQuery()
                                .eq(SysRole::getProjectNo, AppContext.getProjectNo())
                                .eq(SysRole::getSubjectId, AppContext.getSubjectId()));
        return BeanUtil.copyToList(sysRoles, RoleListResp.class);
    }

    private List<RoleSubjectResp> buildSubjectList(List<SysRoleSubject> subjects) {
        if (subjects.isEmpty()) {
            return Collections.emptyList();
        }
        List<RoleSubjectResp> respList = new ArrayList<>();
        for (SysRoleSubject sysRoleSubject : subjects) {
            RoleSubjectResp vo = new RoleSubjectResp();
            vo.setSubjectId(sysRoleSubject.getSubjectId());
            vo.setSubjectName(sysRoleSubject.getSubjectName());
            respList.add(vo);
        }
        return respList;
    }

    /** 构建用户VO列表 */
    private List<RoleUserResp> buildUserList(List<SysRoleUser> roleUsers) {
        if (roleUsers.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> userIds =
                roleUsers.stream().map(SysRoleUser::getUserId).collect(Collectors.toList());

        // 查询用户信息（内部用户用SysUser，外部用户用User表）
        Map<Long, RoleUserResp> userVOMap = new HashMap<>();

        List<SysUser> sysUsers =
                sysUserMapper.selectList(
                        Wrappers.<SysUser>lambdaQuery().in(SysUser::getUserId, userIds));
        for (SysUser sysUser : sysUsers) {
            RoleUserResp vo = new RoleUserResp();
            vo.setUserName(sysUser.getUserName());
            vo.setUserId("U" + sysUser.getUserId());
            vo.setUserAvatar(sysUser.getUserAvatar());
            vo.setWorkNumber(sysUser.getWorkNumber());
            userVOMap.put(sysUser.getUserId(), vo);
        }
        return userIds.stream()
                .map(userVOMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /** 清理孤立用户：检查用户是否没有任何角色绑定，如果有则删除sys_user记录 */
    private void cleanupOrphanedUsers(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        for (Long userId : userIds) {
            // 检查该用户是否还有其他角色绑定
            boolean hasOtherRole =
                    sysRoleUserMapper.selectCount(
                                    new LambdaQueryWrapper<SysRoleUser>()
                                            .eq(SysRoleUser::getUserId, userId)
                                            .eq(
                                                    SysRoleUser::getProjectNo,
                                                    AppContext.getProjectNo()))
                            > 0;

            if (!hasOtherRole) {
                // 该用户没有任何角色绑定，删除sys_user记录
                sysUserMapper.delete(
                        new LambdaQueryWrapper<SysUser>().eq(SysUser::getUserId, userId));
            }
        }
    }

    /** 保存单独用户授权关系 注意：只添加不属于该主体的用户，属于该主体的用户无需再和角色进行关联 */
    private void saveUserRelations(SaveRoleUserReq request, Long roleId) {
        List<String> insideIdStrs = request.getUserInsideIds();
        List<String> externalIdStrs = request.getUserExternalIds();
        Set<Long> allUserIds = collectUserIds(request);
        Long subjectId = request.getSubjectId();

        if (allUserIds.isEmpty()) {
            return;
        }

        List<SysUser> users =
                sysUserMapper.selectList(
                        Wrappers.<SysUser>lambdaQuery().in(SysUser::getUserId, allUserIds));
        Map<Long, SysUser> userMap =
                users.stream().collect(Collectors.toMap(SysUser::getUserId, u -> u));

        List<SysRoleUser> list = new ArrayList<>();
        if (insideIdStrs != null) {
            for (String idStr : insideIdStrs) {
                Long userId = removeUPrefix(idStr);
                // 过滤：只添加不属于该主体的用户
                if (subjectId != null && isUserInSubject(userId, subjectId, userMap)) {
                    continue;
                }
                list.add(
                        buildRoleUser(
                                request, roleId, userId, UserTypeEnum.INSIDE.getCode(), userMap));
            }
        }
        if (externalIdStrs != null) {
            for (String idStr : externalIdStrs) {
                Long userId = removeUPrefix(idStr);
                list.add(
                        buildRoleUser(
                                request, roleId, userId, UserTypeEnum.EXTERNAL.getCode(), userMap));
            }
        }

        if (!list.isEmpty()) {
            sysRoleUserMapper.insert(list);
        }
    }

    private SysRoleUser buildRoleUser(
            SaveRoleUserReq request,
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
        ru.setEffectiveStartDate(request.getEffectiveStartDate());
        ru.setEffectiveEndDate(request.getEffectiveEndDate());
        ru.setEffectiveType(request.getEffectiveType());
        ru.setProjectNo(AppContext.getProjectNo());
        return ru;
    }

    /** 判断用户是否属于指定主体 */
    private boolean isUserInSubject(Long userId, Long subjectId, Map<Long, SysUser> userMap) {
        SysUser user = userMap.get(userId);
        return user != null && user.getSubjectId() != null && user.getSubjectId().equals(subjectId);
    }

    /** 批量检查并创建不存在的用户 */
    private void batchEnsureUsersExist(Set<Long> allUserIds, List<String> insideIdStrs) {
        List<SysUser> existingUsers = sysUserMapper.selectByIds(allUserIds);
        Set<Long> existingUserIds =
                existingUsers.stream().map(SysUser::getId).collect(Collectors.toSet());

        // 批量查询User表，构建userId -> User的映射
        Map<Long, UserBO> userMap = new HashMap<>();
        if (!allUserIds.isEmpty()) {
            List<UserBO> userList = userApi.listByIds(allUserIds);
            for (UserBO u : userList) {
                userMap.put(u.getId(), u);
            }
        }

        Set<Long> insideIds = new HashSet<>();
        if (insideIdStrs != null) {
            for (String id : insideIdStrs) {
                insideIds.add(removeUPrefix(id));
            }
        }
        // 查询部门信息（用于获取负责人）
        List<DepartmentBO> departments = departmentApi.getDepartmentList();
        Map<Long, DepartmentBO> deptMap =
                departments.stream().collect(Collectors.toMap(DepartmentBO::getId, d -> d));
        // 查询主体信息
        List<SubjectBO> subjects = subjectApi.getSubjectList();
        Map<Long, SubjectBO> subjectMap =
                subjects.stream().collect(Collectors.toMap(SubjectBO::getId, d -> d));
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

    /** 保存角色主体关联 */
    private void saveRoleSubject(SaveRoleUserReq request, Long roleId) {
        Long subjectId = request.getSubjectId();
        SysRoleSubject old =
                sysRoleSubjectMapper.selectOne(
                        new LambdaQueryWrapper<SysRoleSubject>()
                                .eq(SysRoleSubject::getRoleId, roleId)
                                .eq(SysRoleSubject::getSubjectId, subjectId)
                                .eq(SysRoleSubject::getProjectNo, AppContext.getProjectNo()));

        SysRoleSubject subject = new SysRoleSubject();
        subject.setRoleId(roleId);
        subject.setSubjectId(subjectId);
        subject.setSubjectName(request.getSubjectName());
        subject.setEffectiveStartDate(request.getEffectiveStartDate());
        subject.setEffectiveEndDate(request.getEffectiveEndDate());
        subject.setEffectiveType(request.getEffectiveType());
        subject.setProjectNo(AppContext.getProjectNo());
        if (old == null) {
            sysRoleSubjectMapper.insert(subject);
        } else {
            subject.setId(old.getId());
            sysRoleSubjectMapper.updateById(subject);
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
                                .eq(SysRoleUser::getProjectNo, AppContext.getProjectNo()));

        if (existing.isEmpty()) {
            return false;
        }

        for (SysRoleUser ru : existing) {
            boolean typeChanged =
                    !Objects.equals(ru.getEffectiveType(), request.getEffectiveType());
            boolean startChanged =
                    !Objects.equals(ru.getEffectiveStartDate(), request.getEffectiveStartDate());
            boolean endChanged =
                    !Objects.equals(ru.getEffectiveEndDate(), request.getEffectiveEndDate());
            if (typeChanged || startChanged || endChanged) {
                return true;
            }
        }
        return false;
    }

    /** 当前角色中是否已有员工设置过自定义时间 */
    private boolean hasCustomTimeInRole(Long roleId) {
        List<SysRoleUser> roleUsers =
                sysRoleUserMapper.selectList(
                        new LambdaQueryWrapper<SysRoleUser>()
                                .eq(SysRoleUser::getRoleId, roleId)
                                .eq(SysRoleUser::getProjectNo, AppContext.getProjectNo()));
        boolean userCustom =
                roleUsers.stream()
                        .anyMatch(
                                ru ->
                                        EffectiveTypeEnum.CUSTOM
                                                .getCode()
                                                .equals(ru.getEffectiveType()));

        List<SysRoleSubject> subjects =
                sysRoleSubjectMapper.selectList(
                        new LambdaQueryWrapper<SysRoleSubject>()
                                .eq(SysRoleSubject::getRoleId, roleId)
                                .eq(SysRoleSubject::getProjectNo, AppContext.getProjectNo()));
        boolean subjectCustom =
                subjects.stream()
                        .anyMatch(
                                s ->
                                        EffectiveTypeEnum.CUSTOM
                                                .getCode()
                                                .equals(s.getEffectiveType()));

        return userCustom || subjectCustom;
    }

    /** 校验：更新时，授权主体后，角色是否已经单独关联了属于该主体的员工 返回这些员工的名称列表，用于提示"是否覆盖" */
    private List<String> checkRoleHasSubjectUsers(Long roleId, Long subjectId) {
        // 查询该角色下单独关联的用户
        List<SysRoleUser> roleUsers =
                sysRoleUserMapper.selectList(
                        new LambdaQueryWrapper<SysRoleUser>()
                                .eq(SysRoleUser::getRoleId, roleId)
                                .eq(SysRoleUser::getProjectNo, AppContext.getProjectNo()));

        if (roleUsers.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> subjectUserIds =
                roleUsers.stream()
                        .filter(roleUser -> Objects.equals(roleUser.getSubjectId(), subjectId))
                        .map(SysRoleUser::getUserId)
                        .toList();
        // 查询这些用户中属于指定主体的用户
        List<SysUser> users =
                sysUserMapper.selectList(
                        new LambdaQueryWrapper<SysUser>().in(SysUser::getUserId, subjectUserIds));
        return users.stream().map(SysUser::getUserName).toList();
    }

    /** 当前选定的角色中绑定的用户是否在别的角色中已经设置过自定义时间 currentRoleId 为 null 时表示新增角色，查询所有角色 */
    private boolean hasCustomTimeInOtherRoles(Long currentRoleId, Set<Long> userIds) {
        LambdaQueryWrapper<SysRoleUser> wrapper =
                new LambdaQueryWrapper<SysRoleUser>()
                        .in(SysRoleUser::getUserId, userIds)
                        .eq(SysRoleUser::getEffectiveType, EffectiveTypeEnum.CUSTOM.getCode())
                        .eq(SysRoleUser::getProjectNo, AppContext.getProjectNo());
        if (currentRoleId != null) {
            wrapper.ne(SysRoleUser::getRoleId, currentRoleId);
        }
        return !sysRoleUserMapper.selectList(wrapper).isEmpty();
    }

    /** 获取用户中，之前已经和任何角色绑定的用户名称列表 */
    private List<String> getUsersBoundToAnyRole(Set<Long> userIds) {
        // 查询这些用户中，已经和任何角色绑定过的用户
        List<SysRoleUser> boundUsers =
                sysRoleUserMapper.selectList(
                        new LambdaQueryWrapper<SysRoleUser>()
                                .in(SysRoleUser::getUserId, userIds)
                                .eq(SysRoleUser::getProjectNo, AppContext.getProjectNo()));

        if (boundUsers.isEmpty()) {
            return Collections.emptyList();
        }

        // 获取已绑定的用户ID
        Set<Long> boundUserIds =
                boundUsers.stream().map(SysRoleUser::getUserId).collect(Collectors.toSet());

        // 查询这些用户的名称
        List<SysUser> users =
                sysUserMapper.selectList(
                        Wrappers.<SysUser>lambdaQuery().in(SysUser::getUserId, boundUserIds));
        return users.stream().map(SysUser::getUserName).toList();
    }

    /** 自动判断有效期类型 */
    private void autoDetectEffectiveType(SaveRoleUserReq request) {
        if (request.getEffectiveStartDate() != null || request.getEffectiveEndDate() != null) {
            request.setEffectiveType(EffectiveTypeEnum.CUSTOM.getCode());
        } else {
            request.setEffectiveType(EffectiveTypeEnum.PERMANENT.getCode());
        }
    }

    /** 收集所有用户ID（去掉U前缀） */
    private Set<Long> collectUserIds(SaveRoleUserReq request) {
        Set<Long> allUserIds = new HashSet<>();
        if (request.getUserInsideIds() != null) {
            for (String id : request.getUserInsideIds()) {
                allUserIds.add(removeUPrefix(id));
            }
        }
        if (request.getUserExternalIds() != null) {
            for (String id : request.getUserExternalIds()) {
                allUserIds.add(removeUPrefix(id));
            }
        }
        return allUserIds;
    }

    /** 去掉ID的U前缀 */
    private Long removeUPrefix(String id) {
        if (id != null && id.startsWith("U")) {
            return Long.parseLong(id.substring(1));
        }
        assert id != null;
        return Long.parseLong(id);
    }

    /** 校验：授权主体时，是否有员工已单独配置在该角色中 通过 user 表查询主体下用户，与前端传入 userId 做对比 */
    private List<String> checkSubjectUserConflict(Long subjectId, Set<Long> userIds) {
        List<UserBO> users = userApi.listBySubjectId(subjectId);
        Set<Long> subjectUserIds = users.stream().map(UserBO::getId).collect(Collectors.toSet());

        Set<Long> conflictIds = new HashSet<>(userIds);
        conflictIds.retainAll(subjectUserIds);

        if (conflictIds.isEmpty()) {
            return Collections.emptyList();
        }

        return users.stream()
                .filter(user -> conflictIds.contains(user.getId()))
                .map(UserBO::getUserName)
                .toList();
    }
}
