package com.jdec.platform.config.api.enums;

import com.jdec.platform.shared.audit.enums.EnumDescribable;
import lombok.Getter;

@Getter
public enum EnableEnum implements EnumDescribable {
    NORMAL(1, "启用"),
    DISABLED(0, "禁用"),
    ;

    private final Integer code;
    private final String desc;

    EnableEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @Override
    public String getDescription() {
        return this.getDesc();
    }

    @Override
    public Object getValue() {
        return this.getCode();
    }
}
