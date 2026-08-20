package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.jdec.platform.shared.audit.annotation.AuditField;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/** 按钮信息配置表 */
@Data
@TableName("sys_button")
@Schema(description = "按钮信息配置")
public class SysButton implements Serializable {

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @AuditField(name = "按钮名称", uniqueIdentifier = true)
    @Schema(description = "按钮名称")
    private String title;

    @Schema(description = "别名")
    private String alias;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "默认背景颜色")
    private String defaultBgColor;

    @Schema(description = "默认字体颜色")
    private String defaultFontColor;

    @Schema(description = "悬浮背景颜色")
    private String levitateBgColor;

    @Schema(description = "悬浮字体颜色")
    private String levitateFontColor;

    @Schema(description = "选中背景颜色")
    private String selectedBgColor;

    @Schema(description = "选中字体颜色")
    private String selectedFontColor;

    @Schema(description = "日志对应图标")
    private String icon;

    @Schema(description = "日志对应状态")
    private String logName;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "1显示 0隐藏")
    private Integer showed;

    @Schema(description = "是否启用(1:启用。0：不启用)")
    private Integer enabled;

    @Schema(description = "所属主体")
    private Long subjectId;

    @Schema(description = "应用编码")
    private String projectNo;

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

    @Schema(description = "1删除0正常")
    @TableLogic
    private Integer deleted;
}
