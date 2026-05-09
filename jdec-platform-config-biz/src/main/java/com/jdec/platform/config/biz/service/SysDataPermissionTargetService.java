package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysDataPermissionTargetApi;
import com.jdec.platform.config.api.dto.request.QuerySysDataPermissionTargetReq;
import com.jdec.platform.config.api.dto.request.SysDataPermissionTargetSaveReq;
import com.jdec.platform.config.api.dto.response.SysDataPermissionTargetResp;
import com.jdec.platform.config.biz.entity.SysDataPermissionTarget;
import com.jdec.platform.config.biz.mapper.SysDataPermissionTargetMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 数据权限目标 Service 实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysDataPermissionTargetService implements SysDataPermissionTargetApi {

    private final SysDataPermissionTargetMapper sysDataPermissionTargetMapper;

    @Override
    public void saveDataPermissionTarget(SysDataPermissionTargetSaveReq req) {
        if (req.getId() == null) {
            // 校验目标是否已存在
            boolean exists =
                    sysDataPermissionTargetMapper.exists(
                            Wrappers.<SysDataPermissionTarget>lambdaQuery()
                                    .eq(
                                            SysDataPermissionTarget::getRoleDataPermissionId,
                                            req.getRoleDataPermissionId())
                                    .eq(SysDataPermissionTarget::getTargetType, req.getTargetType())
                                    .eq(SysDataPermissionTarget::getTargetId, req.getTargetId()));
            if (exists) {
                throw new BusinessException("该目标已存在");
            }
            // 新增
            SysDataPermissionTarget entity = new SysDataPermissionTarget();
            BeanUtils.copyProperties(req, entity);
            sysDataPermissionTargetMapper.insert(entity);
        } else {
            // 编辑
            SysDataPermissionTarget entity = sysDataPermissionTargetMapper.selectById(req.getId());
            if (entity == null) {
                throw new BusinessException("数据权限目标不存在");
            }
            // 校验目标是否已存在（排除自己）
            boolean exists =
                    sysDataPermissionTargetMapper.exists(
                            Wrappers.<SysDataPermissionTarget>lambdaQuery()
                                    .eq(
                                            SysDataPermissionTarget::getRoleDataPermissionId,
                                            req.getRoleDataPermissionId())
                                    .eq(SysDataPermissionTarget::getTargetType, req.getTargetType())
                                    .eq(SysDataPermissionTarget::getTargetId, req.getTargetId())
                                    .ne(SysDataPermissionTarget::getId, req.getId()));
            if (exists) {
                throw new BusinessException("该目标已存在");
            }
            entity.setRoleDataPermissionId(req.getRoleDataPermissionId());
            entity.setTargetType(req.getTargetType());
            entity.setTargetId(req.getTargetId());
            sysDataPermissionTargetMapper.updateById(entity);
        }
    }

    @Transactional
    @Override
    public void deleteDataPermissionTarget(Long id) {
        SysDataPermissionTarget entity = sysDataPermissionTargetMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("数据权限目标不存在");
        }
        sysDataPermissionTargetMapper.deleteById(id);
    }

    @Override
    public void batchSaveDataPermissionTarget(
            Long roleDataPermissionId, List<SysDataPermissionTargetSaveReq> targets) {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        // 先删除该roleDataPermissionId下的所有目标
        sysDataPermissionTargetMapper.delete(
                Wrappers.<SysDataPermissionTarget>lambdaQuery()
                        .eq(
                                SysDataPermissionTarget::getRoleDataPermissionId,
                                roleDataPermissionId));
        // 批量新增
        for (SysDataPermissionTargetSaveReq target : targets) {
            SysDataPermissionTarget entity = new SysDataPermissionTarget();
            BeanUtils.copyProperties(target, entity);
            entity.setRoleDataPermissionId(roleDataPermissionId);
            sysDataPermissionTargetMapper.insert(entity);
        }
    }

    @Override
    public List<SysDataPermissionTargetResp> listDataPermissionTarget(
            QuerySysDataPermissionTargetReq req) {
        List<SysDataPermissionTarget> list =
                sysDataPermissionTargetMapper.selectList(
                        new LambdaQueryWrapper<SysDataPermissionTarget>()
                                .eq(
                                        req.getRoleDataPermissionId() != null,
                                        SysDataPermissionTarget::getRoleDataPermissionId,
                                        req.getRoleDataPermissionId())
                                .eq(
                                        StringUtils.hasText(req.getTargetType()),
                                        SysDataPermissionTarget::getTargetType,
                                        req.getTargetType())
                                .eq(
                                        req.getTargetId() != null,
                                        SysDataPermissionTarget::getTargetId,
                                        req.getTargetId())
                                .orderByDesc(SysDataPermissionTarget::getId));
        if (list.isEmpty()) {
            return new ArrayList<>();
        }
        return list.stream()
                .map(
                        target -> {
                            SysDataPermissionTargetResp resp = new SysDataPermissionTargetResp();
                            resp.setId(target.getId());
                            resp.setRoleDataPermissionId(target.getRoleDataPermissionId());
                            resp.setTargetType(target.getTargetType());
                            resp.setTargetId(target.getTargetId());
                            return resp;
                        })
                .collect(Collectors.toList());
    }

    @Override
    public SysDataPermissionTargetResp getDataPermissionTarget(Long id) {
        SysDataPermissionTarget entity = sysDataPermissionTargetMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("数据权限目标不存在");
        }
        SysDataPermissionTargetResp resp = new SysDataPermissionTargetResp();
        resp.setId(entity.getId());
        resp.setRoleDataPermissionId(entity.getRoleDataPermissionId());
        resp.setTargetType(entity.getTargetType());
        resp.setTargetId(entity.getTargetId());
        return resp;
    }
}
