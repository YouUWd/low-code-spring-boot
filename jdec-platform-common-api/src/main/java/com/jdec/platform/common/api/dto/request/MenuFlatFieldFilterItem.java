package com.jdec.platform.common.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 菜单结构化平铺过滤条件项 (纯单字段平级过滤，面向前端 UI 隔离层)
 *
 * <p>以 fieldId (sys_module_field.id) 作为权威物理凭据，平铺传入，默认以平级 AND 关系连接。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "菜单结构化平铺检索条件项（纯单字段平级过滤，fieldId 驱动）")
public class MenuFlatFieldFilterItem implements Serializable {

    @NotNull(message = "字段 ID 不能为空")
    @Schema(
            description = "字段权威元数据主键 (sys_module_field.id)",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "1001")
    private Long fieldId;

    @Schema(
            description =
                    "操作符: EQ, NEQ, LIKE, GT, GTE, LT, LTE, IN, BETWEEN, IS_NULL, IS_NOT_NULL (默认 EQ)",
            example = "LIKE")
    @Builder.Default
    private String operator = "EQ";

    @Schema(description = "检索值 (单值如 \"高等数学\"，区间如 [90, 100]，多选如 [\"A\", \"B\"])")
    private Object value;

    /** 转换为内部权威 FieldFilterItem 模型 */
    public FieldFilterItem toFieldFilterItem() {
        return FieldFilterItem.builder()
                .fieldId(this.fieldId)
                .operator(this.operator != null ? this.operator : "EQ")
                .value(this.value)
                .build();
    }

    /** 兼容历史方法名 */
    public FieldFilterItem toFilterItem() {
        return toFieldFilterItem();
    }
}
