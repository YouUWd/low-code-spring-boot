package com.jdec.platform.shared.metadata.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据库 Schema 信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseSchema {
    
    /** 数据库名称 */
    private String database;
    
    /** 数据库中的所有表 */
    private List<TableInfo> tables;
}
