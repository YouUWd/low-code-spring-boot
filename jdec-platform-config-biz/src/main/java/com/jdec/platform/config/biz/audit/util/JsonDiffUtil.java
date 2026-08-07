package com.jdec.platform.config.biz.audit.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.config.biz.audit.dto.FieldDiff;
import com.jdec.platform.config.biz.audit.service.DictFieldQueryService;
import com.jdec.platform.config.biz.audit.service.RelationFieldQueryService;
import com.jdec.platform.shared.audit.annotation.AuditField;
import com.jdec.platform.shared.audit.enums.EnumDescribable;
import com.jdec.platform.shared.audit.enums.FieldType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.reflect.Field;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** JSON差异对比工具类（基于zjsonpatch） */
@Slf4j
@Component
@RequiredArgsConstructor
public class JsonDiffUtil {

    private final ObjectMapper objectMapper;
    private final RelationFieldQueryService relationFieldQueryService;
    private final DictFieldQueryService dictFieldQueryService;

    /**
     * 对比两个JSON对象，返回字段差异列表（只对比前端传入的字段）
     *
     * @param oldJson 旧JSON字符串（完整快照）
     * @param newJson 新JSON字符串（前端传入，可能只包含部分字段）
     * @param clazz 目标类
     * @return 字段差异列表
     */
    public <T> List<FieldDiff> compareJsonObjects(String oldJson, String newJson, Class<T> clazz) {
        List<FieldDiff> diffs = new ArrayList<>();

        try {
            JsonNode oldNode =
                    oldJson != null
                            ? objectMapper.readTree(oldJson)
                            : objectMapper.createObjectNode();
            JsonNode newNode =
                    newJson != null
                            ? objectMapper.readTree(newJson)
                            : objectMapper.createObjectNode();

            // 构建字段映射缓存
            Map<String, FieldMetadata> fieldMetadataMap = buildFieldMetadataMap(clazz);

            // 只遍历前端传入的字段（newNode的字段）
            newNode.fields()
                    .forEachRemaining(
                            entry -> {
                                String fieldName = entry.getKey();
                                JsonNode newValue = entry.getValue();

                                FieldMetadata metadata = fieldMetadataMap.get(fieldName);
                                if (metadata == null) {
                                    // 字段没有审计配置，跳过
                                    return;
                                }

                                // 从旧快照中获取对应字段的旧值
                                JsonNode oldValue = oldNode.get(fieldName);

                                // 比较新旧值是否相同
                                if (!areJsonNodesEqual(oldValue, newValue)) {
                                    // 提取Java对象值
                                    Object oldValueObj = extractValueFromNode(oldValue);
                                    Object newValueObj = extractValueFromNode(newValue);

                                    // 处理关联类型字段（ID转中文名）
                                    if (metadata.fieldType == FieldType.RELATION) {
                                        oldValueObj = resolveRelationValue(oldValueObj, metadata);
                                        newValueObj = resolveRelationValue(newValueObj, metadata);
                                    }

                                    // 处理枚举类型字段（枚举值转中文描述）
                                    if (metadata.fieldType == FieldType.ENUM) {
                                        oldValueObj = resolveEnumValue(oldValueObj, metadata);
                                        newValueObj = resolveEnumValue(newValueObj, metadata);
                                    }

                                    // 处理字典类型字段（字典值转中文label）
                                    if (metadata.fieldType == FieldType.DICT) {
                                        oldValueObj = resolveDictValue(oldValueObj, metadata);
                                        newValueObj = resolveDictValue(newValueObj, metadata);
                                    }

                                    FieldDiff diff =
                                            FieldDiff.builder()
                                                    .fieldName(metadata.fieldName)
                                                    .fieldCode(fieldName)
                                                    .fieldType(metadata.fieldType)
                                                    .oldValue(oldValueObj)
                                                    .newer(newValueObj)
                                                    .build();

                                    diffs.add(diff);
                                }
                            });
        } catch (Exception e) {
            log.error("JSON对比失败: oldJson={}, newJson={}, class={}", oldJson, newJson, clazz, e);
        }

        return diffs;
    }

    /**
     * 判断两个JsonNode是否相等
     *
     * @param node1 节点1
     * @param node2 节点2
     * @return 是否相等
     */
    private boolean areJsonNodesEqual(JsonNode node1, JsonNode node2) {
        if (node1 == null && node2 == null) {
            return true;
        }
        if (node1 == null || node2 == null) {
            return false;
        }
        return node1.equals(node2);
    }

    /**
     * 构建删除操作的字段差异列表（所有字段都标记为删除）
     *
     * @param json JSON字符串
     * @param clazz 目标类
     * @return 字段差异列表
     */
    public <T> List<FieldDiff> buildDeleteDiffs(String json, Class<T> clazz) {
        List<FieldDiff> diffs = new ArrayList<>();

        try {
            JsonNode node =
                    json != null ? objectMapper.readTree(json) : objectMapper.createObjectNode();

            // 构建字段映射缓存
            Map<String, FieldMetadata> fieldMetadataMap = buildFieldMetadataMap(clazz);

            // 遍历所有字段
            node.fields()
                    .forEachRemaining(
                            entry -> {
                                String fieldName = entry.getKey();
                                JsonNode value = entry.getValue();

                                FieldMetadata metadata = fieldMetadataMap.get(fieldName);
                                if (metadata == null) {
                                    return;
                                }

                                Object oldValue = extractValueFromNode(value);

                                // 处理关联类型字段
                                if (metadata.fieldType == FieldType.RELATION) {
                                    oldValue = resolveRelationValue(oldValue, metadata);
                                }

                                // 处理枚举类型字段
                                if (metadata.fieldType == FieldType.ENUM) {
                                    oldValue = resolveEnumValue(oldValue, metadata);
                                }

                                // 处理字典类型字段
                                if (metadata.fieldType == FieldType.DICT) {
                                    oldValue = resolveDictValue(oldValue, metadata);
                                }

                                FieldDiff diff =
                                        FieldDiff.builder()
                                                .fieldName(metadata.fieldName)
                                                .fieldCode(fieldName)
                                                .fieldType(metadata.fieldType)
                                                .oldValue(oldValue)
                                                .newer(null)
                                                .build();

                                diffs.add(diff);
                            });
        } catch (Exception e) {
            log.error("构建删除操作差异列表失败: json={}, class={}", json, clazz, e);
        }

        return diffs;
    }

    /**
     * 构建删除操作的字段差异列表（通用版本，无需Class参数）
     *
     * <p>从JSON中提取所有字段作为简单类型处理
     *
     * @param json JSON字符串
     * @return 字段差异列表
     */
    public List<FieldDiff> buildDeleteDiffs(String json) {
        List<FieldDiff> diffs = new ArrayList<>();

        try {
            JsonNode node =
                    json != null ? objectMapper.readTree(json) : objectMapper.createObjectNode();

            // 遍历所有字段，作为简单类型处理
            node.fields()
                    .forEachRemaining(
                            entry -> {
                                String fieldName = entry.getKey();
                                JsonNode value = entry.getValue();

                                Object oldValue = extractValueFromNode(value);

                                FieldDiff diff =
                                        FieldDiff.builder()
                                                .fieldName(fieldName) // 使用字段名作为中文名
                                                .fieldCode(fieldName)
                                                .fieldType(FieldType.SIMPLE)
                                                .oldValue(oldValue)
                                                .newer(null)
                                                .build();

                                diffs.add(diff);
                            });
        } catch (Exception e) {
            log.error("构建删除操作差异列表失败: json={}", json, e);
        }

        return diffs;
    }

    /**
     * 构建新增操作的字段差异列表（所有字段的oldValue为空，newValue为传入值）
     *
     * @param json JSON字符串（新增的数据）
     * @param clazz 目标类
     * @return 字段差异列表
     */
    public <T> List<FieldDiff> buildCreateDiffs(String json, Class<T> clazz) {
        List<FieldDiff> diffs = new ArrayList<>();

        try {
            JsonNode node =
                    json != null ? objectMapper.readTree(json) : objectMapper.createObjectNode();

            // 构建字段映射缓存
            Map<String, FieldMetadata> fieldMetadataMap = buildFieldMetadataMap(clazz);

            // 遍历所有字段
            node.fields()
                    .forEachRemaining(
                            entry -> {
                                String fieldName = entry.getKey();
                                JsonNode value = entry.getValue();

                                FieldMetadata metadata = fieldMetadataMap.get(fieldName);
                                if (metadata == null) {
                                    return;
                                }

                                Object newValue = extractValueFromNode(value);

                                // 处理关联类型字段
                                if (metadata.fieldType == FieldType.RELATION) {
                                    newValue = resolveRelationValue(newValue, metadata);
                                }

                                // 处理枚举类型字段
                                if (metadata.fieldType == FieldType.ENUM) {
                                    newValue = resolveEnumValue(newValue, metadata);
                                }

                                // 处理字典类型字段
                                if (metadata.fieldType == FieldType.DICT) {
                                    newValue = resolveDictValue(newValue, metadata);
                                }

                                FieldDiff diff =
                                        FieldDiff.builder()
                                                .fieldName(metadata.fieldName)
                                                .fieldCode(fieldName)
                                                .fieldType(metadata.fieldType)
                                                .oldValue(null)
                                                .newer(newValue)
                                                .build();

                                diffs.add(diff);
                            });
        } catch (Exception e) {
            log.error("构建新增操作差异列表失败: json={}, class={}", json, clazz, e);
        }

        return diffs;
    }

    /**
     * 构建字段元数据映射
     *
     * @param clazz 目标类
     * @return 字段名 -> 字段元数据
     */
    private <T> Map<String, FieldMetadata> buildFieldMetadataMap(Class<T> clazz) {
        Map<String, FieldMetadata> map = new HashMap<>();

        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            AuditField auditField = field.getAnnotation(AuditField.class);
            Schema schema = field.getAnnotation(Schema.class);

            // 如果@AuditField存在且ignore=true，则跳过
            if (auditField != null && auditField.ignore()) {
                continue;
            }

            // 如果既没有@AuditField也没有@Schema，则跳过
            if (auditField == null && schema == null) {
                continue;
            }

            // 获取字段中文名：优先@AuditField.name，其次@Schema.description
            String fieldName =
                    auditField != null
                            ? auditField.name()
                            : (schema != null ? schema.description() : field.getName());

            // 获取字段类型：@AuditField存在时使用其type，否则默认SIMPLE
            FieldType fieldType = auditField != null ? auditField.type() : FieldType.SIMPLE;

            FieldMetadata metadata = new FieldMetadata();
            metadata.fieldName = fieldName;
            metadata.fieldType = fieldType;

            if (auditField != null && fieldType == FieldType.RELATION) {
                metadata.target = auditField.target();
                metadata.idField = auditField.idField();
                metadata.nameField = auditField.nameField();
                if (auditField.targetClass() != void.class) {
                    metadata.targetClass = auditField.targetClass();
                }
            }

            if (auditField != null && fieldType == FieldType.ENUM) {
                metadata.enumClass = auditField.enumClass();
            }

            if (auditField != null && fieldType == FieldType.DICT) {
                metadata.dictCategoryAlias = auditField.dictCategoryAlias();
                // 指定了nameField时，使用该字段映射展示值，否则默认用label
                metadata.nameField = auditField.nameField();
            }

            map.put(field.getName(), metadata);
        }

        return map;
    }

    /**
     * 从JsonNode提取Java对象值
     *
     * @param node JSON节点
     * @return Java对象
     */
    private Object extractValueFromNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        if (node.isTextual()) {
            return node.asText();
        } else if (node.isNumber()) {
            if (node.isLong() || node.isInt()) {
                return node.asLong();
            } else {
                return node.asDouble();
            }
        } else if (node.isBoolean()) {
            return node.asBoolean();
        } else if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(item -> list.add(extractValueFromNode(item)));
            return list;
        } else if (node.isObject()) {
            return node.toString();
        }

        return node.toString();
    }

    /**
     * 解析关联字段值（将ID转换为中文名）
     *
     * @param value 字段值（可能是单个ID或ID列表）
     * @param metadata 字段元数据
     * @return 解析后的值（中文名或中文名列表）
     */
    private Object resolveRelationValue(Object value, FieldMetadata metadata) {
        if (value == null) {
            return null;
        }

        try {
            List<Long> ids = extractIds(value);

            if (ids.isEmpty()) {
                return value;
            }

            // 查询数据库获取中文名
            Map<Long, String> nameMap =
                    relationFieldQueryService.queryNameByIds(
                            metadata.target, metadata.idField, metadata.nameField, ids);

            // 如果只有一个ID，返回单个名称
            if (ids.size() == 1) {
                return nameMap.getOrDefault(ids.get(0), value.toString());
            }

            // 多个ID，返回中文名列表（逗号分隔）
            List<String> names = new ArrayList<>();
            for (Long id : ids) {
                names.add(nameMap.getOrDefault(id, id.toString()));
            }
            return String.join(",", names);

        } catch (Exception e) {
            log.error(
                    "解析关联字段值失败: value={}, target={}, idField={}, nameField={}",
                    value,
                    metadata.target,
                    metadata.idField,
                    metadata.nameField,
                    e);
            return value;
        }
    }

    /**
     * 解析枚举字段值（将枚举值转换为中文描述）
     *
     * @param value 字段值（可能是枚举name、code或ordinal）
     * @param metadata 字段元数据
     * @return 解析后的值（中文描述）
     */
    private Object resolveEnumValue(Object value, FieldMetadata metadata) {
        if (value == null || metadata.enumClass == null) {
            return value;
        }

        try {
            // 获取枚举类的所有常量
            Enum<?>[] enumConstants = metadata.enumClass.getEnumConstants();
            if (enumConstants == null || enumConstants.length == 0) {
                return value;
            }

            // 根据value类型匹配枚举
            Enum<?> matchedEnum = null;

            if (value instanceof String) {
                String strValue = (String) value;
                // 尝试按name匹配
                for (Enum<?> enumConstant : enumConstants) {
                    if (enumConstant.name().equals(strValue)) {
                        matchedEnum = enumConstant;
                        break;
                    }
                }
            } else if (value instanceof Number) {
                Number numValue = (Number) value;
                // 优先按EnumDescribable.getValue()匹配code值（适用于所有实现EnumDescribable的枚举）
                for (Enum<?> enumConstant : enumConstants) {
                    if (enumConstant instanceof EnumDescribable) {
                        Object enumCode = ((EnumDescribable) enumConstant).getValue();
                        if (enumCode instanceof Number
                                && ((Number) enumCode).longValue() == numValue.longValue()) {
                            matchedEnum = enumConstant;
                            break;
                        }
                    }
                }
                // 未匹配到则回退按ordinal匹配（兼容非EnumDescribable枚举）
                if (matchedEnum == null) {
                    int ordinal = numValue.intValue();
                    if (ordinal >= 0 && ordinal < enumConstants.length) {
                        matchedEnum = enumConstants[ordinal];
                    }
                }
            }

            // 如果匹配到枚举，尝试获取描述
            if (matchedEnum != null) {
                // 如果枚举实现了EnumDescribable接口，调用getDescription()
                if (matchedEnum instanceof EnumDescribable) {
                    return ((EnumDescribable) matchedEnum).getDescription();
                }
                // 否则返回枚举的name
                return matchedEnum.name();
            }

            return value;

        } catch (Exception e) {
            log.error("解析枚举字段值失败: value={}, enumClass={}", value, metadata.enumClass, e);
            return value;
        }
    }

    /**
     * 解析字典字段值（将字典值转换为中文label）
     *
     * @param value 字段值（可能是单个值或值列表）
     * @param metadata 字段元数据
     * @return 解析后的值（中文label或中文label列表）
     */
    private Object resolveDictValue(Object value, FieldMetadata metadata) {
        if (value == null || metadata.dictCategoryAlias == null) {
            return value;
        }

        try {
            List<String> values = new ArrayList<>();

            // 处理不同类型的值
            if (value instanceof String) {
                String strValue = (String) value;
                if (strValue.contains(",")) {
                    // 逗号分隔的值列表
                    values.addAll(Arrays.asList(strValue.split(",")));
                } else {
                    values.add(strValue);
                }
            } else if (value instanceof Collection) {
                Collection<?> collection = (Collection<?>) value;
                for (Object item : collection) {
                    if (item != null) {
                        values.add(item.toString());
                    }
                }
            } else {
                values.add(value.toString());
            }

            if (values.isEmpty()) {
                return value;
            }

            // 查询字典配置获取展示字段值（默认label，指定nameField则用该字段）
            Map<String, String> labelMap =
                    dictFieldQueryService.queryLabelsByValues(
                            metadata.dictCategoryAlias, values, metadata.nameField);

            // 如果只有一个值，返回单个label
            if (values.size() == 1) {
                return labelMap.getOrDefault(values.get(0), value.toString());
            }

            // 多个值，返回中文label列表（逗号分隔）
            List<String> labels = new ArrayList<>();
            for (String val : values) {
                labels.add(labelMap.getOrDefault(val, val));
            }
            return String.join(",", labels);

        } catch (Exception e) {
            log.error(
                    "解析字典字段值失败: value={}, categoryAlias={}", value, metadata.dictCategoryAlias, e);
            return value;
        }
    }

    /**
     * 从对象中提取唯一标识字段的值
     *
     * @param obj 对象
     * @return 唯一标识字段的值，如果没有标注则返回空字符串
     */
    public String extractUniqueIdentifier(Object obj) {
        if (obj == null) {
            return "";
        }

        try {
            Class<?> clazz = obj.getClass();
            Field[] fields = clazz.getDeclaredFields();

            for (Field field : fields) {
                AuditField auditField = field.getAnnotation(AuditField.class);
                if (auditField != null && auditField.uniqueIdentifier()) {
                    field.setAccessible(true);
                    Object value = field.get(obj);
                    return value != null ? value.toString() : "";
                }
            }
        } catch (Exception e) {
            log.error("提取唯一标识字段失败: obj={}", obj, e);
        }

        return "";
    }

    /**
     * 从JSON中直接提取唯一标识字段的值（避免因字段反序列化失败导致提取失败）
     *
     * <p>和 {@link #extractUniqueIdentifier(Object)} 功能相同，但直接从JSON字符串中提取，
     * 无需将JSON反序列化为实体对象。适用于快照JSON可能包含无法反序列化的字段（如LocalDateTime的原始时间戳）的场景。
     *
     * @param json JSON字符串（快照数据）
     * @param clazz 实体类（用于查找 @AuditField(uniqueIdentifier = true) 字段名）
     * @return 唯一标识字段的值，如果没有标注则返回空字符串
     */
    public String extractUniqueIdentifierFromJson(String json, Class<?> clazz) {
        if (json == null || json.isEmpty()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                AuditField auditField = field.getAnnotation(AuditField.class);
                if (auditField != null && auditField.uniqueIdentifier()) {
                    JsonNode valueNode = node.get(field.getName());
                    if (valueNode != null && !valueNode.isNull()) {
                        return valueNode.asText();
                    }
                    return "";
                }
            }
        } catch (Exception e) {
            log.error("从JSON提取唯一标识字段失败: class={}", clazz, e);
        }
        return "";
    }

    /**
     * 构建删除操作的单字段差异（从快照中提取指定字段）
     *
     * @param json 快照JSON字符串
     * @param clazz 目标类
     * @param deleteDisplayField 要显示的字段名（Java字段名）
     * @return 字段差异列表（只包含一个FieldDiff）
     */
    public <T> List<FieldDiff> buildDeleteDiffWithDisplayField(
            String json, Class<T> clazz, String deleteDisplayField) {
        if (deleteDisplayField == null || deleteDisplayField.isEmpty()) {
            // 如果没有指定字段，返回空列表
            return Collections.emptyList();
        }

        List<FieldDiff> diffs = new ArrayList<>();

        try {
            JsonNode node =
                    json != null ? objectMapper.readTree(json) : objectMapper.createObjectNode();

            // 构建字段映射缓存
            Map<String, FieldMetadata> fieldMetadataMap = buildFieldMetadataMap(clazz);

            // 从JSON中提取指定字段
            JsonNode fieldValue = node.get(deleteDisplayField);
            if (fieldValue == null || fieldValue.isNull()) {
                log.warn("删除操作指定的显示字段不存在: field={}, json={}", deleteDisplayField, json);
                return Collections.emptyList();
            }

            FieldMetadata metadata = fieldMetadataMap.get(deleteDisplayField);
            if (metadata == null) {
                log.warn("删除操作指定的显示字段没有审计配置: field={}, class={}", deleteDisplayField, clazz);
                return Collections.emptyList();
            }

            Object oldValue = extractValueFromNode(fieldValue);

            // 处理关联类型字段
            if (metadata.fieldType == FieldType.RELATION) {
                oldValue = resolveRelationValue(oldValue, metadata);
            }

            // 处理枚举类型字段
            if (metadata.fieldType == FieldType.ENUM) {
                oldValue = resolveEnumValue(oldValue, metadata);
            }

            // 处理字典类型字段
            if (metadata.fieldType == FieldType.DICT) {
                oldValue = resolveDictValue(oldValue, metadata);
            }

            FieldDiff diff =
                    FieldDiff.builder()
                            .fieldName(metadata.fieldName)
                            .fieldCode(deleteDisplayField)
                            .fieldType(metadata.fieldType)
                            .oldValue(oldValue)
                            .newer("") // 删除操作，new为空字符串
                            .build();

            diffs.add(diff);
        } catch (Exception e) {
            log.error(
                    "构建删除操作单字段差异失败: json={}, class={}, field={}",
                    json,
                    clazz,
                    deleteDisplayField,
                    e);
        }

        return diffs;
    }

    /**
     * 根据字段注解解析字段值（编程式入口）
     *
     * <p>供编程式审计场景使用：读取目标类上 {@link AuditField} 注解的配置，将字段值转换为审计展示值。 支持 RELATION（关联名称或 targetClass
     * 对象）、ENUM（中文描述）、DICT（字典 label）三种转换。
     *
     * @param fieldName Java字段名
     * @param value 字段值
     * @param clazz 字段所在类（需包含 {@link AuditField} 注解）
     * @return 解析后的审计值，无注解或解析失败时原样返回
     */
    public Object resolveFieldValue(String fieldName, Object value, Class<?> clazz) {
        try {
            Map<String, FieldMetadata> fieldMetadataMap = buildFieldMetadataMap(clazz);
            FieldMetadata metadata = fieldMetadataMap.get(fieldName);
            if (metadata == null) {
                return value;
            }
            return resolveValueByType(value, metadata);
        } catch (Exception e) {
            log.error("解析字段值失败: field={}, class={}", fieldName, clazz, e);
            return value;
        }
    }

    /** 按字段元数据解析字段值 */
    private Object resolveValueByType(Object value, FieldMetadata metadata) {
        // 处理关联类型字段（ID转中文名或关联对象）
        if (metadata.fieldType == FieldType.RELATION) {
            if (metadata.targetClass != null) {
                return resolveRelationObject(value, metadata);
            }
            return resolveRelationValue(value, metadata);
        }

        // 处理枚举类型字段（枚举值转中文描述）
        if (metadata.fieldType == FieldType.ENUM) {
            return resolveEnumValue(value, metadata);
        }

        // 处理字典类型字段（字典值转中文label）
        if (metadata.fieldType == FieldType.DICT) {
            return resolveDictValue(value, metadata);
        }

        return value;
    }

    /**
     * 解析关联字段值为整个关联对象
     *
     * <p>将 ID 关联表中的记录按 {@link FieldMetadata#targetClass} 转换后返回对象。
     *
     * @param value 字段值（可能是单个ID或ID列表）
     * @param metadata 字段元数据
     * @return 解析后的对象（单个对象或对象列表）
     */
    private Object resolveRelationObject(Object value, FieldMetadata metadata) {
        if (value == null) {
            return null;
        }

        try {
            List<Long> ids = extractIds(value);
            if (ids.isEmpty()) {
                return value;
            }

            Map<Long, Object> objMap =
                    relationFieldQueryService.queryObjectsByIds(
                            metadata.target, metadata.idField, metadata.targetClass, ids);

            // 如果只有一个ID，返回单个对象
            if (ids.size() == 1) {
                Object obj = objMap.get(ids.get(0));
                return obj != null ? obj : value;
            }

            // 多个ID，返回对象列表
            List<Object> objs = new ArrayList<>();
            for (Long id : ids) {
                Object obj = objMap.get(id);
                objs.add(obj != null ? obj : id);
            }
            return objs;

        } catch (Exception e) {
            log.error(
                    "解析关联对象失败: value={}, target={}, targetClass={}",
                    value,
                    metadata.target,
                    metadata.targetClass,
                    e);
            return value;
        }
    }

    /** 从字段值中提取ID列表 */
    private List<Long> extractIds(Object value) {
        List<Long> ids = new ArrayList<>();

        // 处理不同类型的值
        if (value instanceof Long) {
            ids.add((Long) value);
        } else if (value instanceof Integer) {
            ids.add(((Integer) value).longValue());
        } else if (value instanceof String) {
            String strValue = (String) value;
            if (strValue.contains(",")) {
                // 逗号分隔的ID列表
                for (String id : strValue.split(",")) {
                    try {
                        ids.add(Long.parseLong(id.trim()));
                    } catch (NumberFormatException ignored) {
                    }
                }
            } else {
                try {
                    ids.add(Long.parseLong(strValue));
                } catch (NumberFormatException ignored) {
                }
            }
        } else if (value instanceof Collection) {
            Collection<?> collection = (Collection<?>) value;
            for (Object item : collection) {
                if (item instanceof Long) {
                    ids.add((Long) item);
                } else if (item instanceof Integer) {
                    ids.add(((Integer) item).longValue());
                } else if (item instanceof String) {
                    try {
                        ids.add(Long.parseLong((String) item));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        return ids;
    }

    /** 字段元数据 */
    private static class FieldMetadata {
        String fieldName;
        FieldType fieldType;
        String target;
        String idField;
        String nameField;
        Class<? extends Enum<?>> enumClass;
        String dictCategoryAlias;
        Class<?> targetClass;
    }
}
