package com.jdec.platform.shared.utils;

/**
 * 配置变更 Redis Channel 工具类
 *
 * <p>统一管理配置变更消息的 Redis Channel 命名规则
 *
 * @author JDEC Platform
 * @since 2026-06-22
 */
public class ConfigChangeChannelUtil {

    /** Channel 前缀 */
    private static final String CHANNEL_PREFIX = "jdec:config:change";

    private ConfigChangeChannelUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 构建 Redis Channel
     *
     * <p>格式：jdec:config:change:{subjectId}:{projectNo}:{changeType}
     *
     * @param subjectId 主体ID
     * @param projectNo 项目编码
     * @param changeType 变更类型
     * @return Redis Channel
     */
    public static String buildChannel(Long subjectId, String projectNo, String changeType) {
        return String.format("%s:%d:%s:%s", CHANNEL_PREFIX, subjectId, projectNo, changeType);
    }

    /**
     * 构建通配符 Channel（用于订阅特定主体和项目的所有变更类型）
     *
     * <p>格式：jdec:config:change:{subjectId}:{projectNo}:*
     *
     * @param subjectId 主体ID
     * @param projectNo 项目编码
     * @return Redis Channel Pattern
     */
    public static String buildChannelPattern(Long subjectId, String projectNo) {
        return String.format("%s:%d:%s:*", CHANNEL_PREFIX, subjectId, projectNo);
    }

    /**
     * 构建通配符 Channel（用于订阅特定主体的所有项目和变更类型）
     *
     * <p>格式：jdec:config:change:{subjectId}:*:*
     *
     * @param subjectId 主体ID
     * @return Redis Channel Pattern
     */
    public static String buildChannelPatternBySubject(Long subjectId) {
        return String.format("%s:%d:*:*", CHANNEL_PREFIX, subjectId);
    }

    /**
     * 构建通配符 Channel（用于订阅特定变更类型）
     *
     * <p>格式：jdec:config:change:{subjectId}:{projectNo}:{changeType}
     *
     * @param subjectId 主体ID
     * @param projectNo 项目编码
     * @param changeType 变更类型
     * @return Redis Channel
     */
    public static String buildChannelByType(Long subjectId, String projectNo, String changeType) {
        return buildChannel(subjectId, projectNo, changeType);
    }

    /**
     * 构建通配符 Channel（订阅所有主体、项目和变更类型）
     *
     * <p>格式：jdec:config:change:*:*:*
     *
     * @return Redis Channel Pattern
     */
    public static String buildChannelPatternAll() {
        return String.format("%s:*:*:*", CHANNEL_PREFIX);
    }
}
