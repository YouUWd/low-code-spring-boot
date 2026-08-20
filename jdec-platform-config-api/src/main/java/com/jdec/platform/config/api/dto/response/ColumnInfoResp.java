package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 列信息响应 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "列详细信息响应")
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

    /** 是否是组合字段: 0-否, 1-是 */
    @Schema(description = "是否是组合字段: 0-否, 1-是（判定依据：SysField 中的 combineInfo 是否不为空）", example = "0")
    private Integer combineFlag;
}
