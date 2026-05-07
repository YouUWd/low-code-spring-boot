package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 字段源映射项
 */
@Data
@Schema(description = "字段源映射项")
public class SourceMappingItem {
    
    /**
     * 实体/表名
     */
    @Schema(description = "实体/表名", requiredMode = Schema.RequiredMode.REQUIRED, example = "student")
    private String entity;
    
    /**
     * 字段名
     */
    @Schema(description = "字段名", requiredMode = Schema.RequiredMode.REQUIRED, example = "last_name")
    private String field;
    
    /**
     * 排序顺序
     */
    @Schema(description = "排序顺序（用于多字段组合时的顺序）", example = "1")
    private Integer sortOrder;
}
