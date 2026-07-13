package com.jdec.platform.common_business.api.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/** 模块字段配置数据类 * */
@Data
@Builder
public class ModuleField {
    @Schema(description = "模块ID")
    private Long moduleId;

    @Schema(description = "字段类型,SIMPLE或者COMBINE")
    private String filedType;

    @Schema(description = "字段名称")
    private String fieldCode;

    @Schema(description = "字段中文名")
    private String fieldName;

    @Schema(description = "字段表名")
    private String tableName;

    @Schema(description = "组合字段关系映射")
    private List<SourceMapping> sourceMappings;

    @Schema(description = "转换方法")
    private String transformer;

    // 字段权限 0:无权限，前端显示***;1:有查看权限;2有编辑权限
    @Schema(description = "字段权限")
    private Integer permissionCode;

    public record SourceMapping(
            @Schema(description = "表名") String entity,
            @Schema(description = "字段名") String field,
            @Schema(description = "顺序") Integer sortOrder) {}
}
