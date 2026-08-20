package com.jdec.platform.shared.constant;

/**
 * 虚拟用户常量定义。
 *
 * <p>用于定时任务等系统级操作的虚拟用户信息，这些用户不需要实际存在于数据库中， 但需要在权限校验时被识别并放行。
 */
public final class VirtualUserConstants {

    private VirtualUserConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** 定时任务系统虚拟用户 - 用户ID */
    public static final Long SCHEDULED_TASK_USER_ID = 9999999L;

    /** 定时任务系统虚拟用户 - 用户名 */
    public static final String SCHEDULED_TASK_USERNAME = "scheduled-task-system";

    /** 定时任务系统虚拟用户 - 手机号 */
    public static final String SCHEDULED_TASK_PHONE = "00000000000";

    /** JWT Token 过期时间：1天（毫秒） */
    public static final long TOKEN_EXPIRATION_ONE_DAY = 86400000L;
}
