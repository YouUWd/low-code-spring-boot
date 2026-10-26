package com.jdec.platform.hr.biz.service;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.hr.api.UserApi;
import com.jdec.platform.hr.api.bo.UserBO;
import com.jdec.platform.hr.api.dto.request.CreateUserReq;
import com.jdec.platform.hr.api.dto.request.UpdateUserReq;
import com.jdec.platform.hr.api.dto.response.UserBasicInfoResp;
import com.jdec.platform.hr.api.dto.response.UserTreeNodeResp;
import com.jdec.platform.hr.biz.entity.Department;
import com.jdec.platform.hr.biz.entity.User;
import com.jdec.platform.hr.biz.mapper.DepartmentMapper;
import com.jdec.platform.hr.biz.mapper.UserMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户服务
 *
 * <p>只读查询方法统一使用 {@code @Transactional(propagation = NOT_SUPPORTED)}：跨库（config/auth → hr）调用时挂起
 * 调用方事务，避免复用调用方已绑定的连接导致数据源路由失效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.HR_MANAGE)
public class UserService implements UserApi {

    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Optional<Integer> getSubjectIdByPhone(String phone) {
        return Optional.ofNullable(
                        userMapper.selectOne(
                                new LambdaQueryWrapper<User>().eq(User::getPhone, phone)))
                .map(User::getSubjectId);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Optional<Integer> getSubjectIdByUserId(Long userId) {
        return Optional.ofNullable(userMapper.selectById(userId)).map(User::getSubjectId);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Optional<String> getWeChatUserIdByPhone(String phone) {
        return Optional.ofNullable(
                        userMapper.selectOne(
                                new LambdaQueryWrapper<User>().eq(User::getPhone, phone)))
                .map(User::getShortName);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Optional<UserBasicInfoResp> getUserBasicInfoByPhone(String phone) {
        return Optional.ofNullable(
                        userMapper.selectOne(
                                new LambdaQueryWrapper<User>().eq(User::getPhone, phone)))
                .map(this::toUserBasicInfo);
    }

    @Override
    @Transactional
    public UserBasicInfoResp createUser(CreateUserReq request) {
        request.validate();
        User user =
                User.builder()
                        .userName(request.getUserName())
                        .nickName(request.getNickName())
                        .userAvatar(request.getUserAvatar())
                        .sex(request.getSex())
                        .subjectId(request.getSubjectId())
                        .shortName(request.getShortName())
                        .workNumber(request.getWorkNumber())
                        .phone(request.getPhone())
                        .departmentId(request.getDepartmentId())
                        .departmentName(request.getDepartmentName())
                        .performanceType(request.getPerformanceType())
                        .userStatus(request.getUserStatus())
                        .jobStatus(request.getJobStatus())
                        .build();
        userMapper.insert(user);
        log.info("用户创建成功: {}", request.getUserName());
        return toUserBasicInfo(user);
    }

    @Override
    @Transactional
    public UserBasicInfoResp updateUser(UpdateUserReq request) {
        request.validate();
        User user =
                User.builder()
                        .id(request.getId())
                        .userName(request.getUserName())
                        .nickName(request.getNickName())
                        .userAvatar(request.getUserAvatar())
                        .sex(request.getSex())
                        .shortName(request.getShortName())
                        .workNumber(request.getWorkNumber())
                        .phone(request.getPhone())
                        .departmentId(request.getDepartmentId())
                        .departmentName(request.getDepartmentName())
                        .performanceType(request.getPerformanceType())
                        .userStatus(request.getUserStatus())
                        .jobStatus(request.getJobStatus())
                        .build();
        userMapper.updateById(user);
        log.info("用户更新成功: {}", request.getId());
        return toUserBasicInfo(userMapper.selectById(request.getId()));
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<UserBO> listByIds(Set<Long> allUserIds) {
        List<User> users =
                userMapper.selectList(
                        Wrappers.<User>lambdaQuery()
                                .in(User::getId, new ArrayList<>(allUserIds))
                                .eq(User::getIsDelete, 1));
        return BeanUtil.copyToList(users, UserBO.class);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UserBO getUserById(Long userId) {
        User user = userMapper.selectById(userId);
        return BeanUtil.copyProperties(user, UserBO.class);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<UserBO> listBySubjectId(Long subjectId) {
        List<User> users =
                userMapper.selectList(
                        Wrappers.<User>lambdaQuery().eq(User::getSubjectId, subjectId));
        return BeanUtil.copyToList(users, UserBO.class);
    }

    /**
     * 根据手机号获取用户信息
     *
     * @param phone 手机号
     * @return 用户信息
     */
    public Optional<User> getUserByPhone(String phone) {
        return Optional.ofNullable(
                userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone)));
    }

    private UserBasicInfoResp toUserBasicInfo(User user) {
        return UserBasicInfoResp.builder()
                .id(user.getId())
                .userName(user.getUserName())
                .phone(user.getPhone())
                .subjectId(user.getSubjectId())
                .shortName(user.getShortName())
                .workNumber(user.getWorkNumber())
                .departmentName(user.getDepartmentName())
                .deptIds(user.getDepartmentId())
                .userStatus(user.getUserStatus())
                .build();
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<UserTreeNodeResp> getUserTreeList(Integer userType) {
        // userType: 0=全部, 1=内部, 2=外部。当前项目仅支持内部用户树
        if (userType != null && userType == 2) {
            return Collections.emptyList();
        }

        // 1. 获取部门列表
        List<Department> departments =
                departmentMapper.selectList(
                        new LambdaQueryWrapper<Department>().eq(Department::getStatus, 1));
        if (departments.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 获取用户列表
        List<User> users =
                userMapper.selectList(
                        new LambdaQueryWrapper<User>()
                                .eq(User::getState, 1)
                                .eq(User::getIsDelete, 1));

        // 3. 将用户按部门分类
        Map<String, List<UserTreeNodeResp>> deptUsers = new LinkedHashMap<>();
        for (User user : users) {
            String deptIds = user.getDepartmentId();
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
                                .id("U" + user.getId())
                                .sysId(user.getId())
                                .label(user.getUserName())
                                .departId("D" + deptId)
                                .sex(user.getSex() != null ? user.getSex() : 1)
                                .avatar(user.getUserAvatar())
                                .workNumber(
                                        user.getWorkNumber() != null ? user.getWorkNumber() : "")
                                .type(3)
                                .departName(
                                        user.getDepartmentName() != null
                                                ? user.getDepartmentName()
                                                : "")
                                .build();
                deptUsers.computeIfAbsent("D" + deptId, k -> new ArrayList<>()).add(userNode);
            }
        }

        // 4. 构建部门节点并挂载用户
        List<UserTreeNodeResp> departListWithUsers = new ArrayList<>();
        for (Department dept : departments) {
            String pid = (dept.getPid() != null && dept.getPid() != 0) ? "D" + dept.getPid() : "D0";
            UserTreeNodeResp node =
                    UserTreeNodeResp.builder()
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
        return buildNestedTree(departListWithUsers, "D0");
    }

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
}
