package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/** 数据权限类型定义表 */
@Data
@TableName("sys_data_permission_type")
@Schema(description = "数据权限类型定义")
public class SysDataPermissionType implements Serializable {

    @Schema(description = "类型ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "类型编码")
    private String code;

    @Schema(description = "类型名称")
    private String name;

    @Schema(description = "类型描述")
    private String description;

    @Schema(description = "是否需要指定目标 1:是 0:否")
    private Integer hasTarget;

    @Schema(description = "目标类型 user/dept/company 等")
    private String targetType;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "状态 1:启用 0:禁用")
    private Integer status;

    @Schema(description = "所属主体")
    private Long subjectId;

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
