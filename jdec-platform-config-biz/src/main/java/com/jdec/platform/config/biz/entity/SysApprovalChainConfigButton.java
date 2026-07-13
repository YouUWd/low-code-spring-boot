package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/** 系统表-审批链配置按钮表 */
@Data
@TableName("sys_approval_chain_config_button")
@Schema(description = "审批链配置按钮")
public class SysApprovalChainConfigButton implements Serializable {

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "sys_approval_chain_config表对应的ID")
    private Long pid;

    @Schema(description = "操作类型 sys_config表对应的ID")
    private Long buttonTypeId;

    @Schema(description = "按钮 sys_config_button按钮表对应ID")
    private Long buttonId;

    @Schema(description = "日志对应图标")
    private String icon;

    @Schema(description = "日志对应状态描述")
    private String logName;

    @Schema(description = "是否自动进入下一任务 1:是 0:否")
    private Integer autoNextTaskFlag;

    @Schema(description = "消息模板 sys_wechat_template对应ID")
    private String msgTemplateIds;

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
}
