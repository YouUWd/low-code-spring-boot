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
 * 审批链历史状态表
 *
 * @author JDEC Team
 * @since 2026-07-03
 */
@Getter
@Setter
@TableName("approval_chain_status")
public class ApprovalChainStatus extends Model<ApprovalChainStatus> {

    private static final long serialVersionUID = 1L;

    /** id */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** approval_chain表对应的ID */
    @TableField("pid")
    private Long pid;

    /** 审批流水编号 */
    @TableField("serial_number")
    private String serialNumber;

    /** 审批流版本号 */
    @TableField("serial_no")
    private Integer serialNo;

    /** 当前步骤 */
    @TableField("current_step")
    private Integer currentStep;

    /** 所属主体 */
    @TableField("subject_id")
    private Long subjectId;

    /** 状态名称 */
    @TableField("title")
    private String title;

    /** 分类（主状态，子状态） */
    @TableField("status_type")
    private Integer statusType;

    /** 状态值 */
    @TableField("status_value")
    private Integer statusValue;

    /** 状态背景色 */
    @TableField("status_background")
    private String statusBackground;

    /** 状态字体颜色 */
    @TableField("status_font_color")
    private String statusFontColor;

    /** 是否启用(1:启用。0：不启用) */
    @TableField("enabled")
    private Byte enabled;

    /** 审批是否显示(1:显示。0：不显示) */
    @TableField("approval_show_flag")
    private Byte approvalShowFlag;

    /** 排序字段 */
    @TableField("sort_order")
    private Integer sortOrder;

    /** 应用编码 */
    @TableField("project_no")
    private String projectNo;

    /** 操作用户ID */
    @TableField("created_by")
    private Long createdBy;

    /** 操作用户名称 */
    @TableField("created_name")
    private String createdName;

    /** 创建时间 */
    @TableField("created_date")
    private LocalDateTime createdDate;

    /** 更改人ID */
    @TableField("updated_by")
    private Long updatedBy;

    /** 更改人名称 */
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
