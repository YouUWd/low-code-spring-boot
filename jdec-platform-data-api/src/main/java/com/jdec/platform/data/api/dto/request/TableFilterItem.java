package com.jdec.platform.data.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 物理表结构化过滤条件项 (基于物理表名与列名) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "物理表结构化过滤条件项（基于表名与列名驱动，支持树形条件组）")
public class TableFilterItem implements Serializable {

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
    private List<TableFilterItem> children;

    public static TableFilterItem eq(String tableName, String columnName, Object value) {
        return TableFilterItem.builder()
                .tableName(tableName)
                .columnName(columnName)
                .operator("EQ")
                .value(value)
                .build();
    }

    public static TableFilterItem eq(String columnName, Object value) {
        return eq(null, columnName, value);
    }

    public static TableFilterItem like(String tableName, String columnName, Object value) {
        return TableFilterItem.builder()
                .tableName(tableName)
                .columnName(columnName)
                .operator("LIKE")
                .value(value)
                .build();
    }

    public static TableFilterItem in(String tableName, String columnName, Object value) {
        return TableFilterItem.builder()
                .tableName(tableName)
                .columnName(columnName)
                .operator("IN")
                .value(value)
                .build();
    }

    public static TableFilterItem and(List<TableFilterItem> children) {
        return TableFilterItem.builder().logic("AND").children(children).build();
    }

    public static TableFilterItem or(List<TableFilterItem> children) {
        return TableFilterItem.builder().logic("OR").children(children).build();
    }
}
