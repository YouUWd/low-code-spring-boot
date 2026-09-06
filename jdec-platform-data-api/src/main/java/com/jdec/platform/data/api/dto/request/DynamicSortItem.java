package com.jdec.platform.data.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 结构化排序规则项
 *
 * <p>基于三元组 (moduleId, tableName, columnName) 精准定位排序目标字段， 并支持指定排序方向 (ASC / DESC)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "结构化排序规则项")
public class DynamicSortItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "归属模块 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "101")
    private Long moduleId;

    @Schema(
            description = "物理表名 (主表或伴生表)",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "clazz")
    private String tableName;

    @Schema(
            description = "排序列名/属性名",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "class_name")
    private String columnName;

    @Schema(
            description = "排序方向: ASC 或 DESC",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "ASC")
    @Builder.Default
    private String direction = "ASC";
}
