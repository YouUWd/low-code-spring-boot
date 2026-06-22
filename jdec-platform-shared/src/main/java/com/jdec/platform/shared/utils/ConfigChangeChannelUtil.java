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
     * <p>格式：jdec:config:change:{subjectId}:{projectNo}
     *
     * @param subjectId 主体ID
     * @param projectNo 项目编码
     * @return Redis Channel
     */
    public static String buildChannel(Long subjectId, String projectNo) {
        return String.format("%s:%d:%s", CHANNEL_PREFIX, subjectId, projectNo);
    }

    /**
     * 构建通配符 Channel（用于订阅）
     *
     * <p>格式：jdec:config:change:{subjectId}:*
     *
     * @param subjectId 主体ID
     * @return Redis Channel Pattern
     */
    public static String buildChannelPattern(Long subjectId) {
        return String.format("%s:%d:*", CHANNEL_PREFIX, subjectId);
    }

    /**
     * 构建通配符 Channel（订阅所有主体）
     *
     * <p>格式：jdec:config:change:*:*
     *
     * @return Redis Channel Pattern
     */
    public static String buildChannelPatternAll() {
        return String.format("%s:*:*", CHANNEL_PREFIX);
    }
}
