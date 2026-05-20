package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 交互权限保存/编辑请求 */
@Data
@Schema(description = "交互权限保存/编辑请求")
public class SysInteractionPermissionSaveReq {

    @Schema(description = "主键ID，为空时新增")
    private Long id;

    @Schema(description = "模块id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "模块id不能为空")
    private Long moduleId;

    @Schema(description = "模块标识", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "模块标识不能为空")
    private String moduleCode;

    @Schema(description = "交互权限类型.1:申请 2:编辑 3:查看", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "交互权限类型不能为空")
    private Integer type;

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
