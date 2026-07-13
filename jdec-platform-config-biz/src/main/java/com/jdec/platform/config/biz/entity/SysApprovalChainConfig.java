package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/** 系统表-审批链配置表 */
@Data
@TableName("sys_approval_chain_config")
@Schema(description = "审批链配置")
public class SysApprovalChainConfig implements Serializable {

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "所属模块ID 0表示不属于模块")
    private Long moduleId;

    @Schema(description = "上一步步骤")
    private Integer upStep;

    @Schema(description = "当前步骤")
    private Integer currentStep;

    @Schema(description = "下一步步骤")
    private Integer nextStep;

    @Schema(description = "审批链分类ID")
    private Long approvalChainTypeId;

    @Schema(description = "审批规则")
    private String approvalRule;

    @Schema(description = "驳回规则")
    private String rejectRule;

    @Schema(description = "0~100 approval_rule为3时设置需要审批人数的百分比")
    private Integer roleApprovalPercent;

    @Schema(description = "审批角色组 sys_role表的id 审批角色组 approval_rule为1 2 3时")
    private Long approveRoleId;

    @Schema(description = "审批人 sys_user表的id approval_rule为0时")
    private Long approverId;

    @Schema(description = "移交人ID approval_rule为0时使用")
    private Long delegateApproverId;

    @Schema(description = "是否跳过步骤 1:跳过 0:不跳过")
    private Integer skipped;

    @Schema(description = "1:显示，0:隐藏")
    private Integer showed;

    @Schema(description = "子模块集合ID, 英文逗号分隔")
    private String childModuleIds;

    @Schema(description = "当前状态ID")
    private Long currentStatusId;

    @Schema(description = "下一状态ID")
    private Long nextStatusId;

    @Schema(description = "1:自动审批，0:不能自动审批，默认为0")
    private Integer autoApproved;

    @Schema(description = "系统自动办理时间 auto_approved为1时必填")
    private Integer autoApprovedTime;

    @Schema(description = "办理人未办理的最迟时间 auto_approved_time为0时可填")
    private Integer deadlineTime;

    @Schema(description = "按钮ID列表")
    private String buttonList;

    @Schema(description = "是否协同办理 0:否 1:是")
    private Integer collaborated;

    @Schema(description = "是否可以重复提交 0:否 1:是")
    private Integer repeated;

    @Schema(description = "消息模板ID集合, 英文逗号分隔")
    private String msgTemplateIds;

    @Schema(description = "是否默认，1：是，0：否")
    private Integer defaultFlag;

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
