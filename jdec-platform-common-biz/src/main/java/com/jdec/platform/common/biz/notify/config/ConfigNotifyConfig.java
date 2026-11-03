package com.jdec.platform.common.biz.notify.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置变更通知相关配置
 *
 * <p>提供消费 Redis 消息后的异步派发线程池（对标原方案的 Guava 线程池： 核心 2 ~ 最大 8 线程、CallerRunsPolicy 回退、守护线程）。
 *
 * @author JDEC Platform
 * @since 2026-11-03
 */
@Slf4j
@Configuration
public class ConfigNotifyConfig {

    @Bean("configChangeNotifyExecutor")
    public ExecutorService configChangeNotifyExecutor() {
        ThreadFactory threadFactory =
                r -> {
                    Thread t =
                            new Thread(
                                    r,
                                    "ConfigChangeNotify-" + new AtomicInteger(1).incrementAndGet());
                    t.setDaemon(true);
                    return t;
                };
        return new ThreadPoolExecutor(
                2,
                8,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1024),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
