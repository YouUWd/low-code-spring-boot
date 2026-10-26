package com.jdec.platform.config.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/** 模块类型枚举 */
@Getter
public enum ModuleTypeEnum {

    /** 列表模块 */
    LIST("LIST", "列表"),

    /** 详情模块 */
    DETAIL("DETAIL", "详情");

    /** 数据库值 */
    @EnumValue private final String value;

    /** 描述 */
    private final String description;

    ModuleTypeEnum(String value, String description) {
        this.value = value;
        this.description = description;
    }
}
