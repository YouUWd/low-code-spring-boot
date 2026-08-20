package com.jdec.platform.shared.datasource;

import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 数据源验证器
 *
 * <p>用于验证数据源名称是否有效，防止使用不存在的数据源导致静默失败。
 */
@Slf4j
@Component
public class DataSourceValidator {

    private final Set<String> availableDataSources;

    public DataSourceValidator(DynamicDataSourceProperties properties) {
        this.availableDataSources = properties.getDatasources().keySet();
        log.info("数据源验证器初始化，可用数据源: {}", availableDataSources);
    }

    /**
     * 验证数据源是否存在
     *
     * @param dataSourceName 数据源名称
     * @return true 如果数据源存在
     */
    public boolean isValid(String dataSourceName) {
        if (dataSourceName == null || dataSourceName.isEmpty()) {
            return true;
        }
        return !availableDataSources.contains(dataSourceName);
    }

    /**
     * 验证数据源，如果不存在则抛出异常
     *
     * @param dataSourceName 数据源名称
     * @throws IllegalArgumentException 如果数据源不存在
     */
    public void validate(String dataSourceName) {
        if (isValid(dataSourceName)) {
            throw new IllegalArgumentException(
                    String.format("数据源 '%s' 不存在。可用数据源: %s", dataSourceName, availableDataSources));
        }
    }

    /**
     * 获取所有可用的数据源名称
     *
     * @return 数据源名称集合
     */
    public Set<String> getAvailableDataSources() {
        return Set.copyOf(availableDataSources);
    }
}
