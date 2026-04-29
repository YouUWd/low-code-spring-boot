package com.jdec.platform.config.biz.registry;

import com.jdec.platform.config.biz.entity.SysModule;
import com.jdec.platform.config.biz.entity.SysModuleFieldConfig;
import com.jdec.platform.config.biz.entity.SysModulePermission;
import com.jdec.platform.config.biz.mapper.SysModuleFieldConfigMapper;
import com.jdec.platform.config.biz.mapper.SysModuleMapper;
import com.jdec.platform.config.biz.mapper.SysModulePermissionMapper;
import com.jdec.platform.shared.annotation.ApiModule;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.enums.PermissionType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * 模块 VO 自动注册器
 *
 * <p>在应用启动时扫描所有带有 @ModuleVO 注解的 Record 类， 自动注册到模块管理系统，并创建对应的模块字段配置和权限。
 * 
 * <p>设计特点：
 * <ul>
 *   <li>VO 类的字段信息只收集一次</li>
 *   <li>字段信息在创建字段配置和权限时复用</li>
 *   <li>无需反查数据库获取字段信息</li>
 *   <li>权限初始化逻辑集中在本类中</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class ApiModuleRegistry {

    private final SysModuleMapper sysModuleMapper;
    private final SysModuleFieldConfigMapper fieldConfigMapper;
    private final SysModulePermissionMapper permissionMapper;
    private final ApiModuleScanner scanner;

    /**
     * 字段信息内部类
     *
     * <p>用于在模块注册过程中传递字段的元数据信息
     * <p>包含字段编码、字段名称和字段类型
     */
    private static class FieldInfo {
        private final String fieldCode;
        private final String fieldName;
        private final String fieldType;

        FieldInfo(String fieldCode, String fieldName, String fieldType) {
            this.fieldCode = fieldCode;
            this.fieldName = fieldName;
            this.fieldType = fieldType;
        }

        String getFieldCode() {
            return fieldCode;
        }

        String getFieldName() {
            return fieldName;
        }

        String getFieldType() {
            return fieldType;
        }
    }


    /** 应用启动完成后自动注册所有 VO */
    @EventListener(ApplicationReadyEvent.class)
    public void registerModuleVOs() {
        log.info("开始扫描并注册模块 VO...");

        // 扫描所有带有 @ModuleVO 注解的类
        List<Class<?>> moduleVOClasses = scanner.scanModules();

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
        ApiModule annotation = voClass.getAnnotation(ApiModule.class);
        if (annotation == null || !annotation.enabled()) {
            return;
        }

        String moduleCode = annotation.moduleCode();
        String moduleName = annotation.moduleName();
        String description = StringUtils.defaultIfEmpty(annotation.description(),annotation.moduleName());

        log.info("注册模块: {} ({})", moduleName, moduleCode);

        // 1. 检查模块是否已存在
        LambdaQueryWrapper<SysModule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModule::getModuleCode, moduleCode);
        SysModule existingModule = sysModuleMapper.selectOne(wrapper);
        if (existingModule != null) {
            log.info("模块已存在，跳过创建: {}", moduleCode);
            // 模块已存在，但需要检查字段是否有变更
            syncModuleFieldsAndPermissions(existingModule, voClass);
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

        // 3. 收集 VO 类的字段信息（只收集一次）
        List<FieldInfo> fieldInfos = collectVOFieldInfo(voClass);


        // 4. 创建模块字段配置
        if (!fieldInfos.isEmpty()) {
            registerRecordFields(module, fieldInfos);
        }

        // 5. 初始化模块权限（基于收集的字段信息）
        PermissionType[] permissionTypes = annotation.permissionTypes();
        initializePermissionsForModule(module, fieldInfos, permissionTypes);
    }

    /**
     * 同步模块字段和权限
     *
     * <p>当模块已存在时，检查 VO 类的字段是否有变更
     * <p>如果有新增字段，创建对应的字段配置和权限
     * <p>如果有删除字段，删除对应的字段配置和权限
     *
     * @param module 已存在的模块
     * @param voClass VO 类
     */
    private void syncModuleFieldsAndPermissions(SysModule module, Class<?> voClass) {
        log.info("检查模块字段变更: {} ({})", module.getModuleName(), module.getModuleCode());

        // 获取模块的 permissionTypes 配置
        ApiModule annotation = voClass.getAnnotation(ApiModule.class);
        PermissionType[] permissionTypes = annotation != null ? annotation.permissionTypes() : new PermissionType[0];

        // 1. 收集当前 VO 类的字段信息
        List<FieldInfo> currentFieldInfos = collectVOFieldInfo(voClass);
        Set<String> currentFieldCodes = currentFieldInfos.stream()
                .map(FieldInfo::getFieldCode)
                .collect(Collectors.toSet());

        // 2. 查询数据库中已存在的字段
        LambdaQueryWrapper<SysModuleFieldConfig> fieldWrapper = new LambdaQueryWrapper<>();
        fieldWrapper.eq(SysModuleFieldConfig::getModuleId, module.getId());
        List<SysModuleFieldConfig> existingFields = fieldConfigMapper.selectList(fieldWrapper);
        Set<String> existingFieldCodes = existingFields.stream()
                .map(SysModuleFieldConfig::getFieldCode)
                .collect(Collectors.toSet());

        // 3. 检查新增字段
        Set<String> newFieldCodes = new HashSet<>(currentFieldCodes);
        newFieldCodes.removeAll(existingFieldCodes);
        if (!newFieldCodes.isEmpty()) {
            log.info("检测到新增字段: {}", newFieldCodes);
            List<FieldInfo> newFields = currentFieldInfos.stream()
                    .filter(f -> newFieldCodes.contains(f.getFieldCode()))
                    .collect(Collectors.toList());
            registerRecordFields(module, newFields);
            createNewFieldPermissions(module, newFields, permissionTypes);
        }

        // 4. 检查删除字段
        Set<String> deletedFieldCodes = new HashSet<>(existingFieldCodes);
        deletedFieldCodes.removeAll(currentFieldCodes);
        if (!deletedFieldCodes.isEmpty()) {
            log.info("检测到删除字段: {}", deletedFieldCodes);
            deleteFieldsAndPermissions(module, deletedFieldCodes);
        }

        // 5. 确保模块级权限存在（WRITE 和 DELETE）
        ensureModuleLevelPermissions(module, permissionTypes);

        if (newFieldCodes.isEmpty() && deletedFieldCodes.isEmpty()) {
            log.info("模块字段无变更: {}", module.getModuleCode());
        }
    }

    /**
     * 确保模块级权限存在
     *
     * <p>检查并创建缺失的模块级权限（WRITE 和 DELETE）
     *
     * @param module 模块
     * @param permissionTypes 权限类型数组
     */
    private void ensureModuleLevelPermissions(SysModule module, PermissionType[] permissionTypes) {
        if (permissionTypes == null || permissionTypes.length == 0) {
            // 如果没有指定权限类型，确保所有模块级权限存在
            createModulePermission(module, PermissionType.WRITE);
            createModulePermission(module, PermissionType.DELETE);
        } else {
            // 根据指定的权限类型创建模块级权限
            for (PermissionType permissionType : permissionTypes) {
                if (permissionType == PermissionType.WRITE || permissionType == PermissionType.DELETE) {
                    createModulePermission(module, permissionType);
                }
            }
        }
    }

    /**
     * 为新增字段创建权限
     *
     * <p>根据模块的 permissionTypes 配置创建相应的权限
     *
     * @param module 模块
     * @param newFields 新增字段列表
     * @param permissionTypes 权限类型数组
     */
    private void createNewFieldPermissions(SysModule module, List<FieldInfo> newFields, PermissionType[] permissionTypes) {
        if (permissionTypes == null || permissionTypes.length == 0) {
            // 如果没有指定权限类型，创建所有字段级权限（READ 和 UPDATE）
            for (FieldInfo fieldInfo : newFields) {
                createFieldPermission(module, fieldInfo.getFieldCode(), fieldInfo.getFieldName(), PermissionType.READ);
                createFieldPermission(module, fieldInfo.getFieldCode(), fieldInfo.getFieldName(), PermissionType.UPDATE);
            }
        } else {
            // 根据指定的权限类型创建权限
            for (PermissionType permissionType : permissionTypes) {
                if (permissionType == PermissionType.READ || permissionType == PermissionType.UPDATE) {
                    // 字段级权限
                    for (FieldInfo fieldInfo : newFields) {
                        createFieldPermission(module, fieldInfo.getFieldCode(), fieldInfo.getFieldName(), permissionType);
                    }
                }
                // WRITE 和 DELETE 是模块级权限，不需要为新字段创建
            }
        }
    }

    /**
     * 删除字段和相关权限
     *
     * <p>将字段标记为不可见（visible=false），而不是物理删除
     * <p>这样可以保留历史数据，并在字段重新出现时恢复
     *
     * @param module 模块
     * @param deletedFieldCodes 删除的字段编码集合
     */
    private void deleteFieldsAndPermissions(SysModule module, Set<String> deletedFieldCodes) {
        for (String fieldCode : deletedFieldCodes) {
            // 1. 将字段标记为不可见
            LambdaQueryWrapper<SysModuleFieldConfig> fieldWrapper = new LambdaQueryWrapper<>();
            fieldWrapper.eq(SysModuleFieldConfig::getModuleId, module.getId())
                    .eq(SysModuleFieldConfig::getFieldCode, fieldCode);
            SysModuleFieldConfig fieldConfig = fieldConfigMapper.selectOne(fieldWrapper);
            
            if (fieldConfig != null) {
                fieldConfig.setEnabled(false);
                fieldConfig.setUpdateTime(LocalDateTime.now());
                fieldConfigMapper.updateById(fieldConfig);
                log.debug("字段已禁用: {}.{}", module.getModuleCode(), fieldCode);
            }

            // 2. 禁用相关权限（而不是删除）
            LambdaQueryWrapper<SysModulePermission> permissionWrapper = new LambdaQueryWrapper<>();
            permissionWrapper.eq(SysModulePermission::getModuleId, module.getId())
                    .eq(SysModulePermission::getFieldCode, fieldCode);
            List<SysModulePermission> permissions = permissionMapper.selectList(permissionWrapper);
            
            for (SysModulePermission permission : permissions) {
                permission.setEnabled(false);
                permissionMapper.updateById(permission);
                log.debug("字段权限已禁用: {}.{}", module.getModuleCode(), fieldCode);
            }
        }
    }

    /**
     * 收集 VO 类的字段信息
     *
     * <p>支持 Record 类和普通 Class 类
     * <p>只收集一次，避免重复反射
     *
     * @param voClass VO 类
     * @return 字段信息列表
     */
    private List<FieldInfo> collectVOFieldInfo(Class<?> voClass) {
        List<FieldInfo> fieldInfos = new ArrayList<>();

        if (voClass.isRecord()) {
            // 处理 Record 类
            collectFromRecord(voClass, fieldInfos);
        } else {
            // 处理普通 Class 类
            collectFromClass(voClass, fieldInfos);
        }

        return fieldInfos;
    }

    /**
     * 从 Record 类收集字段信息
     *
     * @param voClass Record 类
     * @param fieldInfos 字段信息列表
     */
    private void collectFromRecord(Class<?> voClass, List<FieldInfo> fieldInfos) {
        RecordComponent[] components = voClass.getRecordComponents();
        if (components == null) {
            return;
        }

        for (RecordComponent component : components) {
            String fieldCode = component.getName();
            String fieldName = getFieldName(component);
            String fieldType = getFieldType(component);

            fieldInfos.add(new FieldInfo(fieldCode, fieldName, fieldType));
        }
    }

    /**
     * 从普通 Class 类收集字段信息
     *
     * <p>通过反射获取 Class 的所有字段
     *
     * @param voClass 普通 Class 类
     * @param fieldInfos 字段信息列表
     */
    private void collectFromClass(Class<?> voClass, List<FieldInfo> fieldInfos) {
        java.lang.reflect.Field[] fields = voClass.getDeclaredFields();

        for (java.lang.reflect.Field field : fields) {
            // 跳过 static 和 final 字段
            int modifiers = field.getModifiers();
            if (java.lang.reflect.Modifier.isStatic(modifiers) || 
                java.lang.reflect.Modifier.isFinal(modifiers)) {
                continue;
            }

            String fieldCode = field.getName();
            String fieldName = getFieldNameFromField(field);
            String fieldType = getFieldTypeFromClass(field.getType());

            fieldInfos.add(new FieldInfo(fieldCode, fieldName, fieldType));
        }
    }

    /**
     * 从 Field 对象获取字段名称
     *
     * @param field Field 对象
     * @return 字段名称
     */
    private String getFieldNameFromField(java.lang.reflect.Field field) {
        // 尝试从 @Schema 注解获取
        Schema schema = field.getAnnotation(Schema.class);
        if (schema != null) {
            String description = schema.description();
            if (description != null && !description.isEmpty()) {
                log.debug("字段 {} 的 Schema description: {}", field.getName(), description);
                return description;
            }
        }

        log.debug("字段 {} 没有 Schema 注解或 description 为空，使用字段名", field.getName());
        return field.getName();
    }

    /**
     * 从 Class 类型获取字段类型
     *
     * @param type 字段类型
     * @return 字段类型字符串
     */
    private String getFieldTypeFromClass(Class<?> type) {
        if (type == Integer.class || type == int.class) {
            return "integer";
        } else if (type == String.class) {
            return "string";
        } else if (type == Boolean.class || type == boolean.class) {
            return "boolean";
        } else if (type == Long.class || type == long.class) {
            return "long";
        } else if (type == Double.class || type == double.class) {
            return "double";
        } else if (type == Float.class || type == float.class) {
            return "float";
        } else if (type == java.time.LocalDateTime.class) {
            return "datetime";
        } else if (type == java.time.LocalDate.class) {
            return "date";
        } else if (type == java.time.LocalTime.class) {
            return "time";
        }
        return "string";
    }

    /** 注册字段配置 */
    private void registerRecordFields(SysModule module, List<FieldInfo> fieldInfos) {
        int sortOrder = 0;
        for (FieldInfo fieldInfo : fieldInfos) {
            sortOrder++;

            // 检查字段是否已存在
            LambdaQueryWrapper<SysModuleFieldConfig> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysModuleFieldConfig::getModuleId, module.getId())
                    .eq(SysModuleFieldConfig::getFieldCode, fieldInfo.getFieldCode());
            SysModuleFieldConfig existing = fieldConfigMapper.selectOne(wrapper);
            
            if (existing != null) {
                // 字段已存在，不做任何处理，交给前端用户控制
                log.debug("字段已存在，保持原配置: {}.{}", module.getModuleCode(), fieldInfo.getFieldCode());
                continue;
            }

            // 创建字段配置
            SysModuleFieldConfig fieldConfig =
                    SysModuleFieldConfig.builder()
                            .moduleId(module.getId())
                            .fieldCode(fieldInfo.getFieldCode())
                            .fieldName(fieldInfo.getFieldName())
                            .fieldType(fieldInfo.getFieldType())
                            .visible(true)
                            .sortOrder(sortOrder)
                            .width(100)
                            .align("left")
                            .enabled(true)
                            .createTime(LocalDateTime.now())
                            .updateTime(LocalDateTime.now())
                            .build();

            fieldConfigMapper.insert(fieldConfig);
            log.debug("字段创建成功: {}.{} ({})", module.getModuleCode(), fieldInfo.getFieldCode(), fieldInfo.getFieldName());
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

    /**
     * 为模块初始化权限
     *
     * <p>基于收集的字段信息直接创建权限，无需查询数据库
     * <p>权限粒度划分：
     * <ul>
     *   <li>write、delete: 模块级权限</li>
     *   <li>read、update: 字段级权限</li>
     * </ul>
     *
     * @param module 模块
     * @param fieldInfos 字段信息列表
     * @param permissionTypes 权限类型数组
     */
    private void initializePermissionsForModule(SysModule module, List<FieldInfo> fieldInfos, PermissionType[] permissionTypes) {
        log.info(
                "开始为模块初始化权限: {} ({}) [permissionTypes={}]",
                module.getModuleName(),
                module.getModuleCode(),
                permissionTypes != null && permissionTypes.length > 0 ? java.util.Arrays.toString(permissionTypes) : "none");

        try {
            if (permissionTypes == null || permissionTypes.length == 0) {
                // 创建所有权限类型
                // 模块级权限: write、delete
                createModulePermission(module, PermissionType.WRITE);
                createModulePermission(module, PermissionType.DELETE);
                // 字段级权限: read、update
                createFieldPermissions(module, fieldInfos);
            } else {
                // 根据指定的权限类型创建权限
                for (PermissionType permissionType : permissionTypes) {
                    switch (permissionType) {
                        case WRITE:
                        case DELETE:
                            // 模块级权限
                            createModulePermission(module, permissionType);
                            break;
                        case READ:
                        case UPDATE:
                            // 字段级权限
                            createFieldPermissions(module, fieldInfos, permissionType);
                            break;
                        default:
                            log.warn("不支持的权限类型: {}", permissionType);
                    }
                }
            }

            log.info("模块权限初始化完成: {}", module.getModuleCode());
        } catch (Exception e) {
            log.error("模块权限初始化失败: {}", module.getModuleCode(), e);
            throw new RuntimeException("权限初始化失败", e);
        }
    }

    /**
     * 创建模块级权限
     *
     * @param module 模块
     * @param permissionType 权限类型
     */
    private void createModulePermission(SysModule module, PermissionType permissionType) {
        String permissionCode = String.format("%s:*:%s", module.getModuleCode(), permissionType.getCode());

        // 检查权限是否已存在
        LambdaQueryWrapper<SysModulePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModulePermission::getPermissionCode, permissionCode);
        SysModulePermission existing = permissionMapper.selectOne(wrapper);

        if (existing != null) {
            log.debug("模块级 {} 权限已存在，跳过创建: {}", permissionType.getCode().toUpperCase(), permissionCode);
            return;
        }

        // 构建权限名称和描述
        String permissionName = buildPermissionName(module.getModuleName(), permissionType);
        String description = buildPermissionDescription(module.getModuleName(), permissionType);

        SysModulePermission permission =
                SysModulePermission.builder()
                        .moduleId(module.getId())
                        .fieldCode("*")
                        .permissionType(permissionType.getCode().toUpperCase())
                        .permissionCode(permissionCode)
                        .permissionName(permissionName)
                        .description(description)
                        .enabled(true)
                        .build();

        permissionMapper.insert(permission);
        log.debug("模块级 {} 权限创建成功: {}", permissionType.getCode().toUpperCase(), permissionCode);
    }

    /**
     * 为模块的字段创建权限
     *
     * <p>基于收集的字段信息创建字段级权限
     * <p>默认创建 READ 和 UPDATE 权限
     *
     * @param module 模块
     * @param fieldInfos 字段信息列表
     */
    private void createFieldPermissions(SysModule module, List<FieldInfo> fieldInfos) {
        for (FieldInfo fieldInfo : fieldInfos) {
            // 为字段创建 READ 权限
            createFieldPermission(module, fieldInfo.getFieldCode(), fieldInfo.getFieldName(), PermissionType.READ);

            // 为字段创建 UPDATE 权限
            createFieldPermission(module, fieldInfo.getFieldCode(), fieldInfo.getFieldName(), PermissionType.UPDATE);
        }
    }

    /**
     * 为模块的字段创建指定类型的权限
     *
     * <p>基于收集的字段信息创建字段级权限
     *
     * @param module 模块
     * @param fieldInfos 字段信息列表
     * @param permissionType 权限类型
     */
    private void createFieldPermissions(SysModule module, List<FieldInfo> fieldInfos, PermissionType permissionType) {
        for (FieldInfo fieldInfo : fieldInfos) {
            createFieldPermission(module, fieldInfo.getFieldCode(), fieldInfo.getFieldName(), permissionType);
        }
    }

    /**
     * 创建字段级权限
     *
     * @param module 模块
     * @param fieldCode 字段编码
     * @param fieldName 字段名称
     * @param permissionType 权限类型
     */
    private void createFieldPermission(SysModule module, String fieldCode, String fieldName, PermissionType permissionType) {
        String permissionCode = String.format("%s:%s:%s", module.getModuleCode(), fieldCode, permissionType.getCode());

        // 检查权限是否已存在
        LambdaQueryWrapper<SysModulePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModulePermission::getPermissionCode, permissionCode);
        SysModulePermission existing = permissionMapper.selectOne(wrapper);

        if (existing != null) {
            log.debug("字段级 {} 权限已存在，跳过创建: {}", permissionType.getCode().toUpperCase(), permissionCode);
            return;
        }

        String permissionName = String.format("%s-%s", fieldName, getOperationName(permissionType));
        String description = String.format("允许%s%s字段", getOperationName(permissionType), fieldName);

        SysModulePermission permission =
                SysModulePermission.builder()
                        .moduleId(module.getId())
                        .fieldCode(fieldCode)
                        .permissionType(permissionType.getCode().toUpperCase())
                        .permissionCode(permissionCode)
                        .permissionName(permissionName)
                        .description(description)
                        .enabled(true)
                        .build();

        permissionMapper.insert(permission);
        log.debug("字段级 {} 权限创建成功: {}", permissionType.getCode().toUpperCase(), permissionCode);
    }

    /**
     * 构建权限名称
     *
     * @param moduleName 模块名称
     * @param permissionType 权限类型
     * @return 权限名称
     */
    private String buildPermissionName(String moduleName, PermissionType permissionType) {
        return String.format("%s-%s", moduleName, getOperationName(permissionType));
    }

    /**
     * 构建权限描述
     *
     * @param moduleName 模块名称
     * @param permissionType 权限类型
     * @return 权限描述
     */
    private String buildPermissionDescription(String moduleName, PermissionType permissionType) {
        return String.format("允许%s%s数据", getOperationName(permissionType), moduleName);
    }

    /**
     * 获取操作名称
     *
     * @param permissionType 权限类型
     * @return 操作名称
     */
    private String getOperationName(PermissionType permissionType) {
        switch (permissionType) {
            case READ:
                return "查看";
            case WRITE:
                return "创建";
            case UPDATE:
                return "修改";
            case DELETE:
                return "删除";
            default:
                return permissionType.getCode();
        }
    }
}
