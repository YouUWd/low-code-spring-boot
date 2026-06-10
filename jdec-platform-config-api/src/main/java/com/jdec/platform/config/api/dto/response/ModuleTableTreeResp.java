package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 模块关联的表树形节点 包含简单表与复合表 */
@Data
@Schema(description = "模块关联的表节点")
public class ModuleTableTreeResp {

    @Schema(description = "表名（简单表为物理表名，复合表为虚拟分类名如 'composite_fields'）", example = "student")
    private String tableName;

    @Schema(description = "表中文描述", example = "学生信息表")
    private String tableDesc;

    @Schema(description = "表类型：SIMPLE - 简单表, COMBINE - 复合表/组合表", example = "SIMPLE")
    private String tableType;

    @Schema(description = "关联表是否只读（0=否，1=是）", example = "0")
    private Integer readOnly;

    @Schema(description = "表下的字段列表")
    private List<ModuleFieldTreeResp> fields;
}
