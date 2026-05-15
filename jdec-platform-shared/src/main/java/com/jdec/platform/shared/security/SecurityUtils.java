package com.jdec.platform.shared.security;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONUtil;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.security.model.LoginUser;
import org.springframework.http.HttpStatus;

/** 安全工具类 */
public class SecurityUtils {

    private static final String LOGIN_USER_KEY = "loginUser";

    /** 获取当前登录用户 */
    public static LoginUser getLoginUser() {
        if (!StpUtil.isLogin()) {
            return null;
        }
        Object object = StpUtil.getSession().get(LOGIN_USER_KEY);
        return JSONUtil.toBean(object.toString(), LoginUser.class);
    }

    /** 获取当前登录用户（必须登录） */
    public static LoginUser getLoginUserOrThrow() {
        LoginUser loginUser = getLoginUser();
        if (loginUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED.value(), "未登录或登录已过期");
        }
        return loginUser;
    }

    /** 获取当前登录用户ID */
    public static Long getLoginUserId() {
        LoginUser user = getLoginUserOrThrow();
        return user.getUserId();
    }

    /** 存储登录用户信息 */
    public static void setLoginUser(LoginUser loginUser) {
        StpUtil.getSession().set(LOGIN_USER_KEY, JSONUtil.toJsonStr(loginUser));
    }

    /** 判断是否超级管理员 */
    public static boolean isSuperAdmin() {
        LoginUser user = getLoginUser();
        return user != null && Boolean.TRUE.equals(user.getSuperAdmin());
    }
}
