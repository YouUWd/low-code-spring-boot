package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 角色交互权限响应 */
@Data
@Schema(description = "角色交互权限响应")
public class RoleInteractionPermissionResp {

    @Schema(description = "交互权限ID")
    private Long permissionId;

    @Schema(description = "交互权限名称")
    private String permissionName;

    @Schema(description = "交互权限编码")
    private String permissionCode;

    @Schema(description = "模块ID")
    private Long moduleId;

    @Schema(description = "交互权限类型（1-申请 2-编辑 3-查看）")
    private Integer type;
}
