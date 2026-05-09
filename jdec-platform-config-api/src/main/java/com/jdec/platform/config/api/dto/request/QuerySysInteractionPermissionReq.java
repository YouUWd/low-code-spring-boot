package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 交互权限列表查询请求 */
@Data
@Schema(description = "交互权限列表查询请求")
public class QuerySysInteractionPermissionReq {

    @Schema(description = "权限名称（模糊查询）")
    private String name;

    @Schema(description = "权限编码（模糊查询）")
    private String code;

    @Schema(description = "状态 1:启用 0:禁用")
    private Integer status;

    @Schema(description = "父权限ID")
    private Long parentId;
}
