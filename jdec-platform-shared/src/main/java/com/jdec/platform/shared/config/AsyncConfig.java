package com.jdec.platform.shared.config;

import com.jdec.platform.shared.context.ContextCopyingDecorator;
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
        // 复制请求属性
        executor.setTaskDecorator(new ContextCopyingDecorator());

        executor.initialize();
        return executor;
    }
}
