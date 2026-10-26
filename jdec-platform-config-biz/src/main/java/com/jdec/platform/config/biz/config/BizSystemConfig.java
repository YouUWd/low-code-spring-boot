package com.jdec.platform.config.biz.config;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 业务系统配置 */
@Data
@Component
@ConfigurationProperties(prefix = "biz.system.data-permission")
public class BizSystemConfig {

    /** 业务系统数据权限接口地址映射 (key: projectNo, value: url) */
    private Map<String, String> urls = new HashMap<>();
}
