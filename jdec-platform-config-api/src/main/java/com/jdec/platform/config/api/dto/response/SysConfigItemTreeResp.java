package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 配置项树形响应 */
@Data
@Schema(description = "配置项树形响应")
public class SysConfigItemTreeResp {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "冗余分类alias")
    private String categoryAlias;

    @Schema(description = "父级ID")
    private Long pid;

    @Schema(description = "层级路径")
    private String path;

    @Schema(description = "层级深度")
    private Integer depth;

    @Schema(description = "显示名称")
    private String label;

    @Schema(description = "存储值")
    private String value;

    @Schema(description = "说明")
    private String description;

    @Schema(description = "扩展属性")
    private String extra;

    @Schema(description = "1系统内置 2用户自定义")
    private Integer source;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "1启用 0禁用")
    private Integer status;

    @Schema(description = "应用编码")
    private String projectNo;

    @Schema(description = "主体ID")
    private Long subjectId;

    @Schema(description = "子节点")
    private List<SysConfigItemTreeResp> children;
}
