package com.jdec.platform.dataengine.biz.operatelog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数字带单位字段的变更值。
 *
 * <p>当字段业务类型为「数字带单位」时，remark 的 {@code old}/{@code newer} 不再直接放数值， 而是包装为该结构：{@code value} 为字段数值（旧值为
 * null 时为占位符 {@code "--"}），{@code unit} 为取自 {@code sys_field.unit_column_name} 指向的同表列的实际单位值。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "数字带单位字段的变更值（数值 + 单位）")
public class ColumnValueWithUnitDTO implements Serializable {

    @Schema(description = "字段数值（旧值为 null 时为占位符 '--'）")
    private Object value;

    @Schema(description = "单位值（取自 sys_field.unit_column_name 指向的同表列）")
    private Object unit;
}
