package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.config.api.enums.EnableEnum;
import com.jdec.platform.config.api.enums.YesNoEnum;
import com.jdec.platform.shared.audit.annotation.AuditField;
import com.jdec.platform.shared.audit.enums.FieldType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 审批链分类保存请求 */
@Data
@Schema(description = "审批链分类保存请求")
public class ApprovalChainTypeSaveReq {

    @Schema(description = "主键ID（编辑时必填,新增时为空）")
    @AuditField(name = "主键ID", ignore = true)
    private Long id;

    @Schema(description = "分类名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "分类名称不能为空")
    @AuditField(name = "分类名称", uniqueIdentifier = true)
    private String title;

    @Schema(description = "模块ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "模块ID不能为空")
    @AuditField(
            name = "模块",
            type = FieldType.RELATION,
            target = "sys_module",
            idField = "id",
            nameField = "module_name")
    private Long moduleId;

    @Schema(description = "是否默认审批链分类（1：是，0：否）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "是否默认不能为空")
    @AuditField(name = "是否默认", type = FieldType.ENUM, enumClass = YesNoEnum.class)
    private Integer defaultFlag;

    @Schema(description = "是否启用(1:启用, 0：不启用)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "是否启用不能为空")
    @AuditField(name = "是否启用", type = FieldType.ENUM, enumClass = EnableEnum.class)
    private Integer enabled;
}
