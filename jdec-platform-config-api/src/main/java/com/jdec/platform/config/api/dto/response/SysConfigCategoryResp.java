package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 配置分类响应 */
@Data
@Schema(description = "配置分类响应")
public class SysConfigCategoryResp {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "分类名称")
    private String label;

    @Schema(description = "分类英文标识")
    private String categoryAlias;

    @Schema(description = "分类说明")
    private String description;

    @Schema(description = "格式：list / tree / kv")
    private String format;

    @Schema(description = "1系统内置 2用户自定义")
    private Integer source;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "1启用 0禁用")
    private Integer status;

    @Schema(description = "应用编码")
    private String projectNo;

    @Schema(description = "主体ID，0表示全局")
    private Long subjectId;
}
