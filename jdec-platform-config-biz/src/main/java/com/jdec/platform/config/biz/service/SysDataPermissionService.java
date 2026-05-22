package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysDataPermissionApi;
import com.jdec.platform.config.api.dto.request.QuerySysDataPermissionReq;
import com.jdec.platform.config.api.dto.request.SysDataPermissionSaveReq;
import com.jdec.platform.config.api.dto.response.SysDataPermissionResp;
import com.jdec.platform.config.biz.entity.SysDataPermission;
import com.jdec.platform.config.biz.mapper.SysDataPermissionMapper;
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

/** 数据权限 Service 实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysDataPermissionService implements SysDataPermissionApi {

    private final SysDataPermissionMapper sysDataPermissionMapper;

    @Override
    public void saveDataPermission(SysDataPermissionSaveReq req) {
        if (req.getId() == null) {
            // 校验是否已存在
            boolean exists =
                    sysDataPermissionMapper.exists(
                            Wrappers.<SysDataPermission>lambdaQuery()
                                    .eq(SysDataPermission::getBizTypeCode, req.getBizTypeCode())
                                    .eq(SysDataPermission::getBizId, req.getBizId()));
            if (exists) {
                throw new BusinessException("该数据权限已存在");
            }
            // 新增
            SysDataPermission entity = new SysDataPermission();
            BeanUtils.copyProperties(req, entity);
            sysDataPermissionMapper.insert(entity);
        } else {
            // 编辑
            SysDataPermission entity = sysDataPermissionMapper.selectById(req.getId());
            if (entity == null) {
                throw new BusinessException("数据权限不存在");
            }
            // 校验是否已存在（排除自己）
            boolean exists =
                    sysDataPermissionMapper.exists(
                            Wrappers.<SysDataPermission>lambdaQuery()
                                    .eq(SysDataPermission::getBizTypeCode, req.getBizTypeCode())
                                    .eq(SysDataPermission::getBizId, req.getBizId())
                                    .ne(SysDataPermission::getId, req.getId()));
            if (exists) {
                throw new BusinessException("该数据权限已存在");
            }
            BeanUtils.copyProperties(req, entity);
            entity.setId(req.getId());
            sysDataPermissionMapper.updateById(entity);
        }
    }

    @Transactional
    @Override
    public void deleteDataPermission(Long id) {
        SysDataPermission entity = sysDataPermissionMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("数据权限不存在");
        }
        sysDataPermissionMapper.deleteById(id);
    }

    @Transactional
    @Override
    public void batchSaveDataPermission(List<SysDataPermissionSaveReq> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return;
        }
        // 批量新增
        for (SysDataPermissionSaveReq permission : permissions) {
            SysDataPermission entity = new SysDataPermission();
            BeanUtils.copyProperties(permission, entity);
            sysDataPermissionMapper.insert(entity);
        }
    }

    @Override
    public List<SysDataPermissionResp> listDataPermission(QuerySysDataPermissionReq req) {
        List<SysDataPermission> list =
                sysDataPermissionMapper.selectList(
                        new LambdaQueryWrapper<SysDataPermission>()
                                .eq(
                                        StringUtils.hasText(req.getBizTypeCode()),
                                        SysDataPermission::getBizTypeCode,
                                        req.getBizTypeCode())
                                .eq(
                                        req.getBizId() != null,
                                        SysDataPermission::getBizId,
                                        req.getBizId())
                                .orderByDesc(SysDataPermission::getId));
        if (list.isEmpty()) {
            return new ArrayList<>();
        }
        return list.stream()
                .map(
                        permission -> {
                            SysDataPermissionResp resp = new SysDataPermissionResp();
                            resp.setId(permission.getId());
                            resp.setBizTypeCode(permission.getBizTypeCode());
                            resp.setBizTypeName(permission.getBizTypeName());
                            resp.setBizId(permission.getBizId());
                            resp.setBizName(permission.getBizName());
                            return resp;
                        })
                .collect(Collectors.toList());
    }

    @Override
    public SysDataPermissionResp getDataPermission(Long id) {
        SysDataPermission entity = sysDataPermissionMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("数据权限不存在");
        }
        SysDataPermissionResp resp = new SysDataPermissionResp();
        resp.setId(entity.getId());
        resp.setBizTypeCode(entity.getBizTypeCode());
        resp.setBizTypeName(entity.getBizTypeName());
        resp.setBizId(entity.getBizId());
        resp.setBizName(entity.getBizName());
        return resp;
    }

    @Override
    public List<SysDataPermissionResp> listDataPermissionCategories() {
        // 查询所有数据权限，按 type_code/type_name 分组去重
        List<SysDataPermission> list =
                sysDataPermissionMapper.selectList(
                        Wrappers.<SysDataPermission>lambdaQuery()
                                .orderByDesc(SysDataPermission::getId));

        if (list.isEmpty()) {
            return new ArrayList<>();
        }
        // 手动去重（按 bizTypeCode 分组）
        return list.stream()
                .collect(
                        Collectors.toMap(
                                SysDataPermission::getBizTypeCode,
                                p -> p,
                                (existing, replacement) -> existing))
                .values()
                .stream()
                .map(
                        permission -> {
                            SysDataPermissionResp resp = new SysDataPermissionResp();
                            resp.setBizTypeCode(permission.getBizTypeCode());
                            resp.setBizTypeName(permission.getBizTypeName());
                            return resp;
                        })
                .collect(Collectors.toList());
    }
}
