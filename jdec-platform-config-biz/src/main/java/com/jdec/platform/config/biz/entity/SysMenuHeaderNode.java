package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/** 菜单表头节点 */
@Data
@TableName("sys_menu_header_node")
@Schema(description = "菜单表头节点")
public class SysMenuHeaderNode implements Serializable {

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "列表菜单ID")
    private Long listMenuId;

    @Schema(description = "父节点ID")
    private Long parentId;

    @Schema(description = "节点类型")
    private String nodeType;

    @Schema(description = "来源菜单ID")
    private Long sourceMenuId;

    @Schema(description = "来源字段ID")
    private Long sourceFieldId;

    @Schema(description = "标签名称")
    private String label;

    @Schema(description = "颜色")
    private String color;

    @Schema(description = "宽度")
    private Integer width;

    @Schema(description = "是否可排序")
    private Boolean sorted;

    @Schema(description = "搜索类型")
    private String searchType;

    @Schema(description = "是否省略")
    private Boolean omitted;

    @Schema(description = "是否选中")
    private Boolean checked;

    @Schema(description = "所属主体")
    private Long subjectId;

    @Schema(description = "应用编码")
    @TableField(fill = FieldFill.INSERT)
    private String projectNo;

    @Schema(description = "创建人")
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @Schema(description = "创建人名称")
    private String createdName;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdDate;

    @Schema(description = "更新人")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @Schema(description = "更新人名称")
    private String updatedName;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedDate;
}
