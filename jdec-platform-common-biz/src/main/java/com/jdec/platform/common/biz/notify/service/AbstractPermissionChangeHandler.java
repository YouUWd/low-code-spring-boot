package com.jdec.platform.common.biz.notify.service;

import com.jdec.platform.common.biz.notify.dto.ConfigChangeNotifyEvent;
import com.jdec.platform.common.biz.notify.model.ClientSession;
import com.jdec.platform.shared.dto.ConfigChangeMessage;
import com.jdec.platform.shared.enums.ChangeType;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * 配置变更 Handler 抽象基类
 *
 * <p>封装通用逻辑：解析 payload → 选择匹配策略 → 组装事件 → 推送。 具体 Handler 只需声明 {@link #supportedTypes()}（支持的变更类型）与
 * {@link #strategy()}（匹配策略）。
 *
 * @author JDEC Platform
 * @since 2026-11-03
 */
@Slf4j
public abstract class AbstractPermissionChangeHandler implements PermissionChangeMessageHandle {

    /** 会话匹配策略 */
    protected enum Strategy {
        /** 角色 + 受影响用户/主体匹配（含模拟登录） */
        MOCK_USER,
        /** 仅按受影响用户/主体匹配 */
        LOGIN_USER,
        /** 角色删除：角色匹配或用户/主体匹配 */
        ROLE_REMOVE,
        /** 按主体广播 */
        SUBJECT_BROADCAST
    }

    /** 该 Handler 处理的变更类型 */
    protected abstract Set<ChangeType> supportedTypes();

    /** 匹配策略，默认按角色+受影响用户/主体 */
    protected Strategy strategy() {
        return Strategy.MOCK_USER;
    }

    @Override
    public boolean isReferToChangeType(ChangeType type) {
        return type != null && supportedTypes().contains(type);
    }

    @Override
    public void handle(ConfigChangeMessage message, ChangeType changeType, Long channelSubjectId) {
        Map<String, Object> payload = message.getPayload();
        Long roleId = toLong(payload == null ? null : payload.get("roleId"));
        Set<Long> roleIds = toLongSet(payload == null ? null : payload.get("affectedRoleIds"));
        // 兼容旧消息：未携带 affectedRoleIds 时回退到单值 roleId
        if (roleIds.isEmpty() && roleId != null) {
            roleIds = Set.of(roleId);
        }
        Set<Long> userIds = toLongSet(payload == null ? null : payload.get("affectedUserIds"));
        Set<Long> subjectIds =
                toLongSet(payload == null ? null : payload.get("affectedSubjectIds"));

        List<ClientSession> sessions =
                resolveSessions(roleIds, userIds, subjectIds, channelSubjectId);

        // 角色删除场景：彻底排除操作人本人（含 roleId 匹配路径），自己删除/编辑角色时不通知自己
        if (strategy() == Strategy.ROLE_REMOVE) {
            Long operatorUserId = toLong(payload == null ? null : payload.get("operatorUserId"));
            if (operatorUserId != null) {
                sessions =
                        sessions.stream()
                                .filter(s -> !operatorUserId.equals(s.getUserId()))
                                .toList();
            }
        }

        ConfigChangeNotifyEvent event = buildEvent(message, changeType);
        log.info("配置变更 {} 匹配到 {} 个 SSE 会话，准备推送", changeType.getCode(), sessions.size());
        sendMessage(sessions, event);
    }

    /** 根据策略与 payload 解析受影响会话（分层兜底，保证一定能找到目标） */
    protected List<ClientSession> resolveSessions(
            Set<Long> roleIds, Set<Long> userIds, Set<Long> subjectIds, Long channelSubjectId) {
        switch (strategy()) {
            case LOGIN_USER:
                return getAffectedClientSessionListWithLoginUser(userIds, subjectIds);
            case ROLE_REMOVE:
                return getAffectedClientSessionListWithRoleRemove(roleIds, userIds, subjectIds);
            case SUBJECT_BROADCAST:
                return getAffectedClientSessionListBySubject(channelSubjectId);
            case MOCK_USER:
            default:
                if (!roleIds.isEmpty()) {
                    if (userIds.isEmpty() && subjectIds.isEmpty()) {
                        // 仅有角色维度时按角色广播
                        return getAffectedClientSessionListByRole(roleIds);
                    }
                    return getAffectedClientSessionListWithMockUser(roleIds, userIds, subjectIds);
                }
                if (!userIds.isEmpty() || !subjectIds.isEmpty()) {
                    return getAffectedClientSessionListWithLoginUser(userIds, subjectIds);
                }
                // 兜底：按主体广播，避免变更「石沉大海」
                return getAffectedClientSessionListBySubject(channelSubjectId);
        }
    }

    /** 组装推送事件体 */
    protected ConfigChangeNotifyEvent buildEvent(
            ConfigChangeMessage message, ChangeType changeType) {
        ConfigChangeNotifyEvent event = new ConfigChangeNotifyEvent();
        event.setChangeType(changeType.getCode());
        event.setSubjectId(message.getSubjectId());
        event.setProjectNo(message.getProjectNo());
        event.setMessageId(message.getMessageId());
        event.setPublishTime(message.getPublishTime());
        // 安全：操作员 token 仅用于后端校验，推送前端前不携带，避免泄露
        event.setToken(null);
        event.setPayload(message.getPayload());
        return event;
    }

    protected static Long toLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        try {
            return Long.valueOf(o.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    protected static Set<Long> toLongSet(Object o) {
        Set<Long> set = new HashSet<>();
        if (o instanceof Collection) {
            for (Object item : (Collection<?>) o) {
                Long v = toLong(item);
                if (v != null) {
                    set.add(v);
                }
            }
        }
        return set;
    }
}
