package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.Data;

/** 角色模块字段权限响应 */
@Data
@Schema(description = "角色模块字段权限响应")
public class RoleModuleFieldPermissionResp implements Serializable {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "模块ID")
    private Long moduleId;

    @Schema(description = "字段ID")
    private Long fieldId;

    @Schema(description = "是否可读: 0-否, 1-是")
    private Integer readable;

    @Schema(description = "是否可写: 0-否, 1-是")
    private Integer writable;

    @Schema(description = "是否可更新: 0-否, 1-是")
    private Integer updatable;
}
