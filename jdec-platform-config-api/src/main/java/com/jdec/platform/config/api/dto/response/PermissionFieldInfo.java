package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 权限字段精简信息 DTO */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "权限字段精简信息")
public class PermissionFieldInfo implements Serializable {

    @Schema(description = "字段配置ID (sys_module_field.id)")
    private Long id;

    @Schema(description = "模块ID")
    private Long moduleId;

    @Schema(description = "物理表名")
    private String tableName;

    @Schema(description = "列名")
    private String columnName;

    @Schema(description = "显示名称")
    private String displayName;
}
