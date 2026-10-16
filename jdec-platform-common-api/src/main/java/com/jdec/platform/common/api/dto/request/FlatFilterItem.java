package com.jdec.platform.common.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 基于物理表与列名的平铺单字段过滤条件项 (FlatFilterItem)
 *
 * <p>适用于使用方未知 fieldId、仅知晓当前菜单及其子孙菜单下物理表与字段的查询场景。 支持通过可选的 menuId 指定靠近的菜单以解决跨菜单同名表歧义。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "基于物理表与列名的平铺过滤条件项")
public class FlatFilterItem implements Serializable {

    @Schema(description = "可选: 最靠近该物理表的菜单 ID (用于多级同名表精准定位；缺省时由后端自动推导)", example = "1001")
    private Long menuId;

    @Schema(description = "物理表名 (单表或主表查询时可缺省)", example = "info")
    private String tableName;

    @NotBlank(message = "物理列名不能为空")
    @Schema(
            description = "物理列名 (如 company_name 或 id)",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "company_name")
    private String columnName;

    @Schema(
            description =
                    "操作符: EQ, NEQ, LIKE, GT, GTE, LT, LTE, IN, BETWEEN, IS_NULL, IS_NOT_NULL (默认 EQ)",
            example = "EQ")
    @Builder.Default
    private String operator = "EQ";

    @Schema(description = "检索值 (单值如 \"科技\"，区间如 [90, 100]，多选集合如 [\"A\", \"B\"])")
    private Object value;
}
