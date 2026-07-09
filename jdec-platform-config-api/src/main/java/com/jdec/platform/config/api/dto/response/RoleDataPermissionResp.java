package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 角色数据权限响应 */
@Data
@Schema(description = "角色数据权限响应")
public class RoleDataPermissionResp {

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "权限列表")
    private List<DataPermissionListReq> dataPermission;

    @Data
    @Schema(description = "角色数据权限列表响应")
    public static class DataPermissionListReq {
        @Schema(description = "权限节点id")
        private Long permissionId;

        @Schema(description = "业务名称列表")
        private List<String> bizNames;
    }
}
