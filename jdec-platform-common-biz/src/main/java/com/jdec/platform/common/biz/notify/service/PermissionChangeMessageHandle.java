package com.jdec.platform.common.biz.notify.service;

import com.jdec.platform.common.biz.notify.dto.ConfigChangeNotifyEvent;
import com.jdec.platform.common.biz.notify.holder.SseSessionHolder;
import com.jdec.platform.common.biz.notify.model.ClientSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 配置变更消息处理接口（消费侧）
 *
 * <p>对标原 WebSocket 方案的 {@code PermissionChangeMessageHandle}， 但推送目标由 {@code WebSocketSession} 改为
 * {@link SseEmitter}。
 *
 * <p>接口提供多种「会话匹配策略」default 方法，子类（具体 Handler）按需选用：
 *
 * <ul>
 *   <li>{@link #getAffectedClientSessionListWithMockUser}：按角色 + 受影响用户/主体匹配，并支持模拟登录视角
 *   <li>{@link #getAffectedClientSessionListWithLoginUser}：仅按受影响用户/主体匹配（角色新增/配置变更场景）
 *   <li>{@link #getAffectedClientSessionListWithRoleRemove}：角色删除场景（登录人与被模拟人都要通知）
 *   <li>{@link #getAffectedClientSessionListBySubject}：按主体广播（模块级变更等无法精准匹配的场景）
 * </ul>
 *
 * @author JDEC Platform
 * @since 2026-11-03
 */
public interface PermissionChangeMessageHandle {

    /** 是否处理该变更类型 */
    boolean isReferToChangeType(com.jdec.platform.shared.enums.ChangeType type);

    /** 处理消息：匹配会话并推送 */
    void handle(
            com.jdec.platform.shared.dto.ConfigChangeMessage message,
            com.jdec.platform.shared.enums.ChangeType changeType,
            Long channelSubjectId);

    /** 按角色 + 受影响用户/主体匹配，并特别处理模拟登录（mock）视角 */
    default List<ClientSession> getAffectedClientSessionListWithMockUser(
            Set<Long> roleIds, Set<Long> userIds, Set<Long> subjectIds) {
        List<ClientSession> result = new ArrayList<>();
        for (ClientSession s : SseSessionHolder.getAllSessions()) {
            if (s.getRoleId() == null || !roleIds.contains(s.getRoleId())) {
                continue;
            }
            Long effectiveUser =
                    Boolean.TRUE.equals(s.getIsMockLogin()) ? s.getMockUserId() : s.getUserId();
            if ((effectiveUser != null && userIds.contains(effectiveUser))
                    || (s.getSubjectId() != null && subjectIds.contains(s.getSubjectId()))) {
                result.add(s);
            }
        }
        return result;
    }

    /** 按角色匹配（忽略受影响用户/主体集合，用于仅知道 roleId 的场景） */
    default List<ClientSession> getAffectedClientSessionListByRole(Set<Long> roleIds) {
        List<ClientSession> result = new ArrayList<>();
        if (roleIds == null || roleIds.isEmpty()) {
            return result;
        }
        for (ClientSession s : SseSessionHolder.getAllSessions()) {
            if (roleIds.contains(s.getRoleId())) {
                result.add(s);
            }
        }
        return result;
    }

    /** 仅按受影响用户/主体匹配（角色新增、角色配置变更等场景） */
    default List<ClientSession> getAffectedClientSessionListWithLoginUser(
            Set<Long> userIds, Set<Long> subjectIds) {
        List<ClientSession> result = new ArrayList<>();
        for (ClientSession s : SseSessionHolder.getAllSessions()) {
            Long effectiveUser =
                    Boolean.TRUE.equals(s.getIsMockLogin()) ? s.getMockUserId() : s.getUserId();
            if ((effectiveUser != null && userIds.contains(effectiveUser))
                    || (s.getSubjectId() != null && subjectIds.contains(s.getSubjectId()))) {
                result.add(s);
            }
        }
        return result;
    }

    /** 角色删除场景：登录人与被模拟人都要通知，故「角色匹配 或 用户/主体匹配」 */
    default List<ClientSession> getAffectedClientSessionListWithRoleRemove(
            Set<Long> roleIds, Set<Long> userIds, Set<Long> subjectIds) {
        List<ClientSession> result = new ArrayList<>();
        for (ClientSession s : SseSessionHolder.getAllSessions()) {
            Long effectiveUser =
                    Boolean.TRUE.equals(s.getIsMockLogin()) ? s.getMockUserId() : s.getUserId();
            boolean roleMatch = s.getRoleId() != null && roleIds.contains(s.getRoleId());
            boolean userMatch =
                    (effectiveUser != null && userIds.contains(effectiveUser))
                            || (s.getSubjectId() != null && subjectIds.contains(s.getSubjectId()));
            if (roleMatch || userMatch) {
                result.add(s);
            }
        }
        return result;
    }

    /**
     * 取「除申请人本人以外」的所有前端会话（审批链变更场景）
     *
     * <p>遍历全量在线会话，剔除 {@code createUserId} 命中的连接（兼容模拟登录视角： 当连接处于模拟登录态时以 {@code mockUserId}
     * 作为有效用户身份参与比对）， 其余全部纳入推送集合。
     *
     * <p>用于审批链新增/提交/通过/驳回/取消等「申请人自己操作不应触发自己刷新」的场景， 与文档中 {@code
     * getAffectedClientSessionListWithoutCreateUser(applicantId)} 语义一致。
     *
     * @param createUserId 申请人（创建人）用户ID，为 null 时退化为全量广播
     * @return 需要推送的会话列表
     */
    default List<ClientSession> getAffectedClientSessionListWithoutCreateUser(Long createUserId) {
        List<ClientSession> result = new ArrayList<>();
        if (createUserId == null) {
            // 缺少申请人维度时，退化为全量广播，避免变更「石沉大海」
            result.addAll(SseSessionHolder.getAllSessions());
            return result;
        }
        for (ClientSession s : SseSessionHolder.getAllSessions()) {
            Long effectiveUser =
                    Boolean.TRUE.equals(s.getIsMockLogin()) ? s.getMockUserId() : s.getUserId();
            // 排除申请人本人（含模拟登录态下被模拟人即申请人本人）
            if (createUserId.equals(effectiveUser)) {
                continue;
            }
            result.add(s);
        }
        return result;
    }

    /** 按主体广播（channel 中的 subjectId），用于模块级变更等无法精准匹配的场景 */
    default List<ClientSession> getAffectedClientSessionListBySubject(Long subjectId) {
        List<ClientSession> result = new ArrayList<>();
        if (subjectId == null) {
            result.addAll(SseSessionHolder.getAllSessions());
            return result;
        }
        for (ClientSession s : SseSessionHolder.getAllSessions()) {
            if (subjectId.equals(s.getSubjectId())) {
                result.add(s);
            }
        }
        return result;
    }

    /** 推送消息到匹配的会话 */
    default void sendMessage(List<ClientSession> sessions, ConfigChangeNotifyEvent event) {
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        for (ClientSession s : sessions) {
            if (s == null || s.getEmitter() == null) {
                continue;
            }
            try {
                s.getEmitter().send(SseEmitter.event().name(event.getChangeType()).data(event));
                s.setLastMsgTime(System.currentTimeMillis());
            } catch (Exception e) {
                SseSessionHolder.removeSession(s.getUserId(), s.getSessionId());
            }
        }
    }
}
