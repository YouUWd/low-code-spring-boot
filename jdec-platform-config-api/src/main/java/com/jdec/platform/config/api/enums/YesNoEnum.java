package com.jdec.platform.config.api.enums;

import com.jdec.platform.shared.audit.enums.EnumDescribable;
import lombok.Getter;

/** 是否枚举 */
@Getter
public enum YesNoEnum implements EnumDescribable {
    YES(1, "是"),
    NO(0, "否"),
    ;

    private final Integer code;
    private final String desc;

    YesNoEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @Override
    public String getDescription() {
        return this.desc;
    }

    @Override
    public Object getValue() {
        return this.code;
    }
}
