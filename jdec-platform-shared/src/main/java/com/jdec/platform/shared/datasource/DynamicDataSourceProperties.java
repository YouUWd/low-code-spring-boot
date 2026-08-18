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
 *       datasources:
 *         primary:
 *           url: ...
 *           username: ...
 *           password: ...
 *         slave:
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
        private Integer minimumIdle = 5;
        private Integer maximumPoolSize = 20;
        private Long connectionTimeout = 30000L;
        private Long idleTimeout = 600000L;
        private Long maxLifetime = 1800000L;
    }
}
