package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 状态拖拽请求 */
@Data
@Schema(description = "状态拖拽请求")
public class SysStatusDragReq {

    @Schema(description = "被拖拽节点ID")
    private Long id;

    @Schema(description = "目标父节点ID，拖到顶层传0")
    private Long targetPid;

    @Schema(description = "排序值，从0开始")
    private Integer sortOrder;
}
