package com.jdec.platform.config.api.enums;

import com.jdec.platform.shared.audit.enums.EnumDescribable;
import lombok.Getter;

@Getter
public enum ShowStatusEnum implements EnumDescribable {
    NORMAL(1, "显示"),
    DISABLED(0, "隐藏"),
    ;

    private final Integer code;
    private final String desc;

    ShowStatusEnum(Integer code, String desc) {
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
