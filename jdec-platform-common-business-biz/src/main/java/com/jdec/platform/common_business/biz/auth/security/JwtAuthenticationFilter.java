package com.jdec.platform.common_business.biz.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.common_business.biz.auth.Common.AuthCommonConstant;
import com.jdec.platform.common_business.biz.auth.service.AuthApi;
import com.jdec.platform.common_business.biz.configcenter.client.ConfigCenterApiClient;
import com.jdec.platform.common_business.biz.context.TokenContextHolder;
import com.jdec.platform.common_business.biz.dto.LoginUserPrincipal;
import com.jdec.platform.common_business.biz.dto.configcenter.response.ConfigApiResponse;
import com.jdec.platform.common_business.biz.dto.configcenter.response.UserCheckResp;
import com.jdec.platform.common_business.biz.enums.ApiResponseCodeEnum;
import com.jdec.platform.common_business.biz.util.JsonUtil;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.model.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** JWT 认证过滤器 — 从请求头中提取 Bearer Token 并设置 Spring Security 上下文。 */
@Slf4j
@Component(value = "comBizJwtAuthenticationFilter")
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;

    private final ConfigCenterApiClient configCenterApiClient;

    private final AuthApi authApi;

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String currentUri = request.getRequestURI();
        if (Arrays.stream(AuthCommonConstant.PUBLIC_ENDPOINTS)
                        .anyMatch(x -> currentUri.startsWith(x))
                || Arrays.stream(AuthCommonConstant.SWAGGER_ENDPOINTS)
                        .anyMatch(x -> currentUri.startsWith(x))) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = extractToken(request);
        if (token != null) {
            try {
                if (!StringUtils.hasText(token)) {
                    throw new BusinessException(
                            ApiResponseCodeEnum.LONGIN_FORBIDDEN.getCode(),
                            ApiResponseCodeEnum.LONGIN_FORBIDDEN.getMessage());
                }
                String phone = jwtTokenProvider.getUserPhone(token);
                if (!StringUtils.hasText(phone)) {
                    throw new BusinessException(
                            ApiResponseCodeEnum.LONGIN_FORBIDDEN.getCode(),
                            ApiResponseCodeEnum.LONGIN_FORBIDDEN.getMessage());
                }
                Boolean flag = authApi.checkUserToken(token, phone);
                // 校验用户是否已经在大系统登录
                if (!flag) {
                    log.warn("token:{}已过期，手机号:{}请重新登录", token, phone);
                    throw new BusinessException(
                            ApiResponseCodeEnum.LONGIN_FORBIDDEN.getCode(),
                            ApiResponseCodeEnum.LONGIN_FORBIDDEN.getMessage());
                }

                LoginUserPrincipal userPrincipal = null;
                String key = AuthCommonConstant.LOGIN_KEY_PREFIX + phone;
                String userInfo = redisTemplate.opsForValue().get(key);
                if (!StringUtils.isEmpty(userInfo)) {
                    userPrincipal = JsonUtil.json2Obj(userInfo, LoginUserPrincipal.class);
                }
                TokenContextHolder.setCurrentUserToken(token);

                if (userPrincipal == null
                        || userPrincipal.getUserId() == null
                        || userPrincipal.getSubjectId() == null) {
                    ConfigApiResponse<UserCheckResp> apiResponse =
                            configCenterApiClient.getCurrentUserCheck(phone);
                    if (!ConfigApiResponse.isSuccessResponse(apiResponse)) {
                        throw new BusinessException(
                                ApiResponseCodeEnum.LONGIN_FORBIDDEN.getCode(),
                                ApiResponseCodeEnum.LONGIN_FORBIDDEN.getMessage());
                    }
                    UserCheckResp userCheckResp = apiResponse.getData();
                    if (userPrincipal == null) {
                        userPrincipal = new LoginUserPrincipal();
                    }
                    userPrincipal.setUserId(userCheckResp.getUserId());
                    userPrincipal.setUserName(userCheckResp.getUserName());
                    userPrincipal.setSubjectId(userCheckResp.getSubjectId().longValue());
                }

                if (userPrincipal == null || userPrincipal.getUserId() == null) {
                    throw new BusinessException(
                            ApiResponseCodeEnum.LONGIN_FORBIDDEN.getCode(),
                            ApiResponseCodeEnum.LONGIN_FORBIDDEN.getMessage());
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userPrincipal, // principal = userId
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("JWT 认证通过 (Redis 校验成功): phone={},token={}", phone, token);
            } catch (Exception e) {
                log.error("JwtAuthenticationFilter login user token=" + token + " error,e=", e);
                if (e instanceof BusinessException eb) {
                    writeJsonError(response, eb.getCode(), eb.getMessage());
                } else {
                    writeJsonError(
                            response,
                            ApiResponseCodeEnum.LONGIN_FORBIDDEN.getCode(),
                            ApiResponseCodeEnum.LONGIN_FORBIDDEN.getMessage());
                }
                return;
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            TokenContextHolder.clear();
        }
    }

    /** 从 Authorization 请求头中提取 Bearer Token。 */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    /** 向响应写入 JSON 格式的错误信息（与全局异常处理器风格一致）。 */
    private void writeJsonError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(status, message));
    }
}
