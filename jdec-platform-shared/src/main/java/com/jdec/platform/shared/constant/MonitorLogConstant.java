package com.jdec.platform.shared.constant;

/**
 * 监控日志常量
 *
 * <p>与数据引擎 {@code monitor_log} 的日志类型取值保持一致（同 {@code sys_config_item} 中「日志类型」配置项）。
 */
public final class MonitorLogConstant {

    /** 日志类型：操作日志 */
    public static final Integer TYPE_OPERATION_LOG = 0;

    /** 日志类型：消息日志 */
    public static final Integer TYPE_MESSAGE_LOG = 1;

    /** 日志类型：系统日志 */
    public static final Integer TYPE_SYSTEM_LOG = 2;

    /** 操作日志子类型标识（approve / content JSON 内的 logSubType 取值） */
    public static final String OPERATION_LOG = "operationLog";

    /** 消息日志子类型标识（approve / content JSON 内的 logSubType 取值） */
    public static final String MESSAGE_LOG = "messageLog";

    /** 系统日志子类型标识（approve / content JSON 内的 logSubType 取值） */
    public static final String SYSTEM_LOG = "systemLog";

    private MonitorLogConstant() {}
}
