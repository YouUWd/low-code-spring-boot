package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 字段源映射响应 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "字段源映射响应")
public class SysFieldSourceMappingResp {

    @Schema(description = "物理表名", example = "student")
    private String table;

    @Schema(description = "表显示名称", example = "学生表")
    private String tableDisplayName;

    @Schema(description = "字段名", example = "last_name")
    private String field;

    @Schema(description = "字段显示名称", example = "姓氏")
    private String fieldDisplayName;

    @Schema(description = "排序顺序（用于多字段组合时的顺序）", example = "1")
    private Integer sortOrder;
}
