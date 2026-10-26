package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 移动模块请求 DTO 用于处理模块的拖拽移动，更新父子层级关系
 *
 * <p>前端只需提供：要移动的模块、目标父模块、目标位置 后端自动计算并更新所有受影响节点的排序
 */
@Data
@Schema(description = "移动模块请求")
public class MoveModuleReq {

    /** 要移动的模块 ID */
    @Schema(description = "要移动的模块ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "5")
    private Long moduleId;

    /** 目标父模块 ID（0 表示移动到根级） */
    @Schema(
            description = "目标父模块ID（0表示移动到根级）",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "2")
    private Long targetParentId;

    /** 目标位置的排序顺序 后端会自动调整该位置及之后的所有同级节点的排序 */
    @Schema(
            description = "目标位置的排序顺序（从0开始）",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "1")
    private Integer targetSortOrder;
}
