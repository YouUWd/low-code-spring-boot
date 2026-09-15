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
 * <p>核心以 fieldId (sys_module_field.id) 作为权威物理凭据，100% 严密自洽，杜绝歧义与 SQL 注入。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "结构化检索条件项（fieldId 权威驱动）")
public class DynamicFilterItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "字段权威元数据主键 (sys_module_field.id)，优先使用", example = "1001")
    private Long fieldId;

    @Schema(
            description = "操作符: EQ, NEQ, LIKE, GT, GTE, LT, LTE, IN, BETWEEN, IS_NULL, IS_NOT_NULL",
            example = "LIKE")
    private String operator;

    @Schema(description = "检索值 (单值如 \"高等数学\"，区间如 [90, 100]，多选如 [\"A\", \"B\"])")
    private Object value;
}
