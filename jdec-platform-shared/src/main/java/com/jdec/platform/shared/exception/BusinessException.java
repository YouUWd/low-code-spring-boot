package com.jdec.platform.shared.exception;

import lombok.Getter;

/**
 * 业务异常类
 *
 * <p>支持两种构造方式:
 *
 * <ul>
 *   <li>传入 code + message: {@code throw new BusinessException(401, "用户不存在")}
 *   <li>仅传入 message (默认 code=700): {@code throw new BusinessException("操作失败")}
 * </ul>
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 错误码 */
    private final int code;

    /**
     * 构造方法 - 使用 code 和 message
     *
     * @param code 错误码
     * @param message 错误消息
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 构造方法 - 使用 code、message 和异常原因
     *
     * @param code 错误码
     * @param message 错误消息
     * @param cause 异常原因
     */
    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * 构造方法 - 仅使用 message (默认 code=700 WARNING)
     *
     * @param message 错误消息
     */
    public BusinessException(String message) {
        super(message);
        this.code = ApiCodeEnum.WARNING.getCode();
    }

    /**
     * 构造方法 - 使用 message 和异常原因 (默认 code=700 WARNING)
     *
     * @param message 错误消息
     * @param cause 异常原因
     */
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.code = ApiCodeEnum.WARNING.getCode();
    }
}
