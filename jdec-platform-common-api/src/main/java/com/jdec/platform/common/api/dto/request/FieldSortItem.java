package com.jdec.platform.common.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 结构化排序规则项（菜单级契约）
 *
 * <p>以 fieldId (sys_module_field.id) 作为权威凭据，支持指定排序方向 (ASC / DESC)。
 *
 * <p>区分于 data-api 中基于物理表名与列名的 SortItem。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "结构化排序规则项（fieldId 权威驱动）")
public class FieldSortItem implements Serializable {

    @Schema(description = "字段权威元数据主键 (sys_module_field.id)，优先使用", example = "1001")
    private Long fieldId;

    @Schema(
            description = "排序方向: ASC 或 DESC",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "ASC")
    @Builder.Default
    private String direction = "ASC";

    public static FieldSortItem asc(Long fieldId) {
        return FieldSortItem.builder().fieldId(fieldId).direction("ASC").build();
    }

    public static FieldSortItem desc(Long fieldId) {
        return FieldSortItem.builder().fieldId(fieldId).direction("DESC").build();
    }
}
