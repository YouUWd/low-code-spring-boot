package com.jdec.platform.config.api.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 字段词云配置 DTO */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "字段词云配置信息")
public class FieldWordCloudDTO {

    @Schema(description = "表名")
    private String tableName;

    @Schema(description = "列名")
    private String columnName;

    @Schema(description = "关联关系业务编号")
    private String relationBusinessNo;

    @Schema(description = "关联关系名称")
    private String relationName;
}
