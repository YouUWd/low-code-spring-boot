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

    @Schema(description = "该模块下的交互权限列表", nullable = true)
    private List<SysInteractionPermissionResp> permissions;

    @Schema(description = "子模块列表", nullable = true)
    private List<SysModuleInteractionPermissionResp> children;
}
