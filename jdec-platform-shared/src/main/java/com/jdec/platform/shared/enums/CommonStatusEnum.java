package com.jdec.platform.shared.enums;

import lombok.Getter;

@Getter
public enum CommonStatusEnum {
    DISABLE(0, "禁用"),
    ENABLE(1, "启用");

    CommonStatusEnum(Integer code, String value) {
        this.code = code;
        this.value = value;
    }

    private final Integer code;
    private final String value;
}
