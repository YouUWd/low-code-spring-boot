package com.jdec.platform.hr.biz.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jdec.platform.hr.api.UserBaseApi;
import com.jdec.platform.hr.api.dto.request.CreateUserBaseReq;
import com.jdec.platform.hr.api.dto.request.UpdateUserBaseReq;
import com.jdec.platform.hr.api.dto.response.UserBaseResp;
import com.jdec.platform.hr.biz.converter.UserBaseConverter;
import com.jdec.platform.hr.biz.entity.UserBase;
import com.jdec.platform.hr.biz.mapper.UserBaseMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.model.PageResult;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 用户基本信息服务实现 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DataSource(DataSourceConstants.HR_MANAGE)
public class UserBaseService implements UserBaseApi {

    private final UserBaseMapper userBaseMapper;

    @Override
    public Optional<UserBaseResp> getUserBaseById(Integer userId) {
        if (userId == null) {
            return Optional.empty();
        }

        UserBase userBase = userBaseMapper.selectById(userId);
        return Optional.ofNullable(UserBaseConverter.toUserBaseResp(userBase));
    }

    @Override
    public PageResult<UserBaseResp> getUserBaseByPage(Long pageNum, Long pageSize) {
        if (pageNum == null || pageNum < 1) {
            pageNum = 1L;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = 10L;
        }

        Page<UserBase> page = new Page<>(pageNum, pageSize);
        IPage<UserBase> result = userBaseMapper.selectPage(page, null);

        List<UserBaseResp> records = UserBaseConverter.toUserBaseResps(result.getRecords());
        return PageResult.of(pageNum, pageSize, result.getTotal(), records);
    }

    @Override
    @Transactional
    public UserBaseResp createUserBase(CreateUserBaseReq request) {
        UserBase userBase =
                UserBase.builder()
                        .userId(request.getUserId())
                        .userName(request.getUserName())
                        .sex(request.getSex())
                        .jobClass(request.getJobClass())
                        .originalJobClass(request.getOriginalJobClass())
                        .positionId(request.getPositionId())
                        .shortName(request.getShortName())
                        .jobLevel(request.getJobLevel())
                        .jobGrade(request.getJobGrade())
                        .enterDate(request.getEnterDate())
                        .birthday(request.getBirthday())
                        .hometown(request.getHometown())
                        .hometownName(request.getHometownName())
                        .nationality(request.getNationality())
                        .politicalAppearance(request.getPoliticalAppearance())
                        .highestEducation(request.getHighestEducation())
                        .graduationCollege(request.getGraduationCollege())
                        .address(request.getAddress())
                        .marital(request.getMarital())
                        .build();
        userBaseMapper.insert(userBase);
        log.info("用户基本信息创建成功: {}", request.getUserId());
        return UserBaseConverter.toUserBaseResp(userBase);
    }

    @Override
    @Transactional
    public UserBaseResp updateUserBase(UpdateUserBaseReq request) {
        request.validate();
        UserBase userBase =
                UserBase.builder()
                        .id(request.getId())
                        .userName(request.getUserName())
                        .sex(request.getSex())
                        .jobClass(request.getJobClass())
                        .originalJobClass(request.getOriginalJobClass())
                        .positionId(request.getPositionId())
                        .shortName(request.getShortName())
                        .jobLevel(request.getJobLevel())
                        .jobGrade(request.getJobGrade())
                        .enterDate(request.getEnterDate())
                        .birthday(request.getBirthday())
                        .hometown(request.getHometown())
                        .hometownName(request.getHometownName())
                        .nationality(request.getNationality())
                        .politicalAppearance(request.getPoliticalAppearance())
                        .highestEducation(request.getHighestEducation())
                        .graduationCollege(request.getGraduationCollege())
                        .address(request.getAddress())
                        .marital(request.getMarital())
                        .build();
        userBaseMapper.updateById(userBase);
        log.info("用户基本信息更新成功: {}", request.getId());
        return UserBaseConverter.toUserBaseResp(userBaseMapper.selectById(request.getId()));
    }

    @Override
    @Transactional
    public void deleteUserBase(Integer userId) {
        if (userId == null) {
            return;
        }
        userBaseMapper.deleteById(userId);
        log.info("用户基本信息删除成功: {}", userId);
    }
}
