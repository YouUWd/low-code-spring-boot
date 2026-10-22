package com.jdec.platform.shared.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "oss")
public class OssProperties {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucketName;
    private boolean pathStyleAccess;

    /** 外部公开访问域名或反代前缀 (可选，配置后优先使用) */
    private String domain;
}
