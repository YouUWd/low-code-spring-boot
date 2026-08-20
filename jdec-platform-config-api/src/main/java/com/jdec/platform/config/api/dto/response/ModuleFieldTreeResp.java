package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 模块表下的字段树形节点 */
@Data
@Schema(description = "模块表包含的字段节点")
public class ModuleFieldTreeResp {

    @Schema(description = "字段ID（更新或删除配置时使用，为 simpleFieldId 或 combineFieldId）", example = "1")
    private Long id;

    @Schema(description = "字段编码（简单字段对应 columnName，组合字段对应 logicalField）", example = "student_no")
    private String fieldCode;

    @Schema(description = "字段名称（简单字段映射为物理 displayName，组合字段为显示名称）", example = "学号")
    private String fieldName;

    @Schema(description = "是否是组合字段：0-否（简单物理字段），1-是（复合组合字段）", example = "0")
    private Integer combineFlag;
}
