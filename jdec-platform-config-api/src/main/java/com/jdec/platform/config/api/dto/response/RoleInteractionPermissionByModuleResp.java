package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 按模块查询角色交互权限响应 */
@Data
@Schema(description = "按模块查询角色交互权限响应")
public class RoleInteractionPermissionByModuleResp {

    @Schema(description = "申请类权限列表（type=1）")
    private List<InteractionPermissionItem> applyPermissions;

    @Schema(description = "编辑类权限列表（type=2）")
    private List<InteractionPermissionItem> editPermissions;

    @Schema(description = "查看类权限列表（type=3）")
    private List<InteractionPermissionItem> viewPermissions;

    /** 交互权限项 */
    @Data
    @Schema(description = "交互权限项")
    public static class InteractionPermissionItem {

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

        @Schema(description = "排序")
        private Integer sort;
    }
}
