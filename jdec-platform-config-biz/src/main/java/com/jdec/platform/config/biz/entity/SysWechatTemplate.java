package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 企业微信消息通知模板配置表 实体类 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_wechat_template")
public class SysWechatTemplate {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目编号 */
    private String projectNo;

    /** 主体 ID */
    private Long subjectId;

    /** 消息标题 */
    private String templateTitle;

    /** 发送类型(配置ID) */
    private Long templateSendType;

    /** 接收消息类型(配置ID数组, JSON) */
    private String templateAcceptType;

    /** 触发类型(配置ID) */
    private Long templateTriggerType;

    /** 消息内容 */
    private String templateContent;

    /** 额外接收人 ID 数组 (JSON) */
    private String extraReceiverIds;

    /** 额外接收人姓名 (逗号分隔) */
    private String extraReceiverNames;

    /** 消息参数配置 ID 数组 (JSON) */
    private String configParamIds;

    /** 模块 ID */
    private Long moduleId;

    /** 模块名称 */
    private String moduleName;

    /** 是否审批链: 0-否, 1-是 */
    private Integer approvalFlag;

    /** 创建人 ID */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdDate;

    /** 创建人姓名 */
    private String createdName;

    /** 更新人 ID */
    private String updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedDate;

    /** 更新人姓名 */
    private String updatedName;

    /** 是否删除: 0-未删除, 1-已删除 */
    private Integer deleted;
}
