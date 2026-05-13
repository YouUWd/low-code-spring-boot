package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 配置项保存/编辑请求 */
@Data
@Schema(description = "配置项保存/编辑请求")
public class SysConfigItemSaveReq {

    @Schema(description = "主键ID，为空时新增")
    private Long id;

    @Schema(description = "所属分类ID")
    private Long categoryId;

    @Schema(description = "父级ID，0为根节点")
    private Long pid;

    @Schema(description = "显示名称（给人看）")
    private String label;

    @Schema(description = "存储值（给程序用）")
    private String value;

    @Schema(description = "说明")
    private String description;

    @Schema(description = "扩展属性 颜色/图标等")
    private String extra;

    @Schema(description = "1系统内置 2用户自定义")
    private Integer source;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "1启用 0禁用")
    private Integer status;
}
