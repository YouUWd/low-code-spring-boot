package com.jdec.platform.shared.wechat;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 企业微信配置 - 支持多主体 */
@Data
@Configuration
@ConfigurationProperties(prefix = "wechat.work")
public class WeChatWorkConfig {

    /** 多主体配置 */
    private Map<String, SubjectConfig> subjects = new HashMap<>();

    /** API基础URL */
    private String apiBaseUrl = "https://qyapi.weixin.qq.com";

    /** Token缓存时间（秒） */
    private Long tokenCacheSeconds = 7000L;

    /** 主体配置 */
    @Data
    public static class SubjectConfig {
        /** 企业ID */
        private String corpId;

        /** 应用Secret */
        private String corpSecret;

        /** 应用AgentId */
        private Integer agentId;

        /** Token */
        private String token;

        /** AES Key */
        private String aesKey;
    }

    /** 根据主体ID获取配置 */
    public SubjectConfig getSubjectConfig(Long subjectId) {
        String key = getSubjectKey(subjectId);
        return subjects.get(key);
    }

    /** 根据主体ID获取配置key */
    private String getSubjectKey(Long subjectId) {
        // 1: environment, 2: electric, 3: energy
        if (subjectId == null) {
            return "environment";
        }
        return switch (subjectId.intValue()) {
            case 2 -> "electric";
            case 3 -> "energy";
            default -> "environment";
        };
    }
}
