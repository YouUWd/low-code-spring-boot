package com.jdec.platform.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.shared.model.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置 — 无状态 JWT 方案。
 *
 * <p>规则优先级（从上到下）：
 *
 * <ol>
 *   <li>公开业务端点（登录、验证码等）→ 无需 Token
 *   <li>Swagger / OpenAPI 文档端点 → 无需 Token
 *   <li>CORS 预检请求（OPTIONS） → 无需 Token
 *   <li>/api/** 下的其它请求 → 必须携带有效 JWT
 *   <li>非 /api 路径 → 放行，由 Spring MVC 决定 404
 * </ol>
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    /** 无需认证的业务端点。 */
    private static final String[] PUBLIC_ENDPOINTS = {
        "/api/auth/code/send", "/api/auth/login",
    };

    /** 无需认证的业务端点。 */
    private static final String[] CONFIG_ENDPOINTS = {
        "/api/config/**",
        "/api/metadata/**",
        "/api/data/**",
    };

    /** Swagger / OpenAPI 相关端点。 */
    private static final String[] SWAGGER_ENDPOINTS = {
        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(PUBLIC_ENDPOINTS)
                                        .permitAll()
                                        .requestMatchers(CONFIG_ENDPOINTS)
                                        .permitAll()
                                        .requestMatchers(SWAGGER_ENDPOINTS)
                                        .permitAll()
                                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                                        .permitAll()
                                        .requestMatchers("/api/**")
                                        .authenticated()
                                        .anyRequest()
                                        .permitAll())
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(
                                                (req, res, e) ->
                                                        writeJsonError(res, 401, "未登录或令牌已过期"))
                                        .accessDeniedHandler(
                                                (req, res, e) -> writeJsonError(res, 403, "权限不足")))
                .addFilterBefore(
                        jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 向响应写入 JSON 格式的错误信息（与全局异常处理器风格一致）。 */
    private void writeJsonError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(status, message));
    }
}
