package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 数据库 Schema 信息响应 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "数据库Schema信息")
public class DatabaseSchemaResp {

    /** 数据库名称 */
    @Schema(description = "数据库名称", example = "config_center")
    private String database;

    /** 数据库中的所有表 */
    @Schema(description = "数据库中的所有表信息")
    private List<TableInfoResp> tables;
}
