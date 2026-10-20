package com.jdec.platform.shared.datasource;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 多数据源配置属性，绑定 YAML 中 {@code spring.datasource.dynamic} 前缀。
 *
 * <pre>
 * spring:
 *   datasource:
 *     dynamic:
 *       primary: primary
 *       hikari:
 *         minimum-idle: 5
 *         maximum-pool-size: 20
 *         connection-timeout: 30000
 *         idle-timeout: 600000
 *         max-lifetime: 1800000
 *         validation-timeout: 5000
 *         leak-detection-threshold: 0      # 0=关闭；生产建议 60000
 *         keepalive-time: 0                # 0=关闭；建议 300000 且 < max-lifetime
 *       datasources:
 *         primary:
 *           url: ...
 *           username: ...
 *           password: ...
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "spring.datasource.dynamic")
public class DynamicDataSourceProperties {

    /** 默认数据源名称。 */
    private String primary = DataSourceConstants.PRIMARY;

    /** HikariCP 连接池全局配置。 */
    private HikariConfig hikari = new HikariConfig();

    /** 多数据源配置 Map，key 为数据源名称。 */
    private Map<String, DataSourceProperty> datasources = new LinkedHashMap<>();

    @Data
    public static class DataSourceProperty {
        private String url;
        private String username;
        private String password;
        private String driverClassName = "com.mysql.cj.jdbc.Driver";
    }

    @Data
    public static class HikariConfig {

        /** 最小空闲连接数。 */
        private Integer minimumIdle = 5;

        /** 最大连接数（池大小）。 */
        private Integer maximumPoolSize = 20;

        /** 获取连接超时（ms），超时抛 SQLException。 */
        private Long connectionTimeout = 30000L;

        /** 空闲连接回收阈值（ms），仅在空闲连接数 > minimumIdle 时生效。 */
        private Long idleTimeout = 600000L;

        /** 连接最大存活时间（ms），应小于 MySQL wait_timeout。 */
        private Long maxLifetime = 1800000L;

        /**
         * 连接泄漏检测阈值（ms）。
         *
         * <p>{@code 0} 表示关闭检测。开启后若连接借出超过该时长仍未归还， Hikari 会打 ERROR 日志并打印借出方的堆栈，便于定位"忘关连接 / 事务悬挂"。
         *
         * <p>HikariCP 要求非 0 时必须 ≥ 2000，且 &lt; maxLifetime。 开发环境推荐 10000（10s），生产 60000（60s）。
         */
        private Long leakDetectionThreshold = 0L;

        /**
         * 连接有效性校验超时（ms）。
         *
         * <p>每次从池中取连接时用 {@code Connection.isValid()} 校验， 这是 JDBC4 标准 API，MySQL Connector/J 8.x
         * 原生支持。
         *
         * <p><b>不要配置 {@code connectionTestQuery}</b>——那会让 HikariCP 退化为 "每次借连接都执行一条 SELECT
         * 1"，有额外网络往返开销，MySQL 8 上无必要。
         */
        private Long validationTimeout = 5000L;

        /**
         * 连接保活时间（ms）。
         *
         * <p>{@code 0} 表示关闭保活。开启后 Hikari 会定期在空闲连接上执行 {@code isValid()}，防止 MySQL {@code
         * wait_timeout} 或中间件 （如 ProxySQL、负载均衡）踢连接后池中出现死连接。
         *
         * <p>HikariCP 要求非 0 时 ≥ 30000，且必须 &lt; maxLifetime。推荐 300000（5min）。
         */
        private Long keepaliveTime = 0L;
    }
}
