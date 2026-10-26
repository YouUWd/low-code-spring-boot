package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 表级权限分组响应 DTO */
@Data
@Schema(description = "表级权限分组响应")
public class PermissionTableGroupResp {

    @Schema(description = "表名")
    private String tableName;

    @Schema(description = "可读字段集合")
    private List<PermissionFieldInfo> readableFields;

    @Schema(description = "可写字段集合")
    private List<PermissionFieldInfo> writableFields;

    @Schema(description = "可更新字段集合")
    private List<PermissionFieldInfo> updatableFields;
}
