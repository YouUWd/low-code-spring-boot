package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 菜单拖拽请求
 */
@Data
@Schema(description = "菜单拖拽请求")
public class SysMenuDragReq {

    @Schema(description = "被拖拽节点ID")
    private Long id;

    @Schema(description = "目标父节点ID，拖到顶层传0")
    private Long targetPid;
}
