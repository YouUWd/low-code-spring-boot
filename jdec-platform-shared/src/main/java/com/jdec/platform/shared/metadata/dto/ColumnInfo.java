package com.jdec.platform.shared.metadata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "数据库列信息")
public class ColumnInfo {
    
    /** 列名 */
    @Schema(description = "列名", example = "id")
    private String columnName;
    
    /** 列类型 */
    @Schema(description = "列数据类型", example = "bigint")
    private String columnType;
    
    /** 列注释 */
    @Schema(description = "列描述/注释", example = "主键ID")
    private String columnComment;

}
