package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.config.api.SysModuleApi;
import com.jdec.platform.config.api.dto.request.RegisterModuleReq;
import com.jdec.platform.config.api.dto.request.UpdateModuleReq;
import com.jdec.platform.config.api.dto.response.ModuleEntryResp;
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

/** 系统模块服务实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysModuleService implements SysModuleApi {

    private final SysModuleMapper moduleMapper;
    private final SysModuleFieldConfigMapper fieldConfigMapper;
    private final ModuleConverter moduleConverter;

    // ==================== 模块管理 ====================

    @Cacheable(value = "module", key = "'list'")
    public List<ModuleEntryResp> getModuleList() {
        List<SysModule> modules = moduleMapper.selectList(null);
        return moduleConverter.toModuleEntries(modules);
    }

    @Cacheable(value = "module", key = "#moduleId")
    public ModuleEntryResp getModuleById(Long moduleId) {
        SysModule module = moduleMapper.selectById(moduleId);
        return moduleConverter.toModuleEntry(module);
    }

    @Cacheable(value = "module", key = "'code:' + #moduleCode")
    public ModuleEntryResp getModuleByCode(String moduleCode) {
        SysModule module =
                moduleMapper.selectOne(
                        new LambdaQueryWrapper<SysModule>()
                                .eq(SysModule::getModuleCode, moduleCode));
        return moduleConverter.toModuleEntry(module);
    }

    @CacheEvict(value = "module", allEntries = true)
    public ModuleEntryResp registerModule(RegisterModuleReq request) {
        // 验证请求
        request.validate();

        // 检查编码是否已存在
        long count =
                moduleMapper.selectCount(
                        new LambdaQueryWrapper<SysModule>()
                                .eq(SysModule::getModuleCode, request.getCode()));
        if (count > 0) {
            throw new IllegalArgumentException("模块编码已存在: " + request.getCode());
        }

        // 创建模块
        SysModule module =
                SysModule.builder()
                        .moduleCode(request.getCode())
                        .moduleName(request.getName())
                        .description(request.getDescription())
                        .build();

        moduleMapper.insert(module);
        return moduleConverter.toModuleEntry(module);
    }

    @CacheEvict(value = "module", allEntries = true)
    public ModuleEntryResp updateModule(Long moduleId, UpdateModuleReq request) {
        // 验证请求
        request.validate();

        // 检查模块是否存在
        SysModule module = moduleMapper.selectById(moduleId);
        if (module == null) {
            throw new IllegalArgumentException("模块不存在: " + moduleId);
        }

        // 更新字段
        if (request.getName() != null) {
            module.setModuleName(request.getName());
        }
        if (request.getDescription() != null) {
            module.setDescription(request.getDescription());
        }

        moduleMapper.updateById(module);
        return moduleConverter.toModuleEntry(module);
    }

    @CacheEvict(value = "module", allEntries = true)
    public void deleteModule(Long moduleId) {
        // 检查模块是否存在
        SysModule module = moduleMapper.selectById(moduleId);
        if (module == null) {
            throw new IllegalArgumentException("模块不存在: " + moduleId);
        }

        // 删除模块的字段配置
        fieldConfigMapper.delete(
                new LambdaQueryWrapper<SysModuleFieldConfig>()
                        .eq(SysModuleFieldConfig::getModuleId, moduleId));

        // 删除模块
        moduleMapper.deleteById(moduleId);
    }
}
