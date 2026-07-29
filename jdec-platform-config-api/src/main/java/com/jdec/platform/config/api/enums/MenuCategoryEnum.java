package com.jdec.platform.config.api.enums;

import com.jdec.platform.shared.audit.enums.EnumDescribable;
import lombok.Getter;

/** 菜单分类枚举 */
@Getter
public enum MenuCategoryEnum implements EnumDescribable {
    BUSINESS(1, "业务菜单"),
    SYSTEM(2, "系统菜单"),
    ;

    private final Integer code;
    private final String desc;

    MenuCategoryEnum(Integer code, String desc) {
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
