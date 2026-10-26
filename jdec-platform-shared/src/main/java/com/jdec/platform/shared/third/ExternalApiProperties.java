package com.jdec.platform.shared.third;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 外部 API 统一配置属性。 */
@Data
@Configuration
@ConfigurationProperties(prefix = "external-api")
public class ExternalApiProperties {

    /** 按名称注册的 API 配置，key 为 API 标识（如 "word-api"）。 */
    private Map<String, ApiConfig> apis = new HashMap<>();

    /**
     * 根据 API 名称获取配置。
     *
     * @param name API 标识
     * @return 对应的配置
     * @throws IllegalArgumentException 如果配置不存在
     */
    public ApiConfig getApi(String name) {
        ApiConfig config = apis.get(name);
        if (config == null) {
            throw new IllegalArgumentException("未找到外部 API 配置: " + name);
        }
        return config;
    }

    /** 单个 API 的配置。 */
    @Data
    public static class ApiConfig {

        /** API 密钥（用于签名等） */
        private String apiSecret;

        private String apiKey;

        /** 是否启用 */
        private boolean enabled = true;

        /** 基础 URL */
        private String baseUrl;

        /** 请求超时时间 */
        private Duration timeout = Duration.ofSeconds(10);

        /** 自定义请求头 */
        private Map<String, String> headers = new HashMap<>();
    }
}
