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
                                                    .newValue(newValueObj)
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
                                                .newValue(null)
                                                .build();

                                diffs.add(diff);
                            });
        } catch (Exception e) {
            log.error("构建删除操作差异列表失败: json={}, class={}", json, clazz, e);
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
                                                .newValue(newValue)
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
            }

            if (auditField != null && fieldType == FieldType.ENUM) {
                metadata.enumClass = auditField.enumClass();
            }

            if (auditField != null && fieldType == FieldType.DICT) {
                metadata.dictCategoryAlias = auditField.dictCategoryAlias();
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

            // 查询字典配置获取中文label
            Map<String, String> labelMap =
                    dictFieldQueryService.queryLabelsByValues(metadata.dictCategoryAlias, values);

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

    /** 字段元数据 */
    private static class FieldMetadata {
        String fieldName;
        FieldType fieldType;
        String target;
        String idField;
        String nameField;
        Class<? extends Enum<?>> enumClass;
        String dictCategoryAlias;
    }
}
