package com.jdec.platform.config.biz.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.shared.annotation.RedisTopicListener;
import com.jdec.platform.shared.dto.ConfigChangeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * 低代码模块配置变更消息 Redis 监听器
 *
 * <p>订阅 "jdec:config:change:*:*:MODULE_CHANGED" 广播通道，
 * 在接收到任意节点的配置变更事件时，自动清空或精准失效指定模块配置元数据缓存，保障集群状态一致性。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RedisTopicListener(topic = "jdec:config:change:*:*:MODULE_CHANGED", pattern = true)
public class ModuleChangeListener implements MessageListener {

    private final ConfigApi configApi;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String body = new String(message.getBody());
        log.info("收到模块配置变更通知 Redis 广播，channel={}, message={}", channel, body);

        try {
            ConfigChangeMessage msg = objectMapper.readValue(body, ConfigChangeMessage.class);
            Long moduleId = null;
            if (msg != null
                    && msg.getPayload() != null
                    && msg.getPayload().get("moduleId") != null) {
                Object val = msg.getPayload().get("moduleId");
                if (val instanceof Number) {
                    moduleId = ((Number) val).longValue();
                } else {
                    moduleId = Long.valueOf(val.toString());
                }
            }

            if (moduleId != null) {
                configApi.clearModuleCache(moduleId);
                log.info("已精准淘汰模块 {} 的配置元数据缓存", moduleId);
            } else {
                configApi.clearMetaCache();
                log.info("未解析到 moduleId，全量清空配置元数据缓存");
            }
        } catch (Exception e) {
            log.error("解析或处理配置变更广播通知失败，使用全量清退兜底", e);
            try {
                configApi.clearMetaCache();
            } catch (Exception ex) {
                log.error("全量清退配置元数据缓存兜底失败", ex);
            }
        }
    }
}
