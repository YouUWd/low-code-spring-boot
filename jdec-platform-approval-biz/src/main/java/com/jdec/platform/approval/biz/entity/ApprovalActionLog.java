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
 * 单据审批操作日志
 *
 * @author JDEC Team
 * @since 2026-06-12
 */
@Getter
@Setter
@TableName("approval_action_log")
public class ApprovalActionLog extends Model<ApprovalActionLog> {

    private static final long serialVersionUID = 1L;

    /** id */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 操作类型 */
    @TableField("action_type")
    private Integer actionType;

    /** 操作日志对应的图片类型 */
    @TableField("action_icon")
    private String actionIcon;

    /** 操作日志对应的显示状态名称 */
    @TableField("action_name")
    private String actionName;

    /** 审批流水编号 */
    @TableField("serial_number")
    private String serialNumber;

    /** 业务id */
    @TableField("business_id")
    private Long businessId;

    /** 模块id */
    @TableField("module_id")
    private Long moduleId;

    /** 当前业务主状态id */
    @TableField("status_group_id")
    private Long statusGroupId;

    /** 当前业务状态id */
    @TableField("status_id")
    private Long statusId;

    /** 动作内容描述 */
    @TableField("description")
    private String description;

    /** 真实操作人(当前登录人) */
    @TableField("real_user_id")
    private Long realUserId;

    /** 真实操作人(当前登录人)名称 */
    @TableField("real_user_name")
    private String realUserName;

    /** 模拟操作用户 */
    @TableField("created_by")
    private Long createdBy;

    /** 模拟操作用户名称 */
    @TableField("created_name")
    private String createdName;

    /** 创建时间 */
    @TableField("created_date")
    private LocalDateTime createdDate;

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
