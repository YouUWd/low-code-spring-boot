package com.jdec.platform.config.api.enums;

import lombok.Getter;

/** 字段类型枚举 */
@Getter
public enum FieldType {
    TEXT("text", "文本"),
    NUMBER("number", "数字"),
    DATE("date", "日期"),
    DATETIME("datetime", "日期时间"),
    BOOLEAN("boolean", "布尔值"),
    SELECT("select", "下拉选择"),
    MULTISELECT("multiselect", "多选"),
    TEXTAREA("textarea", "文本域"),
    EMAIL("email", "邮箱"),
    PHONE("phone", "电话"),
    URL("url", "网址");

    private final String code;
    private final String description;

    FieldType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static FieldType fromCode(String code) {
        for (FieldType type : FieldType.values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown field type: " + code);
    }
}
