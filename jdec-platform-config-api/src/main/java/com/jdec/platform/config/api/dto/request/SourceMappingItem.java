package com.jdec.platform.config.api.dto.request;

import lombok.Data;

/**
 * 字段源映射项
 */
@Data
public class SourceMappingItem {
    
    /**
     * 实体/表名
     */
    private String entity;
    
    /**
     * 字段名
     */
    private String field;
    
    /**
     * 排序顺序
     */
    private Integer sortOrder;
}
