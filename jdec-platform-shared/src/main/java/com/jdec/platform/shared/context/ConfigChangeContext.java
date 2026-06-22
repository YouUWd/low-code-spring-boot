package com.jdec.platform.shared.context;

import java.util.Collections;
import java.util.List;

/**
 * 配置变更上下文（ThreadLocal）
 *
 * <p>用于在 Service 方法与 {@code ConfigChangeNotifyAspect} 之间传递受影响的用户/主体 ID 列表。
 *
 * <p>使用方式：
 *
 * <pre>
 * // Service 层：在操作前/后 set
 * ConfigChangeContext.set(userIds, subjectIds);
 *
 * // Aspect 层：读取后追加到 payload，最终在 finally 中调用 clear()
 * </pre>
 *
 * @author JDEC Platform
 * @since 2026-06-22
 */
public final class ConfigChangeContext {

    private ConfigChangeContext() {}

    private static final ThreadLocal<Payload> HOLDER = new ThreadLocal<>();

    /**
     * 设置受影响的用户/主体 ID 列表
     *
     * @param affectedUserIds 受影响的用户 ID 列表
     * @param affectedSubjectIds 受影响的主体 ID 列表
     */
    public static void set(List<Long> affectedUserIds, List<Long> affectedSubjectIds) {
        HOLDER.set(new Payload(affectedUserIds, affectedSubjectIds));
    }

    /** 获取受影响的用户 ID 列表，未设置时返回空列表 */
    public static List<Long> getAffectedUserIds() {
        Payload p = HOLDER.get();
        return p != null && p.userIds != null ? p.userIds : Collections.emptyList();
    }

    /** 获取受影响的主体 ID 列表，未设置时返回空列表 */
    public static List<Long> getAffectedSubjectIds() {
        Payload p = HOLDER.get();
        return p != null && p.subjectIds != null ? p.subjectIds : Collections.emptyList();
    }

    /** 清理 ThreadLocal，防止内存泄漏。Aspect 在 finally 中负责调用 */
    public static void clear() {
        HOLDER.remove();
    }

    private record Payload(List<Long> userIds, List<Long> subjectIds) {}
}
