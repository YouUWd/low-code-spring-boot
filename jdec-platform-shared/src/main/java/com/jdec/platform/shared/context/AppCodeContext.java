package com.jdec.platform.shared.context;

import com.jdec.platform.shared.constants.SystemConstant;

/** AppCode 请求上下文 */
public class AppCodeContext {

    private static final ThreadLocal<String> APP_CODE_HOLDER = new ThreadLocal<>();

    /** 设置当前请求的 appCode */
    public static void set(String appCode) {
        APP_CODE_HOLDER.set(appCode);
    }

    /** 获取当前请求的 appCode */
    public static String get() {
        String appCode = APP_CODE_HOLDER.get();
        return appCode != null ? appCode : SystemConstant.DEFAULT_APP_CODE;
    }

    /** 移除当前线程的 appCode */
    public static void remove() {
        APP_CODE_HOLDER.remove();
    }
}
