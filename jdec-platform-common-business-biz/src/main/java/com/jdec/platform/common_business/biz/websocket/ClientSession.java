package com.jdec.platform.common_business.biz.websocket;

import lombok.Builder;
import lombok.Data;
import org.springframework.web.socket.WebSocketSession;

@Builder
@Data
public class ClientSession {

    private volatile long userId;

    private volatile long subjectId;

    private volatile long roleId;

    private volatile long mockUserId;

    private volatile long mockSubjectId;

    private volatile long mockRoleId;

    private WebSocketSession webSocketSession;

    private Long lastMsgTime = System.currentTimeMillis();
}
