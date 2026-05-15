package com.jdec.platform.shared.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 API 响应封装。
 *
 * <pre>
 * {
 *   "status": 200,
 *   "msg": "ok",
 *   "data": { ... }
 * }
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private int status;
    private String msg;
    private T data;

    /** 错误类型，用于前端统一处理，可选值: toast(默认), confirm, redirect_login, silent 等 */
    private String errorType;

    public ApiResponse(int status, String msg, T data) {
        this.status = status;
        this.msg = msg;
        this.data = data;
        this.errorType = null; // 默认为 null，前端按 toast 处理
    }

    // ===== 工厂方法 =====

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "ok", data, null);
    }

    public static <T> ApiResponse<T> success(String msg, T data) {
        return new ApiResponse<>(200, msg, data, null);
    }

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(200, "ok", null, null);
    }

    public static <T> ApiResponse<T> error(int status, String msg) {
        return new ApiResponse<>(status, msg, null, null);
    }

    public static <T> ApiResponse<T> error(int status, String msg, String errorType) {
        return new ApiResponse<>(status, msg, null, errorType);
    }

    public static <T> ApiResponse<T> error(String msg) {
        return new ApiResponse<>(500, msg, null, null);
    }
}
