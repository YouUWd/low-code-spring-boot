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
 * 单据审批链记录按钮表
 *
 * @author JDEC Team
 * @since 2026-06-12
 */
@Getter
@Setter
@TableName("approval_chain_button")
public class ApprovalChainButton extends Model<ApprovalChainButton> {

    private static final long serialVersionUID = 1L;

    /** 主键id */
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

    /** 操作类型 sys_config_item表对应的ID */
    @TableField("button_type_id")
    private Long buttonTypeId;

    /** 操作类型 sys_config_item表对应的Value */
    @TableField("button_type_value")
    private String buttonTypeValue;

    /** 按钮 sys_button按钮表对应ID */
    @TableField("button_id")
    private Long buttonId;

    /** 日志对应图标 */
    @TableField("icon")
    private String icon;

    /** 日志对应状态描述 */
    @TableField("log_name")
    private String logName;

    /** 消息模板 sys_wechat_template对应ID */
    @TableField("msg_template_ids")
    private String msgTemplateIds;

    /** 创建人 */
    @TableField("created_by")
    private Long createdBy;

    /** 创建人名称 */
    @TableField("created_name")
    private String createdName;

    /** 创建时间 */
    @TableField("created_date")
    private LocalDateTime createdDate;

    /** 更改人 */
    @TableField("updated_by")
    private Long updatedBy;

    /** 更改名称） */
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
