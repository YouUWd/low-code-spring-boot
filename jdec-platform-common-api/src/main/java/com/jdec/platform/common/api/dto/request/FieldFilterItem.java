package com.jdec.platform.common.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 结构化查询过滤条件项（菜单级契约）
 *
 * <p>以 fieldId (sys_module_field.id) 作为权威凭据，支持嵌套条件组 (logic: AND / OR, children)。
 *
 * <p>区分于 data-api 中基于物理表名与列名的 FilterItem。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "结构化检索条件项（fieldId 权威驱动，支持树形条件组）")
public class FieldFilterItem implements Serializable {

    @Schema(description = "字段权威元数据主键 (sys_module_field.id)，单条件时必填", example = "1001")
    private Long fieldId;

    @Schema(
            description = "操作符: EQ, NEQ, LIKE, GT, GTE, LT, LTE, IN, BETWEEN, IS_NULL, IS_NOT_NULL",
            example = "LIKE")
    private String operator;

    @Schema(description = "检索值 (单值如 \"高等数学\"，区间如 [90, 100]，多选如 [\"A\", \"B\"])")
    private Object value;

    @Schema(description = "条件组合逻辑: AND, OR (当存在 children 子条件时生效)", example = "AND")
    private String logic;

    @Schema(description = "嵌套子过滤条件列表（用于组装复合逻辑组，如 A OR (B AND C)）")
    private List<FieldFilterItem> children;

    public static FieldFilterItem eq(Long fieldId, Object value) {
        return FieldFilterItem.builder().fieldId(fieldId).operator("EQ").value(value).build();
    }

    public static FieldFilterItem neq(Long fieldId, Object value) {
        return FieldFilterItem.builder().fieldId(fieldId).operator("NEQ").value(value).build();
    }

    public static FieldFilterItem like(Long fieldId, Object value) {
        return FieldFilterItem.builder().fieldId(fieldId).operator("LIKE").value(value).build();
    }

    public static FieldFilterItem in(Long fieldId, Object value) {
        return FieldFilterItem.builder().fieldId(fieldId).operator("IN").value(value).build();
    }

    public static FieldFilterItem between(Long fieldId, Object start, Object end) {
        return FieldFilterItem.builder()
                .fieldId(fieldId)
                .operator("BETWEEN")
                .value(List.of(start, end))
                .build();
    }

    public static FieldFilterItem isNull(Long fieldId) {
        return FieldFilterItem.builder().fieldId(fieldId).operator("IS_NULL").build();
    }

    public static FieldFilterItem isNotNull(Long fieldId) {
        return FieldFilterItem.builder().fieldId(fieldId).operator("IS_NOT_NULL").build();
    }

    public static FieldFilterItem gt(Long fieldId, Object value) {
        return FieldFilterItem.builder().fieldId(fieldId).operator("GT").value(value).build();
    }

    public static FieldFilterItem gte(Long fieldId, Object value) {
        return FieldFilterItem.builder().fieldId(fieldId).operator("GTE").value(value).build();
    }

    public static FieldFilterItem lt(Long fieldId, Object value) {
        return FieldFilterItem.builder().fieldId(fieldId).operator("LT").value(value).build();
    }

    public static FieldFilterItem lte(Long fieldId, Object value) {
        return FieldFilterItem.builder().fieldId(fieldId).operator("LTE").value(value).build();
    }

    public static FieldFilterItem and(List<FieldFilterItem> children) {
        return FieldFilterItem.builder().logic("AND").children(children).build();
    }

    public static FieldFilterItem and(FieldFilterItem... children) {
        return and(List.of(children));
    }

    public static FieldFilterItem or(List<FieldFilterItem> children) {
        return FieldFilterItem.builder().logic("OR").children(children).build();
    }

    public static FieldFilterItem or(FieldFilterItem... children) {
        return or(List.of(children));
    }
}
