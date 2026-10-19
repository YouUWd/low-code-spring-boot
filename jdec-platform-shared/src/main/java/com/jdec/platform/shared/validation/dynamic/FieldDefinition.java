package com.jdec.platform.shared.validation.dynamic;

import java.util.List;

/**
 * 字段定义。
 *
 * @param moduleId 所属模块
 * @param fieldKey 字段标识，同时作为记录 row 的读取键；推荐直接使用 {@code 表名.列名}
 * @param valueType 值类型
 * @param writable 是否允许客户端提交
 * @param applicability 适用条件，为空时始终适用
 * @param rules 字段规则
 */
public record FieldDefinition(
        String moduleId,
        String fieldKey,
        ValueType valueType,
        boolean writable,
        ApplicabilityPolicy applicability,
        List<FieldRule> rules) {

    public FieldDefinition {
        if (moduleId == null || moduleId.isBlank()) {
            throw new IllegalArgumentException("moduleId 不能为空");
        }
        if (fieldKey == null || fieldKey.isBlank()) {
            throw new IllegalArgumentException("fieldKey 不能为空");
        }
        if (valueType == null) {
            valueType = ValueType.ANY;
        }
        if (applicability == null) {
            applicability = AlwaysApplicable.INSTANCE;
        }
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    /** 便捷构造：始终适用、可写。 */
    public FieldDefinition(
            String moduleId, String fieldKey, ValueType valueType, FieldRule... rules) {
        this(
                moduleId,
                fieldKey,
                valueType,
                true,
                AlwaysApplicable.INSTANCE,
                rules == null ? List.of() : List.of(rules));
    }
}
