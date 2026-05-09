package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 数据权限目标保存/编辑请求 */
@Data
@Schema(description = "数据权限目标保存/编辑请求")
public class SysDataPermissionTargetSaveReq {

    @Schema(description = "主键ID，为空时新增")
    private Long id;

    @Schema(description = "关联role_data_permission.id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "关联ID不能为空")
    private Long roleDataPermissionId;

    @Schema(description = "目标类型 user/dept/company", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "目标类型不能为空")
    private String targetType;

    @Schema(description = "目标ID（用户ID/部门ID等）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "目标ID不能为空")
    private Long targetId;
}
