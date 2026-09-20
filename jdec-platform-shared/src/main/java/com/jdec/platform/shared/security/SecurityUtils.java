package com.jdec.platform.shared.security;

import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.security.model.LoginUser;
import org.springframework.http.HttpStatus;

/** 安全工具类 */
public class SecurityUtils {

    /** 获取当前登录用户 */
    public static LoginUser getLoginUser() {
        return UserContext.getLoginUser();
    }

    /** 获取当前登录用户（必须登录） */
    public static LoginUser getLoginUserOrThrow() {
        LoginUser loginUser = getLoginUser();
        if (loginUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED.value(), "未登录或登录已过期");
        }
        return loginUser;
    }

    /** 获取当前登录用户ID（必须登录） */
    public static Long getLoginUserId() {
        LoginUser user = getLoginUserOrThrow();
        return user.getUserId();
    }

    /** 判断是否超级管理员 */
    public static boolean isSuperAdmin() {
        return UserContext.isSuperAdmin();
    }

    /** 判断当前是否为模拟登录 */
    public static boolean isMockLogin() {
        return UserContext.isMockLogin();
    }

    /** 获取真实操作用户ID（模拟登录时返回 token 中的真实用户ID，非模拟登录返回当前用户ID） */
    public static Long getRealUserId() {
        getLoginUserOrThrow();
        return UserContext.getRealUserId();
    }

    /** 获取模拟用户ID（仅在模拟登录时有值） */
    public static Long getMockUserId() {
        return UserContext.getMockUserId();
    }

    /** 获取当前角色ID（从请求头获取） */
    public static Long getRoleId() {
        return UserContext.getRoleId();
    }

    /** 获取当前访问页面URL（从请求头获取） */
    public static String getHrefUrl() {
        return UserContext.getHrefUrl();
    }
}
