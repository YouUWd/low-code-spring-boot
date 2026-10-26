package com.jdec.platform.shared.dto;

import java.util.Map;
import lombok.Data;

/**
 * 配置变更消息体
 *
 * <p>用于在 Redis Pub/Sub 中传递配置变更通知
 *
 * @author JDEC Platform
 * @since 2026-06-22
 */
@Data
public class ConfigChangeMessage {

    /** 消息唯一ID，格式 msg_{timestamp}_{random} */
    private String messageId;

    /** 发布时间（ISO 8601格式） */
    private String publishTime;

    /** 主体ID */
    private Long subjectId;

    /** 项目编码 */
    private String projectNo;

    /** 变更内容（业务系统根据 channel 中的 changeType 自行解析） */
    private Map<String, Object> payload;

    /** 操作人信息 */
    private OperatorInfo operator;

    /** 请求令牌（用于前端更新缓存） */
    private String token;

    /** 操作人信息 */
    @Data
    public static class OperatorInfo {
        /** 用户ID */
        private Long userId;

        /** 用户名 */
        private String userName;
    }
}
