package com.jdec.platform.data.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 结构化查询过滤条件项
 *
 * <p>基于三元组 (moduleId, tableName, columnName) 精准定位字段坐标， 检索操作符默认依据表头配置的 searchType 自动推导，亦支持显式传入
 * operator 覆盖。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "结构化检索条件项")
public class DynamicFilterItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "归属模块ID (用于跨模块同名表字段消歧，可选，默认根模块ID)", example = "102")
    private Long moduleId;

    @Schema(
            description = "模块血缘链路 (用于同表同字段跨模块语义消歧，如 [101, 103] 或 [101, 104])",
            example = "[101, 103]")
    private java.util.List<Long> modulePath;

    @Schema(description = "物理表名 (可选，默认按字段元数据自动解析)", example = "student_course")
    private String tableName;

    @Schema(
            description = "物理列名/属性名",
            example = "course_name",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String columnName;

    @Schema(description = "检索值 (单值如 \"高等数学\"，区间如 [90, 100]，多选如 [\"A\", \"B\"])")
    private Object value;

    @Schema(
            description =
                    "操作符 (可选，默认由表头配置的 searchType 自动推导): EQ, NEQ, LIKE, GT, GTE, LT, LTE, IN, BETWEEN, IS_NULL, IS_NOT_NULL",
            example = "LIKE")
    private String operator;
}
