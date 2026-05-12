package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/** 角色-特殊权限关联表 */
@Data
@TableName("sys_role_special_permission")
@Schema(description = "角色-特殊权限关联")
public class SysRoleSpecialPermission implements Serializable {

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "特殊权限ID")
    private Long specialPermissionId;

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
