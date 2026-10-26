package com.jdec.platform.shared.context;

/**
 * Token 上下文（ThreadLocal）
 *
 * <p>用于在拦截器与切面之间传递当前请求的 JWT Token，以便在发送配置变更消息时携带 token。
 *
 * <p>使用方式：
 *
 * <pre>
 * // 拦截器：在请求开始时设置 token
 * TokenContext.setToken(token);
 *
 * // 切面：读取 token 并添加到消息 payload
 * String token = TokenContext.getToken();
 *
 * // 拦截器：在请求结束时清理
 * TokenContext.clear();
 * </pre>
 *
 * @author JDEC Platform
 * @since 2026-07-02
 */
public final class TokenContext {

    private TokenContext() {}

    private static final ThreadLocal<String> TOKEN_HOLDER = new ThreadLocal<>();

    /**
     * 设置当前请求的 token
     *
     * @param token JWT token（不含 Bearer 前缀）
     */
    public static void setToken(String token) {
        TOKEN_HOLDER.set(token);
    }

    /**
     * 获取当前请求的 token
     *
     * @return JWT token，未设置时返回 null
     */
    public static String getToken() {
        return TOKEN_HOLDER.get();
    }

    /**
     * 清理 ThreadLocal，防止内存泄漏
     *
     * <p>拦截器在 afterCompletion 中负责调用
     */
    public static void clear() {
        TOKEN_HOLDER.remove();
    }

    /** 判断当前是否有 token */
    public static boolean hasToken() {
        return TOKEN_HOLDER.get() != null;
    }
}
