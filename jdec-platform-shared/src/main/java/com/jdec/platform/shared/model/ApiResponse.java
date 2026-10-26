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

    // ===== 工厂方法 =====

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "ok", data);
    }

    public static <T> ApiResponse<T> success(String msg, T data) {
        return new ApiResponse<>(200, msg, data);
    }

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(200, "ok", null);
    }

    public static <T> ApiResponse<T> error(int status, String msg) {
        return new ApiResponse<>(status, msg, null);
    }

    public static <T> ApiResponse<T> error(String msg) {
        return new ApiResponse<>(500, msg, null);
    }

    public static <T> ApiResponse<T> customResponse(int status, String msg, T data) {
        return new ApiResponse<>(status, msg, data);
    }
}
