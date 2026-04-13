package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.config.api.SysModuleFieldConfigApi;
import com.jdec.platform.config.api.dto.request.ModuleFieldConfigReq;
import com.jdec.platform.config.api.dto.response.ModuleFieldConfigResp;
import com.jdec.platform.config.biz.converter.ModuleConverter;
import com.jdec.platform.config.biz.entity.SysModule;
import com.jdec.platform.config.biz.entity.SysModuleFieldConfig;
import com.jdec.platform.config.biz.mapper.SysModuleFieldConfigMapper;
import com.jdec.platform.config.biz.mapper.SysModuleMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/** 模块字段配置服务实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysModuleFieldConfigService implements SysModuleFieldConfigApi {

    private final SysModuleMapper moduleMapper;
    private final SysModuleFieldConfigMapper fieldConfigMapper;
    private final ModuleConverter moduleConverter;

    @Cacheable(value = "moduleFieldConfig", key = "#moduleCode")
    public List<ModuleFieldConfigResp> getModuleFieldConfigs(String moduleCode) {
        SysModule module =
                moduleMapper.selectOne(
                        new LambdaQueryWrapper<SysModule>()
                                .eq(SysModule::getModuleCode, moduleCode));
        if (module == null) {
            throw new IllegalArgumentException("模块不存在: " + moduleCode);
        }

        List<SysModuleFieldConfig> configs =
                fieldConfigMapper.selectList(
                        new LambdaQueryWrapper<SysModuleFieldConfig>()
                                .eq(SysModuleFieldConfig::getModuleId, module.getId()));
        return moduleConverter.toModuleFieldConfigResps(configs);
    }

    @CacheEvict(value = "moduleFieldConfig", key = "#moduleCode")
    public ModuleFieldConfigResp addFieldConfig(String moduleCode, ModuleFieldConfigReq request) {
        request.validate();

        SysModule module =
                moduleMapper.selectOne(
                        new LambdaQueryWrapper<SysModule>()
                                .eq(SysModule::getModuleCode, moduleCode));
        if (module == null) {
            throw new IllegalArgumentException("模块不存在: " + moduleCode);
        }

        SysModuleFieldConfig existing =
                fieldConfigMapper.selectOne(
                        new LambdaQueryWrapper<SysModuleFieldConfig>()
                                .eq(SysModuleFieldConfig::getModuleId, module.getId())
                                .eq(SysModuleFieldConfig::getFieldCode, request.getFieldCode()));
        if (existing != null) {
            throw new IllegalArgumentException("字段已存在: " + request.getFieldCode());
        }

        SysModuleFieldConfig entity =
                moduleConverter.toSysModuleFieldConfig(request, module.getId());
        fieldConfigMapper.insert(entity);

        return moduleConverter.toModuleFieldConfigResp(entity);
    }

    @CacheEvict(value = "moduleFieldConfig", key = "#moduleCode")
    public ModuleFieldConfigResp updateFieldConfig(
            String moduleCode, String fieldCode, ModuleFieldConfigReq request) {
        request.validate();

        SysModule module =
                moduleMapper.selectOne(
                        new LambdaQueryWrapper<SysModule>()
                                .eq(SysModule::getModuleCode, moduleCode));
        if (module == null) {
            throw new IllegalArgumentException("模块不存在: " + moduleCode);
        }

        SysModuleFieldConfig existing =
                fieldConfigMapper.selectOne(
                        new LambdaQueryWrapper<SysModuleFieldConfig>()
                                .eq(SysModuleFieldConfig::getModuleId, module.getId())
                                .eq(SysModuleFieldConfig::getFieldCode, fieldCode));
        if (existing == null) {
            throw new IllegalArgumentException("字段配置不存在: " + fieldCode);
        }

        existing.setFieldName(request.getFieldName());
        existing.setFieldType(request.getFieldType());
        existing.setVisible(request.getVisible());
        existing.setSortOrder(request.getSortOrder());
        existing.setWidth(request.getWidth());
        existing.setAlign(request.getAlign());

        fieldConfigMapper.updateById(existing);

        return moduleConverter.toModuleFieldConfigResp(existing);
    }

    @CacheEvict(value = "moduleFieldConfig", key = "#moduleCode")
    public void deleteFieldConfig(String moduleCode, String fieldCode) {
        SysModule module =
                moduleMapper.selectOne(
                        new LambdaQueryWrapper<SysModule>()
                                .eq(SysModule::getModuleCode, moduleCode));
        if (module == null) {
            throw new IllegalArgumentException("模块不存在: " + moduleCode);
        }

        fieldConfigMapper.delete(
                new LambdaQueryWrapper<SysModuleFieldConfig>()
                        .eq(SysModuleFieldConfig::getModuleId, module.getId())
                        .eq(SysModuleFieldConfig::getFieldCode, fieldCode));
    }

    @CacheEvict(value = "moduleFieldConfig", key = "#moduleCode")
    public List<ModuleFieldConfigResp> updateFieldConfigs(
            String moduleCode, List<ModuleFieldConfigReq> requests) {
        SysModule module =
                moduleMapper.selectOne(
                        new LambdaQueryWrapper<SysModule>()
                                .eq(SysModule::getModuleCode, moduleCode));
        if (module == null) {
            throw new IllegalArgumentException("模块不存在: " + moduleCode);
        }

        for (ModuleFieldConfigReq request : requests) {
            request.validate();
        }

        for (ModuleFieldConfigReq request : requests) {
            SysModuleFieldConfig existing =
                    fieldConfigMapper.selectOne(
                            new LambdaQueryWrapper<SysModuleFieldConfig>()
                                    .eq(SysModuleFieldConfig::getModuleId, module.getId())
                                    .eq(
                                            SysModuleFieldConfig::getFieldCode,
                                            request.getFieldCode()));
            if (existing != null) {
                existing.setFieldName(request.getFieldName());
                existing.setFieldType(request.getFieldType());
                existing.setVisible(request.getVisible());
                existing.setSortOrder(request.getSortOrder());
                existing.setWidth(request.getWidth());
                existing.setAlign(request.getAlign());
                fieldConfigMapper.updateById(existing);
            }
        }

        return moduleConverter.toModuleFieldConfigResps(
                fieldConfigMapper.selectList(
                        new LambdaQueryWrapper<SysModuleFieldConfig>()
                                .eq(SysModuleFieldConfig::getModuleId, module.getId())));
    }
}
