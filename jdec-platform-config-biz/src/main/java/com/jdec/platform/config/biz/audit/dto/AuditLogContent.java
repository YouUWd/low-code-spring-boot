package com.jdec.platform.config.biz.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 审计日志内容 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogContent {

    /** 业务模块 */
    private String module;

    /** 业务单号/标识 */
    private String orderId;

    /** 跳转链接 */
    private String hrefUrl;

    /** 控制器名称 */
    private String controlName;
}
