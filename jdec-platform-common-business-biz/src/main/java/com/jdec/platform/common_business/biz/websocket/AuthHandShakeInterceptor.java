package com.jdec.platform.common_business.biz.websocket;

import com.jdec.platform.shared.exception.BusinessException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Slf4j
public class AuthHandShakeInterceptor implements HandshakeInterceptor {
    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes)
            throws Exception {
        // 检查连接参数是否完整
        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            String userIdParam = servletRequest.getServletRequest().getParameter("userId");

            if (StringUtils.isBlank(userIdParam)) {
                //                response.setStatusCode(HttpStatusCode.valueOf(1006));
                throw new BusinessException(400, "websocket连接参数不完整，请检查。");
            }

            attributes.put("userId", userIdParam); // 将参数存储在attributes中，稍后在WebSocketHandler中使用
        }

        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        log.debug("握手成功。。。");
    }
}
