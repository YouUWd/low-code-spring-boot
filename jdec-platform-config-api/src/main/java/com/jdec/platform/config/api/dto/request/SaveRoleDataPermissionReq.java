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

    @Schema(description = "权限ID列表（格式：permissionTypeId-bizId）")
    private List<String> permissionIds;
}
