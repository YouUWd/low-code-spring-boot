package com.jdec.platform.common_business.biz.context;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TokenContextHolder {

    private static final ThreadLocal<String> CURRENT_TOKEN = new ThreadLocal<>();

    /** 设置当前用户 token */
    public static void setCurrentUserToken(String token) {
        CURRENT_TOKEN.set(token);
        log.debug("Set current user token : {}", token);
    }

    /** 获取当前用户 token */
    public static String getCurrentUserToken() {
        return CURRENT_TOKEN.get();
    }

    /** 清理所有上下文 */
    public static void clear() {
        CURRENT_TOKEN.remove();
    }
}
