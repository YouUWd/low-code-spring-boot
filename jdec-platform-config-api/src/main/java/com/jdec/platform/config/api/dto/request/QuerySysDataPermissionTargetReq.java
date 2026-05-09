package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 数据权限目标列表查询请求 */
@Data
@Schema(description = "数据权限目标列表查询请求")
public class QuerySysDataPermissionTargetReq {

    @Schema(description = "关联role_data_permission.id")
    private Long roleDataPermissionId;

    @Schema(description = "目标类型 user/dept/company")
    private String targetType;

    @Schema(description = "目标ID")
    private Long targetId;
}
