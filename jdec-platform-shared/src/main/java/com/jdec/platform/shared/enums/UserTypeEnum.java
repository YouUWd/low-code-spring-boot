package com.jdec.platform.shared.enums;

import lombok.Getter;

/** 用户类型枚举 */
@Getter
public enum UserTypeEnum {
    INSIDE(1, "内部用户"),
    EXTERNAL(2, "外部用户"),
    ;

    private final Integer code;
    private final String desc;

    UserTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
