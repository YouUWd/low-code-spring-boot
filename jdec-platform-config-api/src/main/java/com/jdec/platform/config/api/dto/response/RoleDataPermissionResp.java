package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 角色数据权限响应 */
@Data
@Schema(description = "角色数据权限响应")
public class RoleDataPermissionResp {

    @Schema(description = "数据权限关联ID")
    private Long id;

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "拼接后的权限ID（格式：permissionId-bizId）")
    private String permissionId;
}
