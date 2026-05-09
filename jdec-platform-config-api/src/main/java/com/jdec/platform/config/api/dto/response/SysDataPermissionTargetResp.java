package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 数据权限目标列表响应 */
@Data
@Schema(description = "数据权限目标列表响应")
public class SysDataPermissionTargetResp {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "关联role_data_permission.id")
    private Long roleDataPermissionId;

    @Schema(description = "目标类型 user/dept/company")
    private String targetType;

    @Schema(description = "目标ID")
    private Long targetId;
}
