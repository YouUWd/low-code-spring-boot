package com.jdec.platform.common_business.biz.websocket;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
public class SessionHolder {

    public static final ConcurrentHashMap<Long, List<ClientSession>> keyToSessionMap =
            new ConcurrentHashMap();

    /**
     * 连接建立时，放入对应的映射Map
     *
     * @param roleId
     * @param userId
     * @param session
     */
    public static void addRoleWsMap(Long roleId, Long userId, WebSocketSession session) {
        if (SessionHolder.keyToSessionMap.containsKey(roleId)) {
            log.debug("roleId:{}已经存在打开的连接", roleId);
            SessionHolder.keyToSessionMap
                    .get(roleId)
                    .add(
                            ClientSession.builder()
                                    .userId(userId)
                                    .webSocketSession(session)
                                    .lastMsgTime(System.currentTimeMillis())
                                    .build());
        } else {
            log.debug("roleId:{}没有打开的连接", roleId);
            List<ClientSession> userModuleSession = new ArrayList<>();
            userModuleSession.add(
                    ClientSession.builder()
                            .userId(userId)
                            .webSocketSession(session)
                            .lastMsgTime(System.currentTimeMillis())
                            .build());
            SessionHolder.keyToSessionMap.put(roleId, userModuleSession);
        }
    }

    /**
     * 断开连接时清理映射关系
     *
     * @param roleId 角色Id
     * @param userId 用户Id
     * @param channelId 通道Id,用户和模块id对应的连接可能有多个，只清理对应的需要关闭的连接
     */
    public static void removeRoleWsMap(Long roleId, Long userId, String channelId) {
        List<ClientSession> sessions = SessionHolder.keyToSessionMap.get(roleId);

        // 根据角色Id，查找对应的连接通道，并从map中移除。
        if (!CollectionUtils.isEmpty(sessions)) {
            Iterator<ClientSession> iterator = sessions.iterator();
            while (iterator.hasNext()) {
                ClientSession itemSession = iterator.next();
                if (userId.equals(itemSession.getUserId())
                        && channelId.equals(itemSession.getWebSocketSession().getId())) {
                    iterator.remove();
                    break;
                }
            }
        }
    }

    /**
     * 根据角色Id、用户Id、模块Id找到对应的连接通道
     *
     * @param roleId
     * @param userId
     * @return
     */
    public static List<ClientSession> getTargetSession(Long roleId, Long userId, String channelId) {
        List<ClientSession> sessions = SessionHolder.keyToSessionMap.get(roleId);

        if (!CollectionUtils.isEmpty(sessions)) {
            List<ClientSession> matchedSessions =
                    sessions.stream().filter(ele -> userId.equals(ele.getUserId())).toList();

            // 如果传了channelId，则筛选指定的连接
            if (StringUtils.hasText(channelId)) {
                matchedSessions =
                        matchedSessions.stream()
                                .filter(ele -> channelId.equals(ele.getWebSocketSession().getId()))
                                .toList();
            }

            return matchedSessions;
        } else {
            return new ArrayList<>();
        }
    }

    /**
     * 根据角色Id、用户Id、模块Id找到对应的连接通道
     *
     * @param userId
     * @return
     */
    public static synchronized void roleChangeSessionProcess(
            Long preRoleId, Long currentRoleId, Long userId) {
        if (Objects.equals(preRoleId, currentRoleId)) {
            log.debug("切换前后角色未发生变化，websocket不用更换角色分组。");
            return;
        }

        List<ClientSession> sessions = SessionHolder.keyToSessionMap.get(preRoleId);

        if (!CollectionUtils.isEmpty(sessions)) {
            Iterator<ClientSession> sessionIterator = sessions.iterator();
            while (sessionIterator.hasNext()) {
                ClientSession clientSession = sessionIterator.next();
                if (userId.equals(clientSession.getUserId())) {
                    log.debug(
                            "切换前的roleId:{}有用户id:{}的连接，将其放入新roleId:{}下",
                            preRoleId,
                            userId,
                            currentRoleId);
                    if (!SessionHolder.keyToSessionMap.containsKey(currentRoleId)) {
                        log.debug("没有用户使用过roleId:{}", currentRoleId);
                        SessionHolder.keyToSessionMap.put(currentRoleId, new ArrayList<>());
                    }
                    SessionHolder.keyToSessionMap
                            .get(currentRoleId)
                            .add(
                                    ClientSession.builder()
                                            .userId(userId)
                                            .webSocketSession(clientSession.getWebSocketSession())
                                            .lastMsgTime(clientSession.getLastMsgTime())
                                            .build());
                    sessionIterator.remove();
                }
            }
        } else {
            log.debug(
                    "切换前的roleId:{}没有任何用户的连接，直接将用户id:{}放入roleId:{}下管理。程序逻辑异常。",
                    preRoleId,
                    userId,
                    currentRoleId);
            if (!SessionHolder.keyToSessionMap.containsKey(currentRoleId)) {
                log.debug("没有用户使用过roleId:{}", currentRoleId);
                SessionHolder.keyToSessionMap.put(currentRoleId, new ArrayList<>());
            }
        }
    }
}
