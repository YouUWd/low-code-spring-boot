package com.jdec.platform.common.biz.perf;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StopWatch;

/**
 * 公共业务聚合服务性能耗时追踪器 (CommonPerfTracker)
 *
 * <p>基于 Spring {@link StopWatch} 实现各关键 API 调用的分段计时，保障线程安全， 并在方法结束时将完整的耗时瀑布链路统一输出到独立的性能日志文件中。
 */
public class CommonPerfTracker {

    private static final Logger PERF_LOG = LoggerFactory.getLogger("COMMON_PERF_LOGGER");

    private final String method;
    private final String tag;
    private final StopWatch stopWatch;
    private final Map<String, Object> extraInfo = new LinkedHashMap<>();

    private CommonPerfTracker(String method, String tag) {
        this.method = method;
        this.tag = tag;
        this.stopWatch =
                new StopWatch(method + (tag != null && !tag.isBlank() ? " [" + tag + "]" : ""));
    }

    /**
     * 开启一个追踪器实例（建议作为方法栈内局部变量使用，天然保障线程隔离）
     *
     * @param method 业务方法名
     * @param tag 附加标识（如 menuId 等）
     * @return 追踪器实例
     */
    public static CommonPerfTracker start(String method, String tag) {
        return new CommonPerfTracker(method, tag);
    }

    public static CommonPerfTracker start(String method) {
        return new CommonPerfTracker(method, null);
    }

    /** 记录有返回值的关键 API 调用耗时（线程安全） */
    public synchronized <T> T record(String taskName, Supplier<T> supplier) {
        startTask(taskName);
        try {
            return supplier.get();
        } finally {
            stopTask();
        }
    }

    /** 记录无返回值的关键 API 调用耗时（线程安全） */
    public synchronized void record(String taskName, Runnable runnable) {
        startTask(taskName);
        try {
            runnable.run();
        } finally {
            stopTask();
        }
    }

    /** 添加附加上下文信息（如 rows, total, pageNo 等） */
    public synchronized CommonPerfTracker addInfo(String key, Object value) {
        if (key != null && value != null) {
            extraInfo.put(key, value);
        }
        return this;
    }

    private void startTask(String taskName) {
        if (stopWatch.isRunning()) {
            stopWatch.stop();
        }
        stopWatch.start(taskName);
    }

    private void stopTask() {
        if (stopWatch.isRunning()) {
            stopWatch.stop();
        }
    }

    /** 结束追踪并输出性能打点日志到独立性能日志文件 */
    public synchronized void finish() {
        stopTask();
        long totalCost = stopWatch.getTotalTimeMillis();

        StringBuilder sb = new StringBuilder();
        sb.append("method=").append(method);
        if (tag != null && !tag.isBlank()) {
            sb.append(", tag=").append(tag);
        }
        sb.append(", totalCost=").append(totalCost).append("ms");

        // 拼接各关键 API 调用的分段耗时
        StopWatch.TaskInfo[] tasks = stopWatch.getTaskInfo();
        if (tasks != null && tasks.length > 0) {
            sb.append(", steps=[");
            for (int i = 0; i < tasks.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(tasks[i].getTaskName())
                        .append(":")
                        .append(tasks[i].getTimeMillis())
                        .append("ms");
            }
            sb.append("]");
        }

        if (!extraInfo.isEmpty()) {
            for (Map.Entry<String, Object> entry : extraInfo.entrySet()) {
                sb.append(", ").append(entry.getKey()).append("=").append(entry.getValue());
            }
        }

        PERF_LOG.info(sb.toString());
    }
}
