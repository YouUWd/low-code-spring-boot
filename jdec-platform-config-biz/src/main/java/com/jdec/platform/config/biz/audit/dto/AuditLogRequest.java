package com.jdec.platform.config.biz.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** HTTP审计日志请求体 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogRequest {

    /** 日志类型 */
    private String logType;

    /** 主体ID */
    private Long subjectId;

    /** 登录用户ID */
    private Long loginUserId;

    /** 登录角色ID */
    private Long loginRoleId;

    /** 模拟标识（0：正常，1：模拟） */
    private Integer mockFlag;

    /** 模拟用户ID */
    private Long mockUserId;

    /** 模拟角色ID */
    private Long mockRoleId;

    /** IP地址 */
    private String ip;

    /** 浏览器 */
    private String browser;

    /** 操作平台 */
    private String platForm;

    /** 日志内容（JSON字符串） */
    private String logContent;

    /** 日志备注（字段差异JSON字符串） */
    private String logRemark;
}
