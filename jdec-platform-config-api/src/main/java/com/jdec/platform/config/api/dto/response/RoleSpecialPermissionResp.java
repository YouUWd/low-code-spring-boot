package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 角色特殊权限响应 */
@Data
@Schema(description = "角色特殊权限响应")
public class RoleSpecialPermissionResp {

    @Schema(description = "特殊权限ID")
    private Long specialPermissionId;

    @Schema(description = "权限编码")
    private String code;

    @Schema(description = "权限名称")
    private String name;

    @Schema(description = "权限描述")
    private String description;
}
