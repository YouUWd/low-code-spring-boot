package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/** 系统表-角色用户表 */
@Data
@TableName("sys_role_user")
@Schema(description = "角色用户关联")
public class SysRoleUser implements Serializable {

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "记录主体，方便查询")
    private Long subjectId;

    @Schema(description = "角色id")
    private Long roleId;

    @Schema(description = "用户id")
    private Long userId;

    @Schema(description = "用户类型 1内部用户 2外部用户")
    private Integer userType;

    @Schema(description = "原始用户ID")
    private Long originalUserId;

    @Schema(description = "是否当前用户 0否 1是")
    private Integer currentFlag;

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

    @Schema(description = "有效期开始")
    private LocalDate effectiveStartDate;

    @Schema(description = "有效期结束")
    private LocalDate effectiveEndDate;

    @Schema(description = "有效期类型：1-永久，2-自定义")
    private Integer effectiveType;

    @Schema(description = "状态(0-有效，1失效)")
    private Integer status;
}
