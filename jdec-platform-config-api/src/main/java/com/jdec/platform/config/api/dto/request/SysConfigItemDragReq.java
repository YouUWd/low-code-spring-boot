package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 配置项拖拽请求 */
@Data
@Schema(description = "配置项拖拽请求")
public class SysConfigItemDragReq {

    @Schema(description = "被拖拽的节点ID", required = true)
    private Long dragId;

    @Schema(description = "目标父节点ID（0表示拖到分类根节点下）", required = true)
    private Long targetPid;

    @Schema(description = "目标位置的排序值（可选，用于精确控制排序）")
    private Integer targetSort;
}
