package com.jdec.platform.shared.enums;

import com.jdec.platform.shared.audit.enums.EnumDescribable;
import lombok.Getter;

@Getter
public enum CommonStatusEnum implements EnumDescribable {
    DISABLE(0, "禁用"),
    ENABLE(1, "启用");

    CommonStatusEnum(Integer code, String value) {
        this.code = code;
        this.value = value;
    }

    private final Integer code;
    private final String value;

    @Override
    public String getDescription() {
        return this.value;
    }

    @Override
    public Object getValue() {
        return this.code;
    }
}
