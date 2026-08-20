package com.jdec.platform.shared.enums;

import lombok.Getter;

@Getter
public enum UserStatusFlagEnum {
    ENABLE(1, "启用"),
    DISABLE(2, "禁用");

    UserStatusFlagEnum(Integer code, String value) {
        this.code = code;
        this.value = value;
    }

    private final Integer code;
    private final String value;
}
