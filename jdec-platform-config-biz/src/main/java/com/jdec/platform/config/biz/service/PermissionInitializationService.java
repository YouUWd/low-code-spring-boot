package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.config.biz.entity.SysModule;
import com.jdec.platform.config.biz.entity.SysModuleFieldConfig;
import com.jdec.platform.config.biz.entity.SysModulePermission;
import com.jdec.platform.config.biz.mapper.SysModuleFieldConfigMapper;
import com.jdec.platform.config.biz.mapper.SysModuleMapper;
import com.jdec.platform.config.biz.mapper.SysModulePermissionMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 权限初始化服务 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class PermissionInitializationService {

    private final SysModulePermissionMapper permissionMapper;
    private final SysModuleMapper moduleMapper;
    private final SysModuleFieldConfigMapper fieldConfigMapper;

    /** 为模块初始化权限 */
    public void initializePermissionsForModule(Long moduleId) {
        SysModule module = moduleMapper.selectById(moduleId);
        if (module == null) {
            log.warn("模块不存在，无法初始化权限: moduleId={}", moduleId);
            return;
        }

        log.info("开始为模块初始化权限: {} ({})", module.getModuleName(), module.getModuleCode());

        try {
            // 1. 创建模块级 WRITE 权限
            createModuleWritePermission(module);

            // 2. 为模块的所有字段创建 READ 和 UPDATE 权限
            createFieldPermissions(module);

            log.info("模块权限初始化完成: {}", module.getModuleCode());
        } catch (Exception e) {
            log.error("模块权限初始化失败: {}", module.getModuleCode(), e);
            throw new RuntimeException("权限初始化失败", e);
        }
    }

    /** 创建模块级 WRITE 权限 */
    private void createModuleWritePermission(SysModule module) {
        String permissionCode = String.format("%s:*:write", module.getModuleCode());

        // 检查权限是否已存在
        LambdaQueryWrapper<SysModulePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModulePermission::getPermissionCode, permissionCode);
        SysModulePermission existing = permissionMapper.selectOne(wrapper);

        if (existing != null) {
            log.debug("模块级 WRITE 权限已存在，跳过创建: {}", permissionCode);
            return;
        }

        SysModulePermission permission =
                SysModulePermission.builder()
                        .moduleId(module.getId())
                        .fieldCode("*")
                        .permissionType("WRITE")
                        .permissionCode(permissionCode)
                        .permissionName(String.format("%s-创建", module.getModuleName()))
                        .description(String.format("允许创建%s数据", module.getModuleName()))
                        .isEnabled(1)
                        .build();

        permissionMapper.insert(permission);
        log.debug("模块级 WRITE 权限创建成功: {}", permissionCode);
    }

    /** 为模块的所有字段创建 READ 和 UPDATE 权限 */
    private void createFieldPermissions(SysModule module) {
        // 查询模块的所有字段
        LambdaQueryWrapper<SysModuleFieldConfig> fieldWrapper = new LambdaQueryWrapper<>();
        fieldWrapper.eq(SysModuleFieldConfig::getModuleId, module.getId());
        List<SysModuleFieldConfig> fields = fieldConfigMapper.selectList(fieldWrapper);

        if (fields == null || fields.isEmpty()) {
            log.debug("模块没有字段配置，跳过字段权限创建: {}", module.getModuleCode());
            return;
        }

        for (SysModuleFieldConfig field : fields) {
            createReadPermission(module, field);
            createUpdatePermission(module, field);
        }
    }

    /** 为字段创建 READ 权限 */
    private void createReadPermission(SysModule module, SysModuleFieldConfig field) {
        String permissionCode =
                String.format("%s:%s:read", module.getModuleCode(), field.getFieldCode());

        // 检查权限是否已存在
        LambdaQueryWrapper<SysModulePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModulePermission::getPermissionCode, permissionCode);
        SysModulePermission existing = permissionMapper.selectOne(wrapper);

        if (existing != null) {
            log.debug("READ 权限已存在，跳过创建: {}", permissionCode);
            return;
        }

        SysModulePermission permission =
                SysModulePermission.builder()
                        .moduleId(module.getId())
                        .fieldCode(field.getFieldCode())
                        .permissionType("READ")
                        .permissionCode(permissionCode)
                        .permissionName(String.format("%s-查看", field.getFieldName()))
                        .description(String.format("允许查看%s字段", field.getFieldName()))
                        .isEnabled(1)
                        .build();

        permissionMapper.insert(permission);
        log.debug("READ 权限创建成功: {}", permissionCode);
    }

    /** 为字段创建 UPDATE 权限 */
    private void createUpdatePermission(SysModule module, SysModuleFieldConfig field) {
        String permissionCode =
                String.format("%s:%s:update", module.getModuleCode(), field.getFieldCode());

        // 检查权限是否已存在
        LambdaQueryWrapper<SysModulePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModulePermission::getPermissionCode, permissionCode);
        SysModulePermission existing = permissionMapper.selectOne(wrapper);

        if (existing != null) {
            log.debug("UPDATE 权限已存在，跳过创建: {}", permissionCode);
            return;
        }

        SysModulePermission permission =
                SysModulePermission.builder()
                        .moduleId(module.getId())
                        .fieldCode(field.getFieldCode())
                        .permissionType("UPDATE")
                        .permissionCode(permissionCode)
                        .permissionName(String.format("%s-修改", field.getFieldName()))
                        .description(String.format("允许修改%s字段", field.getFieldName()))
                        .isEnabled(1)
                        .build();

        permissionMapper.insert(permission);
        log.debug("UPDATE 权限创建成功: {}", permissionCode);
    }

    /** 为所有模块初始化权限 */
    public void initializePermissionsForAllModules() {
        log.info("开始为所有模块初始化权限...");

        List<SysModule> modules = moduleMapper.selectList(null);
        if (modules == null || modules.isEmpty()) {
            log.info("没有模块需要初始化权限");
            return;
        }

        for (SysModule module : modules) {
            try {
                initializePermissionsForModule(module.getId());
            } catch (Exception e) {
                log.error("模块权限初始化失败: {}", module.getModuleCode(), e);
            }
        }

        log.info("所有模块权限初始化完成");
    }
}
