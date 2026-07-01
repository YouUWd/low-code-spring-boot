package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 角色数据权限响应 */
@Data
@Schema(description = "角色数据权限响应")
public class RoleDataPermissionResp {

    @Schema(description = "数据权限ID")
    private Long dataPermissionId;

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "拼接后的权限ID列表（格式：dataPermissionId-bizId）")
    private List<String> permissionIds;
}
