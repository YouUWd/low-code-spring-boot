package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 模块表下的字段树形节点 */
@Data
@Schema(description = "模块表包含的字段节点")
public class ModuleFieldTreeResp {

    @Schema(description = "字段ID（更新或删除配置时使用，为 simpleFieldId 或 combineFieldId）", example = "1")
    private Long id;

    @Schema(description = "模块ID", example = "1001")
    private Long moduleId;

    @Schema(description = "物理表名", example = "student")
    private String tableName;

    @Schema(description = "物理表列名", example = "student_no")
    private String columnName;

    @Schema(description = "字段显示名称", example = "学号")
    private String displayName;
}
