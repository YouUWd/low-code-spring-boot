package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 角色菜单响应 */
@Data
@Schema(description = "角色菜单响应")
public class RoleMenuResp {

    @Schema(description = "菜单ID")
    private Long menuId;

    @Schema(description = "菜单标题")
    private String menuTitle;
}
