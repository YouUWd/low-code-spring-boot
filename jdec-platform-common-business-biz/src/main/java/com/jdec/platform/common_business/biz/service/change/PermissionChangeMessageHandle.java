package com.jdec.platform.common_business.biz.service.change;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import com.jdec.platform.common_business.biz.dto.ConfigChangeMessageDTO;
import com.jdec.platform.common_business.biz.enums.ConfigCenterChangeType;
import com.jdec.platform.common_business.biz.util.JsonUtil;
import com.jdec.platform.common_business.biz.websocket.ClientSession;
import com.jdec.platform.common_business.biz.websocket.MockUserHolder;
import com.jdec.platform.common_business.biz.websocket.UserSessionHolder;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;

public interface PermissionChangeMessageHandle {

    public static final Logger log = LoggerFactory.getLogger(PermissionChangeMessageHandle.class);

    public Boolean isReferToChangeType(ConfigCenterChangeType changeType);

    public void handle(
            ConfigChangeMessageDTO changeMessageDTO,
            ConfigCenterChangeType changeType,
            String body);

    default void sendMessageByRoleAndUser(
            Long roleId,
            List<Long> userIdList,
            ConfigChangeMessageDTO changeMessageDTO,
            String body) {
        for (Long userId : userIdList) {
            List<ClientSession> clientSessions = UserSessionHolder.getClientSessionByUserId(userId);
            if (CollectionUtil.isEmpty(clientSessions)) {
                log.info(
                        "send message can not found connections  user={},roleId={},message={}",
                        userId,
                        roleId,
                        changeMessageDTO.toString());
                continue;
            }
            CopyOnWriteArrayList<ClientSession> sessionCopyOnWriteArrayList =
                    new CopyOnWriteArrayList<>();
            sessionCopyOnWriteArrayList.addAll(clientSessions);
            sendMessage(sessionCopyOnWriteArrayList, changeMessageDTO, body);
        }
    }

    default void sendMessage(
            CopyOnWriteArrayList<ClientSession> clientSessions,
            ConfigChangeMessageDTO changeMessageDTO,
            String body) {
        for (ClientSession session : clientSessions) {
            if (session == null || !session.getWebSocketSession().isOpen()) {
                log.info(
                        "send message connection lose user={},connectionId={}",
                        session.getUserId(),
                        session.getWebSocketSession().getId());
                continue;
            }
            Long pubTime = null;
            if (StringUtils.isNotEmpty(changeMessageDTO.getPublishTime())) {
                pubTime =
                        DateUtil.parseLocalDateTime(
                                        changeMessageDTO.getPublishTime(),
                                        DatePattern.NORM_DATETIME_PATTERN)
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli();
            }
            //            if (pubTime != null && pubTime < session.getLastMsgTime()) {
            //                log.info(
            //                        "send message is expire discard
            // user={},connectionId={},message={}",
            //                        session.getUserId(),
            //                        session.getWebSocketSession().getId(),
            //                        body);
            //                continue;
            //            }
            customMessage(session, changeMessageDTO);
            try {
                String text = JsonUtil.obj2Json(changeMessageDTO);
                session.getWebSocketSession().sendMessage(new TextMessage(text));
                if (pubTime != null) {
                    session.setLastMsgTime(pubTime);
                }
                log.info(
                        "send message to user={},connectionId={},message={}",
                        session.getUserId(),
                        session.getWebSocketSession().getId(),
                        text);
            } catch (Exception e) {
                log.error(
                        "send message error to user={},connectionId={}",
                        session.getUserId(),
                        session.getWebSocketSession().getId(),
                        e);
            }
        }
    }

    default void customMessage(ClientSession session, ConfigChangeMessageDTO changeMessageDTO) {}

    default Set<Long> getLastAffectedUserIds(List<Long> AffectedUserIds) {
        Set<Long> affectUserSet = new HashSet<>();
        affectUserSet.addAll(AffectedUserIds);
        for (Long userId : AffectedUserIds) {
            // 是一个 被 mocked 的用户，查找登录人
            if (MockUserHolder.isCurrentUserMocked(userId)) {
                Set<Long> uSet = MockUserHolder.getMappingList(userId);
                if (CollectionUtil.isNotEmpty(uSet)) {
                    affectUserSet.addAll(uSet);
                    log.info(
                            "user={} is mocked,loginUser={} add to LastAffectedUserIds",
                            userId,
                            uSet);
                }
            }
        }
        return affectUserSet;
    }

    /**
     * @param roleId 不为空
     * @param affectedUserIds 可为空
     * @param affectedSubjectIds 可为空
     * @return
     */
    default CopyOnWriteArrayList<ClientSession> getAffectedClientSessionListWithMockUser(
            Long roleId, List<Long> affectedUserIds, List<Long> affectedSubjectIds) {

        CopyOnWriteArrayList<ClientSession> result = new CopyOnWriteArrayList<>();
        List<ClientSession> allList = UserSessionHolder.getAllClientSession();
        if (CollectionUtil.isEmpty(allList)) {
            return result;
        }

        Set<Long> affectedUserIdSet = new HashSet<>();
        if (CollectionUtil.isNotEmpty(affectedUserIds)) {
            affectedUserIdSet = affectedUserIds.stream().collect(Collectors.toSet());
        }
        Set<Long> affectedSubjectIdSet = new HashSet<>();
        if (CollectionUtil.isNotEmpty(affectedSubjectIds)) {
            affectedSubjectIdSet = affectedSubjectIds.stream().collect(Collectors.toSet());
        }

        Set<String> unique = new HashSet<>();
        for (ClientSession clientSession : allList) {
            // 当前的连接正在 使用模拟用户
            if (clientSession.getMockUserId() != 0 && clientSession.getMockRoleId() != 0) {
                // 受影响具体用户 模拟用户的情况
                if (affectedUserIdSet.contains(clientSession.getMockUserId())
                        && roleId.equals(clientSession.getMockRoleId())) {
                    if (!unique.contains(clientSession.getWebSocketSession().getId())) {
                        result.add(clientSession);
                        unique.add(clientSession.getWebSocketSession().getId());
                        log.debug(
                                "登录用户:{}当前的模拟用户id{}对应的连接在受影响用户范围中，角色id:{} ， 通道匹配成功。",
                                clientSession.getUserId(),
                                clientSession.getMockUserId(),
                                roleId);
                    }
                }
                // 受影响主题    模拟用户主题
                if (affectedSubjectIdSet.contains(clientSession.getMockSubjectId())
                        && roleId.equals(clientSession.getMockRoleId())) {
                    if (!unique.contains(clientSession.getWebSocketSession().getId())) {
                        result.add(clientSession);
                        unique.add(clientSession.getWebSocketSession().getId());
                        log.debug(
                                "登录用户:{}，模拟用户:{}的主体:{}对应的连接在受影响的主体范围中，角色id:{} ， 通道匹配成功。",
                                clientSession.getUserId(),
                                clientSession.getMockUserId(),
                                clientSession.getMockSubjectId(),
                                roleId);
                    }
                }

            } else {
                // 当前连接正常登录人操作

                // 受影响具体用户 登录用户的情况
                if (affectedUserIdSet.contains(clientSession.getUserId())
                        && roleId.equals(clientSession.getRoleId())) {
                    if (!unique.contains(clientSession.getWebSocketSession().getId())) {
                        log.debug(
                                "登录用户:{}对应的连接在受影响用户范围中，角色id:{} ， 通道匹配成功。",
                                clientSession.getUserId(),
                                roleId);
                        result.add(clientSession);
                        unique.add(clientSession.getWebSocketSession().getId());
                    }
                }

                // 受影响主题    登录用户主题
                if (affectedSubjectIdSet.contains(clientSession.getSubjectId())
                        && roleId.equals(clientSession.getRoleId())) {
                    if (!unique.contains(clientSession.getWebSocketSession().getId())) {
                        result.add(clientSession);
                        unique.add(clientSession.getWebSocketSession().getId());
                        log.debug(
                                "登录用户:{}，主体:{}对应的连接在受影响的主体范围中，角色id:{} ， 通道匹配成功。",
                                clientSession.getUserId(),
                                clientSession.getSubjectId(),
                                roleId);
                    }
                }
            }
            if (!unique.contains(clientSession.getWebSocketSession().getId())) {
                log.debug(
                        "登录用户:{}, 角色id:{} , 主体id:{} , 模拟用户:{}  , 模拟用户角色:{} , 模拟用户主体:{}未匹配成功。",
                        clientSession.getUserId(),
                        clientSession.getRoleId(),
                        clientSession.getSubjectId(),
                        clientSession.getMockUserId(),
                        clientSession.getMockRoleId(),
                        clientSession.getMockSubjectId());
            }
        }
        return result;
    }

    /**
     * @param roleId 不为空
     * @param affectedUserIds 可为空
     * @param affectedSubjectIds 可为空
     * @return
     */
    default CopyOnWriteArrayList<ClientSession> getAffectedClientSessionListWithRoleChange(
            Long roleId, List<Long> affectedUserIds, List<Long> affectedSubjectIds) {

        CopyOnWriteArrayList<ClientSession> result = new CopyOnWriteArrayList<>();
        List<ClientSession> allList = UserSessionHolder.getAllClientSession();
        if (CollectionUtil.isEmpty(allList)) {
            return result;
        }

        Set<Long> affectedUserIdSet = new HashSet<>();
        if (CollectionUtil.isNotEmpty(affectedUserIds)) {
            affectedUserIdSet = affectedUserIds.stream().collect(Collectors.toSet());
        }
        Set<Long> affectedSubjectIdSet = new HashSet<>();
        if (CollectionUtil.isNotEmpty(affectedSubjectIds)) {
            affectedSubjectIdSet = affectedSubjectIds.stream().collect(Collectors.toSet());
        }

        Set<String> unique = new HashSet<>();
        for (ClientSession clientSession : allList) {
            log.debug(
                    "登录用户:{}, 角色id:{} , 主体id:{} , 模拟用户:{}  , 模拟用户主体:{},模拟用户角色:{}",
                    clientSession.getUserId(),
                    clientSession.getRoleId(),
                    clientSession.getSubjectId(),
                    clientSession.getMockUserId(),
                    clientSession.getMockSubjectId(),
                    clientSession.getMockRoleId());

            // 当前的连接正在 使用模拟用户
            if (clientSession.getMockUserId() != 0 && clientSession.getMockRoleId() != 0) {
                // 受影响具体用户 模拟用户的情况
                if (affectedUserIdSet.contains(clientSession.getMockUserId())) {
                    if (!unique.contains(clientSession.getWebSocketSession().getId())) {
                        result.add(clientSession);
                        unique.add(clientSession.getWebSocketSession().getId());
                        log.debug(
                                "登录用户:{}当前的模拟用户id{}对应的连接在受影响用户范围中，角色id:{} ， 通道匹配成功。",
                                clientSession.getUserId(),
                                clientSession.getMockUserId(),
                                roleId);
                    }
                }
                // 受影响主题    模拟用户主题
                if (affectedSubjectIdSet.contains(clientSession.getMockSubjectId())) {
                    if (!unique.contains(clientSession.getWebSocketSession().getId())) {
                        result.add(clientSession);
                        unique.add(clientSession.getWebSocketSession().getId());
                        log.debug(
                                "登录用户:{}，模拟用户:{}的主体:{}对应的连接在受影响的主体范围中，角色id:{} ， 通道匹配成功。",
                                clientSession.getUserId(),
                                clientSession.getMockUserId(),
                                clientSession.getMockSubjectId(),
                                roleId);
                    }
                }

            } else {
                // 当前连接正常登录人操作

                // 受影响具体用户 登录用户的情况
                if (affectedUserIdSet.contains(clientSession.getUserId())) {
                    if (!unique.contains(clientSession.getWebSocketSession().getId())) {
                        log.debug(
                                "登录用户:{}对应的连接在受影响用户范围中，角色id:{} ， 通道匹配成功。",
                                clientSession.getUserId(),
                                roleId);
                        result.add(clientSession);
                        unique.add(clientSession.getWebSocketSession().getId());
                    }
                }

                // 受影响主题    登录用户主题
                if (affectedSubjectIdSet.contains(clientSession.getSubjectId())) {
                    if (!unique.contains(clientSession.getWebSocketSession().getId())) {
                        result.add(clientSession);
                        unique.add(clientSession.getWebSocketSession().getId());
                        log.debug(
                                "登录用户:{}，主体:{}对应的连接在受影响的主体范围中，角色id:{} ， 通道匹配成功。",
                                clientSession.getUserId(),
                                clientSession.getSubjectId(),
                                roleId);
                    }
                }
            }
            if (!unique.contains(clientSession.getWebSocketSession().getId())) {
                log.debug(
                        "登录用户:{}, 角色id:{} , 主体id:{} , 模拟用户:{}  , 模拟用户主体:{}未匹配成功。",
                        clientSession.getUserId(),
                        clientSession.getRoleId(),
                        clientSession.getSubjectId(),
                        clientSession.getMockUserId(),
                        clientSession.getMockSubjectId());
            }
        }
        return result;
    }

    /**
     * 角色删除很特殊 通道上既要关注登录人 又要关注被模拟人
     *
     * @param roleId 不为空
     * @param affectedUserIds 可为空
     * @param affectedSubjectIds 可为空
     * @return
     */
    default CopyOnWriteArrayList<ClientSession> getAffectedClientSessionListWithRoleRemove(
            Long roleId, List<Long> affectedUserIds, List<Long> affectedSubjectIds) {

        CopyOnWriteArrayList<ClientSession> result = new CopyOnWriteArrayList<>();
        List<ClientSession> allList = UserSessionHolder.getAllClientSession();
        if (CollectionUtil.isEmpty(allList)) {
            return result;
        }

        Set<Long> affectedUserIdSet = new HashSet<>();
        if (CollectionUtil.isNotEmpty(affectedUserIds)) {
            affectedUserIdSet = affectedUserIds.stream().collect(Collectors.toSet());
        }
        Set<Long> affectedSubjectIdSet = new HashSet<>();
        if (CollectionUtil.isNotEmpty(affectedSubjectIds)) {
            affectedSubjectIdSet = affectedSubjectIds.stream().collect(Collectors.toSet());
        }

        Set<String> unique = new HashSet<>();
        for (ClientSession clientSession : allList) {
            log.debug(
                    "登录用户:{}, 角色id:{} , 主体id:{} , 模拟用户:{}  , 模拟用户主体:{},模拟用户角色:{}",
                    clientSession.getUserId(),
                    clientSession.getRoleId(),
                    clientSession.getSubjectId(),
                    clientSession.getMockUserId(),
                    clientSession.getMockSubjectId(),
                    clientSession.getMockRoleId());

            // 受影响具体用户 模拟用户的情况
            if (affectedUserIdSet.contains(clientSession.getMockUserId())) {
                if (!unique.contains(clientSession.getWebSocketSession().getId())) {
                    result.add(clientSession);
                    unique.add(clientSession.getWebSocketSession().getId());
                    log.debug(
                            "登录用户:{}当前的模拟用户id{}对应的连接在受影响用户范围中，角色id:{} ， 通道匹配成功。",
                            clientSession.getUserId(),
                            clientSession.getMockUserId(),
                            roleId);
                }
            }
            // 受影响主题    模拟用户主题
            if (affectedSubjectIdSet.contains(clientSession.getMockSubjectId())) {
                if (!unique.contains(clientSession.getWebSocketSession().getId())) {
                    result.add(clientSession);
                    unique.add(clientSession.getWebSocketSession().getId());
                    log.debug(
                            "登录用户:{}，模拟用户:{}的主体:{}对应的连接在受影响的主体范围中，角色id:{} ， 通道匹配成功。",
                            clientSession.getUserId(),
                            clientSession.getMockUserId(),
                            clientSession.getMockSubjectId(),
                            roleId);
                }
            }

            // 受影响具体用户 登录用户的情况
            if (affectedUserIdSet.contains(clientSession.getUserId())) {
                if (!unique.contains(clientSession.getWebSocketSession().getId())) {
                    log.debug(
                            "登录用户:{}对应的连接在受影响用户范围中，角色id:{} ， 通道匹配成功。",
                            clientSession.getUserId(),
                            roleId);
                    result.add(clientSession);
                    unique.add(clientSession.getWebSocketSession().getId());
                }
            }

            // 受影响主题    登录用户主题
            if (affectedSubjectIdSet.contains(clientSession.getSubjectId())) {
                if (!unique.contains(clientSession.getWebSocketSession().getId())) {
                    result.add(clientSession);
                    unique.add(clientSession.getWebSocketSession().getId());
                    log.debug(
                            "登录用户:{}，主体:{}对应的连接在受影响的主体范围中，角色id:{} ， 通道匹配成功。",
                            clientSession.getUserId(),
                            clientSession.getSubjectId(),
                            roleId);
                }
            }
            if (!unique.contains(clientSession.getWebSocketSession().getId())) {
                log.debug(
                        "登录用户:{}, 角色id:{} , 主体id:{} , 模拟用户:{}  , 模拟用户主体:{}未匹配成功。",
                        clientSession.getUserId(),
                        clientSession.getRoleId(),
                        clientSession.getSubjectId(),
                        clientSession.getMockUserId(),
                        clientSession.getMockSubjectId());
            }
        }
        return result;
    }

    default CopyOnWriteArrayList<ClientSession> getAffectedClientSessionListWithLoginUser(
            Long roleId, List<Long> affectedUserIds, List<Long> affectedSubjectIds) {

        CopyOnWriteArrayList<ClientSession> result = new CopyOnWriteArrayList<>();
        List<ClientSession> allList = UserSessionHolder.getAllClientSession();
        if (CollectionUtil.isEmpty(allList)) {
            return result;
        }
        Set<Long> affectedSubjectIdSet = new HashSet<>();
        if (CollectionUtil.isNotEmpty(affectedSubjectIds)) {
            affectedSubjectIdSet = affectedSubjectIds.stream().collect(Collectors.toSet());
        }

        Set<Long> affectedUserIdSet = new HashSet<>();
        if (CollectionUtil.isNotEmpty(affectedUserIds)) {
            affectedUserIdSet = affectedUserIds.stream().collect(Collectors.toSet());
        }

        Set<String> unique = new HashSet<>();
        for (ClientSession clientSession : allList) {
            // 受影响主题的 登录用户
            if (affectedSubjectIdSet.contains(clientSession.getSubjectId())) {
                if (!unique.contains(clientSession.getWebSocketSession().getId())) {
                    log.debug(
                            "登录用户id:{} , 登录用户主体:{}对应的连接在受影响的主体id:{}范围中，通道匹配成功。",
                            clientSession.getUserId(),
                            clientSession.getSubjectId(),
                            affectedSubjectIdSet);
                    result.add(clientSession);
                    unique.add(clientSession.getWebSocketSession().getId());
                }
            }

            // 受影响具体用户 登录用户的情况
            if (affectedUserIdSet.contains(clientSession.getUserId())) {
                if (!unique.contains(clientSession.getWebSocketSession().getId())) {
                    log.debug(
                            "登录用户id:{} , 登录用户主体:{}对应的连接在受影响的用户id:{}范围中，通道匹配成功。",
                            clientSession.getUserId(),
                            clientSession.getSubjectId(),
                            affectedUserIdSet);
                    result.add(clientSession);
                    unique.add(clientSession.getWebSocketSession().getId());
                }
            }

            if (!unique.contains(clientSession.getWebSocketSession().getId())) {
                log.debug(
                        "登录用户id:{} , 登录用户主体:{} , 登录用户角色id:{}未匹配成功",
                        clientSession.getUserId(),
                        clientSession.getSubjectId(),
                        clientSession.getRoleId());
            }
        }
        return result;
    }
}
