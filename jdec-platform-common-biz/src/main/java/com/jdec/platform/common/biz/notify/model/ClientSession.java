package com.jdec.platform.common.biz.notify.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 客户端会话模型
 *
 * <p>替代原 WebSocket 方案中的 {@code ClientSession}，持有当前连接的 {@link SseEmitter} 以及登录用户的身份维度（用户 / 主体 / 角色 /
 * 模拟登录）。
 *
 * <p>身份维度与 {@code LoginUser} 对齐：
 *
 * <ul>
 *   <li>{@code userId} / {@code subjectId} / {@code roleId}：当前连接视角下的真实身份
 *   <li>{@code mockUserId} / {@code isMockLogin}：模拟登录场景下的被模拟用户
 * </ul>
 *
 * @author JDEC Platform
 * @since 2026-11-03
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientSession {

    /** 本次 SSE 连接唯一标识（每次连接生成，不可变） */
    private String sessionId;

    /** 登录用户ID */
    private Long userId;

    /** 用户所属主体ID */
    private Long subjectId;

    /** 当前角色ID（来自请求 role-id 头/参数） */
    private Long roleId;

    /** 模拟登录时的被模拟用户ID（非模拟登录为 null） */
    private Long mockUserId;

    /** 是否为模拟登录 */
    private Boolean isMockLogin;

    /** SSE 发射器 */
    private SseEmitter emitter;

    /** 最近一次收发消息时间（毫秒），用于健康检查 */
    private volatile long lastMsgTime;
}
