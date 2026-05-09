package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 特殊权限列表查询请求 */
@Data
@Schema(description = "特殊权限列表查询请求")
public class QuerySysSpecialPermissionReq {

    @Schema(description = "权限名称（模糊查询）")
    private String name;

    @Schema(description = "权限编码（模糊查询）")
    private String code;

    @Schema(description = "状态 1:启用 0:禁用")
    private Integer status;
}
