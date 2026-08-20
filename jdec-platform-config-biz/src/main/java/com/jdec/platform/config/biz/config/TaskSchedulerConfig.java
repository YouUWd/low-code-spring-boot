package com.jdec.platform.config.biz.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 定时任务调度器配置。
 *
 * <p>为定时 HTTP 任务提供独立的 {@link ThreadPoolTaskScheduler} 实例，不依赖 Spring 的
 * {@code @EnableScheduling}，任务由业务代码通过 {@code schedule(Runnable, Date)} 手动注册。
 */
@Configuration
public class TaskSchedulerConfig {

    @Bean(name = "scheduledTaskScheduler")
    public ThreadPoolTaskScheduler scheduledTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);
        scheduler.setThreadNamePrefix("scheduled-task-");
        // 应用关闭时等待正在执行的任务完成，避免中断 HTTP 调用
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        return scheduler;
    }
}
