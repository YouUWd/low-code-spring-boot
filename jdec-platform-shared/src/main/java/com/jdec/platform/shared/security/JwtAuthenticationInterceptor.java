package com.jdec.platform.shared.security;

import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.TokenContext;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.security.model.LoginUser;
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
    private static final String HEADER_HREF_URL = "href-url";
    private static final String HEADER_ROLE_ID = "role-id";
    private static final String HEADER_MOCK_USER_ID = "mock-user-id";

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

        // 1.1 支持本地开发与测试专属 Token (尽量从 HTTP 请求头动态提取)
        if ("dev-test-token".equals(token) || "test-token".equals(token)) {
            String roleIdStr = request.getHeader(HEADER_ROLE_ID);
            Long roleId = StringUtils.hasText(roleIdStr) ? Long.parseLong(roleIdStr) : 1L;

            String projectNo = request.getHeader("X-Project-No");
            if (!StringUtils.hasText(projectNo)) {
                projectNo = "school";
            }

            String subjectIdStr = request.getHeader("X-Subject-Id");
            Long subjectId = StringUtils.hasText(subjectIdStr) ? Long.parseLong(subjectIdStr) : 1L;

            String userIdStr = request.getHeader("X-User-Id");
            if (!StringUtils.hasText(userIdStr)) {
                userIdStr = request.getHeader(HEADER_MOCK_USER_ID);
            }
            Long userId = StringUtils.hasText(userIdStr) ? Long.parseLong(userIdStr) : 1L;

            String username = request.getHeader("X-User-Name");
            if (!StringUtils.hasText(username)) {
                username = "dev-admin";
            }

            String phone = request.getHeader("X-User-Phone");
            if (!StringUtils.hasText(phone)) {
                phone = "13800000000";
            }

            LoginUser devUser =
                    LoginUser.builder()
                            .userId(userId)
                            .username(username)
                            .phone(phone)
                            .subjectId(subjectId)
                            .token(token)
                            .hrefUrl(request.getHeader(HEADER_HREF_URL))
                            .roleId(roleId)
                            .isMockLogin(false)
                            .realUserId(userId)
                            .build();

            UserContext.setLoginUser(devUser);
            TokenContext.setToken(token);
            AppContext.setContext(projectNo, subjectId, userId, subjectId);
            return true;
        }

        // 2. JWT 解析
        Claims claims = jwtTokenProvider.parseToken(token);
        if (claims == null) {
            log.warn("JWT 解析失败或已过期");
            throw new BusinessException(HttpStatus.UNAUTHORIZED.value(), "令牌无效或已过期");
        }

        // 3. 从 JWT 中获取必要字段
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

        // 5. 从 JWT 中解析用户信息
        Long userId = claims.get("userId", Long.class);
        String username = claims.get("username", String.class);
        Long subjectId = claims.get("subjectId", Long.class);

        // 6. 从请求头获取额外信息
        String hrefUrl = request.getHeader(HEADER_HREF_URL);
        String roleIdStr = request.getHeader(HEADER_ROLE_ID);
        String mockUserIdStr = request.getHeader(HEADER_MOCK_USER_ID);

        Long roleId = StringUtils.hasText(roleIdStr) ? Long.parseLong(roleIdStr) : null;
        Long mockUserId = StringUtils.hasText(mockUserIdStr) ? Long.parseLong(mockUserIdStr) : null;

        // 7. 判断是否为模拟登录
        boolean isMockLogin = false;
        Long realUserId = userId; // 默认真实用户就是 token 中的用户

        if (mockUserId != null && !mockUserId.equals(userId)) {
            isMockLogin = true;
            log.info("检测到模拟登录: 真实用户ID={}, 模拟用户ID={}, roleId={}", userId, mockUserId, roleId);
        }

        // 8. 构建 LoginUser 对象并存入上下文
        LoginUser loginUser =
                LoginUser.builder()
                        .userId(userId)
                        .username(username)
                        .phone(phone)
                        .subjectId(subjectId)
                        .token(token)
                        .hrefUrl(hrefUrl)
                        .roleId(roleId)
                        .mockUserId(mockUserId)
                        .isMockLogin(isMockLogin)
                        .realUserId(realUserId)
                        .build();

        UserContext.setLoginUser(loginUser);

        // 9. 将 token 存入 TokenContext（保持兼容性）
        TokenContext.setToken(token);

        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {
        // 清理上下文
        TokenContext.clear();
        UserContext.remove();
    }
}
