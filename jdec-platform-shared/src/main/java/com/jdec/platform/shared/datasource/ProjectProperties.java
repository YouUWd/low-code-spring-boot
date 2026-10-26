package com.jdec.platform.shared.datasource;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 项目属性配置 承载 jdec.project 命名空间下的业务配置 */
@Data
@ConfigurationProperties(prefix = "jdec.project")
public class ProjectProperties {

    /**
     * projectNo -> 数据源名称的映射。 key: projectNo 值（如 "school", "CONFIG"） value: datasources 中的数据源 key（如
     * "school", "config_center"）
     */
    private Map<String, String> datasourceMapping = new HashMap<>();

    public String getDataSourceKey(String projectNo) {
        return datasourceMapping.get(projectNo);
    }
}
