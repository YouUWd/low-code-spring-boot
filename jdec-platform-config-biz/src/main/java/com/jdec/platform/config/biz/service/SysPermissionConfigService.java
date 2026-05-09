package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.config.api.SysPermissionConfigApi;
import com.jdec.platform.config.api.dto.request.CreateSysPermissionConfigReq;
import com.jdec.platform.config.api.dto.request.QuerySysPermissionConfigReq;
import com.jdec.platform.config.api.dto.request.UpdateSysPermissionConfigReq;
import com.jdec.platform.config.api.dto.response.SysPermissionConfigDetailResp;
import com.jdec.platform.config.api.dto.response.SysPermissionConfigEntryResp;
import com.jdec.platform.config.biz.entity.SysPermissionConfig;
import com.jdec.platform.config.biz.mapper.SysPermissionConfigMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/** 权限配置管理 Service 实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysPermissionConfigService implements SysPermissionConfigApi {

    private final SysPermissionConfigMapper sysPermissionConfigMapper;

    @Override
    public SysPermissionConfigEntryResp createPermissionConfig(
            CreateSysPermissionConfigReq request) {
        SysPermissionConfig config = new SysPermissionConfig();
        config.setTenantId(request.getTenantId());
        config.setModuleId(request.getModuleId());
        config.setTableName(request.getTableName());
        config.setFieldName(request.getFieldName());
        config.setLogicalField(request.getLogicalField());
        config.setPermission(request.getPermission() != null ? request.getPermission() : 7);
        config.setDescription(request.getDescription());
        config.setPermissionStatus("active");
        config.setCreatedBy(request.getCreatedBy());
        config.setCreatedDate(LocalDateTime.now());
        config.setCreatedName(request.getCreatedName());
        config.setDeleted(0);

        sysPermissionConfigMapper.insert(config);
        return toPermissionConfigEntry(config);
    }

    @Override
    public SysPermissionConfigDetailResp getPermissionConfigById(Long permissionId) {
        SysPermissionConfig config = sysPermissionConfigMapper.selectById(permissionId);
        return toPermissionConfigDetail(config);
    }

    @Override
    public SysPermissionConfigEntryResp getPermissionByField(
            Long tenantId, String tableName, String fieldName) {
        LambdaQueryWrapper<SysPermissionConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysPermissionConfig::getTenantId, tenantId)
                .eq(SysPermissionConfig::getTableName, tableName)
                .eq(SysPermissionConfig::getFieldName, fieldName)
                .eq(SysPermissionConfig::getDeleted, 0);

        SysPermissionConfig config = sysPermissionConfigMapper.selectOne(wrapper);
        return toPermissionConfigEntry(config);
    }

    @Override
    public List<SysPermissionConfigEntryResp> queryPermissionConfigs(
            QuerySysPermissionConfigReq request) {
        LambdaQueryWrapper<SysPermissionConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysPermissionConfig::getTenantId, request.getTenantId())
                .eq(SysPermissionConfig::getDeleted, 0);

        if (request.getTableName() != null) {
            wrapper.like(SysPermissionConfig::getTableName, request.getTableName());
        }
        if (request.getFieldName() != null) {
            wrapper.like(SysPermissionConfig::getFieldName, request.getFieldName());
        }
        if (request.getModuleId() != null) {
            wrapper.eq(SysPermissionConfig::getModuleId, request.getModuleId());
        }
        if (request.getPermissionStatus() != null) {
            wrapper.eq(SysPermissionConfig::getPermissionStatus, request.getPermissionStatus());
        }

        List<SysPermissionConfig> configs = sysPermissionConfigMapper.selectList(wrapper);
        return configs.stream().map(this::toPermissionConfigEntry).collect(Collectors.toList());
    }

    @Override
    public SysPermissionConfigEntryResp updatePermissionConfig(
            UpdateSysPermissionConfigReq request) {
        SysPermissionConfig config = sysPermissionConfigMapper.selectById(request.getId());
        if (config == null) {
            throw new RuntimeException("权限配置不存在");
        }

        config.setPermission(request.getPermission());
        config.setPermissionStatus(request.getPermissionStatus());
        config.setDescription(request.getDescription());
        config.setUpdatedBy(request.getUpdatedBy());
        config.setUpdatedDate(LocalDateTime.now());
        config.setUpdatedName(request.getUpdatedName());

        sysPermissionConfigMapper.updateById(config);
        return toPermissionConfigEntry(config);
    }

    @Override
    public void deletePermissionConfig(Long permissionId) {
        SysPermissionConfig config = sysPermissionConfigMapper.selectById(permissionId);
        if (config != null) {
            config.setDeleted(1);
            sysPermissionConfigMapper.updateById(config);
        }
    }

    @Override
    public void batchDeletePermissionConfigs(List<Long> permissionIds) {
        if (CollectionUtils.isEmpty(permissionIds)) {
            return;
        }
        LambdaQueryWrapper<SysPermissionConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysPermissionConfig::getId, permissionIds);
        List<SysPermissionConfig> configs = sysPermissionConfigMapper.selectList(wrapper);
        configs.forEach(
                config -> {
                    config.setDeleted(1);
                    sysPermissionConfigMapper.updateById(config);
                });
    }

    @Override
    public boolean hasPermission(
            Long tenantId, String tableName, String fieldName, Integer operation) {
        LambdaQueryWrapper<SysPermissionConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysPermissionConfig::getTenantId, tenantId)
                .eq(SysPermissionConfig::getTableName, tableName)
                .eq(SysPermissionConfig::getFieldName, fieldName)
                .eq(SysPermissionConfig::getDeleted, 0);

        SysPermissionConfig config = sysPermissionConfigMapper.selectOne(wrapper);
        if (config == null) {
            return false;
        }

        // 位运算检查权限
        return (config.getPermission() & operation) == operation;
    }

    private SysPermissionConfigEntryResp toPermissionConfigEntry(SysPermissionConfig config) {
        if (config == null) {
            return null;
        }
        SysPermissionConfigEntryResp resp = new SysPermissionConfigEntryResp();
        resp.setId(config.getId());
        resp.setTenantId(config.getTenantId());
        resp.setTableName(config.getTableName());
        resp.setFieldName(config.getFieldName());
        resp.setPermission(config.getPermission());
        resp.setPermissionStatus(config.getPermissionStatus());
        resp.setCreatedDate(config.getCreatedDate());
        resp.setUpdatedDate(config.getUpdatedDate());
        return resp;
    }

    private SysPermissionConfigDetailResp toPermissionConfigDetail(SysPermissionConfig config) {
        if (config == null) {
            return null;
        }
        SysPermissionConfigDetailResp resp = new SysPermissionConfigDetailResp();
        resp.setId(config.getId());
        resp.setTenantId(config.getTenantId());
        resp.setTableName(config.getTableName());
        resp.setFieldName(config.getFieldName());
        resp.setPermission(config.getPermission());
        resp.setModuleId(config.getModuleId());
        resp.setLogicalField(config.getLogicalField());
        resp.setDescription(config.getDescription());
        resp.setPermissionStatus(config.getPermissionStatus());
        resp.setCreatedBy(config.getCreatedBy());
        resp.setCreatedDate(config.getCreatedDate());
        resp.setCreatedName(config.getCreatedName());
        resp.setUpdatedBy(config.getUpdatedBy());
        resp.setUpdatedDate(config.getUpdatedDate());
        resp.setUpdatedName(config.getUpdatedName());
        return resp;
    }
}
