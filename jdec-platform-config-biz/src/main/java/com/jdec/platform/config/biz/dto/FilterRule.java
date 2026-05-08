package com.jdec.platform.config.biz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 过滤规则
 */
@Data
@Schema(description = "过滤规则")
public class FilterRule {
    
    @Schema(description = "字段名", example = "age")
    private String field;
    
    @Schema(description = "操作符", 
            example = "eq", 
            allowableValues = {"eq", "neq", "gt", "gte", "lt", "lte", "like", "in", "notIn", "isNull", "isNotNull", "between"})
    private String operator;
    
    @Schema(description = "比较值（对于 in/notIn 操作符，应传递数组；对于 between 操作符，应传递包含两个元素的数组）", 
            example = "18")
    private Object value;
}
