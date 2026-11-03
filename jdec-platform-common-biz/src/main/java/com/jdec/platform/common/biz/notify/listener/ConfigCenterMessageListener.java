package com.jdec.platform.common.biz.notify.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.common.biz.notify.service.PermissionChangeMessageHandle;
import com.jdec.platform.shared.annotation.RedisTopicListener;
import com.jdec.platform.shared.dto.ConfigChangeMessage;
import com.jdec.platform.shared.enums.ChangeType;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * 配置变更消息 Redis 消费入口（SSE 推送链路的「消费侧」）
 *
 * <p>对标原文档中的 {@code ConfigCenterMessageListener}，关键优化：
 *
 * <ul>
 *   <li><b>改用通配订阅</b> {@code jdec:config:change:*:*:*}，一次性接管所有主体 / 项目 / 变更类型的消息， 彻底规避原方案「订阅侧写死
 *       subjectId/projectNo 列表导致 channel 维度不一致」的隐患。
 *   <li>解析 channel 末段得到 {@code changeType} → 路由到对应 {@link PermissionChangeMessageHandle}。
 *   <li>线程池异步派发，避免阻塞 Redis 订阅线程。
 * </ul>
 *
 * @author JDEC Platform
 * @since 2026-11-03
 */
@Slf4j
@Component
@RedisTopicListener(topic = "jdec:config:change:*:*:*", pattern = true)
public class ConfigCenterMessageListener implements MessageListener {

    private final ObjectMapper objectMapper;
    private final List<PermissionChangeMessageHandle> handlers;
    private final ExecutorService executor;

    public ConfigCenterMessageListener(
            ObjectMapper objectMapper,
            List<PermissionChangeMessageHandle> handlers,
            @org.springframework.beans.factory.annotation.Qualifier("configChangeNotifyExecutor")
                    ExecutorService executor) {
        this.objectMapper = objectMapper;
        this.handlers = handlers;
        this.executor = executor;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
            String body = new String(message.getBody(), StandardCharsets.UTF_8);

            ChannelInfo info = parseChannel(channel);
            if (info == null) {
                log.warn("无法解析配置变更 channel，跳过: {}", channel);
                return;
            }

            ChangeType type = ChangeType.fromCode(info.changeType);
            if (type == null) {
                log.warn("未知的变更类型，跳过: {}", info.changeType);
                return;
            }

            ConfigChangeMessage msg = objectMapper.readValue(body, ConfigChangeMessage.class);

            log.info(
                    "收到配置变更消息 changeType={}, subjectId={}, projectNo={}",
                    type.getCode(),
                    info.subjectId,
                    info.projectNo);

            for (PermissionChangeMessageHandle handle : handlers) {
                if (handle.isReferToChangeType(type)) {
                    executor.submit(
                            () -> {
                                try {
                                    handle.handle(msg, type, info.subjectId);
                                } catch (Exception e) {
                                    log.error("处理配置变更消息异常 changeType={}", type.getCode(), e);
                                }
                            });
                }
            }
        } catch (Exception e) {
            log.error("消费配置变更 Redis 消息失败", e);
        }
    }

    /**
     * 解析 channel：jdec:config:change:{subjectId}:{projectNo}:{changeType}
     *
     * <p>从末尾向前取三段，避免 projectNo 含特殊字符带来的歧义。
     */
    private ChannelInfo parseChannel(String channel) {
        String[] parts = channel.split(":");
        if (parts.length < 4) {
            return null;
        }
        if (!"change".equals(parts[parts.length - 4])) {
            return null;
        }
        try {
            ChannelInfo info = new ChannelInfo();
            info.changeType = parts[parts.length - 1];
            info.projectNo = parts[parts.length - 2];
            info.subjectId = Long.parseLong(parts[parts.length - 3]);
            return info;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** channel 解析结果 */
    private static class ChannelInfo {
        Long subjectId;
        String projectNo;
        String changeType;
    }
}
