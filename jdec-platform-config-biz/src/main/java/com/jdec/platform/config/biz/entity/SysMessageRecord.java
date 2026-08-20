package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 消息发送记录 实体类 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_message_record")
public class SysMessageRecord {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目编号 */
    private String projectNo;

    /** 主体 ID */
    private Long subjectId;

    /** 接收人(企业微信账号/手机号) */
    private String receiver;

    /** 消息类型 */
    private String messageType;

    /** 消息标题 */
    private String messageTitle;

    /** 消息内容 */
    private String messageContent;

    /** 发送状态 */
    private Integer sendStatus;

    /** 底层发送结果响应 */
    private String responseData;

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
    @TableLogic private Integer deleted;
}
