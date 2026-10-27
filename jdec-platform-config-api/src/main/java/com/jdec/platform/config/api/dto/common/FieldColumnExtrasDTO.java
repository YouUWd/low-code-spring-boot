package com.jdec.platform.config.api.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 字段列扩展配置 DTO（词云关联关系 + 业务类型编码） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "字段列扩展配置信息")
public class FieldColumnExtrasDTO {

    @Schema(description = "表名")
    private String tableName;

    @Schema(description = "列名")
    private String columnName;

    @Schema(description = "关联关系业务编号（有值表示来自词云）")
    private String relationBusinessNo;

    @Schema(description = "业务类型编码")
    private String businessTypeCode;
}
