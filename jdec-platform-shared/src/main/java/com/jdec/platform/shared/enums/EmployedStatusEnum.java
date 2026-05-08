package com.jdec.platform.shared.enums;

import lombok.Getter;

@Getter
public enum EmployedStatusEnum {
    EMPLOYED_STATUS_ENABLED(1, "启用"),
    EMPLOYED_STATUS_DISABLED(2, "禁用");

    EmployedStatusEnum(Integer code, String value) {
        this.code = code;
        this.value = value;
    }

    private final Integer code;
    private final String value;
}
