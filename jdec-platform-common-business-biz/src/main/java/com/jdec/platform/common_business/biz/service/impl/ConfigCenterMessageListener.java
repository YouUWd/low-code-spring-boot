package com.jdec.platform.common_business.biz.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Splitter;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.jdec.platform.common_business.biz.dto.ConfigChangeMessageDTO;
import com.jdec.platform.common_business.biz.dto.RoleModulePermissionChangedPayload;
import com.jdec.platform.common_business.biz.enums.ConfigCenterChangeType;
import com.jdec.platform.common_business.biz.service.change.PermissionChangeMessageHandle;
import com.jdec.platform.common_business.biz.util.JsonUtil;
import java.util.List;
import java.util.concurrent.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Slf4j
public class ConfigCenterMessageListener implements MessageListener {

    private final List<PermissionChangeMessageHandle> handleList;

    private final ExecutorService threadPool =
            new ThreadPoolExecutor(
                    2,
                    8,
                    60,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(10),
                    new ThreadFactoryBuilder().setNameFormat("MessageListener-%d").build(),
                    new ThreadPoolExecutor.CallerRunsPolicy());

    @Override
    public void onMessage(Message message, @Nullable byte[] pattern) {
        String channel = new String(message.getChannel());
        String body = new String(message.getBody());
        List<String> chs = Splitter.on(":").splitToList(channel);
        log.info("onMessage receive message  channel=" + channel + " body=" + body);
        try {
            String changeType = (chs != null && chs.size() > 0) ? chs.get(chs.size() - 1) : null;
            ConfigCenterChangeType type =
                    ConfigCenterChangeType.getConfigCenterChangeType(changeType);
            if (type == null) {
                log.info("onMessage receive unknown message  channel=" + channel + " body=" + body);
                return;
            }

            ConfigChangeMessageDTO<RoleModulePermissionChangedPayload> changeMessageDTO =
                    JsonUtil.json2Obj(
                            body,
                            new TypeReference<
                                    ConfigChangeMessageDTO<
                                            RoleModulePermissionChangedPayload>>() {});
            // log.info(changeMessageDTO.toString());
            changeMessageDTO.setChangeType(changeType);
            //            switch (type) {
            //                case ROLE_MODULE_PERMISSION_CHANGED ->
            //                        threadPool.submit(
            //                                () ->
            // processRoleModulePermissionChanged(changeMessageDTO, body));
            //                default -> threadPool.submit(() -> defaultProcess(changeMessageDTO,
            // body));
            //            }
            for (PermissionChangeMessageHandle handle : handleList) {
                if (handle.isReferToChangeType(type)) {
                    threadPool.submit(() -> handle.handle(changeMessageDTO, type, body));
                }
            }
        } catch (Exception e) {
            log.error("onMessage error,e=", e);
        }
    }
}
