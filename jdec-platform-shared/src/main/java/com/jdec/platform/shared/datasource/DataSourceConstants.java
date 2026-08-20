package com.jdec.platform.shared.datasource;

/**
 * 数据源名称常量。
 *
 * <p>与 {@code application-dev.yml} / {@code application-prod.yml} 中 {@code
 * spring.datasource.dynamic.datasources} 下的 key 一一对应。
 */
public final class DataSourceConstants {

    private DataSourceConstants() {}

    /** 主数据源 — JDEC 平台库 */
    public static final String PRIMARY = "primary";

    /** auth_center 数据源 */
    public static final String AUTH_CENTER = "auth_center";

    /** config_center 数据源 */
    public static final String CONFIG_CENTER = "config_center";

    /** hr_manage 数据源 */
    public static final String HR_MANAGE = "hr_manage";

    public static final String STUDENT_MANAGE = "student_manage";
    //    public static final String MonitorLog = "monitorlog_manage";
}
