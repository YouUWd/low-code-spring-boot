package com.jdec.platform.config.biz.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字段变更
 *
 * <p>表示单个字段的变更情况
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColumnChange {

    /** 字段中文名 */
    private String name;

    /** 修改前的值（新增时为空） */
    private String old;

    /** 修改后的值（删除时为空） */
    @Builder.Default
    @com.fasterxml.jackson.annotation.JsonProperty("new")
    private String newValue = "";

    /** 字段代码（可选） */
    private String field;
}
