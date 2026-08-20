package com.jdec.platform.shared.security;

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
}
