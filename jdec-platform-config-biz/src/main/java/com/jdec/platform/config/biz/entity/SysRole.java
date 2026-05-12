package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/** 系统表-角色表 */
@Data
@TableName("sys_role")
@Schema(description = "系统角色")
public class SysRole implements Serializable {

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "所属主体")
    private Long subjectId;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "角色标识")
    private String roleSlug;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "应用编码")
    private String projectNo;

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
