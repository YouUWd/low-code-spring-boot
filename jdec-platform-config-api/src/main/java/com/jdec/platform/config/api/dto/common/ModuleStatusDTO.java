package com.jdec.platform.config.api.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模块状态配置信息 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "模块状态配置信息")
public class ModuleStatusDTO {

    @Schema(description = "主键ID（仅响应中使用）", example = "1")
    private Long id;

    @Schema(description = "状态父ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long statusPid;

    @Schema(description = "状态ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    private Long statusId;
}
