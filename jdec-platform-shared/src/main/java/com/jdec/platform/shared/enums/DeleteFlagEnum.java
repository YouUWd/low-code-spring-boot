package com.jdec.platform.shared.enums;

import lombok.Getter;

@Getter
public enum DeleteFlagEnum {
    NO_DELETE(0, "未删除"),
    DELETED(1, "已删除");

    DeleteFlagEnum(Integer code, String value) {
        this.code = code;
        this.value = value;
    }

    private final Integer code;
    private final String value;
}
