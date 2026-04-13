package com.jdec.platform.shared.datasource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * 动态数据源路由 — Spring 标准 {@link AbstractRoutingDataSource} 实现。
 *
 * <p>根据 {@link DataSourceContextHolder} 中当前线程绑定的数据源 key 决定实际使用哪个物理 DataSource。
 */
public class DynamicRoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        return DataSourceContextHolder.get();
    }
}
