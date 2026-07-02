package com.jdec.platform.shared.security;

import com.jdec.platform.shared.context.TokenContext;
import com.jdec.platform.shared.exception.BusinessException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/** JWT 统一认证拦截器 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationInterceptor implements HandlerInterceptor {

    private static final String REDIS_TOKEN_KEY_PREFIX = "token:";

    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${jwt.header:Authorization}")
    private String tokenHeader;

    @Value("${jwt.prefix:Bearer }")
    private String tokenPrefix;

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 从请求头获取 token
        String authHeader = request.getHeader(tokenHeader);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(tokenPrefix)) {
            log.warn("请求未携带有效的 {} 头", tokenHeader);
            throw new BusinessException(HttpStatus.UNAUTHORIZED.value(), "未登录或登录已过期");
        }

        String token = authHeader.substring(tokenPrefix.length());

        // 2. JWT 解析
        Claims claims = jwtTokenProvider.parseToken(token);
        if (claims == null) {
            log.warn("JWT 解析失败或已过期");
            throw new BusinessException(HttpStatus.UNAUTHORIZED.value(), "令牌无效或已过期");
        }

        // 3. 从 JWT 中获取 phone
        String phone = claims.get("phone", String.class);
        if (!StringUtils.hasText(phone)) {
            log.warn("JWT 中缺少 phone 字段");
            throw new BusinessException(HttpStatus.UNAUTHORIZED.value(), "令牌格式错误");
        }

        // 4. 校验 Redis 中是否存在 token
        String redisKey = REDIS_TOKEN_KEY_PREFIX + phone;
        String cachedToken = stringRedisTemplate.opsForValue().get(redisKey);

        if (!token.equals(cachedToken)) {
            log.warn("Redis 中不存在该 token 或已失效, phone={}", phone);
            throw new BusinessException(HttpStatus.UNAUTHORIZED.value(), "令牌已失效，请重新登录");
        }

        // 5. 将 token 存入 TokenContext
        TokenContext.setToken(token);

        // 6. 构建 LoginUser 并存入 UserContext
        log.debug("JWT 认证通过, phone={}", phone);
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {
        // 清理 TokenContext
        TokenContext.clear();
    }
}
