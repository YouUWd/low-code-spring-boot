package com.jdec.platform.shared.annotation;

import java.lang.annotation.*;

/**
 * Redis Topic 监听器注解
 *
 * <p>用于标记 Redis 消息监听器，并指定要订阅的 topic/channel
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RedisTopicListener {

    /**
     * 要订阅的 topic/channel 名称
     *
     * <p>支持 Spring 表达式和配置占位符，例如：
     *
     * <ul>
     *   <li>"my-channel" - 固定值
     *   <li>"${redis.my-channel}" - 从配置文件读取
     * </ul>
     */
    String topic();

    /**
     * 是否使用模式匹配（pattern）
     *
     * <p>如果为 true，则使用 PatternTopic（支持通配符）；如果为 false，则使用 ChannelTopic（精确匹配）
     */
    boolean pattern() default false;
}
