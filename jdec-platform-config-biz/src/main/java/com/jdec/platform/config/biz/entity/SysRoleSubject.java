package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/** 角色主体绑定表 */
@Data
@TableName("sys_role_subject")
@Schema(description = "角色主体绑定")
public class SysRoleSubject implements Serializable {

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "主体ID")
    private Long subjectId;

    @Schema(description = "有效期开始")
    private LocalDate effectiveStartDate;

    @Schema(description = "有效期结束")
    private LocalDate effectiveEndDate;

    @Schema(description = "1-永久 2-自定义")
    private Integer effectiveType;

    @Schema(description = "状态(0-有效,1失效)")
    private Integer status;

    @Schema(description = "创建人")
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @Schema(description = "创建人名称")
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
