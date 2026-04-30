package com.jdec.platform.shared.metadata.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据库表信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableInfo {
    
    /** 表名 */
    private String tableName;
    
    /** 表注释 */
    private String tableComment;
    
    /** 表的所有列 */
    private List<ColumnInfo> columns;
}
