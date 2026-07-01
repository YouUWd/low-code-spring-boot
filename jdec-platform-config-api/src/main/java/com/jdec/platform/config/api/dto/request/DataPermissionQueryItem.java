package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 数据权限查询项 */
@Data
@Schema(description = "数据权限查询项")
public class DataPermissionQueryItem {

    @Schema(description = "表名", example = "student")
    private String tableName;

    @Schema(description = "字段名", example = "id")
    private String column;
}
