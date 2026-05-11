package com.jdec.platform.config.biz.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jdec.platform.config.api.SysUserApi;
import com.jdec.platform.config.api.bo.SysUserBO;
import com.jdec.platform.config.api.dto.request.QuerySysUserPageReq;
import com.jdec.platform.config.api.dto.request.UpdateSysUserReq;
import com.jdec.platform.config.api.dto.response.SysUserPageResp;
import com.jdec.platform.config.biz.entity.SysRole;
import com.jdec.platform.config.biz.entity.SysRoleUser;
import com.jdec.platform.config.biz.entity.SysUser;
import com.jdec.platform.config.biz.mapper.SysRoleMapper;
import com.jdec.platform.config.biz.mapper.SysRoleUserMapper;
import com.jdec.platform.config.biz.mapper.SysUserMapper;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.enums.UserStatusFlagEnum;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.model.PageResult;
import com.jdec.platform.shared.utils.PageResultUtils;
import java.util.Collections;
import java.util.List;
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
                        .in(SysUser::getId, userIds)
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
                        Wrappers.<SysUser>lambdaQuery()
                                .eq(SysUser::getPhone, phone)
                                .eq(SysUser::getSuperFlag, UserStatusFlagEnum.ENABLE.getCode()));
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
                                .in(SysUser::getId, userIds)
                                .orderByDesc(SysUser::getCreatedDate));
        return users.stream().map(this::toUserPageResp).toList();
    }
}
