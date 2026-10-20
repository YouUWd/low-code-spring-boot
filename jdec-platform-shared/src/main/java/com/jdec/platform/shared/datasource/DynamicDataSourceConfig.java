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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 动态多数据源配置。
 *
 * <p>根据 {@link DynamicDataSourceProperties} 中配置的多个数据源，构建带项目兜底的 {@link
 * ProjectAwareRoutingDataSource} 并注册为 Spring 的主 DataSource。
 *
 * <p><b>关键设计：</b>
 *
 * <ul>
 *   <li>{@code dataSource()} 返回的是 {@link ProjectAwareRoutingDataSource}（{@link
 *       DynamicRoutingDataSource} 的子类），它会在 {@code determineCurrentLookupKey()} 里按 {@code
 *       AppContext.projectNo} 兜底， 覆盖"有事务 / 无事务 / jOOQ 直接执行 / MyBatis"全部路径；
 *   <li>{@code transactionManager()} 使用 Spring 标准 {@link DataSourceTransactionManager}，
 *       不再包含任何数据源切换逻辑——数据源路由全部下沉到 {@code DataSource} 层， 事务管理器只负责事务边界。
 * </ul>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties({DynamicDataSourceProperties.class, ProjectProperties.class})
public class DynamicDataSourceConfig {

    private final DynamicDataSourceProperties properties;

    /**
     * 构建主数据源（带项目兜底路由）。
     *
     * <p>返回类型保持 {@link DynamicRoutingDataSource}，所有依赖它的 Bean （{@code DataSourceResolver}、{@code
     * JooqDataContextFactory}、 {@code DataSourceValidator}、{@code transactionManager} 等）零改动即可注入。
     */
    @Bean
    @Primary
    public DynamicRoutingDataSource dataSource(ProjectProperties projectProperties) {
        // 关键：换成带项目兜底的实现（DataSourceContextHolder 为空时按 projectNo 兜底）
        DynamicRoutingDataSource routingDataSource =
                new ProjectAwareRoutingDataSource(projectProperties);

        Map<Object, Object> targetDataSources = new HashMap<>();
        javax.sql.DataSource defaultDataSource = null;

        for (Map.Entry<String, DynamicDataSourceProperties.DataSourceProperty> entry :
                properties.getDatasources().entrySet()) {
            String name = entry.getKey();
            DynamicDataSourceProperties.DataSourceProperty prop = entry.getValue();

            // 懒加载：首次 getConnection() 时才真正启动 HikariPool，启动过程更快
            HikariDataSource ds = new HikariDataSource();
            ds.setPoolName("HikariPool-" + name);
            ds.setJdbcUrl(prop.getUrl());
            ds.setUsername(prop.getUsername());
            ds.setPassword(prop.getPassword());
            ds.setDriverClassName(prop.getDriverClassName());

            // ---------- 必设项 ----------
            ds.setMinimumIdle(properties.getHikari().getMinimumIdle());
            ds.setMaximumPoolSize(properties.getHikari().getMaximumPoolSize());
            ds.setConnectionTimeout(properties.getHikari().getConnectionTimeout());
            ds.setIdleTimeout(properties.getHikari().getIdleTimeout());
            ds.setMaxLifetime(properties.getHikari().getMaxLifetime());

            // ---------- 可选：校验超时（JDBC4 isValid() 的超时） ----------
            Long vt = properties.getHikari().getValidationTimeout();
            if (vt != null && vt > 0) {
                // 兜底：HikariCP 要求 validationTimeout ∈ [250, connectionTimeout]，
                // 若用户误把 connectionTimeout 配得很小，这里做一次 min 保护
                long connTimeout = properties.getHikari().getConnectionTimeout();
                ds.setValidationTimeout(Math.min(vt, connTimeout - 1));
            }

            // ---------- 可选：连接泄漏检测（> 0 才启用） ----------
            // HikariCP 要求非 0 时 ≥ 2000 且 < maxLifetime；用 > 0 判断可以挡住误配并回落到"关闭"
            Long leak = properties.getHikari().getLeakDetectionThreshold();
            if (leak != null && leak > 0) {
                ds.setLeakDetectionThreshold(leak);
            }

            // ---------- 可选：连接保活（> 0 才启用） ----------
            // HikariCP 要求非 0 时 ≥ 30000 且 < maxLifetime，防止 MySQL wait_timeout 或中间设备踢连接
            Long keepalive = properties.getHikari().getKeepaliveTime();
            if (keepalive != null && keepalive > 0) {
                ds.setKeepaliveTime(keepalive);
            }

            // 注意：不要设置 connectionTestQuery —— HikariCP 在 JDBC4 驱动下应使用 Connection.isValid()，
            // MySQL Connector/J 8.x 原生支持；设置 connectionTestQuery 会退化为每次借连接执行 "SELECT 1"，
            // 增加一次网络往返开销，是官方明确指出的反模式。
            // ds.setConnectionTestQuery("SELECT 1");

            targetDataSources.put(name, ds);
            log.info("注册数据源: {} → {}", name, prop.getUrl());

            if (name.equals(properties.getPrimary())) {
                defaultDataSource = ds;
            }
        }

        // 空配置直接失败，别让 null 往下传
        if (targetDataSources.isEmpty()) {
            throw new IllegalStateException("动态数据源配置为空，请检查 spring.datasource.dynamic.datasources");
        }
        if (defaultDataSource == null) {
            defaultDataSource = (javax.sql.DataSource) targetDataSources.values().iterator().next();
            log.warn("未找到名为 '{}' 的主数据源，使用第一个配置作为默认", properties.getPrimary());
        }

        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(defaultDataSource);
        routingDataSource.afterPropertiesSet();

        log.info(
                "动态数据源初始化完成（ProjectAware 兜底已启用），共 {} 个数据源，默认: {}",
                targetDataSources.size(),
                properties.getPrimary());
        return routingDataSource;
    }

    /**
     * 事务管理器：Spring 标准实现。
     *
     * <p>数据源路由已下沉 事务管理器只需负责事务边界，不再包含任何 set/pop 数据源逻辑。
     *
     * <p>开启 {@code nestedTransactionAllowed}：允许业务使用 {@code Propagation.NESTED} 传播级别 （依赖 JDBC
     * Savepoint，MySQL InnoDB 支持）。
     */
    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(
            DynamicRoutingDataSource dynamicRoutingDataSource) {
        DataSourceTransactionManager transactionManager =
                new DataSourceTransactionManager(dynamicRoutingDataSource);
        transactionManager.setNestedTransactionAllowed(true);
        return transactionManager;
    }
}
