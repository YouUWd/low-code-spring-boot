package com.jdec.platform.shared.metadata.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据库列信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColumnInfo {
    
    /** 列名 */
    private String columnName;
    
    /** 列类型 */
    private String columnType;
    
    /** 列注释 */
    private String columnComment;

}
