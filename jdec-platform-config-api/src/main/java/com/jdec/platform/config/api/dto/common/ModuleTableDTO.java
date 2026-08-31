package com.jdec.platform.config.api.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模块关联表信息 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "模块关联表信息")
public class ModuleTableDTO {

    @Schema(description = "关联ID（更新时使用）", example = "1")
    private Long id;

    @Schema(description = "表名", requiredMode = Schema.RequiredMode.REQUIRED, example = "course")
    private String tableName;

    @Schema(description = "表描述", example = "课程表")
    private String tableDesc;

    @Schema(description = "是否主表（1=是，0=否）", example = "1")
    private Integer isPrimary;

    @Schema(description = "左表关联字段 (从表外键)", example = "id")
    private String joinLeftField;

    @Schema(description = "右表关联字段 (主表关联键)", example = "student_id")
    private String joinRightField;

    @Schema(
            description = "关联关系类型",
            example = "PRIMARY",
            allowableValues = {"PRIMARY", "1:1", "1:N", "N:1"})
    private String relationType;

    @Schema(description = "关联表是否只读（0=否，1=是）", example = "0")
    private Integer readOnly;

    @Schema(description = "排序顺序", example = "1")
    private Integer sortOrder;
}
