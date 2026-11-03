package com.jdec.platform.common.biz.notify.holder;

import com.jdec.platform.common.biz.notify.model.ClientSession;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 会话注册中心（进程内）
 *
 * <p>替代原 WebSocket 方案的 {@code UserSessionHolder}，统一管理所有在线 SSE 连接。
 *
 * <p>结构：{@code userId -> (sessionId -> ClientSession)}，与前端「一个用户可能开多个标签页」的 现实一致；匹配会话时通过 {@link
 * #getAllSessions()} 遍历全量连接。
 *
 * <p>内置心跳：每 30s 向所有连接发送 SSE 注释事件（{@code : heartbeat}），用于：
 *
 * <ul>
 *   <li>维持代理 / 浏览器不过期空闲连接
 *   <li>主动发现已断开的连接并清理（发送失败即移除）
 * </ul>
 *
 * @author JDEC Platform
 * @since 2026-11-03
 */
@Slf4j
public final class SseSessionHolder {

    /** userId -> (sessionId -> ClientSession) */
    private static final ConcurrentHashMap<Long, ConcurrentHashMap<String, ClientSession>>
            USER_SESSIONS = new ConcurrentHashMap<>();

    private static final ScheduledExecutorService HEARTBEAT_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread t = new Thread(r, "SseHeartbeat");
                        t.setDaemon(true);
                        return t;
                    });

    static {
        HEARTBEAT_EXECUTOR.scheduleAtFixedRate(
                SseSessionHolder::heartbeat, 30, 30, TimeUnit.SECONDS);
    }

    private SseSessionHolder() {}

    /** 注册一个 SSE 会话 */
    public static void addSession(Long userId, ClientSession session) {
        if (userId == null || session == null || session.getSessionId() == null) {
            return;
        }
        USER_SESSIONS
                .computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                .put(session.getSessionId(), session);
    }

    /** 移除指定会话 */
    public static void removeSession(Long userId, String sessionId) {
        if (userId == null || sessionId == null) {
            return;
        }
        ConcurrentHashMap<String, ClientSession> map = USER_SESSIONS.get(userId);
        if (map != null) {
            map.remove(sessionId);
            if (map.isEmpty()) {
                USER_SESSIONS.remove(userId);
            }
        }
    }

    /** 获取全量在线会话（遍历所有用户的所有连接） */
    public static List<ClientSession> getAllSessions() {
        List<ClientSession> all = new ArrayList<>();
        for (ConcurrentHashMap<String, ClientSession> sessions : USER_SESSIONS.values()) {
            all.addAll(sessions.values());
        }
        return all;
    }

    /** 心跳探测：发送注释事件，失败的连接直接清理 */
    private static void heartbeat() {
        for (ClientSession session : getAllSessions()) {
            if (session.getEmitter() == null) {
                continue;
            }
            try {
                session.getEmitter().send(SseEmitter.event().comment("heartbeat"));
                session.setLastMsgTime(System.currentTimeMillis());
            } catch (Exception e) {
                log.debug(
                        "SSE 心跳失败，移除失效会话 userId={}, sessionId={}",
                        session.getUserId(),
                        session.getSessionId());
                removeSession(session.getUserId(), session.getSessionId());
            }
        }
    }
}
