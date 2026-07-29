package com.jdec.platform.config.biz.audit.dto;

import com.jdec.platform.shared.audit.enums.FieldType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 字段差异 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldDiff {

    /** 字段名（中文） */
    private String fieldName;

    /** 字段代码 */
    private String fieldCode;

    /** 修改前值 */
    private Object oldValue;

    /** 修改后值 */
    private Object newer;

    /** 字段类型 */
    private FieldType fieldType;
}
