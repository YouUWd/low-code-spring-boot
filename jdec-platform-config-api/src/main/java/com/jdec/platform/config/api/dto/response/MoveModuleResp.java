package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/** 移动模块响应 DTO 只包含移动后变动的核心字段 */
@Data
@Schema(description = "移动模块响应")
public class MoveModuleResp {

    @Schema(description = "模块ID", example = "5")
    private Long id;

    @Schema(description = "父模块ID", example = "2")
    private Long parentId;

    @Schema(description = "排序顺序", example = "1")
    private Integer sortOrder;

    @Schema(description = "更新时间")
    private LocalDateTime updatedDate;
}
