package com.jdec.platform.common_business.biz.websocket;

import com.jdec.platform.common_business.api.SystemManageServiceApi;
import com.jdec.platform.common_business.api.dto.common.SubjectRoleDTO;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** websocket连接、端口、接收消息事件监听处理类 */
@Slf4j
@Component
public class NotifyWebsocketHandler extends TextWebSocketHandler {

    @Autowired private SystemManageServiceApi systemManageService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {

        Long userId =
                session.getAttributes().get("userId") == null
                        ? null
                        : Long.parseLong(String.valueOf(session.getAttributes().get("userId")));

        Long roleId = 0L;
        Long subjectId = 0L;
        Long mockUserId = 0L;
        Long mockSubjectId = 0L;
        Long mockRoleId = 0L;

        SubjectRoleDTO subjectRoleDTO = systemManageService.getUserCurrentRole(null, userId);
        if (Objects.nonNull(subjectRoleDTO)) {
            roleId = subjectRoleDTO.getRoleId() != null ? subjectRoleDTO.getRoleId() : 0L;
            subjectId = subjectRoleDTO.getSubjectId() != null ? subjectRoleDTO.getSubjectId() : 0L;
            mockUserId =
                    subjectRoleDTO.getMockUserId() != null ? subjectRoleDTO.getMockUserId() : 0L;
            mockSubjectId =
                    subjectRoleDTO.getMockSubjectId() != null
                            ? subjectRoleDTO.getMockSubjectId()
                            : 0L;
            mockRoleId =
                    subjectRoleDTO.getMockRoleId() != null ? subjectRoleDTO.getMockRoleId() : 0L;
            log.debug("建立连接时当前用户id:{}的当前角色id是:{}", userId, roleId);
        } else {
            log.debug("建立连接时当前用户id:{}无当前角色。", userId);
        }

        log.debug("roleId:{} , userId:{} sessionId:{}连接成功。", roleId, userId, session.getId());

        // 放入对应的Map
        // SessionHolder.addRoleWsMap(roleId, userId, session);
        UserSessionHolder.addRoleWsMap(
                subjectId, roleId, userId, mockUserId, mockSubjectId, mockRoleId, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status)
            throws Exception {
        Long userId =
                session.getAttributes().get("userId") == null
                        ? null
                        : Long.parseLong(String.valueOf(session.getAttributes().get("userId")));

        Long roleId = 0L;

        SubjectRoleDTO subjectRoleDTO = systemManageService.getUserCurrentRole(null, userId);
        if (Objects.nonNull(subjectRoleDTO)) {
            roleId = subjectRoleDTO.getRoleId();
            log.debug("关闭连接时当前用户id:{}的当前角色id是:{}，清理对应角色和用户的连接。", userId, roleId);
        } else {
            log.debug("关闭连接时当前用户id:{}无当前角色。", userId);
        }

        // SessionHolder.removeRoleWsMap(roleId, userId, session.getId());
        UserSessionHolder.removeRoleWsMap(roleId, userId, session.getId());
        log.debug("roleId:{} , userId:{} 连接关闭。sessionId:{}", roleId, userId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
            throws Exception {
        Long userId =
                session.getAttributes().get("userId") == null
                        ? null
                        : Long.parseLong(String.valueOf(session.getAttributes().get("userId")));

        session.sendMessage(message);
    }
}
