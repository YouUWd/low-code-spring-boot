package com.jdec.platform.config.api.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 全局物理表关联关系 DTO */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "全局物理表关联关系信息")
public class TableRelationDTO {

    @Schema(description = "关联ID", example = "1")
    private Long id;

    @Schema(description = "主表名", requiredMode = Schema.RequiredMode.REQUIRED, example = "student")
    private String mainTable;

    @Schema(description = "主表关联字段", example = "id")
    private String mainField;

    @Schema(
            description = "被关联表名",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "student_profile")
    private String joinTable;

    @Schema(description = "被关联表关联字段", example = "student_id")
    private String joinField;

    @Schema(
            description = "关系类型: 1:1, 1:N, N:1",
            allowableValues = {"1:1", "1:N", "N:1"},
            example = "1:1")
    private String relationType;

    @Schema(description = "关联说明", example = "学生扩展档案")
    private String description;
}
