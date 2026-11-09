package com.jdec.platform.data.api.model.predicate;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collection;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字段比较谓词（ComparisonPredicate）。
 *
 * <p>基于 MySQL 标准设计：表示具体的原子比较操作，如：`field op value`。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "字段/表达式比较谓词")
public class ComparisonPredicate implements Predicate {

    @Schema(description = "左侧字段或表达式")
    private FieldExpression expression;

    @Schema(
            description =
                    "比较操作符: EQ, NEQ, LIKE, NOT_LIKE, GT, GTE, LT, LTE, IN, NOT_IN, BETWEEN, IS_NULL,"
                            + " IS_NOT_NULL, JSON_CONTAINS, JSON_CONTAINS_ALL",
            example = "EQ")
    @JsonProperty("op")
    @JsonAlias({"op", "operator"})
    private String operator;

    @Schema(description = "右侧比较目标值")
    @JsonProperty("value")
    private Object value;

    @JsonProperty("field")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getField() {
        if (expression instanceof ColumnRef col) {
            return col.getField();
        }
        return null;
    }

    @JsonProperty("field")
    public void setField(String field) {
        if (field != null && !field.isBlank()) {
            this.expression = ColumnRef.of(field);
        }
    }

    @JsonIgnore
    public String getTableName() {
        if (expression instanceof ColumnRef col) {
            return col.getTableName();
        }
        return null;
    }

    @JsonIgnore
    public String getColumnName() {
        if (expression instanceof ColumnRef col) {
            return col.getColumnName();
        }
        return null;
    }

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

    public static ComparisonPredicate of(String fieldOrColumnName, String operator, Object value) {
        return ComparisonPredicate.builder()
                .expression(ColumnRef.of(fieldOrColumnName))
                .operator(operator)
                .value(value)
                .build();
    }

    public static ComparisonPredicate eq(String field, Object value) {
        return of(field, "EQ", value);
    }

    public static ComparisonPredicate neq(String field, Object value) {
        return of(field, "NEQ", value);
    }

    public static ComparisonPredicate like(String field, Object value) {
        return of(field, "LIKE", value);
    }

    public static ComparisonPredicate gt(String field, Object value) {
        return of(field, "GT", value);
    }

    public static ComparisonPredicate gte(String field, Object value) {
        return of(field, "GTE", value);
    }

    public static ComparisonPredicate lt(String field, Object value) {
        return of(field, "LT", value);
    }

    public static ComparisonPredicate lte(String field, Object value) {
        return of(field, "LTE", value);
    }

    public static ComparisonPredicate in(String field, Collection<?> values) {
        return of(field, "IN", values);
    }

    public static ComparisonPredicate notIn(String field, Collection<?> values) {
        return of(field, "NOT_IN", values);
    }

    public static ComparisonPredicate between(String field, Object start, Object end) {
        return of(field, "BETWEEN", List.of(start, end));
    }

    public static ComparisonPredicate isNull(String field) {
        return of(field, "IS_NULL", null);
    }

    public static ComparisonPredicate isNotNull(String field) {
        return of(field, "IS_NOT_NULL", null);
    }
}
