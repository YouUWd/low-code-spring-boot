package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 角色数据权限详情响应 */
@Data
@Schema(description = "角色数据权限详情响应")
public class RoleDataPermissionDetailResp {

    @Schema(description = "数据权限关联ID")
    private Long id;

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "数据权限编码")
    private String code;

    @Schema(description = "业务ID列表")
    private List<String> bizIds;
}
