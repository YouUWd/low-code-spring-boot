package com.jdec.platform.dataengine.api.dto.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模块字段配置 DTO */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "模块字段配置")
public class ModuleFieldDTO {
    @Schema(description = "字段ID（更新时使用）", example = "1")
    private Long id;

    @Schema(description = "模块ID", example = "1")
    @JsonIgnore
    private Long moduleId;

    @Schema(description = "表名", requiredMode = Schema.RequiredMode.REQUIRED, example = "student")
    @JsonIgnore
    private String tableName;

    @Schema(description = "列名", requiredMode = Schema.RequiredMode.REQUIRED, example = "student_no")
    @JsonIgnore
    private String columnName;

    @Schema(description = "字段显示名称", example = "学生编号")
    private String displayName;

    @Schema(description = "业务唯一标识顺序", example = "1")
    @JsonIgnore
    private Integer bizKeyOrder;

    @Schema(description = "排序顺序", example = "1")
    @JsonIgnore
    private Integer sortOrder;

    private Boolean apply = true;

    private Boolean view = true;

    private Boolean edit = true;
}
