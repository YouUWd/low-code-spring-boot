package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.jdec.platform.shared.audit.annotation.AuditField;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/** 系统表-状态表 */
@Data
@TableName("sys_status")
@Schema(description = "系统状态")
public class SysStatus implements Serializable {

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "父id")
    private Long pid;

    @Schema(description = "所属主体")
    private Long subjectId;

    @AuditField(name = "状态名称", uniqueIdentifier = true)
    @Schema(description = "状态名称")
    private String title;

    @Schema(description = "分类（主状态，子状态）")
    private Integer statusType;

    @Schema(description = "状态值")
    private Integer statusValue;

    @Schema(description = "状态背景色")
    private String statusBackground;

    @Schema(description = "状态字体颜色")
    private String statusFontColor;

    @Schema(description = "是否启用(1:启用。0：不启用)")
    private Integer enabled;

    @Schema(description = "审批是否显示(1:显示。0：不显示)")
    private Integer approvalShowFlag;

    @Schema(description = "排序值，从0开始")
    private Integer sortOrder;

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
