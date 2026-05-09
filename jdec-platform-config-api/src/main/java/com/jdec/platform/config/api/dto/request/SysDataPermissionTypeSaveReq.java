package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 数据权限类型保存/编辑请求 */
@Data
@Schema(description = "数据权限类型保存/编辑请求")
public class SysDataPermissionTypeSaveReq {

    @Schema(description = "主键ID，为空时新增")
    private Long id;

    @Schema(description = "类型编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "类型编码不能为空")
    private String code;

    @Schema(description = "类型名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "类型名称不能为空")
    private String name;

    @Schema(description = "是否有目标", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "是否有目标不能为空")
    private Integer hasTarget;

    @Schema(description = "类型描述")
    private String description;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "状态 1:启用 0:禁用")
    private Integer status = 1;
}
