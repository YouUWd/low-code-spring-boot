package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 字段配置响应 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "字段配置响应")
public class SysFieldResp {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "表名")
    private String tableName;

    @Schema(description = "列名")
    private String columnName;

    @Schema(description = "前端显示名称")
    private String displayName;

    @Schema(description = "关联关系业务编号")
    private String relationBusinessNo;

    @Schema(description = "关联关系名称")
    private String relationName;
}
