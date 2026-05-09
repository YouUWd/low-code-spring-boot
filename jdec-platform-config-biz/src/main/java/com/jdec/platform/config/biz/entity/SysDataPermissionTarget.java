package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/** 数据权限目标表 */
@Data
@TableName("sys_data_permission_target")
@Schema(description = "数据权限目标")
public class SysDataPermissionTarget implements Serializable {

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "关联role_data_permission.id")
    private Long roleDataPermissionId;

    @Schema(description = "目标类型 user/dept/company")
    private String targetType;

    @Schema(description = "目标ID（用户ID/部门ID等）")
    private Long targetId;

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

    @Schema(description = "1删除0正常")
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
