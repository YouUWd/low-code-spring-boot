package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.jdec.platform.shared.audit.annotation.AuditField;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/** 配置项表 */
@Data
@TableName("sys_config_item")
@Schema(description = "配置项表")
public class SysConfigItem implements Serializable {

    @Schema(description = "主键")
    @TableId(type = IdType.AUTO)
    private Long id;

    @AuditField(name = "所属分类")
    @Schema(description = "冗余分类alias，方便直接按alias查询")
    private String categoryAlias;

    @AuditField(name = "父级ID")
    @Schema(description = "父级ID，0为根节点")
    private Long pid;

    @AuditField(name = "层级路径")
    @Schema(description = "层级路径 /1/3/7/")
    private String path;

    @AuditField(name = "层级深度")
    @Schema(description = "层级深度，根=1")
    private Integer depth;

    @AuditField(name = "配置项名称", uniqueIdentifier = true)
    @Schema(description = "显示名称（给人看）")
    private String label;

    @AuditField(name = "配置项值")
    @Schema(description = "存储值（给程序用）")
    private String value;

    @AuditField(name = "说明")
    @Schema(description = "说明")
    private String description;

    @AuditField(name = "扩展属性")
    @Schema(description = "扩展属性 颜色/图标等")
    private String extra;

    @AuditField(name = "排序")
    @Schema(description = "排序")
    private Integer sort;

    @AuditField(name = "状态")
    @Schema(description = "1启用 0禁用")
    private Integer status;

    @Schema(description = "应用编码")
    @TableField(fill = FieldFill.INSERT)
    private String projectNo;

    @Schema(description = "主体ID，0=不区分主体/全局")
    private Long subjectId;

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
