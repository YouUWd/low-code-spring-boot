package com.jdec.platform.config.biz.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审计日志发送上下文
 *
 * <p>封装调用方自定义的审计日志字段（模块、业务子模块、单据ID、操作名称、操作结果、备注、时间）。 其余信息（登录用户、模拟用户、角色、主体、IP、浏览器、平台等） 由 {@link
 * com.jdec.platform.config.biz.audit.service.AuditLogSendService} 根据当前请求上下文自动补全，调用方无需关心。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogSendContext {

    /** 模块名称（@DataAudit 注解中的 module 值，如：系统设置） */
    private String module;

    /** 业务子模块名称（如：状态管理），用于 hrefName 字段 */
    private String subModule;

    /** 单据ID（记录主键） */
    private Long dataId;

    /** 单据操作名称（如：修改了角色配置、新增了菜单配置） */
    private String controlName;

    /** 操作结果（成功/失败，可为空） */
    private String operatorResult;

    /** 日志备注（字段差异JSON字符串） */
    private String logRemark;

    /** 日志时间（格式：yyyy-MM-dd HH:mm:ss），为空时默认取当前时间 */
    private String logDate;
}
