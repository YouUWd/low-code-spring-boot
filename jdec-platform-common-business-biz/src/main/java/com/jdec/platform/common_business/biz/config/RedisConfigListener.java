package com.jdec.platform.common_business.biz.config;

import com.jdec.platform.common_business.biz.common.CommonConstant;
import com.jdec.platform.common_business.biz.enums.ConfigCenterChangeType;
import com.jdec.platform.common_business.biz.service.impl.ConfigCenterMessageListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

// @Configuration
@Slf4j
public class RedisConfigListener {

    // @Value("${app.project.number}")
    private String projectNumber;

    /**
     * 配置订阅 基于MessageListenerAdapter和RedisMessageListenerContainer
     *
     * @param redisMessageSubscriber
     * @return
     */
    // @Bean
    public MessageListenerAdapter messageListenerAdapter(
            ConfigCenterMessageListener redisMessageSubscriber) {
        return new MessageListenerAdapter(redisMessageSubscriber, "handleMessage");
    }

    // @Bean(value = "comBizRedisMessageListenerContainer")
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter messageListenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // 添加消息监听器和监听的频道
        // 基于频道的发布/订阅：
        for (Long subjectId : CommonConstant.SUBJECT_MAP.keySet()) {
            for (ConfigCenterChangeType changeType : ConfigCenterChangeType.values()) {
                String channel =
                        String.format(
                                CommonConstant.CONFIG_CENTER_REDIS_CHANNEL_KEY,
                                subjectId.toString(),
                                projectNumber,
                                changeType.name());
                container.addMessageListener(messageListenerAdapter, new ChannelTopic(channel));
                log.info("RedisMessageListenerContainer Subscriber channel=" + channel);
            }

            // 基于模式的发布/订阅：
            // container.addMessageListener(messageListenerAdapter, new
            // PatternTopic("your-pattern-*"));
        }
        return container;
    }

    //    /**
    //     * 基于MessageListener的配置
    //     * 直接使用RedisMessageSubscriber
    //     * @param connectionFactory
    //     * @param configCenterMessageListener
    //     * @return
    //     */
    //    @Bean
    //    public RedisMessageListenerContainer redisMessageListenerContainer(
    //            RedisConnectionFactory connectionFactory,  ConfigCenterMessageListener
    // configCenterMessageListener) {
    //
    //        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    //        container.setConnectionFactory(connectionFactory);
    //
    //        // 在这里设置你的动态频道名称，可以从配置文件或其他地方获取
    //        String dynamicChannel = "your-channel";
    //
    //        // 基于频道的发布/订阅：
    //        ChannelTopic channelTopic = new ChannelTopic(dynamicChannel);
    //
    //        // 基于模式的发布/订阅：
    //        // container.addMessageListener(redisMessageSubscriber, new
    // PatternTopic("your-pattern-*"));
    //
    //        // 添加消息监听器和监听的动态频道
    //        container.addMessageListener(configCenterMessageListener, channelTopic);
    //
    //        return container;
    //    }
}
