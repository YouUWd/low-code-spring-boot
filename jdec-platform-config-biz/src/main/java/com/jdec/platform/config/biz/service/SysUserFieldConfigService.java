package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.config.api.SysUserFieldConfigApi;
import com.jdec.platform.config.api.dto.request.UserFieldConfigReq;
import com.jdec.platform.config.api.dto.response.UserFieldConfigResp;
import com.jdec.platform.config.biz.converter.ModuleConverter;
import com.jdec.platform.config.biz.entity.SysModule;
import com.jdec.platform.config.biz.entity.SysUserFieldConfig;
import com.jdec.platform.config.biz.mapper.SysModuleMapper;
import com.jdec.platform.config.biz.mapper.SysUserFieldConfigMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

/** 用户字段配置服务实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysUserFieldConfigService implements SysUserFieldConfigApi {

    private final SysModuleMapper moduleMapper;
    private final SysUserFieldConfigMapper userFieldConfigMapper;
    private final ModuleConverter moduleConverter;

    public List<UserFieldConfigResp> getUserFieldConfigs(Long userId, String moduleCode) {
        SysModule module =
                moduleMapper.selectOne(
                        new LambdaQueryWrapper<SysModule>()
                                .eq(SysModule::getModuleCode, moduleCode));
        if (module == null) {
            throw new IllegalArgumentException("模块不存在: " + moduleCode);
        }

        List<SysUserFieldConfig> configs =
                userFieldConfigMapper.selectList(
                        new LambdaQueryWrapper<SysUserFieldConfig>()
                                .eq(SysUserFieldConfig::getUserId, userId)
                                .eq(SysUserFieldConfig::getModuleId, module.getId()));
        return moduleConverter.toUserFieldConfigResps(configs);
    }

    @CacheEvict(value = "userFieldConfig", key = "#userId + ':' + #moduleCode")
    public void saveUserFieldConfigs(
            Long userId, String moduleCode, List<UserFieldConfigReq> requests) {
        SysModule module =
                moduleMapper.selectOne(
                        new LambdaQueryWrapper<SysModule>()
                                .eq(SysModule::getModuleCode, moduleCode));
        if (module == null) {
            throw new IllegalArgumentException("模块不存在: " + moduleCode);
        }

        userFieldConfigMapper.delete(
                new LambdaQueryWrapper<SysUserFieldConfig>()
                        .eq(SysUserFieldConfig::getUserId, userId)
                        .eq(SysUserFieldConfig::getModuleId, module.getId()));

        for (UserFieldConfigReq request : requests) {
            SysUserFieldConfig entity =
                    moduleConverter.toSysUserFieldConfig(request, userId, module.getId());
            userFieldConfigMapper.insert(entity);
        }
    }

    @CacheEvict(value = "userFieldConfig", key = "#userId + ':' + #moduleCode")
    public void resetUserFieldConfigs(Long userId, String moduleCode) {
        SysModule module =
                moduleMapper.selectOne(
                        new LambdaQueryWrapper<SysModule>()
                                .eq(SysModule::getModuleCode, moduleCode));
        if (module == null) {
            throw new IllegalArgumentException("模块不存在: " + moduleCode);
        }

        userFieldConfigMapper.delete(
                new LambdaQueryWrapper<SysUserFieldConfig>()
                        .eq(SysUserFieldConfig::getUserId, userId)
                        .eq(SysUserFieldConfig::getModuleId, module.getId()));
    }
}
