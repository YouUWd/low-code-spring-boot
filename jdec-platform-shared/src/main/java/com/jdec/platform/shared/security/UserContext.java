package com.jdec.platform.shared.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 用户上下文工具类
 *
 * <p>提供便捷的方式获取当前登录用户信息
 * <p>基于 Spring Security 的 SecurityContext 实现
 * <p>线程安全：使用 ThreadLocal 存储用户信息
 */
@Slf4j
public class UserContext {

    private UserContext() {
        // 工具类，不允许实例化
    }

    /**
     * 获取当前用户ID
     *
     * @return 用户ID，如果未认证则返回null
     */
    public static Long getCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                log.debug("未认证用户");
                return null;
            }

            Object principal = auth.getPrincipal();
            if (principal instanceof Long) {
                return (Long) principal;
            }

            // 尝试转换为 Long
            if (principal instanceof String) {
                try {
                    return Long.parseLong((String) principal);
                } catch (NumberFormatException e) {
                    log.warn("用户ID格式错误: {}", principal);
                    return null;
                }
            }

            log.warn("无法识别的用户ID类型: {}", principal.getClass().getName());
            return null;
        } catch (Exception e) {
            log.error("获取当前用户ID异常", e);
            return null;
        }
    }


    /**
     * 清除当前用户上下文
     *
     * <p>通常在登出时调用
     */
    public static void clearContext() {
        try {
            SecurityContextHolder.clearContext();
            log.debug("用户上下文已清除");
        } catch (Exception e) {
            log.error("清除用户上下文异常", e);
        }
    }
}
