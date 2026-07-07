package com.jdec.platform.shared.context;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置变更上下文（ThreadLocal）
 *
 * <p>用于在 Service 方法与 {@code ConfigChangeNotifyAspect} 之间传递受影响的用户/主体/模块 ID 列表。
 *
 * <p>使用方式：
 *
 * <pre>
 * // Service 层：在操作前/后 set
 * ConfigChangeContext.set(userIds, subjectIds, moduleIds);
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
        HOLDER.set(new Payload(affectedUserIds, affectedSubjectIds, null, new HashMap<>()));
    }

    /**
     * 设置受影响的用户/主体/模块 ID 列表
     *
     * @param affectedUserIds 受影响的用户 ID 列表
     * @param affectedSubjectIds 受影响的主体 ID 列表
     * @param affectedModuleIds 受影响的模块 ID 列表
     */
    public static void set(
            List<Long> affectedUserIds,
            List<Long> affectedSubjectIds,
            List<Long> affectedModuleIds) {
        HOLDER.set(
                new Payload(
                        affectedUserIds, affectedSubjectIds, affectedModuleIds, new HashMap<>()));
    }

    /**
     * 设置受影响的用户/主体 ID 列表，并携带自定义 payload 数据
     *
     * @param affectedUserIds 受影响的用户 ID 列表
     * @param affectedSubjectIds 受影响的主体 ID 列表
     * @param customPayload 自定义 payload 数据（会追加到最终消息的 payload 中）
     */
    public static void set(
            List<Long> affectedUserIds,
            List<Long> affectedSubjectIds,
            Map<String, Object> customPayload) {
        HOLDER.set(new Payload(affectedUserIds, affectedSubjectIds, null, customPayload));
    }

    /**
     * 设置受影响的用户/主体/模块 ID 列表，并携带自定义 payload 数据
     *
     * @param affectedUserIds 受影响的用户 ID 列表
     * @param affectedSubjectIds 受影响的主体 ID 列表
     * @param affectedModuleIds 受影响的模块 ID 列表
     * @param customPayload 自定义 payload 数据（会追加到最终消息的 payload 中）
     */
    public static void set(
            List<Long> affectedUserIds,
            List<Long> affectedSubjectIds,
            List<Long> affectedModuleIds,
            Map<String, Object> customPayload) {
        HOLDER.set(
                new Payload(affectedUserIds, affectedSubjectIds, affectedModuleIds, customPayload));
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

    /** 获取受影响的模块 ID 列表，未设置时返回空列表 */
    public static List<Long> getAffectedModuleIds() {
        Payload p = HOLDER.get();
        return p != null && p.moduleIds != null ? p.moduleIds : Collections.emptyList();
    }

    /** 获取自定义 payload 数据，未设置时返回空 Map */
    public static Map<String, Object> getCustomPayload() {
        Payload p = HOLDER.get();
        return p != null && p.customPayload != null ? p.customPayload : Collections.emptyMap();
    }

    /** 清理 ThreadLocal，防止内存泄漏。Aspect 在 finally 中负责调用 */
    public static void clear() {
        HOLDER.remove();
    }

    private record Payload(
            List<Long> userIds,
            List<Long> subjectIds,
            List<Long> moduleIds,
            Map<String, Object> customPayload) {}
}
