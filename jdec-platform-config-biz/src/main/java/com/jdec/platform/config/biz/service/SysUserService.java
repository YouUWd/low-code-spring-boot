package com.jdec.platform.config.biz.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jdec.platform.config.api.SysUserApi;
import com.jdec.platform.config.api.bo.SysUserBO;
import com.jdec.platform.config.api.dto.request.QuerySysUserPageReq;
import com.jdec.platform.config.api.dto.request.UpdateSysUserReq;
import com.jdec.platform.config.api.dto.response.SysUserPageResp;
import com.jdec.platform.config.api.dto.response.UserSearchTreeNodeResp;
import com.jdec.platform.config.biz.entity.SysRole;
import com.jdec.platform.config.biz.entity.SysRoleUser;
import com.jdec.platform.config.biz.entity.SysUser;
import com.jdec.platform.config.biz.mapper.SysRoleMapper;
import com.jdec.platform.config.biz.mapper.SysRoleUserMapper;
import com.jdec.platform.config.biz.mapper.SysUserMapper;
import com.jdec.platform.hr.api.DepartmentApi;
import com.jdec.platform.hr.api.bo.DepartmentBO;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.enums.UserStatusFlagEnum;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.model.PageResult;
import com.jdec.platform.shared.utils.PageResultUtils;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

/** 用户 Service 实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysUserService implements SysUserApi {
    private final SysUserMapper sysUserMapper;
    private final SysRoleUserMapper sysRoleUserMapper;
    private final SysRoleMapper sysRoleMapper;
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

        // 2. 根据角色 ID 列表查询关联的用户 ID
        List<Long> roleIds = roles.stream().map(SysRole::getId).toList();
        List<SysRoleUser> roleUsers =
                sysRoleUserMapper.selectList(
                        Wrappers.<SysRoleUser>lambdaQuery()
                                .in(SysRoleUser::getRoleId, roleIds)
                                .select(SysRoleUser::getUserId));
        if (roleUsers.isEmpty()) {
            return PageResult.of(
                    query.getPageNum(), query.getPageSize(), 0L, Collections.emptyList());
        }

        // 3. 根据用户 ID 列表分页查询用户
        List<Long> userIds = roleUsers.stream().map(SysRoleUser::getUserId).distinct().toList();
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
        // 1. 根据projectNo查询sys_role_user表获取用户id列表
        List<SysRoleUser> roleUsers =
                sysRoleUserMapper.selectList(
                        Wrappers.<SysRoleUser>lambdaQuery()
                                .eq(SysRoleUser::getProjectNo, AppContext.getProjectNo())
                                .select(SysRoleUser::getUserId));
        if (roleUsers.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 根据用户id列表查询sys_user表
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
    public List<UserSearchTreeNodeResp> getUserTreeListForSearch(Integer userType) {
        // userType: 0=全部, 1=内部, 2=外部。当前项目仅支持内部用户树
        if (userType != null && userType == 2) {
            return Collections.emptyList();
        }

        // 2. 获取用户列表
        List<SysRole> sysRoles =
                sysRoleMapper.selectList(
                        new LambdaQueryWrapper<SysRole>()
                                .eq(SysRole::getSubjectId, AppContext.getSubjectId())
                                .eq(SysRole::getProjectNo, AppContext.getProjectNo()));
        List<Long> roleIds = sysRoles.stream().map(SysRole::getId).toList();
        if (CollUtil.isEmpty(roleIds)) {
            return Collections.emptyList();
        }
        List<SysRoleUser> sysRoleUsers =
                sysRoleUserMapper.selectList(
                        new LambdaQueryWrapper<SysRoleUser>().in(SysRoleUser::getRoleId, roleIds));
        List<Long> roleUserIds = sysRoleUsers.stream().map(SysRoleUser::getUserId).toList();
        if (CollUtil.isEmpty(roleUserIds)) {
            return Collections.emptyList();
        }
        List<SysUser> users =
                sysUserMapper.selectList(
                        new LambdaQueryWrapper<SysUser>().in(SysUser::getUserId, roleUserIds));

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

        // 6. 过滤掉没有用户的部门
        return filterEmptyDepartments(tree);
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
}
