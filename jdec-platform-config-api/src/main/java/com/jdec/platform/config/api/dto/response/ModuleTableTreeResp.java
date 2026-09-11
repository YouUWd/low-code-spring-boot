package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 模块关联的表树形节点 */
@Data
@Schema(description = "模块关联的表节点")
public class ModuleTableTreeResp {

    @Schema(description = "表名", example = "student")
    private String tableName;

    @Schema(description = "表中文描述", example = "学生信息表")
    private String tableDesc;

    @Schema(description = "表下的字段列表")
    private List<ModuleFieldTreeResp> fields;
}
