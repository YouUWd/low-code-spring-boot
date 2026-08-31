package com.jdec.platform.config.api.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模块简单字段配置 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "模块简单字段配置")
public class ModuleSimpleFieldDTO {

    @Schema(description = "字段ID（更新时使用）", example = "1")
    private Long id;

    @Schema(description = "表名", requiredMode = Schema.RequiredMode.REQUIRED, example = "student")
    private String tableName;

    @Schema(description = "列名", requiredMode = Schema.RequiredMode.REQUIRED, example = "student_no")
    private String columnName;

    @Schema(description = "字段显示名称", example = "学生编号")
    private String displayName;

    @Schema(description = "排序顺序", example = "1")
    private Integer sortOrder;
}
