package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysSpecialPermissionApi;
import com.jdec.platform.config.api.dto.request.QuerySysSpecialPermissionReq;
import com.jdec.platform.config.api.dto.request.SysSpecialPermissionSaveReq;
import com.jdec.platform.config.api.dto.response.SysSpecialPermissionOptionResp;
import com.jdec.platform.config.api.dto.response.SysSpecialPermissionResp;
import com.jdec.platform.config.biz.entity.SysSpecialPermission;
import com.jdec.platform.config.biz.mapper.SysSpecialPermissionMapper;
import com.jdec.platform.shared.context.AppCodeContext;
import com.jdec.platform.shared.context.SubjectContext;
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

/** 特殊权限 Service 实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysSpecialPermissionService implements SysSpecialPermissionApi {

    private final SysSpecialPermissionMapper sysSpecialPermissionMapper;

    @Override
    public void saveSpecialPermission(SysSpecialPermissionSaveReq req) {
        if (req.getId() == null) {
            // 校验编码唯一性
            boolean exists =
                    sysSpecialPermissionMapper.exists(
                            Wrappers.<SysSpecialPermission>lambdaQuery()
                                    .eq(SysSpecialPermission::getCode, req.getCode())
                                    .eq(SysSpecialPermission::getSubjectId, SubjectContext.get())
                                    .eq(SysSpecialPermission::getAppCode, AppCodeContext.get()));
            if (exists) {
                throw new BusinessException("权限编码已存在");
            }
            // 新增
            SysSpecialPermission entity = new SysSpecialPermission();
            BeanUtils.copyProperties(req, entity);
            entity.setSubjectId(SubjectContext.get());
            entity.setAppCode(AppCodeContext.get());
            sysSpecialPermissionMapper.insert(entity);
        } else {
            // 编辑
            SysSpecialPermission entity =
                    sysSpecialPermissionMapper.selectOne(
                            Wrappers.<SysSpecialPermission>lambdaQuery()
                                    .eq(SysSpecialPermission::getId, req.getId())
                                    .eq(SysSpecialPermission::getSubjectId, SubjectContext.get())
                                    .eq(SysSpecialPermission::getAppCode, AppCodeContext.get()));
            if (entity == null) {
                throw new BusinessException("特殊权限不存在");
            }
            // 校验编码唯一性（排除自己）
            if (!entity.getCode().equals(req.getCode())) {
                boolean exists =
                        sysSpecialPermissionMapper.exists(
                                Wrappers.<SysSpecialPermission>lambdaQuery()
                                        .eq(SysSpecialPermission::getCode, req.getCode())
                                        .ne(SysSpecialPermission::getId, req.getId())
                                        .eq(
                                                SysSpecialPermission::getSubjectId,
                                                SubjectContext.get())
                                        .eq(
                                                SysSpecialPermission::getAppCode,
                                                AppCodeContext.get()));
                if (exists) {
                    throw new BusinessException("权限编码已存在");
                }
            }
            entity.setCode(req.getCode());
            entity.setName(req.getName());
            entity.setDescription(req.getDescription());
            entity.setSort(req.getSort());
            entity.setStatus(req.getStatus());
            sysSpecialPermissionMapper.updateById(entity);
        }
    }

    @Transactional
    @Override
    public void deleteSpecialPermission(Long id) {
        boolean exists =
                sysSpecialPermissionMapper.exists(
                        Wrappers.<SysSpecialPermission>lambdaQuery()
                                .eq(SysSpecialPermission::getId, id)
                                .eq(SysSpecialPermission::getSubjectId, SubjectContext.get())
                                .eq(SysSpecialPermission::getAppCode, AppCodeContext.get()));
        if (!exists) {
            throw new BusinessException("特殊权限不存在");
        }
        sysSpecialPermissionMapper.deleteById(id);
    }

    @Override
    public List<SysSpecialPermissionOptionResp> listSpecialPermissionOptions() {
        List<SysSpecialPermission> list =
                sysSpecialPermissionMapper.selectList(
                        new LambdaQueryWrapper<SysSpecialPermission>()
                                .eq(SysSpecialPermission::getAppCode, AppCodeContext.get())
                                .eq(SysSpecialPermission::getSubjectId, SubjectContext.get())
                                .eq(SysSpecialPermission::getStatus, 1)
                                .orderByAsc(SysSpecialPermission::getSort)
                                .orderByAsc(SysSpecialPermission::getId));
        return list.stream()
                .map(
                        permission -> {
                            SysSpecialPermissionOptionResp resp =
                                    new SysSpecialPermissionOptionResp();
                            resp.setId(permission.getId());
                            resp.setName(permission.getName());
                            resp.setCode(permission.getCode());
                            return resp;
                        })
                .collect(Collectors.toList());
    }

    @Override
    public List<SysSpecialPermissionResp> listSpecialPermission(QuerySysSpecialPermissionReq req) {
        List<SysSpecialPermission> list =
                sysSpecialPermissionMapper.selectList(
                        new LambdaQueryWrapper<SysSpecialPermission>()
                                .eq(SysSpecialPermission::getAppCode, AppCodeContext.get())
                                .eq(SysSpecialPermission::getSubjectId, SubjectContext.get())
                                .like(
                                        StringUtils.hasText(req.getName()),
                                        SysSpecialPermission::getName,
                                        req.getName())
                                .like(
                                        StringUtils.hasText(req.getCode()),
                                        SysSpecialPermission::getCode,
                                        req.getCode())
                                .eq(
                                        req.getStatus() != null,
                                        SysSpecialPermission::getStatus,
                                        req.getStatus())
                                .orderByAsc(SysSpecialPermission::getSort)
                                .orderByAsc(SysSpecialPermission::getId));
        if (list.isEmpty()) {
            return new ArrayList<>();
        }
        return list.stream()
                .map(
                        permission -> {
                            SysSpecialPermissionResp resp = new SysSpecialPermissionResp();
                            resp.setId(permission.getId());
                            resp.setCode(permission.getCode());
                            resp.setName(permission.getName());
                            resp.setDescription(permission.getDescription());
                            resp.setSort(permission.getSort());
                            resp.setStatus(permission.getStatus());
                            return resp;
                        })
                .collect(Collectors.toList());
    }

    @Override
    public SysSpecialPermissionResp getSpecialPermission(Long id) {
        SysSpecialPermission entity =
                sysSpecialPermissionMapper.selectOne(
                        Wrappers.<SysSpecialPermission>lambdaQuery()
                                .eq(SysSpecialPermission::getId, id)
                                .eq(SysSpecialPermission::getSubjectId, SubjectContext.get())
                                .eq(SysSpecialPermission::getAppCode, AppCodeContext.get()));
        if (entity == null) {
            throw new BusinessException("特殊权限不存在");
        }
        SysSpecialPermissionResp resp = new SysSpecialPermissionResp();
        resp.setId(entity.getId());
        resp.setCode(entity.getCode());
        resp.setName(entity.getName());
        resp.setDescription(entity.getDescription());
        resp.setSort(entity.getSort());
        resp.setStatus(entity.getStatus());
        return resp;
    }
}
