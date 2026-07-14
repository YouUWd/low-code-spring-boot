package com.jdec.platform.common_business.biz.websocket;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
public class UserSessionHolder {

    public static final ConcurrentHashMap<Long, ConcurrentHashMap<String, ClientSession>>
            UserToSessionMap = new ConcurrentHashMap();

    /**
     * 连接建立时，放入对应的映射Map
     *
     * @param roleId
     * @param userId
     * @param session
     */
    public static synchronized void addRoleWsMap(
            Long subjectId,
            Long roleId,
            Long userId,
            Long mockUserId,
            Long mockSubjectId,
            Long mockRoleId,
            WebSocketSession session) {

        if (!UserToSessionMap.containsKey(userId)) {
            UserToSessionMap.put(userId, new ConcurrentHashMap<>());
        }
        ClientSession clientSession =
                ClientSession.builder()
                        .userId(userId)
                        .roleId(roleId)
                        .subjectId(subjectId)
                        .mockUserId(mockUserId)
                        .mockSubjectId(mockSubjectId)
                        .mockRoleId(mockRoleId)
                        .webSocketSession(session)
                        .lastMsgTime(System.currentTimeMillis())
                        .build();
        UserToSessionMap.get(userId).put(session.getId(), clientSession);
        log.debug(
                "userId:{},subjectId={},roleId={},channelId={},已经存在打开的连接",
                userId,
                subjectId,
                roleId,
                session.getId());
    }

    /**
     * 断开连接时清理映射关系
     *
     * @param roleId 角色Id
     * @param userId 用户Id
     * @param channelId 通道Id,用户和模块id对应的连接可能有多个，只清理对应的需要关闭的连接
     */
    public static synchronized void removeRoleWsMap(Long roleId, Long userId, String channelId) {
        if (UserToSessionMap.containsKey(userId)) {
            UserToSessionMap.get(userId).remove(channelId);
            log.info("userId:{},channelId={},移除用户连接", userId, channelId);
            if (UserToSessionMap.get(userId).size() == 0) {
                UserToSessionMap.remove(userId);
                log.info("userId:{},channelId={},移除用户连接集合", userId, channelId);
            }
        }
    }

    public static void roleChangeSessionProcess(
            boolean mock,
            long loginUserId,
            long subjectId,
            long roleId,
            long mockUserId,
            long mockSubjectId,
            long mockRoleId) {
        Map<String, ClientSession> clientSessionMap = UserToSessionMap.get(loginUserId);
        if (clientSessionMap == null || clientSessionMap.size() == 0) {
            return;
        }
        List<ClientSession> sessionList =
                clientSessionMap.values().stream().collect(Collectors.toList());
        if (!mock) {
            for (ClientSession clientSession : sessionList) {
                clientSession.setSubjectId(subjectId);
                clientSession.setRoleId(roleId);
                clientSession.setMockUserId(0L);
                clientSession.setMockSubjectId(0L);
                clientSession.setMockRoleId(0L);
            }
        } else {
            for (ClientSession clientSession : sessionList) {
                clientSession.setSubjectId(subjectId);
                clientSession.setMockUserId(mockUserId);
                clientSession.setMockSubjectId(mockSubjectId);
                clientSession.setMockRoleId(mockRoleId);
            }
        }
    }

    public static List<ClientSession> getClientSessionByUserId(Long userId) {
        if (UserToSessionMap.containsKey(userId)) {
            return UserToSessionMap.get(userId).values().stream().collect(Collectors.toList());
        }
        return List.of();
    }

    public static List<ClientSession> getAllClientSession() {
        List<ClientSession> all =
                UserSessionHolder.UserToSessionMap.values().stream()
                        .map(x -> x.values())
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());
        return all;
    }
}
