package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 数据权限节点列表查询请求 */
@Data
@Schema(description = "数据权限节点列表查询请求")
public class QuerySysDataPermissionReq {

    @Schema(description = "角色ID（用于查询角色关联的权限节点及数据权限）")
    private Long roleId;

    @Schema(description = "类型名称（模糊查询）")
    private String name;

    @Schema(description = "类型编码（模糊查询）")
    private String code;

    @Schema(description = "状态 1:启用 0:禁用")
    private Integer status;
}
