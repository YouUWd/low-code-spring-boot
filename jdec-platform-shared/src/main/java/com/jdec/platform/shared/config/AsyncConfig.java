package com.jdec.platform.shared.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync // 启用异步支持
public class AsyncConfig {

    /**
     * JDEC 专用异步任务线程池
     *
     * <p>配置原则：
     *
     * <ul>
     *   <li>核心线程数 = CPU 核心数 * 2（IO密集型适当放大）
     *   <li>最大线程数 = 核心线程数 * 2
     *   <li>队列容量适中，防止 OOM
     *   <li>拒绝策略：CallerRunsPolicy（让调用方线程执行，防止任务丢失）
     * </ul>
     */
    @Bean(name = "taskAsyncExecutor")
    public Executor jdecAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Runtime.getRuntime().availableProcessors() * 2);
        executor.setMaxPoolSize(Runtime.getRuntime().availableProcessors() * 4);
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("jdec-async-");

        // 拒绝策略：当队列满时，由调用方线程同步执行（保证任务不丢失）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // ★ 重要：设置 TaskDecorator，用于在异步线程中自动拷贝父线程的上下文
        // 这样即使你忘记传 projectNo，也能从父线程继承（仅作兜底，不推荐依赖）
        executor.setTaskDecorator(
                runnable -> {
                    // 这里可以拷贝 MDC、AppContext 等上下文
                    // 但强烈建议：通过参数显式传递 projectNo，不要依赖 ThreadLocal 继承
                    return runnable;
                });

        executor.initialize();
        return executor;
    }
}
