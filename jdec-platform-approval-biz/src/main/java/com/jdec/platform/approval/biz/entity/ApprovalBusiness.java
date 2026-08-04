package com.jdec.platform.approval.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 通用单据列表
 *
 * @author JDEC Team
 * @since 2026-06-12
 */
@Getter
@Setter
@TableName("approval_business")
public class ApprovalBusiness extends Model<ApprovalBusiness> {

    private static final long serialVersionUID = 1L;

    /** 单据ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 主体ID */
    @TableField("subject_id")
    private Long subjectId;

    /** 模块ID */
    @TableField("module_id")
    private Long moduleId;

    /** 真实业务ID */
    @TableField("business_id")
    private Long businessId;

    /** 业务记录ID */
    @TableField("record_id")
    private Long recordId;

    /** 当前审批链分类ID */
    @TableField("approval_chain_type_id")
    private Long approvalChainTypeId;

    /** 单据编号 */
    @TableField("business_number")
    private String businessNumber;

    /** 审批流编号 */
    @TableField("serial_number")
    private String serialNumber;

    /** 当前单据版本号 */
    @TableField("version_no")
    private Integer versionNo;

    /** 当前审批流版本号 */
    @TableField("serial_no")
    private Integer serialNo;

    /** 当前审批流步骤 */
    @TableField("current_step")
    private Integer currentStep;

    /** 当前审批流状态ID */
    @TableField("status_id")
    private Long statusId;

    /** 状态名称 */
    @TableField("status_title")
    private String statusTitle;

    /** 状态背景色 */
    @TableField("status_background")
    private String statusBackground;

    /** 状态字体颜色 */
    @TableField("status_font_color")
    private String statusFontColor;

    /** 当前审批流状态 */
    @TableField("status_value")
    private Integer statusValue;

    /** 当前审批角色组 */
    @TableField("approve_role_id")
    private Long approveRoleId;

    /** 当前指定审批人ID */
    @TableField("approver_id")
    private Long approverId;

    /** 当前步骤已审批人，驳回会会清空，用于百分比判断 */
    @TableField("approver_id_list")
    private String approverIdList;

    /** 历史审批人ID-审批角色ID列表，如 2-3,3-3 */
    @TableField("approval_uids")
    private String approvalUids;

    /** 单据申请人ID */
    @TableField("created_by")
    private Long createdBy;

    /** 创建时间 */
    @TableField("created_date")
    private LocalDateTime createdDate;

    /** 单据申请人名称 */
    @TableField("created_name")
    private String createdName;

    /** 更改人ID */
    @TableField("updated_by")
    private Long updatedBy;

    /** 更新时间 */
    @TableField("updated_date")
    private LocalDateTime updatedDate;

    /** 更改人名称 */
    @TableField("updated_name")
    private String updatedName;

    /** '1:删除,0:正常' */
    @TableField("deleted")
    private Boolean deleted;

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
