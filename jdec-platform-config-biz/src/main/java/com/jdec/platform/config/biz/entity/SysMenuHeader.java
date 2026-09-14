package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/** 列表表头节点 */
@Data
@TableName("sys_menu_header")
@Schema(description = "列表表头节点")
public class SysMenuHeader implements Serializable {

    @Schema(description = "主键")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "虚拟id(类型-id)，如 MENU_47 / Field_10")
    private String virId;

    @Schema(description = "所属列表菜单ID(表头归属)")
    private Long menuId;

    @Schema(description = "父节点虚拟id(NULL/0=顶层)，如 MENU_47 / Field_10")
    private String parentId;

    @Schema(description = "节点类型: 0-菜单 / 1-字段")
    private Integer nodeType;

    @Schema(description = "来源ID(节点类型为菜单时=来源菜单ID，为字段时=来源字段ID)")
    private Long sourceId;

    @Schema(description = "模块id(菜单节点=菜单绑定模块id，字段节点=字段所属模块id)")
    private Long moduleId;

    @Schema(description = "显示名覆盖。FIELD空=取字段显示名，MENU空=取来源菜单名")
    private String label;

    @Schema(description = "菜单显示颜色")
    private String colors;

    @Schema(description = "列宽(px)")
    private Integer width;

    @Schema(description = "字段索引(模块id.表名.字段名)，如 1001.info.company_name")
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
