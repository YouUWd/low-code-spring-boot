package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 角色交互权限保存请求 */
@Data
@Schema(description = "角色交互权限保存请求")
public class SaveRoleInteractionPermissionReq {

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "交互权限ID列表")
    private List<Long> permissionIds;
}
