package com.jdec.platform.shared.validation.dynamic;

import com.fasterxml.jackson.databind.JsonNode;

/** 字段值类型。 */
public enum ValueType {
    STRING,
    NUMBER,
    INTEGER,
    BOOLEAN,
    OBJECT,
    ARRAY,
    ANY;

    /** 判断非 null 值是否符合类型。显式 null 由 {@link RequiredRule} 负责。 */
    public boolean matches(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return true;
        }
        return switch (this) {
            case STRING -> value.isTextual();
            case NUMBER -> value.isNumber();
            case INTEGER -> value.isIntegralNumber();
            case BOOLEAN -> value.isBoolean();
            case OBJECT -> value.isObject();
            case ARRAY -> value.isArray();
            case ANY -> true;
        };
    }
}
