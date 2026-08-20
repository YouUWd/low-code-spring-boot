package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 数据库表信息响应 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "数据库表信息")
public class TableInfoResp {

    /** 表名 */
    @Schema(description = "表名", example = "sys_module")
    private String tableName;

    /** 表注释 */
    @Schema(description = "表描述/注释", example = "模块基本信息表")
    private String tableComment;

    /** 列信息列表 */
    @Schema(description = "列详细信息列表")
    private List<ColumnInfoResp> columns;
}
