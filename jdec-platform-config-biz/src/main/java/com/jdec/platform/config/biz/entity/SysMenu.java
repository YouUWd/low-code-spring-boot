package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.jdec.platform.shared.audit.annotation.AuditField;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/** 系统菜单 */
@Data
@TableName("sys_menu")
@Schema(description = "系统菜单")
public class SysMenu implements Serializable {

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "父级id")
    private Long pid;

    @Schema(description = "所属主体")
    private Long subjectId;

    @AuditField(name = "菜单名称", uniqueIdentifier = true)
    @Schema(description = "菜单名称")
    private String title;

    @Schema(description = "参数")
    private String param;

    @Schema(description = "菜单类型.1:导航 2:页面 3:标签 4:表")
    private Integer menuType;

    @Schema(description = "菜单分类.1:业务菜单 2:系统菜单")
    private Integer category;

    @Schema(description = "默认图标")
    private String imgDefault;

    @Schema(description = "选中后图标")
    private String imgActive;

    @Schema(description = "关联模块id")
    private Long moduleId;

    @Schema(description = "应用编码")
    @TableField(fill = FieldFill.INSERT)
    private String projectNo;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "0隐藏，1显示")
    private Integer showed;

    @Schema(description = "1启用，0停用")
    private Integer enabled;

    @Schema(description = "帮助文档地址")
    private String docUrl;

    @Schema(description = "帮助文档名称")
    private String docName;

    @Schema(description = "演示视频地址")
    private String videoUrl;

    @Schema(description = "帮助视频名")
    private String videoName;

    @Schema(description = "模拟操作用户")
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @Schema(description = "模拟操作用户名称")
    private String createdName;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdDate;

    @Schema(description = "更改人")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @Schema(description = "更改人名称")
    private String updatedName;

    @Schema(description = "更改时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedDate;
}
