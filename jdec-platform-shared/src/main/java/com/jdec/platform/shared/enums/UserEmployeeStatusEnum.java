package com.jdec.platform.shared.enums;

import lombok.Getter;

@Getter
public enum UserEmployeeStatusEnum {
    EMPLOYED(1, "在职"),
    DEPARTURE(2, "离职");

    UserEmployeeStatusEnum(Integer code, String value) {
        this.code = code;
        this.value = value;
    }

    private final Integer code;
    private final String value;
}
