package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 角色数据权限保存请求 */
@Data
@Schema(description = "角色数据权限保存请求")
public class SaveRoleDataPermissionReq {

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "权限列表")
    private List<SaveDataPermissionReq> dataPermissions;

    @Data
    @Schema(description = "角色数据权限保存请求")
    public static class SaveDataPermissionReq {
        @Schema(description = "权限节点id")
        private Long permissionId;

        @Schema(description = "业务id列表", example = "1,2,3")
        private String bizIds;
    }
}
