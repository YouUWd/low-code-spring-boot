package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.shared.model.PageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 角色配置查询条件 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "角色配置查询条件")
public class QueryRoleConfigPageReq extends PageRequest {

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "用户ID")
    private Long userId;
}
