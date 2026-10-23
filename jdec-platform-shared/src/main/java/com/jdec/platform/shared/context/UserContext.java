package com.jdec.platform.shared.context;

import com.jdec.platform.shared.security.model.LoginUser;

/** 用户上下文 - 使用 ThreadLocal 存储当前登录用户信息 */
public class UserContext {

    private static final ThreadLocal<LoginUser> USER_THREAD_LOCAL = new ThreadLocal<>();

    /** 设置当前登录用户 */
    public static void setLoginUser(LoginUser loginUser) {
        USER_THREAD_LOCAL.set(loginUser);
    }

    /** 获取当前登录用户 */
    public static LoginUser getLoginUser() {
        return USER_THREAD_LOCAL.get();
    }

    /** 清除当前登录用户 */
    public static void remove() {
        USER_THREAD_LOCAL.remove();
    }

    /** 判断当前是否已登录 */
    public static boolean isLogin() {
        return USER_THREAD_LOCAL.get() != null;
    }

    /** 获取当前登录用户的 userId（未登录返回 null） */
    public static Long getUserId() {
        LoginUser user = USER_THREAD_LOCAL.get();
        return user != null ? user.getUserId() : null;
    }

    /** 获取当前登录用户名（未登录返回 null） */
    public static String getUsername() {
        LoginUser user = USER_THREAD_LOCAL.get();
        return user != null ? user.getUsername() : null;
    }

    /** 获取当前登录用户手机号（未登录返回 null） */
    public static String getPhone() {
        LoginUser user = USER_THREAD_LOCAL.get();
        return user != null ? user.getPhone() : null;
    }

    /** 获取当前登录用户所属主体ID（未登录返回 null） */
    public static Long getSubjectId() {
        LoginUser user = USER_THREAD_LOCAL.get();
        return user != null ? user.getSubjectId() : null;
    }

    /** 获取当前角色ID（从请求头获取，未登录返回 null） */
    public static Long getRoleId() {
        LoginUser user = USER_THREAD_LOCAL.get();
        return user != null ? user.getRoleId() : null;
    }

    /** 获取当前访问页面URL（从请求头 href-url 获取，未登录返回 null） */
    public static String getHrefUrl() {
        LoginUser user = USER_THREAD_LOCAL.get();
        return user != null ? user.getHrefUrl() : null;
    }

    /** 获取模拟用户ID（仅在模拟登录时有值，未登录返回 null） */
    public static Long getMockUserId() {
        LoginUser user = USER_THREAD_LOCAL.get();
        return user != null ? user.getMockUserId() : null;
    }

    /** 获取真实操作用户ID（模拟登录时为 token 中的用户ID，未登录返回 null） */
    public static Long getRealUserId() {
        LoginUser user = USER_THREAD_LOCAL.get();
        return user != null ? user.getRealUserId() : null;
    }

    /** 判断是否超级管理员（未登录返回 false） */
    public static boolean isSuperAdmin() {
        LoginUser user = USER_THREAD_LOCAL.get();
        return user != null && Boolean.TRUE.equals(user.getSuperAdmin());
    }

    /** 判断当前是否为模拟登录（未登录返回 false） */
    public static boolean isMockLogin() {
        LoginUser user = USER_THREAD_LOCAL.get();
        return user != null && Boolean.TRUE.equals(user.getIsMockLogin());
    }

    /** 获取操作用户ID（模拟登录时为模拟用户ID，没有模拟用户时为当前登录用户ID，未登录返回 null） */
    public static Long getOperateUserId() {
        LoginUser user = USER_THREAD_LOCAL.get();
        return user != null
                ? user.getMockUserId() != null ? user.getMockUserId() : user.getUserId()
                : null;
    }
}
