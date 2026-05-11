package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 交互权限保存/编辑请求 */
@Data
@Schema(description = "交互权限保存/编辑请求")
public class SysInteractionPermissionSaveReq {

    @Schema(description = "主键ID，为空时新增")
    private Long id;

    @Schema(description = "父权限ID，顶级为0")
    private Long parentId;

    @Schema(description = "模块id")
    private Long moduleId;

    @Schema(description = "模块标识")
    private String moduleCode;

    @Schema(description = "权限名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "权限名称不能为空")
    private String name;

    @Schema(description = "权限编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "权限编码不能为空")
    private String code;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "状态 1:启用 0:禁用", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer status = 1;
}
