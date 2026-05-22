package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 模块交互权限树响应 */
@Data
@Schema(description = "模块交互权限树节点")
public class SysModuleInteractionPermissionResp {

    @Schema(description = "模块ID")
    private Long moduleId;

    @Schema(description = "模块名称")
    private String moduleName;

    @Schema(description = "模块编码")
    private String moduleCode;

    @Schema(description = "申请类型权限列表（type=1）", nullable = true)
    private List<SysInteractionPermissionResp> applyPermissions;

    @Schema(description = "编辑类型权限列表（type=2）", nullable = true)
    private List<SysInteractionPermissionResp> editPermissions;

    @Schema(description = "查看类型权限列表（type=3）", nullable = true)
    private List<SysInteractionPermissionResp> viewPermissions;

    @Schema(description = "子模块列表", nullable = true)
    private List<SysModuleInteractionPermissionResp> children;
}
