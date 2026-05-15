package com.jdec.platform.shared.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误类型枚举
 *
 * <p>用于指导前端如何处理错误，前端无需关注具体错误码
 */
@Getter
@AllArgsConstructor
public enum ErrorType {

    /** 默认：Toast 提示 */
    TOAST("toast"),

    /** 需要用户确认的操作 */
    CONFIRM("confirm"),

    /** 跳转到登录页 */
    REDIRECT_LOGIN("redirect_login"),

    /** 静默处理，不显示提示 */
    SILENT("silent"),

    /** 弹窗提示 */
    MODAL("modal");

    private final String value;
}
