package com.jdec.platform.shared.exception;

import com.jdec.platform.shared.model.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 全局异常处理器
 *
 * <p>处理权限异常和其他系统异常
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {


    /**
     * 处理业务异常
     *
     * @param e 业务异常
     * @return 400 Bad Request 响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {

        ApiResponse<Void> response = ApiResponse.error(400, e.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    /**
     * 处理数据库唯一约束冲突异常
     *
     * @param e 重复键异常
     * @return 400 Bad Request 响应
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateKeyException(DuplicateKeyException e) {
        log.warn("数据库唯一约束冲突", e);

        // 从异常消息中提取关键文案
        // 格式: Duplicate entry 'value' for key 'table.column_name'
        String message = "数据已存在";
        String exceptionMsg = e.getMessage();
        
        if (exceptionMsg != null && exceptionMsg.contains("Duplicate entry")) {
            try {
                // 提取 'value' - 找到 "Duplicate entry '" 后的值
                String prefix = "Duplicate entry '";
                int valueStart = exceptionMsg.indexOf(prefix);
                if (valueStart >= 0) {
                    valueStart += prefix.length();
                    int valueEnd = exceptionMsg.indexOf("'", valueStart);
                    
                    // 提取表名 - 找到 "for key '" 后的值
                    String keyPrefix = "for key '";
                    int keyStart = exceptionMsg.indexOf(keyPrefix);
                    if (keyStart >= 0) {
                        keyStart += keyPrefix.length();
                        int keyEnd = exceptionMsg.indexOf("'", keyStart);
                        
                        if (valueEnd > valueStart && keyEnd > keyStart) {
                            String value = exceptionMsg.substring(valueStart, valueEnd);
                            String keyInfo = exceptionMsg.substring(keyStart, keyEnd);
                            String tableName = keyInfo.split("\\.")[0]; // 获取表名
                            message = String.format("'%s' 在 %s 表中已存在", value, tableName);
                        }
                    }
                }
            } catch (Exception ex) {
                log.debug("解析异常消息失败", ex);
            }
        }

        ApiResponse<Void> response = ApiResponse.error(400, message);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }
    /**
     * 无对应Handler - 过滤掉静态资源请求
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ApiResponse<Void>> handleNoHandlerFoundException(
            NoHandlerFoundException e,
            HttpServletRequest request) {

        String uri = request.getRequestURI();

        // 过滤掉静态资源请求，不打印错误日志
        if (isStaticResource(uri)) {
            return null;
        }

        log.warn("接口不存在: {}", uri);
        ApiResponse<Void> response = ApiResponse.error("接口不存在: " + uri);
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }
    /**
     * 判断是否为静态资源请求
     */
    private boolean isStaticResource(String uri) {
        return uri.endsWith(".ico")
                || uri.endsWith(".css")
                || uri.endsWith(".js")
                || uri.endsWith(".png")
                || uri.endsWith(".jpg")
                || uri.endsWith(".gif")
                || uri.endsWith(".woff")
                || uri.endsWith(".woff2")
                || uri.endsWith(".ttf")
                || uri.startsWith("/webjars/")
                || uri.startsWith("/static/")
                || uri.startsWith("/assets/");
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
