package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.shared.audit.annotation.AuditField;
import com.jdec.platform.shared.audit.enums.FieldType;
import com.jdec.platform.shared.enums.CommonStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 特殊权限保存/编辑请求 */
@Data
@Schema(description = "特殊权限保存/编辑请求")
public class SysSpecialPermissionSaveReq {

    @AuditField(name = "主键ID", ignore = true)
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "权限节点名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "权限名称不能为空")
    @AuditField(name = "权限节点名称", uniqueIdentifier = true)
    private String name;

    @Schema(description = "权限节点编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "权限编码不能为空")
    @AuditField(name = "权限节点编码")
    private String code;

    @Schema(description = "权限节点描述")
    @AuditField(name = "权限节点描述")
    private String description;

    @Schema(description = "排序")
    @AuditField(name = "排序", ignore = true)
    private Integer sort;

    @Schema(description = "状态 1:启用 0:禁用", requiredMode = Schema.RequiredMode.REQUIRED)
    @AuditField(name = "权限节点状态", type = FieldType.ENUM, enumClass = CommonStatusEnum.class)
    private Integer status = 1;
}
