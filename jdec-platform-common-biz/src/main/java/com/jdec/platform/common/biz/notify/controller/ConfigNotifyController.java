package com.jdec.platform.common.biz.notify.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.common.biz.notify.holder.SseSessionHolder;
import com.jdec.platform.common.biz.notify.model.ClientSession;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.TokenContext;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.security.JwtTokenProvider;
import com.jdec.platform.shared.security.model.LoginUser;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 配置变更 SSE 推送端点
 *
 * <p>替代原 WebSocket 的 {@code /ws/notify} 连接建立 / 握手鉴权 / 会话登记逻辑。
 *
 * <p><b>鉴权说明</b>：浏览器原生 {@code EventSource} 无法自定义请求头，因此鉴权令牌通过 query 参数传入， 由本控制器完成 JWT 解析 + Redis
 * 令牌校验（与 {@code JwtAuthenticationInterceptor} 等价的安全强度）。 该路径已在 {@code WebMvcConfig} 中排除全局拦截器。
 *
 * <p>客户端示例：
 *
 * <pre>
 * const es = new EventSource(`/api/common/notify/sse?token=xxx&roleId=1&subjectId=2`);
 * es.addEventListener('ROLE_MENU_CHANGED', e => { const data = JSON.parse(e.data); /* 刷新逻辑 *\/ });
 * </pre>
 *
 * @author JDEC Platform
 * @since 2026-11-03
 */
@Slf4j
@RestController
@RequestMapping("/api/common/notify")
@RequiredArgsConstructor
public class ConfigNotifyController {

    /** SSE 连接超时（毫秒）：30 分钟，配合心跳维持长连接 */
    private static final long SSE_TIMEOUT = 30L * 60 * 1000;

    private static final String REDIS_TOKEN_KEY_PREFIX = "token:";

    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(
            @RequestParam("token") String token,
            @RequestParam(value = "roleId", required = false) Long roleId,
            @RequestParam(value = "subjectId", required = false) Long subjectId,
            @RequestParam(value = "mockUserId", required = false) Long mockUserId,
            @RequestParam(value = "projectNo", required = false) String projectNo) {

        LoginUser loginUser = authenticate(token);

        // 仅用于连接期间可能的上下文读取，方法返回前清理，避免 ThreadLocal 泄漏
        try {
            UserContext.setLoginUser(loginUser);
            TokenContext.setToken(token);
            if (projectNo != null) {
                AppContext.setContext(projectNo, subjectId);
            }

            SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
            ClientSession session =
                    ClientSession.builder()
                            .sessionId(UUID.randomUUID().toString())
                            .userId(loginUser.getUserId())
                            // 主体ID 必须来自前端传入，不能取 token 解析中的主体
                            .subjectId(subjectId)
                            .roleId(roleId != null ? roleId : loginUser.getRoleId())
                            .mockUserId(mockUserId != null ? mockUserId : loginUser.getMockUserId())
                            .isMockLogin(
                                    (mockUserId != null
                                                    && !mockUserId.equals(loginUser.getUserId()))
                                            || Boolean.TRUE.equals(loginUser.getIsMockLogin()))
                            .emitter(emitter)
                            .lastMsgTime(System.currentTimeMillis())
                            .build();

            SseSessionHolder.addSession(loginUser.getUserId(), session);
            log.info(
                    "SSE 连接建立 userId={}, sessionId={}, roleId={}, mock={}",
                    loginUser.getUserId(),
                    session.getSessionId(),
                    session.getRoleId(),
                    session.getIsMockLogin());

            emitter.onCompletion(
                    () ->
                            SseSessionHolder.removeSession(
                                    loginUser.getUserId(), session.getSessionId()));
            emitter.onTimeout(
                    () ->
                            SseSessionHolder.removeSession(
                                    loginUser.getUserId(), session.getSessionId()));
            emitter.onError(
                    e ->
                            SseSessionHolder.removeSession(
                                    loginUser.getUserId(), session.getSessionId()));

            // 首帧：连接成功事件
            try {
                emitter.send(
                        SseEmitter.event()
                                .name("CONNECTED")
                                .data(
                                        Map.of(
                                                "status",
                                                "connected",
                                                "sessionId",
                                                session.getSessionId())));
            } catch (Exception ignored) {
                // 首帧失败不影响后续推送，由回调统一清理
            }
            return emitter;
        } finally {
            UserContext.remove();
            TokenContext.clear();
            AppContext.remove();
        }
    }

    /**
     * SSE 超时异常处理：超时属于连接正常生命周期，不写响应体，浏览器 EventSource 感知到流结束会自动重连。
     *
     * <p>本端点声明了 {@code produces = text/event-stream}，响应 Content-Type 在进入方法前已被固定。 若交给全局 {@code
     * GlobalExceptionHandler} 返回 {@code ApiResponse}，Spring 找不到能写 {@code text/event-stream}
     * 的消息转换器，会抛 {@code HttpMessageNotWritableException}。
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncTimeout(AsyncRequestTimeoutException e) {
        log.info("SSE 连接超时，连接已释放: {}", e.getMessage());
    }

    /**
     * SSE 端点专用异常处理：直接向响应流写 JSON，绕过消息转换器。
     *
     * <p>同理，响应已预设 {@code text/event-stream}，全局异常处理器的 JSON 序列化无法写出。 这里先重置响应头，再手动序列化 {@link
     * ApiResponse}，避免二次抛出 {@code HttpMessageNotWritableException}。
     */
    @ExceptionHandler(Exception.class)
    public void handleException(Exception e, HttpServletResponse response) throws IOException {
        if (response.isCommitted()) {
            log.debug("SSE 响应已提交，异常不再回写: {}", e.getMessage());
            return;
        }

        ApiResponse<Object> body;
        if (e instanceof BusinessException be) {
            log.warn("SSE 鉴权/业务异常: code={}, message={}", be.getCode(), be.getMessage());
            body = ApiResponse.customResponse(be.getCode(), be.getMessage(), be.getData());
        } else {
            log.warn("SSE 连接异常: {}", e.getMessage(), e);
            body = ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "SSE 连接异常");
        }

        response.reset();
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(body));
        response.flushBuffer();
    }

    /** 解析并校验 token（与 JwtAuthenticationInterceptor 等价的强度） */
    private LoginUser authenticate(String token) {
        if (!org.springframework.util.StringUtils.hasText(token)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED.value(), "缺少 token");
        }
        Claims claims = jwtTokenProvider.parseToken(token);
        if (claims == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED.value(), "令牌无效或已过期");
        }

        // 与拦截器一致：phone 为必填字段，缺失直接拒绝
        String phone = claims.get("phone", String.class);
        if (!org.springframework.util.StringUtils.hasText(phone)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED.value(), "令牌格式错误");
        }

        // 校验 Redis 中是否存在且未失效的 token
        String cached = stringRedisTemplate.opsForValue().get(REDIS_TOKEN_KEY_PREFIX + phone);
        if (!token.equals(cached)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED.value(), "令牌已失效，请重新登录");
        }

        // 与拦截器一致
        Long userId = claims.get("userId", Long.class);
        String username = claims.get("username", String.class);
        Long subjectId = claims.get("subjectId", Long.class);

        return LoginUser.builder()
                .userId(userId)
                .username(username)
                .phone(phone)
                .subjectId(subjectId)
                .token(token)
                .realUserId(userId)
                .build();
    }
}
