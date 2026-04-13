package com.jdec.platform.config.biz.registry;

import com.jdec.platform.config.biz.entity.SysModule;
import com.jdec.platform.config.biz.entity.SysModuleFieldConfig;
import com.jdec.platform.config.biz.mapper.SysModuleFieldConfigMapper;
import com.jdec.platform.config.biz.mapper.SysModuleMapper;
import com.jdec.platform.config.biz.service.PermissionInitializationService;
import com.jdec.platform.shared.annotation.ModuleVO;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 模块 VO 自动注册器
 *
 * <p>在应用启动时扫描所有带有 @ModuleVO 注解的 Record 类， 自动注册到模块管理系统，并创建对应的模块字段配置和权限。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class ModuleVORegistry {

    private final SysModuleMapper sysModuleMapper;
    private final SysModuleFieldConfigMapper fieldConfigMapper;
    private final ModuleVOScanner moduleVOScanner;
    private final PermissionInitializationService permissionInitializationService;

    /** 应用启动完成后自动注册所有 VO */
    @EventListener(ApplicationReadyEvent.class)
    public void registerModuleVOs() {
        log.info("开始扫描并注册模块 VO...");

        // 扫描所有带有 @ModuleVO 注解的类
        List<Class<?>> moduleVOClasses = moduleVOScanner.scanModuleVOClasses();

        for (Class<?> voClass : moduleVOClasses) {
            try {
                registerModuleVO(voClass);
            } catch (Exception e) {
                log.error("注册模块 VO 失败: {}", voClass.getName(), e);
            }
        }

        log.info("模块 VO 注册完成，共注册 {} 个模块", moduleVOClasses.size());
    }

    /** 注册单个模块 VO */
    private void registerModuleVO(Class<?> voClass) {
        ModuleVO annotation = voClass.getAnnotation(ModuleVO.class);
        if (annotation == null || !annotation.enabled()) {
            return;
        }

        String moduleCode = annotation.moduleCode();
        String moduleName = annotation.moduleName();
        String description = annotation.description();

        log.info("注册模块: {} ({})", moduleName, moduleCode);

        // 1. 检查模块是否已存在
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysModule> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(SysModule::getModuleCode, moduleCode);
        SysModule existingModule = sysModuleMapper.selectOne(wrapper);
        if (existingModule != null) {
            log.info("模块已存在，跳过创建: {}", moduleCode);
            return;
        }

        // 2. 创建模块
        SysModule module =
                SysModule.builder()
                        .moduleCode(moduleCode)
                        .moduleName(moduleName)
                        .description(description)
                        .build();

        sysModuleMapper.insert(module);
        log.info("模块创建成功: {} (ID: {})", moduleName, module.getId());

        // 3. 创建模块字段配置
        if (voClass.isRecord()) {
            registerRecordFields(module, voClass);
        }

        // 4. 初始化模块权限
        permissionInitializationService.initializePermissionsForModule(module.getId());
    }

    /** 为 Record 类注册字段配置 */
    private void registerRecordFields(SysModule module, Class<?> recordClass) {
        RecordComponent[] components = recordClass.getRecordComponents();
        if (components == null || components.length == 0) {
            return;
        }

        int sortOrder = 0;
        for (RecordComponent component : components) {
            sortOrder++;

            String fieldCode = component.getName();
            String fieldName = getFieldName(component);
            String fieldType = getFieldType(component);

            // 检查字段是否已存在
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysModuleFieldConfig>
                    wrapper =
                            new com.baomidou.mybatisplus.core.conditions.query
                                    .LambdaQueryWrapper<>();
            wrapper.eq(SysModuleFieldConfig::getModuleId, module.getId())
                    .eq(SysModuleFieldConfig::getFieldCode, fieldCode);
            SysModuleFieldConfig existing = fieldConfigMapper.selectOne(wrapper);
            if (existing != null) {
                log.debug("字段已存在，跳过创建: {}.{}", module.getModuleCode(), fieldCode);
                continue;
            }

            // 创建字段配置
            SysModuleFieldConfig fieldConfig =
                    SysModuleFieldConfig.builder()
                            .moduleId(module.getId())
                            .fieldCode(fieldCode)
                            .fieldName(fieldName)
                            .fieldType(fieldType)
                            .visible(true)
                            .sortOrder(sortOrder)
                            .width(100)
                            .align("left")
                            .createTime(LocalDateTime.now())
                            .updateTime(LocalDateTime.now())
                            .build();

            fieldConfigMapper.insert(fieldConfig);
            log.debug("字段创建成功: {}.{} ({})", module.getModuleCode(), fieldCode, fieldName);
        }
    }

    /** 从 @Schema 注解获取字段名称 */
    private String getFieldName(RecordComponent component) {
        // 首先尝试从 RecordComponent 获取注解
        Schema schema = component.getAnnotation(Schema.class);
        if (schema != null) {
            String description = schema.description();
            if (description != null && !description.isEmpty()) {
                log.debug("字段 {} 的 Schema description: {}", component.getName(), description);
                return description;
            }
        }

        // 如果 RecordComponent 没有注解，尝试从 record 的 accessor 方法获取
        try {
            java.lang.reflect.Method accessor = component.getAccessor();
            if (accessor != null) {
                schema = accessor.getAnnotation(Schema.class);
                if (schema != null) {
                    String description = schema.description();
                    if (description != null && !description.isEmpty()) {
                        log.debug(
                                "字段 {} 从 accessor 方法获取 Schema description: {}",
                                component.getName(),
                                description);
                        return description;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("从 accessor 方法获取注解失败: {}", component.getName(), e);
        }

        log.debug("字段 {} 没有 Schema 注解或 description 为空，使用字段名", component.getName());
        return component.getName();
    }

    /** 获取字段类型 */
    private String getFieldType(RecordComponent component) {
        Class<?> type = component.getType();
        if (type == Integer.class) {
            return "integer";
        } else if (type == String.class) {
            return "string";
        } else if (type == Boolean.class) {
            return "boolean";
        } else if (type == Long.class) {
            return "long";
        } else if (type == Double.class) {
            return "double";
        } else if (type == java.time.LocalDateTime.class) {
            return "datetime";
        } else if (type == java.time.LocalDate.class) {
            return "date";
        }
        return "string";
    }
}
