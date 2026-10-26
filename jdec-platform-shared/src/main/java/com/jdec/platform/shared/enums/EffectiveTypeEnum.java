package com.jdec.platform.shared.enums;

import lombok.Getter;

/** 有效期类型枚举 */
@Getter
public enum EffectiveTypeEnum {
    PERMANENT(1, "永久"),
    CUSTOM(2, "自定义"),
    ;

    private final Integer code;
    private final String desc;

    EffectiveTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
