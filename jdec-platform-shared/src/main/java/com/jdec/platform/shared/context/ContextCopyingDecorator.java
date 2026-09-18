package com.jdec.platform.shared.context;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * 把请求线程的「请求快照」+ MDC 复制到异步线程。
 *
 * <p>注意：只复制 POJO，不复制 HttpServletRequest。
 */
public class ContextCopyingDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // ★ 在主线程（提交任务的线程）执行，捕获快照
        RequestContextSnapshot snapshot = RequestContextSnapshotHolder.get();
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();

        return () -> {
            try {
                if (snapshot != null) {
                    RequestContextSnapshotHolder.set(snapshot);
                }
                if (mdcContext != null) {
                    MDC.setContextMap(mdcContext);
                }
                runnable.run();
            } finally {
                // 线程池复用必须清理
                RequestContextSnapshotHolder.clear();
                MDC.clear();
            }
        };
    }
}
