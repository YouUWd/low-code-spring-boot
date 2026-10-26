package com.jdec.platform.config.api.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模块组合字段配置 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "模块组合字段配置")
public class ModuleCombineFieldDTO {

    @Schema(description = "字段ID（更新时使用）", example = "1")
    private Long id;

    @Schema(
            description = "逻辑字段名（驼峰格式）",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "fullName")
    private String logicalField;

    @Schema(description = "字段显示名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "全名")
    private String displayName;

    @Schema(description = "物理字段映射")
    private List<ModuleSourceMappingDTO> sourceMapping;

    @Schema(description = "转换表达式", example = "CONCAT(${last_name}, ${first_name})")
    private String transformer;

    @Schema(description = "是否启用", example = "1")
    private Integer enabled;
}
