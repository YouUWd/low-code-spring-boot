package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.config.api.enums.InteractionTypeEnum;
import com.jdec.platform.shared.audit.annotation.AuditField;
import com.jdec.platform.shared.audit.enums.FieldType;
import com.jdec.platform.shared.enums.CommonStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 交互权限保存/编辑请求 */
@Data
@Schema(description = "交互权限保存/编辑请求")
public class SysInteractionPermissionSaveReq {

    @Schema(description = "权限节点名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "权限名称不能为空")
    @AuditField(name = "权限节点名称", uniqueIdentifier = true)
    private String name;

    @Schema(description = "交互权限类型.1:申请 2:编辑 3:查看", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "交互权限类型不能为空")
    @AuditField(name = "权限节点类型", type = FieldType.ENUM, enumClass = InteractionTypeEnum.class)
    private Integer type;

    @Schema(description = "主键ID，为空时新增")
    @AuditField(name = "权限ID", ignore = true)
    private Long id;

    @Schema(description = "模块id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "模块id不能为空")
    @AuditField(
            name = "所属模块",
            type = FieldType.RELATION,
            target = "sys_module",
            idField = "id",
            nameField = "module_name")
    private Long moduleId;

    @Schema(description = "模块标识", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "模块标识不能为空")
    @AuditField(name = "模块标识")
    private String moduleCode;

    @Schema(description = "权限节点编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "权限编码不能为空")
    @AuditField(name = "权限节点编码")
    private String code;

    @Schema(description = "排序")
    @AuditField(name = "排序", ignore = true)
    private Integer sort;

    @Schema(description = "状态 1:启用 0:禁用", requiredMode = Schema.RequiredMode.REQUIRED)
    @AuditField(name = "权限节点状态", type = FieldType.ENUM, enumClass = CommonStatusEnum.class)
    private Integer status = 1;
}
