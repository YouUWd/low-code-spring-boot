package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysDataPermissionMappingApi;
import com.jdec.platform.config.api.dto.request.QuerySysDataPermissionMappingReq;
import com.jdec.platform.config.api.dto.request.SysDataPermissionMappingSaveReq;
import com.jdec.platform.config.api.dto.response.SysDataPermissionMappingResp;
import com.jdec.platform.config.biz.entity.SysDataPermissionMapping;
import com.jdec.platform.config.biz.mapper.SysDataPermissionMappingMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
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
public class SysDataPermissionMappingService implements SysDataPermissionMappingApi {

    private final SysDataPermissionMappingMapper sysDataPermissionMappingMapper;

    @Override
    public void saveDataPermissionMapping(SysDataPermissionMappingSaveReq req) {
        if (req.getId() == null) {
            // 校验是否已存在
            boolean exists =
                    sysDataPermissionMappingMapper.exists(
                            Wrappers.<SysDataPermissionMapping>lambdaQuery()
                                    .eq(
                                            SysDataPermissionMapping::getDataPermissionCode,
                                            req.getDataPermissionCode())
                                    .eq(SysDataPermissionMapping::getBizId, req.getBizId()));
            if (exists) {
                throw new BusinessException("该数据权限已存在");
            }
            // 新增
            SysDataPermissionMapping entity = new SysDataPermissionMapping();
            BeanUtils.copyProperties(req, entity);
            sysDataPermissionMappingMapper.insert(entity);
        } else {
            // 编辑
            SysDataPermissionMapping entity =
                    sysDataPermissionMappingMapper.selectById(req.getId());
            if (entity == null) {
                throw new BusinessException("数据权限不存在");
            }
            // 校验是否已存在（排除自己）
            boolean exists =
                    sysDataPermissionMappingMapper.exists(
                            Wrappers.<SysDataPermissionMapping>lambdaQuery()
                                    .eq(
                                            SysDataPermissionMapping::getDataPermissionCode,
                                            req.getDataPermissionCode())
                                    .eq(SysDataPermissionMapping::getBizId, req.getBizId())
                                    .ne(SysDataPermissionMapping::getId, req.getId()));
            if (exists) {
                throw new BusinessException("该数据权限已存在");
            }
            BeanUtils.copyProperties(req, entity);
            entity.setId(req.getId());
            sysDataPermissionMappingMapper.updateById(entity);
        }
    }

    @Transactional
    @Override
    public void deleteDataPermissionMapping(Long id) {
        SysDataPermissionMapping entity = sysDataPermissionMappingMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("数据权限不存在");
        }
        sysDataPermissionMappingMapper.deleteById(id);
    }

    @Transactional
    @Override
    public void batchSaveDataPermissionMapping(List<SysDataPermissionMappingSaveReq> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return;
        }
        // 批量新增
        for (SysDataPermissionMappingSaveReq permission : permissions) {
            SysDataPermissionMapping entity = new SysDataPermissionMapping();
            BeanUtils.copyProperties(permission, entity);
            sysDataPermissionMappingMapper.insert(entity);
        }
    }

    @Override
    public List<SysDataPermissionMappingResp> listDataPermissionMapping(
            QuerySysDataPermissionMappingReq req) {
        List<SysDataPermissionMapping> list =
                sysDataPermissionMappingMapper.selectList(
                        new LambdaQueryWrapper<SysDataPermissionMapping>()
                                .eq(
                                        StringUtils.hasText(req.getDataPermissionCode()),
                                        SysDataPermissionMapping::getDataPermissionCode,
                                        req.getDataPermissionCode())
                                .eq(
                                        req.getBizId() != null,
                                        SysDataPermissionMapping::getBizId,
                                        req.getBizId())
                                .orderByDesc(SysDataPermissionMapping::getId));
        if (list.isEmpty()) {
            return new ArrayList<>();
        }
        return list.stream()
                .map(
                        permission -> {
                            SysDataPermissionMappingResp resp = new SysDataPermissionMappingResp();
                            resp.setId(permission.getId());
                            resp.setDataPermissionCode(permission.getDataPermissionCode());
                            resp.setBizId(permission.getBizId());
                            resp.setBizName(permission.getBizName());
                            return resp;
                        })
                .collect(Collectors.toList());
    }

    @Override
    public SysDataPermissionMappingResp getDataPermissionMapping(Long id) {
        SysDataPermissionMapping entity = sysDataPermissionMappingMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("数据权限不存在");
        }
        SysDataPermissionMappingResp resp = new SysDataPermissionMappingResp();
        resp.setId(entity.getId());
        resp.setDataPermissionCode(entity.getDataPermissionCode());
        resp.setBizId(entity.getBizId());
        resp.setBizName(entity.getBizName());
        return resp;
    }

    @Override
    public List<SysDataPermissionMappingResp> listDataPermissionCategories() {
        // 查询所有数据权限，按 type_code/type_name 分组去重
        List<SysDataPermissionMapping> list =
                sysDataPermissionMappingMapper.selectList(
                        Wrappers.<SysDataPermissionMapping>lambdaQuery()
                                .orderByDesc(SysDataPermissionMapping::getId));

        if (list.isEmpty()) {
            return new ArrayList<>();
        }
        // 手动去重（按 bizTypeCode 分组）
        return list.stream()
                .collect(
                        Collectors.toMap(
                                SysDataPermissionMapping::getDataPermissionCode,
                                p -> p,
                                (existing, replacement) -> existing))
                .values()
                .stream()
                .map(
                        permission -> {
                            SysDataPermissionMappingResp resp = new SysDataPermissionMappingResp();
                            resp.setDataPermissionCode(permission.getDataPermissionCode());
                            return resp;
                        })
                .collect(Collectors.toList());
    }
}
