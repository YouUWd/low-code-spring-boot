package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.jdec.platform.shared.audit.annotation.AuditField;
import com.jdec.platform.shared.audit.enums.FieldType;
import com.jdec.platform.shared.enums.CommonStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/** 数据权限类型定义表 */
@Data
@TableName("sys_data_permission")
@Schema(description = "数据权限类型定义")
public class SysDataPermission implements Serializable {

    @AuditField(name = "类型ID", ignore = true)
    @Schema(description = "类型ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @AuditField(name = "权限节点名称", uniqueIdentifier = true)
    @Schema(description = "权限节点名称")
    private String name;

    @AuditField(name = "权限节点编码")
    @Schema(description = "权限节点编码")
    private String code;

    @AuditField(name = "权限节点描述")
    @Schema(description = "权限节点描述")
    private String description;

    @AuditField(name = "排序", ignore = true)
    @Schema(description = "排序")
    private Integer sort;

    @AuditField(name = "权限节点状态", type = FieldType.ENUM, enumClass = CommonStatusEnum.class)
    @Schema(description = "权限节点状态 1:启用 0:禁用")
    private Integer status;

    @AuditField(name = "所属主体", ignore = true)
    @Schema(description = "所属主体")
    private Long subjectId;

    @AuditField(name = "应用编码", ignore = true)
    @Schema(description = "应用编码")
    private String projectNo;

    @Schema(description = "模拟操作用户")
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @Schema(description = "模拟操作用户名称")
    private String createdName;

    @AuditField(name = "创建时间", ignore = true)
    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdDate;

    @Schema(description = "更改人")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @Schema(description = "更改人名称")
    private String updatedName;

    @AuditField(name = "更改时间", ignore = true)
    @Schema(description = "更改时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedDate;
}
