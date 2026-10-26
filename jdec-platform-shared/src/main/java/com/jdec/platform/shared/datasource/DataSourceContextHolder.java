package com.jdec.platform.shared.datasource;

import java.util.ArrayDeque;
import java.util.Deque;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据源上下文持有者 — 基于 ThreadLocal + 栈 实现线程级数据源切换。
 *
 * <p>使用栈结构支持嵌套数据源切换：内层方法结束后自动恢复外层数据源。 使用完毕后务必调用 {@link #clear()} 避免内存泄漏和连接串用。
 *
 * <p>包内可见：仅供 {@link DataSourceAspect} 和 {@link DynamicRoutingDataSource} 使用， 业务代码应通过 {@link
 * DataSource @DataSource} 注解切换数据源。
 */
@Slf4j
public final class DataSourceContextHolder {

    private static final ThreadLocal<Deque<String>> CONTEXT =
            ThreadLocal.withInitial(ArrayDeque::new);

    private DataSourceContextHolder() {}

    /** 压入数据源 — 支持嵌套调用，每次切换都压栈保存。 */
    public static void set(String dataSource) {
        log.debug("切换数据源 → {}（栈深度 {}）", dataSource, CONTEXT.get().size());
        CONTEXT.get().push(dataSource);
    }

    /** 获取当前线程使用的数据源（栈顶），栈空时返回 {@code null}（使用默认数据源）。 */
    static String get() {
        return CONTEXT.get().peek();
    }

    /** 弹出当前数据源，恢复上一层数据源；栈空时清除 ThreadLocal 避免内存泄漏。 */
    public static void clear() {
        Deque<String> stack = CONTEXT.get();
        if (!stack.isEmpty()) {
            String removed = stack.pop();
            log.debug("弹出数据源 {}，恢复到 → {}", removed, stack.peek());
        }
        if (stack.isEmpty()) {
            CONTEXT.remove();
        }
    }
}
