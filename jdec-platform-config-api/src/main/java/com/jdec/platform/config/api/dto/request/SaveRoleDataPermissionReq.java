package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 角色数据权限保存请求 */
@Data
@Schema(description = "角色数据权限保存请求")
public class SaveRoleDataPermissionReq {

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "数据权限类型ID")
    private Long dataPermissionTypeId;

    @Schema(description = "用户ID列表（hasTarget为1时必填）")
    private List<Long> userIds;
}
