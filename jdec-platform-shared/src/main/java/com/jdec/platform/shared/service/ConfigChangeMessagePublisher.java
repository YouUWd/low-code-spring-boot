package com.jdec.platform.shared.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.shared.context.TokenContext;
import com.jdec.platform.shared.dto.ConfigChangeMessage;
import com.jdec.platform.shared.enums.ChangeType;
import com.jdec.platform.shared.utils.ConfigChangeChannelUtil;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 配置变更消息发布服务
 *
 * <p>负责将配置变更消息发布到 Redis Pub/Sub Channel
 *
 * <p>使用示例：
 *
 * <pre>
 * // 发布角色菜单变更消息
 * Map&lt;String, Object&gt; payload = new HashMap&lt;&gt;();
 * payload.put("roleId", 5L);
 * publisher.publish(1L, "HR001", ChangeType.ROLE_MENU_CHANGED, payload);
 * </pre>
 *
 * @author JDEC Platform
 * @since 2026-06-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigChangeMessagePublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 发布配置变更消息
     *
     * @param subjectId 主体ID
     * @param projectNo 项目编码
     * @param changeType 变更类型
     * @param payload 变更内容（业务系统根据 changeType 自行解析）
     */
    public void publish(
            Long subjectId, String projectNo, ChangeType changeType, Map<String, Object> payload) {
        publish(subjectId, projectNo, changeType.getCode(), payload);
    }

    /**
     * 发布配置变更消息（字符串类型）
     *
     * @param subjectId 主体ID
     * @param projectNo 项目编码
     * @param changeType 变更类型（字符串形式）
     * @param payload 变更内容
     */
    public void publish(
            Long subjectId, String projectNo, String changeType, Map<String, Object> payload) {
        try {
            // 构建 channel（changeType 放到 channel 中）
            String channel = ConfigChangeChannelUtil.buildChannel(subjectId, projectNo, changeType);

            // 构建消息体（不再包含 changeType）
            ConfigChangeMessage message = buildMessage(subjectId, projectNo, payload);

            // 序列化并发布
            String messageJson = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(channel, messageJson);

            log.info("配置变更消息发布成功: channel={}, payload={}", channel, payload);
            log.debug("完整消息内容: {}", messageJson);
        } catch (Exception e) {
            log.error(
                    "Redis 消息发布失败: subjectId={}, projectNo={}, changeType={}",
                    subjectId,
                    projectNo,
                    changeType,
                    e);
            // 消息发布失败不抛异常，避免影响业务流程
        }
    }

    /** 构建完整消息体 */
    private ConfigChangeMessage buildMessage(
            Long subjectId, String projectNo, Map<String, Object> payload) {
        ConfigChangeMessage message = new ConfigChangeMessage();
        message.setMessageId(generateMessageId());
        message.setPublishTime(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        message.setSubjectId(subjectId);
        message.setProjectNo(projectNo);
        message.setPayload(payload);

        // 添加 token（从 ThreadLocal 中获取）
        try {
            String token = TokenContext.getToken();
            if (token != null) {
                message.setToken(token);
                log.debug("已将 token 添加到配置变更消息中");
            }
        } catch (Exception e) {
            log.warn("获取 token 失败，跳过 token 字段", e);
        }

        // 添加操作人信息
        /*try {
            LoginUser loginUser = SecurityUtils.getLoginUser();
            if (loginUser != null) {
                ConfigChangeMessage.OperatorInfo operator = new ConfigChangeMessage.OperatorInfo();
                operator.setUserId(loginUser.getUserId());
                operator.setUserName(loginUser.getUsername());
                message.setOperator(operator);
            }
        } catch (Exception e) {
            log.warn("获取操作人信息失败，跳过 operator 字段", e);
        }*/

        return message;
    }

    /**
     * 生成消息ID
     *
     * <p>格式：msg_{timestamp}_{random}
     */
    private String generateMessageId() {
        return "msg_"
                + System.currentTimeMillis()
                + "_"
                + UUID.randomUUID().toString().substring(0, 8);
    }
}
