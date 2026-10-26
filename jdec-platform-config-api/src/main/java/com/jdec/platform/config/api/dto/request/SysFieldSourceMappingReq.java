package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 字段源映射保存请求 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "字段源映射保存请求")
public class SysFieldSourceMappingReq {

    @Schema(description = "物理表名", requiredMode = Schema.RequiredMode.REQUIRED, example = "student")
    private String table;

    @Schema(description = "字段名", requiredMode = Schema.RequiredMode.REQUIRED, example = "last_name")
    private String field;

    @Schema(description = "排序顺序（用于多字段组合时的顺序）", example = "1")
    private Integer sortOrder;
}
