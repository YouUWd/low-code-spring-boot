package com.jdec.platform.common.biz.header.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/** 用户自定义表头配置（业务库 user_menu_header 行模型，经 jOOQ 按 projectNo 动态解析数据源读取） */
@Data
@Schema(description = "用户自定义表头配置")
public class UserMenuHeader implements Serializable {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "用户id")
    private Long userId;

    @Schema(description = "虚拟id(类型-id)，如 MENU_47 / Field_10")
    private String virId;

    @Schema(description = "所属菜单ID(详情菜单ID)")
    private Long menuId;

    @Schema(description = "父节点ID(0=顶层)，可为MENU也可为FIELD之下挂子级")
    private String parentId;

    @Schema(description = "节点类型: 0-菜单 / 1-字段")
    private Integer nodeType;

    @Schema(description = "来源ID")
    private Long sourceId;

    @Schema(description = "模块id")
    private Long moduleId;

    @Schema(description = "显示名覆盖。FIELD空=取字段显示名，MENU空=取来源菜单名")
    private String label;

    @Schema(description = "菜单显示颜色")
    private String colors;

    @Schema(description = "列宽(px)")
    private Integer width;

    @Schema(description = "字段索引")
    private String dataIndex;

    @Schema(description = "可排序")
    private Integer sorted;

    @Schema(description = "搜索控件类型(区间/单选/模糊等)")
    private String searchType;

    @Schema(description = "文本是否省略")
    private Integer ellipsis;

    @Schema(description = "固定(left-左固定，right-右固定，空-不固定)")
    private String fixed;

    @Schema(description = "是否选中状态")
    private Integer checked;

    @Schema(description = "排序值")
    private Integer sortValue;

    @Schema(description = "所属主体")
    private Long subjectId;

    @Schema(description = "应用编码")
    private String projectNo;

    @Schema(description = "创建人")
    private Long createdBy;

    @Schema(description = "创建人名称")
    private String createdName;

    @Schema(description = "创建时间")
    private LocalDateTime createdDate;

    @Schema(description = "更新人")
    private Long updatedBy;

    @Schema(description = "更新人名称")
    private String updatedName;

    @Schema(description = "更新时间")
    private LocalDateTime updatedDate;
}
