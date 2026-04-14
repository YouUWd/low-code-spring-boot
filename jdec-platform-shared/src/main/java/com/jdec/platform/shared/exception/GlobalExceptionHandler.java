package com.jdec.platform.shared.exception;

import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.permission.PermissionDeniedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 *
 * <p>处理权限异常和其他系统异常
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理权限拒绝异常
     *
     * @param e 权限拒绝异常
     * @return 403 Forbidden 响应
     */
    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handlePermissionDenied(PermissionDeniedException e) {
        log.warn("权限拒绝: permissionCode={}, permissionType={}", 
            e.getPermissionCode(), e.getPermissionType());

        ApiResponse<Void> response = ApiResponse.error(403, e.getMessage());

        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(response);
    }

    /**
     * 处理其他异常
     *
     * @param e 异常
     * @return 500 Internal Server Error 响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("系统异常", e);

        ApiResponse<Void> response = ApiResponse.error("系统内部错误");

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(response);
    }
}
