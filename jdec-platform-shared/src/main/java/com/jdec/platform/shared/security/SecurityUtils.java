package com.jdec.platform.shared.security;

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

    /** 获取当前登录用户ID */
    public static Long getLoginUserId() {
        LoginUser user = getLoginUserOrThrow();
        return user.getUserId();
    }

    /** 判断是否超级管理员 */
    public static boolean isSuperAdmin() {
        LoginUser user = getLoginUser();
        return user != null && Boolean.TRUE.equals(user.getSuperAdmin());
    }
}
