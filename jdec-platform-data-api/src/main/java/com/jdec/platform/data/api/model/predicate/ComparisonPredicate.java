package com.jdec.platform.data.api.model.predicate;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字段比较谓词（ComparisonPredicate）。
 *
 * <p>表示具体的原子比较操作，如：`field op value`。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "字段/表达式比较谓词")
public class ComparisonPredicate implements Predicate {

    @Schema(description = "左侧字段或表达式")
    private FieldExpression expression;

    @Schema(
            description =
                    "比较操作符: EQ, NEQ, LIKE, GT, GTE, LT, LTE, IN, NOT_IN, BETWEEN, IS_NULL,"
                            + " IS_NOT_NULL, JSON_CONTAINS, JSON_CONTAINS_ALL",
            example = "EQ")
    private String operator;

    @Schema(description = "右侧比较目标值")
    private Object value;

    @Override
    public String getType() {
        return "comparison";
    }

    public static ComparisonPredicate of(
            FieldExpression expression, String operator, Object value) {
        return ComparisonPredicate.builder()
                .expression(expression)
                .operator(operator)
                .value(value)
                .build();
    }

    public static ComparisonPredicate of(
            String tableName, String columnName, String operator, Object value) {
        return ComparisonPredicate.builder()
                .expression(ColumnRef.of(tableName, columnName))
                .operator(operator)
                .value(value)
                .build();
    }

    public static ComparisonPredicate of(String columnName, String operator, Object value) {
        return ComparisonPredicate.builder()
                .expression(ColumnRef.of(columnName))
                .operator(operator)
                .value(value)
                .build();
    }
}
