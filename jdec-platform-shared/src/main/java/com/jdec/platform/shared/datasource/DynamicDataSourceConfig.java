package com.jdec.platform.shared.datasource;

import com.zaxxer.hikari.HikariDataSource;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 动态多数据源配置。
 *
 * <p>根据 {@link DynamicDataSourceProperties} 中配置的多个数据源， 构建 {@link DynamicRoutingDataSource} 并注册为
 * Spring 的主 DataSource。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties({DynamicDataSourceProperties.class, ProjectProperties.class})
public class DynamicDataSourceConfig {

    private final DynamicDataSourceProperties properties;

    @Bean
    @Primary
    public DynamicRoutingDataSource dataSource() {
        DynamicRoutingDataSource routingDataSource = new DynamicRoutingDataSource();

        Map<Object, Object> targetDataSources = new HashMap<>();
        javax.sql.DataSource defaultDataSource = null;

        for (Map.Entry<String, DynamicDataSourceProperties.DataSourceProperty> entry :
                properties.getDatasources().entrySet()) {
            String name = entry.getKey();
            DynamicDataSourceProperties.DataSourceProperty prop = entry.getValue();

            // 使用 HikariCP 数据源
            HikariDataSource ds = new HikariDataSource();
            ds.setPoolName("HikariPool-" + name);
            ds.setJdbcUrl(prop.getUrl());
            ds.setUsername(prop.getUsername());
            ds.setPassword(prop.getPassword());
            ds.setDriverClassName(prop.getDriverClassName());

            // 连接池配置（从 YAML spring.datasource.dynamic.hikari 读取全局配置）
            ds.setMinimumIdle(properties.getHikari().getMinimumIdle());
            ds.setMaximumPoolSize(properties.getHikari().getMaximumPoolSize());
            ds.setConnectionTimeout(properties.getHikari().getConnectionTimeout());
            ds.setIdleTimeout(properties.getHikari().getIdleTimeout());
            ds.setMaxLifetime(properties.getHikari().getMaxLifetime());
            ds.setConnectionTestQuery("SELECT 1");

            targetDataSources.put(name, ds);
            log.info("注册数据源: {} → {}", name, prop.getUrl());

            // 记录主数据源
            if (name.equals(properties.getPrimary())) {
                defaultDataSource = ds;
            }
        }

        if (defaultDataSource == null && !targetDataSources.isEmpty()) {
            defaultDataSource = (javax.sql.DataSource) targetDataSources.values().iterator().next();
            log.warn("未找到名为 '{}' 的主数据源，使用第一个配置作为默认", properties.getPrimary());
        }

        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(defaultDataSource);
        routingDataSource.afterPropertiesSet();

        log.info("动态数据源初始化完成，共 {} 个数据源，默认: {}", targetDataSources.size(), properties.getPrimary());
        return routingDataSource;
    }
}
