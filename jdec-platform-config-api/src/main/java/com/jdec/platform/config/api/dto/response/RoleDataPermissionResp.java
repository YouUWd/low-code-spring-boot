package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 角色数据权限响应 */
@Data
@Schema(description = "角色数据权限响应")
public class RoleDataPermissionResp {

    @Schema(description = "数据权限关联ID")
    private Long id;

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "数据权限类型ID")
    private Long dataPermissionTypeId;

    @Schema(description = "数据权限类型名称")
    private String dataPermissionTypeName;

    @Schema(description = "是否需要指定目标 1:是 0:否")
    private Integer hasTarget;

    @Schema(description = "目标用户ID列表（hasTarget为1时返回）")
    private List<Long> userIds;
}
