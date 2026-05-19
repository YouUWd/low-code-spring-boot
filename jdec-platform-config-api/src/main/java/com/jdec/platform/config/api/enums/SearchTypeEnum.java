package com.jdec.platform.config.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 搜索类型枚举
 *
 * <p>定义列表表头字段的搜索交互模式，纯粹描述数据交互方式，不耦合任何业务语义。 具体的 UI 控件（树形选择、人员选择器等）由前端根据字段的 relation_business_no
 * 等元数据自行决定。
 */
@Getter
public enum SearchTypeEnum {

    /** 文本输入（模糊搜索） */
    INPUT("input", "文本输入"),

    /** 单选（精确匹配） */
    SELECT("select", "单选"),

    /** 多选（IN 匹配） */
    MULTI_SELECT("multi_select", "多选"),

    /** 数值区间 */
    NUMBER_RANGE("number_range", "数值区间"),

    /** 日期区间（年-月-日） */
    DATE_RANGE("date_range", "日期区间"),

    /** 日期时间区间（年-月-日 时:分:秒） */
    DATETIME_RANGE("datetime_range", "日期时间区间"),

    /** 不可搜索 */
    NONE("none", "不可搜索");

    @JsonValue private final String value;

    private final String description;

    SearchTypeEnum(String value, String description) {
        this.value = value;
        this.description = description;
    }
}
