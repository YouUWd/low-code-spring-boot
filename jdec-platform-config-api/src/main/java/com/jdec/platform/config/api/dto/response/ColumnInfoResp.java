package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 数据库列信息响应 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "数据库列信息")
public class ColumnInfoResp {

    /** 列名 */
    @Schema(description = "列名", example = "id")
    private String columnName;

    /** 列类型 */
    @Schema(description = "列数据类型", example = "bigint")
    private String columnType;

    /** 列注释 */
    @Schema(description = "列描述/注释", example = "主键ID")
    private String columnComment;

    // ===== sys_field 配置信息 =====

    /** 前端显示名称 */
    @Schema(description = "前端显示名称（由业务人员配置）", example = "主键ID")
    private String displayName;

    /** 关联关系业务编号 */
    @Schema(description = "关联关系业务编号（如 sys_module 表名）", example = "sys_module")
    private String relationBusinessNo;

    /** 关联关系名称 */
    @Schema(description = "关联关系名称（如 '父模块'）", example = "父模块")
    private String relationName;
}
