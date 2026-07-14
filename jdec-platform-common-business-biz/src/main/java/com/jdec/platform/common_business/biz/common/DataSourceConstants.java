package com.jdec.platform.common_business.biz.common;

/**
 * 数据源名称常量。
 *
 * <p>与 {@code application-dev.yml} / {@code application-prod.yml} 中 {@code
 * spring.datasource.dynamic.datasources} 下的 key 一一对应。
 */
public interface DataSourceConstants {

    /** 主数据源 — JDEC 平台库 */
    String PRIMARY = "primary";

    String STUDENT_MANAGE = "student_manage";
}
