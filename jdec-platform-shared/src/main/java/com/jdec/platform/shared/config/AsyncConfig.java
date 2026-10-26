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

    /**
     * 业务校验专用线程池。
     *
     * <p>校验会穿插在别人的接口（提交、保存、审批）里执行，这些接口本身带事务：事务一旦开启，连接就被锁定在开启事务时
     * 的那个库，校验需要读取业务库时切不过去。校验放到独立线程执行可以拿到新连接、重新按 {@code AppContext.projectNo} 路由到目标库，调用线程仍然同步等待校验结果。
     *
     * <p>与 {@link #jdecAsyncExecutor()} 的区别：这里<b>不使用</b> {@code CallerRunsPolicy}。线程池满时若退回调用方线程执行，
     * 又会落回调用方的事务里，“切库切不走”的问题会重新出现，因此直接拒绝，由调用方提示稍后重试。
     */
    @Bean(name = "validationCheckExecutor")
    public Executor validationCheckExecutor() {
        int corePoolSize = Math.max(2, Runtime.getRuntime().availableProcessors());
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(corePoolSize * 2);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("jdec-check-");
        // 拒绝策略：直接拒绝，绝不能在调用方线程执行（否则仍在调用方事务里，数据源切不走）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());

        executor.initialize();
        return executor;
    }
}
