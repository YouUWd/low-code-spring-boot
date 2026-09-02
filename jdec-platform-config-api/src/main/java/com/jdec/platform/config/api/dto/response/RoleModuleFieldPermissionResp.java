package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.Data;

/** 角色模块字段权限响应 */
@Data
@Schema(description = "角色模块字段权限响应")
public class RoleModuleFieldPermissionResp implements Serializable {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "模块ID")
    private Long moduleId;

    @Schema(description = "物理表名")
    private String tableName;

    @Schema(description = "物理列名")
    private String columnName;

    @Schema(description = "是否可申请/填报(新增): 0-否, 1-是")
    private Integer apply;

    @Schema(description = "是否可查看/浏览: 0-否, 1-是")
    private Integer view;

    @Schema(description = "是否可编辑/修改: 0-否, 1-是")
    private Integer edit;
}
