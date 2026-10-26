package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.jdec.platform.shared.audit.annotation.AuditField;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/** 系统表-权限表 */
@Data
@TableName("sys_right")
@Schema(description = "系统权限")
public class SysRight implements Serializable {

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "所属主体")
    private Long subjectId;

    @Schema(description = "父级ID")
    private Long pid;

    @AuditField(name = "权限名称", uniqueIdentifier = true)
    @Schema(description = "权限名称")
    private String rightName;

    @Schema(description = "权限标识")
    private String rightSlug;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "类型.1:数据权限,2:交互权限,3:其它权限")
    private Integer nodeType;

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
