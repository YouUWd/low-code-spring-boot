package com.jdec.platform.data.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 结构化过滤条件项 (FilterItem，基于物理表名与列名) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "结构化过滤条件项（基于表名与列名驱动，支持树形条件组）")
public class FilterItem implements Serializable {

    @Schema(description = "所属模块 ID (多模块同表或指定子模块过滤时建议显式指定)", example = "1001")
    private Long moduleId;

    @Schema(description = "物理表名 (单表或根表查询时可缺省)", example = "info")
    private String tableName;

    @Schema(description = "物理列名 (如 company_name 或 id)，单条件时必填", example = "company_name")
    private String columnName;

    @Schema(
            description = "操作符: EQ, NEQ, LIKE, GT, GTE, LT, LTE, IN, BETWEEN, IS_NULL, IS_NOT_NULL",
            example = "LIKE")
    private String operator;

    @Schema(description = "检索值 (单值如 '腾讯科技'，区间如 [90, 100]，多选如 ['A', 'B'])", example = "科技")
    private Object value;

    @Schema(
            description = "父子条件组合逻辑: AND, OR (用于连接当前节点自身条件与 children 子条件集合，默认为 AND)",
            example = "AND")
    private String logic;

    @Schema(description = "嵌套子过滤条件列表（同级 filters 列表及同一 children 列表内部均固定使用 AND 拼接）")
    private List<FilterItem> children;

    public static FilterItem eq(String tableName, String columnName, Object value) {
        return FilterItem.builder()
                .tableName(tableName)
                .columnName(columnName)
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
                .operator("IS_NOT_NULL")
                .build();
    }

    public static FilterItem isNotNull(String columnName) {
        return isNotNull(null, columnName);
    }

    public static FilterItem between(
            String tableName, String columnName, Object lower, Object upper) {
        return FilterItem.builder()
                .tableName(tableName)
                .columnName(columnName)
                .operator("BETWEEN")
                .value(java.util.List.of(lower, upper))
                .build();
    }

    public static FilterItem between(String columnName, Object lower, Object upper) {
        return between(null, columnName, lower, upper);
    }

    public static FilterItem and(List<FilterItem> children) {
        return FilterItem.builder().logic("AND").children(children).build();
    }

    public static FilterItem and(FilterItem... children) {
        return and(
                children != null
                        ? java.util.Arrays.asList(children)
                        : java.util.Collections.emptyList());
    }

    public static FilterItem or(List<FilterItem> children) {
        return FilterItem.builder().logic("OR").children(children).build();
    }

    public static FilterItem or(FilterItem... children) {
        return or(
                children != null
                        ? java.util.Arrays.asList(children)
                        : java.util.Collections.emptyList());
    }

    /** 将当前 FilterItem 转换为统一规范的 Predicate AST */
    public com.jdec.platform.data.api.model.predicate.Predicate toPredicate() {
        boolean hasSelf = this.columnName != null && !this.columnName.isBlank();
        com.jdec.platform.data.api.model.predicate.Predicate selfPred = null;
        if (hasSelf) {
            var colRef =
                    com.jdec.platform.data.api.model.predicate.ColumnRef.of(
                            this.moduleId, this.tableName, this.columnName);
            selfPred =
                    com.jdec.platform.data.api.model.predicate.ComparisonPredicate.of(
                            colRef, this.operator, this.value);
        }

        if (this.children == null || this.children.isEmpty()) {
            return selfPred;
        }

        java.util.List<com.jdec.platform.data.api.model.predicate.Predicate> childPreds =
                new java.util.ArrayList<>();
        for (FilterItem child : this.children) {
            if (child != null) {
                var cp = child.toPredicate();
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
        com.jdec.platform.data.api.model.predicate.Predicate childrenCombined =
                "OR".equals(log)
                        ? com.jdec.platform.data.api.model.predicate.CompoundPredicate.or(
                                childPreds)
                        : com.jdec.platform.data.api.model.predicate.CompoundPredicate.and(
                                childPreds);

        if (selfPred != null) {
            return "OR".equals(log)
                    ? com.jdec.platform.data.api.model.predicate.CompoundPredicate.or(
                            selfPred, childrenCombined)
                    : com.jdec.platform.data.api.model.predicate.CompoundPredicate.and(
                            selfPred, childrenCombined);
        }
        return childrenCombined;
    }

    /** 将 Predicate AST 转换为向后兼容的 FilterItem */
    public static FilterItem fromPredicate(
            com.jdec.platform.data.api.model.predicate.Predicate pred) {
        if (pred == null) {
            return null;
        }
        if (pred instanceof com.jdec.platform.data.api.model.predicate.ComparisonPredicate comp) {
            String tbl = null;
            String col = null;
            Long mid = null;
            if (comp.getExpression()
                    instanceof com.jdec.platform.data.api.model.predicate.ColumnRef cr) {
                tbl = cr.getTableName();
                col = cr.getColumnName();
                mid = cr.getModuleId();
            } else if (comp.getExpression() != null) {
                col = comp.getExpression().toString();
            }
            return FilterItem.builder()
                    .moduleId(mid)
                    .tableName(tbl)
                    .columnName(col)
                    .operator(comp.getOperator())
                    .value(comp.getValue())
                    .build();
        }
        if (pred instanceof com.jdec.platform.data.api.model.predicate.CompoundPredicate comp) {
            java.util.List<FilterItem> childItems = new java.util.ArrayList<>();
            if (comp.getPredicates() != null) {
                for (var p : comp.getPredicates()) {
                    var item = fromPredicate(p);
                    if (item != null) {
                        childItems.add(item);
                    }
                }
            }
            return FilterItem.builder().logic(comp.getOperator()).children(childItems).build();
        }
        return null;
    }
}
