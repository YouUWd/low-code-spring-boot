package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.jdec.platform.shared.audit.annotation.AuditField;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/** 配置分类表 */
@Data
@TableName("sys_config_category")
@Schema(description = "配置分类表")
public class SysConfigCategory implements Serializable {

    @Schema(description = "主键")
    @TableId(type = IdType.AUTO)
    private Long id;

    @AuditField(name = "分类名称", uniqueIdentifier = true)
    @Schema(description = "分类名称 如：员工状态")
    private String label;

    @AuditField(name = "分类标识")
    @Schema(description = "分类英文标识 如：employee_status")
    private String categoryAlias;

    @AuditField(name = "分类说明")
    @Schema(description = "分类说明")
    private String description;

    @AuditField(name = "格式")
    @Schema(description = "格式：list / tree / kv")
    private String format;

    @AuditField(name = "来源")
    @Schema(description = "1系统内置 2用户自定义")
    private Integer source;

    @AuditField(name = "排序")
    @Schema(description = "排序")
    private Integer sort;

    @AuditField(name = "状态")
    @Schema(description = "1启用 0禁用")
    private Integer status;

    @Schema(description = "应用编码")
    @TableField(fill = FieldFill.INSERT)
    private String projectNo;

    @Schema(description = "主体ID，0表示全局")
    @TableField(fill = FieldFill.INSERT)
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
