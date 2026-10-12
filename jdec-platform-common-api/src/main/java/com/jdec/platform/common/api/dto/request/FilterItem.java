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
 * <p>以 fieldId (sys_module_field.id) 作为权威物理凭据，支持嵌套条件组 (logic: AND / OR, children)。
 *
 * <p>本类原属 data-api，但实际只被菜单聚合链路使用，故归位到 common-api。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "结构化检索条件项（fieldId 权威驱动，支持树形条件组）")
public class FilterItem implements Serializable {

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
    private List<FilterItem> children;

    public static FilterItem eq(Long fieldId, Object value) {
        return FilterItem.builder().fieldId(fieldId).operator("EQ").value(value).build();
    }

    public static FilterItem neq(Long fieldId, Object value) {
        return FilterItem.builder().fieldId(fieldId).operator("NEQ").value(value).build();
    }

    public static FilterItem like(Long fieldId, Object value) {
        return FilterItem.builder().fieldId(fieldId).operator("LIKE").value(value).build();
    }

    public static FilterItem in(Long fieldId, Object value) {
        return FilterItem.builder().fieldId(fieldId).operator("IN").value(value).build();
    }

    public static FilterItem between(Long fieldId, Object start, Object end) {
        return FilterItem.builder()
                .fieldId(fieldId)
                .operator("BETWEEN")
                .value(List.of(start, end))
                .build();
    }

    public static FilterItem isNull(Long fieldId) {
        return FilterItem.builder().fieldId(fieldId).operator("IS_NULL").build();
    }

    public static FilterItem isNotNull(Long fieldId) {
        return FilterItem.builder().fieldId(fieldId).operator("IS_NOT_NULL").build();
    }

    public static FilterItem gt(Long fieldId, Object value) {
        return FilterItem.builder().fieldId(fieldId).operator("GT").value(value).build();
    }

    public static FilterItem gte(Long fieldId, Object value) {
        return FilterItem.builder().fieldId(fieldId).operator("GTE").value(value).build();
    }

    public static FilterItem lt(Long fieldId, Object value) {
        return FilterItem.builder().fieldId(fieldId).operator("LT").value(value).build();
    }

    public static FilterItem lte(Long fieldId, Object value) {
        return FilterItem.builder().fieldId(fieldId).operator("LTE").value(value).build();
    }

    public static FilterItem and(List<FilterItem> children) {
        return FilterItem.builder().logic("AND").children(children).build();
    }

    public static FilterItem and(FilterItem... children) {
        return and(List.of(children));
    }

    public static FilterItem or(List<FilterItem> children) {
        return FilterItem.builder().logic("OR").children(children).build();
    }

    public static FilterItem or(FilterItem... children) {
        return or(List.of(children));
    }
}
