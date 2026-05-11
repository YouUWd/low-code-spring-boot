package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 交互权限树形结构响应 */
@Data
@Schema(description = "交互权限树形结构响应")
public class SysInteractionPermissionResp {

    @Schema(description = "权限ID")
    private Long id;

    @Schema(description = "父权限ID")
    private Long parentId;

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

    @Schema(description = "子节点列表")
    private List<SysInteractionPermissionResp> children = new ArrayList<>();
}
