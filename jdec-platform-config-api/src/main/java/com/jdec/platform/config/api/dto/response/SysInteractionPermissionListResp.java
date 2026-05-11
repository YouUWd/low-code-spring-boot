package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 交互权限列表响应 */
@Data
@Schema(description = "交互权限列表响应")
public class SysInteractionPermissionListResp {

    @Schema(description = "权限ID")
    private Long id;

    @Schema(description = "父权限ID")
    private Long parentId;

    @Schema(description = "父权限名称")
    private String parentName;

    @Schema(description = "模块id")
    private Long moduleId;

    @Schema(description = "模块标识")
    private String moduleCode;

    @Schema(description = "权限名称")
    private String name;

    @Schema(description = "权限编码")
    private String code;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "状态 1:启用 0:禁用")
    private Integer status;
}
