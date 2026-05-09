package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 角色菜单保存请求 */
@Data
@Schema(description = "角色菜单保存请求")
public class SaveRoleMenuReq {

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "菜单ID列表")
    private List<Long> menuIds;
}
