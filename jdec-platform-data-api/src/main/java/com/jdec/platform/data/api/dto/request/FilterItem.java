package com.jdec.platform.data.api.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jdec.platform.data.api.model.predicate.ComparisonPredicate;
import com.jdec.platform.data.api.model.predicate.CompoundPredicate;
import com.jdec.platform.data.api.model.predicate.Predicate;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 结构化过滤条件项 (FilterItem)
 *
 * <p>兼容过渡对象，核心逻辑建议直接使用 {@link Predicate} AST。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "结构化过滤条件项 (基于物理表与列名)")
public class FilterItem implements Serializable {

    @Schema(description = "统一字段标识 (table.column 格式，如 info.status)", example = "info.status")
    @JsonProperty("field")
    private String field;

    @Schema(description = "物理表名 (单表或根表查询时可缺省)", example = "info")
    private String tableName;

    @Schema(description = "物理列名 (如 company_name 或 id)", example = "company_name")
    private String columnName;

    @Schema(
            description = "操作符: EQ, NEQ, LIKE, GT, GTE, LT, LTE, IN, BETWEEN, IS_NULL, IS_NOT_NULL",
            example = "EQ")
    @JsonProperty("op")
    private String operator;

    @Schema(description = "检索值", example = "1")
    private Object value;

    @Schema(description = "父子条件组合逻辑: AND, OR", example = "AND")
    private String logic;

    @Schema(description = "嵌套子过滤条件列表")
    private List<FilterItem> children;

    public String getTableName() {
        if (tableName != null && !tableName.isBlank()) {
            return tableName.trim();
        }
        if (field != null && field.contains(".")) {
            return field.substring(0, field.indexOf('.')).trim();
        }
        return tableName;
    }

    public String getColumnName() {
        if (columnName != null && !columnName.isBlank()) {
            return columnName.contains(".")
                    ? columnName.substring(columnName.lastIndexOf('.') + 1).trim()
                    : columnName.trim();
        }
        if (field != null && field.contains(".")) {
            return field.substring(field.indexOf('.') + 1).trim();
        }
        return field != null ? field.trim() : null;
    }

    public String getField() {
        if (field != null && !field.isBlank()) {
            return field.trim();
        }
        if (tableName != null && columnName != null) {
            return tableName.trim() + "." + getColumnName();
        }
        return columnName;
    }

    public static FilterItem eq(String tableName, String columnName, Object value) {
        return FilterItem.builder()
                .tableName(tableName)
                .columnName(columnName)
                .field(tableName != null ? tableName + "." + columnName : columnName)
                .operator("EQ")
                .value(value)
                .build();
    }

    public static FilterItem eq(String columnName, Object value) {
        return eq(null, columnName, value);
    }

    public static FilterItem neq(String tableName, String columnName, Object value) {
        return FilterItem.builder()
                .tableName(tableName)
                .columnName(columnName)
                .field(tableName != null ? tableName + "." + columnName : columnName)
                .operator("NEQ")
                .value(value)
                .build();
    }

    public static FilterItem neq(String columnName, Object value) {
        return neq(null, columnName, value);
    }

    public static FilterItem like(String tableName, String columnName, Object value) {
        return FilterItem.builder()
                .tableName(tableName)
                .columnName(columnName)
                .field(tableName != null ? tableName + "." + columnName : columnName)
                .operator("LIKE")
                .value(value)
                .build();
    }

    public static FilterItem like(String columnName, Object value) {
        return like(null, columnName, value);
    }

    public static FilterItem in(String tableName, String columnName, Object value) {
        return FilterItem.builder()
                .tableName(tableName)
                .columnName(columnName)
                .field(tableName != null ? tableName + "." + columnName : columnName)
                .operator("IN")
                .value(value)
                .build();
    }

    public static FilterItem in(String columnName, Object value) {
        return in(null, columnName, value);
    }

    public static FilterItem notIn(String tableName, String columnName, Object value) {
        return FilterItem.builder()
                .tableName(tableName)
                .columnName(columnName)
                .field(tableName != null ? tableName + "." + columnName : columnName)
                .operator("NOT_IN")
                .value(value)
                .build();
    }

    public static FilterItem notIn(String columnName, Object value) {
        return notIn(null, columnName, value);
    }

    public static FilterItem gt(String tableName, String columnName, Object value) {
        return FilterItem.builder()
                .tableName(tableName)
                .columnName(columnName)
                .field(tableName != null ? tableName + "." + columnName : columnName)
                .operator("GT")
                .value(value)
                .build();
    }

    public static FilterItem gt(String columnName, Object value) {
        return gt(null, columnName, value);
    }

    public static FilterItem gte(String tableName, String columnName, Object value) {
        return FilterItem.builder()
                .tableName(tableName)
                .columnName(columnName)
                .field(tableName != null ? tableName + "." + columnName : columnName)
                .operator("GTE")
                .value(value)
                .build();
    }

    public static FilterItem gte(String columnName, Object value) {
        return gte(null, columnName, value);
    }

    public static FilterItem lt(String tableName, String columnName, Object value) {
        return FilterItem.builder()
                .tableName(tableName)
                .columnName(columnName)
                .field(tableName != null ? tableName + "." + columnName : columnName)
                .operator("LT")
                .value(value)
                .build();
    }

    public static FilterItem lt(String columnName, Object value) {
        return lt(null, columnName, value);
    }

    public static FilterItem lte(String tableName, String columnName, Object value) {
        return FilterItem.builder()
                .tableName(tableName)
                .columnName(columnName)
                .field(tableName != null ? tableName + "." + columnName : columnName)
                .operator("LTE")
                .value(value)
                .build();
    }

    public static FilterItem lte(String columnName, Object value) {
        return lte(null, columnName, value);
    }

    public static FilterItem isNull(String tableName, String columnName) {
        return FilterItem.builder()
                .tableName(tableName)
                .columnName(columnName)
                .field(tableName != null ? tableName + "." + columnName : columnName)
                .operator("IS_NULL")
                .build();
    }

    public static FilterItem isNull(String columnName) {
        return isNull(null, columnName);
    }

    public static FilterItem isNotNull(String tableName, String columnName) {
        return FilterItem.builder()
                .tableName(tableName)
                .columnName(columnName)
                .field(tableName != null ? tableName + "." + columnName : columnName)
                .operator("IS_NOT_NULL")
                .build();
    }

    public static FilterItem isNotNull(String columnName) {
        return isNotNull(null, columnName);
    }

    public static FilterItem between(
            String tableName, String columnName, Object start, Object end) {
        return FilterItem.builder()
                .tableName(tableName)
                .columnName(columnName)
                .field(tableName != null ? tableName + "." + columnName : columnName)
                .operator("BETWEEN")
                .value(List.of(start, end))
                .build();
    }

    public static FilterItem between(String columnName, Object start, Object end) {
        return between(null, columnName, start, end);
    }

    /** 将当前 FilterItem 转换为统一规范的 Predicate AST */
    public Predicate toPredicate() {
        String f = getField();
        boolean hasSelf = f != null && !f.isBlank();
        Predicate selfPred = null;
        if (hasSelf) {
            selfPred = ComparisonPredicate.of(f, this.operator, this.value);
        }

        if (this.children == null || this.children.isEmpty()) {
            return selfPred;
        }

        List<Predicate> childPreds = new ArrayList<>();
        for (FilterItem child : this.children) {
            if (child != null) {
                Predicate cp = child.toPredicate();
                if (cp != null) {
                    childPreds.add(cp);
                }
            }
        }

        if (childPreds.isEmpty()) {
            return selfPred;
        }

        String log =
                (this.logic != null && !this.logic.isBlank()) ? this.logic.toUpperCase() : "AND";
        Predicate childrenCombined =
                "OR".equals(log)
                        ? CompoundPredicate.or(childPreds)
                        : CompoundPredicate.and(childPreds);

        if (selfPred != null) {
            return "OR".equals(log)
                    ? CompoundPredicate.or(selfPred, childrenCombined)
                    : CompoundPredicate.and(selfPred, childrenCombined);
        }
        return childrenCombined;
    }

    /** 将 Predicate AST 转换为向后兼容的 FilterItem */
    public static FilterItem fromPredicate(Predicate pred) {
        if (pred == null) {
            return null;
        }
        if (pred instanceof ComparisonPredicate comp) {
            String f = comp.getField();
            return FilterItem.builder()
                    .field(f)
                    .tableName(comp.getTableName())
                    .columnName(comp.getColumnName())
                    .operator(comp.getOperator())
                    .value(comp.getValue())
                    .build();
        }
        if (pred instanceof CompoundPredicate comp) {
            List<FilterItem> childItems = new ArrayList<>();
            if (comp.getPredicates() != null) {
                for (Predicate p : comp.getPredicates()) {
                    FilterItem item = fromPredicate(p);
                    if (item != null) {
                        childItems.add(item);
                    }
                }
            }
            return FilterItem.builder().logic(comp.getOperator()).children(childItems).build();
        }
        return null;
    }

    /** 将 Predicate AST 扁平化提取为叶子 FilterItem 列表 */
    public static List<FilterItem> fromPredicateToList(Predicate pred) {
        List<FilterItem> list = new ArrayList<>();
        if (pred == null) {
            return list;
        }
        if (pred instanceof ComparisonPredicate comp) {
            list.add(
                    FilterItem.builder()
                            .field(comp.getField())
                            .tableName(comp.getTableName())
                            .columnName(comp.getColumnName())
                            .operator(comp.getOperator())
                            .value(comp.getValue())
                            .build());
        } else if (pred instanceof CompoundPredicate comp) {
            if (comp.getPredicates() != null) {
                for (Predicate p : comp.getPredicates()) {
                    list.addAll(fromPredicateToList(p));
                }
            }
        }
        return list;
    }
}
