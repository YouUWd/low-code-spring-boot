package com.jdec.platform.shared.audit.enums;

import lombok.Getter;

/** 字段类型枚举 */
@Getter
public enum FieldType {
    SIMPLE("简单类型"),
    RELATION("关联类型"),
    ENUM("枚举类型"),
    DICT("字典类型");

    private final String description;

    FieldType(String description) {
        this.description = description;
    }
}
