package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 通用配置树节点响应（支持 Category 和 Item 混合） */
@Data
@Schema(description = "通用配置树节点响应")
public class SysConfigTreeNodeResp {

    @Schema(description = "节点类型：category-分类, item-配置项")
    private String nodeType;

    @Schema(description = "节点ID（数字）")
    private Long id;

    @Schema(description = "节点ID（带前缀字符串，category-xxx 或 item-xxx）")
    private String nodeId;

    @Schema(description = "父节点ID（数字）")
    private Long pid;

    @Schema(description = "父节点ID（带前缀字符串）")
    private String parentNodeId;

    @Schema(description = "分类别名（category 类型为自身别名，item 类型为所属分类别名）")
    private String categoryAlias;

    @Schema(description = "显示名称")
    private String label;

    @Schema(description = "存储值（仅 item 类型有值）")
    private String value;

    @Schema(description = "说明")
    private String description;

    @Schema(description = "扩展属性")
    private String extra;

    @Schema(description = "格式：list / tree / kv（仅 category 类型有值）")
    private String format;

    @Schema(description = "1系统内置 2用户自定义")
    private Integer source;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "1启用 0禁用")
    private Integer status;

    @Schema(description = "层级深度")
    private Integer depth;

    @Schema(description = "层级路径")
    private String path;

    @Schema(description = "子节点")
    private List<SysConfigTreeNodeResp> children;
}
