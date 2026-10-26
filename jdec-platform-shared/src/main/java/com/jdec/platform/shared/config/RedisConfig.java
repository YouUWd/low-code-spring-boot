package com.jdec.platform.shared.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jdec.platform.shared.annotation.RedisTopicListener;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/** Redis 配置：使用 Jackson JSON 序列化替代默认的 JDK 序列化。 */
@Slf4j
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Jackson 序列化器
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(mapper);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // Key 使用 String 序列化
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value 使用 JSON 序列化
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Redis 消息监听容器 - 自动扫描并注册带有 @RedisTopicListener 注解的监听器
     *
     * @param connectionFactory Redis 连接工厂
     * @param listeners 所有 MessageListener Bean
     * @param environment Spring 环境变量，用于解析配置占位符
     * @return RedisMessageListenerContainer
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            List<MessageListener> listeners,
            Environment environment) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // 自动扫描并注册带有 @RedisTopicListener 注解的监听器
        for (MessageListener listener : listeners) {
            // 获取真实的目标类（处理 Spring 代理）
            Class<?> targetClass = AopUtils.getTargetClass(listener);
            RedisTopicListener annotation = targetClass.getAnnotation(RedisTopicListener.class);

            if (annotation != null) {
                // 解析 topic（支持配置占位符）
                String topicValue = environment.resolvePlaceholders(annotation.topic());

                // 根据 pattern 选择 Topic 类型
                Topic topic =
                        annotation.pattern()
                                ? new PatternTopic(topicValue)
                                : new ChannelTopic(topicValue);

                container.addMessageListener(listener, topic);
                log.info(
                        "注册 Redis 监听器: {} -> {} (pattern: {})",
                        targetClass.getSimpleName(),
                        topicValue,
                        annotation.pattern());
            }
        }

        return container;
    }
}
