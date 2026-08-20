package com.jdec.platform.config.api.enums;

import com.jdec.platform.shared.audit.enums.EnumDescribable;
import lombok.Getter;

@Getter
public enum InteractionTypeEnum implements EnumDescribable {
    APPLY(1, "申请"),
    EDIT(2, "编辑"),
    VIEW(3, "查看"),
    ;

    private final Integer code;
    private final String desc;

    InteractionTypeEnum(Integer code, String desc) {
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
