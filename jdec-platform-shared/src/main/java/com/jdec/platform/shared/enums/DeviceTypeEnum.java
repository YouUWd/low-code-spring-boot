package com.jdec.platform.shared.enums;

import lombok.Getter;

@Getter
public enum DeviceTypeEnum {
    PC(0, "PC"),
    ;

    DeviceTypeEnum(Integer code, String value) {
        this.code = code;
        this.value = value;
    }

    private final Integer code;
    private final String value;
}
