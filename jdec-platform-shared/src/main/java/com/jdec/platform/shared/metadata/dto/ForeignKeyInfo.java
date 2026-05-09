package com.jdec.platform.shared.metadata.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 数据库外键信息 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForeignKeyInfo {

    /** 约束名称 */
    private String constraintName;

    /** 列名 */
    private String columnName;

    /** 引用的表名 */
    private String referencedTable;

    /** 引用的列名 */
    private String referencedColumn;
}
