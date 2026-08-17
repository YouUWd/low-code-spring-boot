package com.jdec.platform.approval.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 单据审批链记录表
 *
 * @author JDEC Team
 * @since 2026-06-12
 */
@Getter
@Setter
@TableName("approval_chain")
public class ApprovalChain extends Model<ApprovalChain> {

    private static final long serialVersionUID = 1L;

    /** 主键id */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 父id */
    @TableField("pid")
    private Long pid;

    /** 所属模块ID 0表示不属于模块 */
    @TableField("module_id")
    private Long moduleId;

    /** 审批流水编号 */
    @TableField("serial_number")
    private String serialNumber;

    /** 审批流版本号 */
    @TableField("serial_no")
    private Integer serialNo;

    /** pid和moduleId的哈希值 */
    @TableField("pid_module_hash")
    private String pidModuleHash;

    /** 当前步骤 */
    @TableField("current_step")
    private Integer currentStep;

    /** 下一步步骤 */
    @TableField("next_step")
    private Integer nextStep;

    /** 上一步步骤 */
    @TableField("up_step")
    private Integer upStep;

    /** 审批链分类 */
    @TableField("approval_chain_type_id")
    private Long approvalChainTypeId;

    /** 审批规则 */
    @TableField("approval_rule")
    private String approvalRule;

    /** 驳回规则 */
    @Schema(description = "驳回规则")
    @TableField("reject_rule")
    private String rejectRule;

    /** 0~100 approval_rule为3时设置需要审批人数的百分比 */
    @TableField("role_approval_percent")
    private Integer roleApprovalPercent;

    /** 审批角色组 sys_role表的id 审批角色组 approvale_rule 为1 2 3时 */
    @TableField("approve_role_id")
    private Long approveRoleId;

    /** 审批人 sys_user表的id approval_rule为0时 */
    @TableField("approver_id")
    private Long approverId;

    /** 当前步骤已审批人，驳回会会清空，用于百分比判断 */
    @TableField("approver_id_list")
    private String approverIdList;

    /** 审批原因 */
    @TableField("reason")
    private String reason;

    /** 移交人id approval_rule 为0时使用 */
    @TableField("delegate_approver_id")
    private Long delegateApproverId;

    /** 是否跳过步骤 1:跳过 0:不跳过 */
    @TableField("skipped")
    private Boolean skipped;

    /** 1:显示，0:隐藏 */
    @TableField("showed")
    private Boolean showed;

    /** 子模块集合Id, 英文逗号分隔 */
    @TableField("child_module_ids")
    private String childModuleIds;

    /** 当前主状态id */
    @TableField("current_status_group_id")
    private Long currentStatusGroupId;

    /** 当前子状态id */
    @TableField("current_status_child_id")
    private Long currentStatusChildId;

    /** 下一主状态id */
    @TableField("next_status_group_id")
    private Long nextStatusGroupId;

    /** 下一子状态id */
    @TableField("next_status_child_id")
    private Long nextStatusChildId;

    /** 1:自动审批，0:不能自动审批，默认为0 */
    @TableField("auto_approved")
    private Boolean autoApproved;

    /** 系统自动办理时间 auto_approved为1时必填 */
    @TableField("auto_approved_time")
    private Integer autoApprovedTime;

    /** 办理人未办理的最迟时间 auto_approved_time为0时可填 */
    @TableField("deadline_time")
    private LocalDateTime deadlineTime;

    /** 按钮Id列表 */
    @TableField("button_list")
    private String buttonList;

    /** 是否协同办理 0:否 1:是 */
    @TableField("collaborated")
    private Boolean collaborated;

    /** 是否可以重复提交 0:否 1:是 */
    @TableField("repeated")
    private Boolean repeated;

    /** 消息模板Id集合, 英文逗号分隔 */
    @TableField("msg_template_ids")
    private String msgTemplateIds;

    /** 是否默认，1：是，0：否 */
    @TableField("default_flag")
    private Boolean defaultFlag;

    /** 当前步骤已办理过的人id集合, 英文逗号分隔，不会清空 */
    @TableField("approval_uids")
    private String approvalUids;

    /** 当前步骤已办理过的人id */
    @TableField("approval_uid")
    private Long approvalUid;

    /** 是否已审批，1：是，0：否 */
    @TableField("approved")
    private Boolean approved;

    /** 是否发送过消息，1：是，0：否 */
    @TableField("sended_msg")
    private Boolean sendedMsg;

    /** 创建人（用户表id） */
    @TableField("created_by")
    private Long createdBy;

    /** 创建人名称（用户表user_name） */
    @TableField("created_name")
    private String createdName;

    /** 创建时间 */
    @TableField("created_date")
    private LocalDateTime createdDate;

    /** 更改人（用户表id） */
    @TableField("updated_by")
    private Long updatedBy;

    /** 更改名称（用户表user_name） */
    @TableField("updated_name")
    private String updatedName;

    /** 更改时间 */
    @TableField("updated_date")
    private LocalDateTime updatedDate;

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
