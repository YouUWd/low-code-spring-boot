package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 配置分类保存/编辑请求 */
@Data
@Schema(description = "配置分类保存/编辑请求")
public class SysConfigCategorySaveReq {

    @Schema(description = "主键ID，为空时新增")
    private Long id;

    @Schema(description = "分类名称 如：员工状态")
    private String label;

    @Schema(description = "分类英文标识 如：employee_status")
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

    @Schema(description = "主体ID，0表示全局")
    private Long subjectId;
}
