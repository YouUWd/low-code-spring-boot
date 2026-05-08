package com.jdec.platform.config.biz.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jdec.platform.config.api.SysUserApi;
import com.jdec.platform.config.api.bo.UserBO;
import com.jdec.platform.config.api.dto.request.QuerySysUserPageReq;
import com.jdec.platform.config.api.dto.request.UpdateSysUserReq;
import com.jdec.platform.config.api.dto.response.SysUserPageResp;
import com.jdec.platform.config.biz.entity.SysUser;
import com.jdec.platform.config.biz.mapper.SysUserMapper;
import com.jdec.platform.shared.enums.DeleteFlagEnum;
import com.jdec.platform.shared.enums.UserStatusFlagEnum;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.model.PageResult;
import com.jdec.platform.shared.utils.PageResultUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

/**
 * 用户 Service 实现
 */
@Service
@RequiredArgsConstructor
public class SysUserService implements SysUserApi {
    private final SysUserMapper sysUserMapper;

    @Override
    public PageResult<SysUserPageResp> getUserPage(QuerySysUserPageReq query) {
        Page<SysUser> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .like(StrUtil.isNotBlank(query.getUserName()), SysUser::getUserName, query.getUserName())
                .like(StrUtil.isNotBlank(query.getWorkNumber()), SysUser::getWorkNumber, query.getWorkNumber())
                .like(StrUtil.isNotBlank(query.getPhone()), SysUser::getPhone, query.getPhone())
                .eq(query.getUserType() != null, SysUser::getUserType, query.getUserType())
                .eq(query.getStatusFlag() != null, SysUser::getStatusFlag, query.getStatusFlag())
                .like(StrUtil.isNotBlank(query.getCompanyName()), SysUser::getCompanyName, query.getCompanyName())
                .like(StrUtil.isNotBlank(query.getDepartName()), SysUser::getDepartName, query.getDepartName())
                .like(StrUtil.isNotBlank(query.getSubjectName()), SysUser::getSubjectName, query.getSubjectName())
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
    public UserBO getUserByPhone(String phone) {
        SysUser sysUser = sysUserMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getPhone, phone)
                        .eq(SysUser::getDeleted, DeleteFlagEnum.NO_DELETE.getCode())
                        .eq(SysUser::getSuperFlag, UserStatusFlagEnum.ENABLE.getCode())
        );
        return BeanUtil.toBean(sysUser, UserBO.class);
    }
}
