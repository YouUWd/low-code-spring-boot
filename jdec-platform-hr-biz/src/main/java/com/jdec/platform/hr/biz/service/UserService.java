package com.jdec.platform.hr.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.hr.api.UserApi;
import com.jdec.platform.hr.api.dto.request.CreateUserReq;
import com.jdec.platform.hr.api.dto.request.UpdateUserReq;
import com.jdec.platform.hr.api.dto.response.UserBasicInfoResp;
import com.jdec.platform.hr.biz.entity.User;
import com.jdec.platform.hr.biz.mapper.UserMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户服务 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@DataSource(DataSourceConstants.HR_MANAGE)
public class UserService implements UserApi {

    private final UserMapper userMapper;

    @Override
    public Optional<Integer> getSubjectIdByPhone(String phone) {
        return Optional.ofNullable(
                        userMapper.selectOne(
                                new LambdaQueryWrapper<User>().eq(User::getPhone, phone)))
                .map(User::getSubjectId);
    }

    @Override
    public Optional<String> getWeChatUserIdByPhone(String phone) {
        return Optional.ofNullable(
                        userMapper.selectOne(
                                new LambdaQueryWrapper<User>().eq(User::getPhone, phone)))
                .map(User::getShortName);
    }

    @Override
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
                .userStatus(user.getUserStatus())
                .build();
    }
}
