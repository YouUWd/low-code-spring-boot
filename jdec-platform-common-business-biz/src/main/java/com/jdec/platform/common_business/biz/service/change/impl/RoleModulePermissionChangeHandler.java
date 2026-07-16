package com.jdec.platform.common_business.biz.service.change.impl;

import com.jdec.platform.common_business.biz.context.TokenContextHolder;
import com.jdec.platform.common_business.biz.dto.ChangedPayload;
import com.jdec.platform.common_business.biz.dto.ConfigChangeMessageDTO;
import com.jdec.platform.common_business.biz.dto.RoleModulePermissionChangedPayload;
import com.jdec.platform.common_business.biz.enums.ConfigCenterChangeType;
import com.jdec.platform.common_business.biz.service.change.PermissionChangeMessageHandle;
import com.jdec.platform.common_business.biz.websocket.ClientSession;
import com.jdec.platform.common_business.biz.websocket.RedisChannelProcessService;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
@AllArgsConstructor
@Slf4j
public class RoleModulePermissionChangeHandler implements PermissionChangeMessageHandle {
    private final RedisChannelProcessService redisChannelProcessService;

    private final Set<ConfigCenterChangeType> referToChangeTypeSet =
            Set.of(
                    ConfigCenterChangeType.ROLE_MODULE_PERMISSION_CHANGED,
                    ConfigCenterChangeType.ROLE_INTERACTION_PERMISSION_CHANGED);

    @Override
    public Boolean isReferToChangeType(ConfigCenterChangeType changeType) {
        return referToChangeTypeSet.contains(changeType);
    }

    /**
     * 模块权限 变更消息处理
     *
     * @param changeMessageDTO
     * @param changeType
     * @param body
     */
    @Override
    public void handle(
            ConfigChangeMessageDTO changeMessageDTO,
            ConfigCenterChangeType changeType,
            String body) {
        RoleModulePermissionChangedPayload payload =
                (RoleModulePermissionChangedPayload) changeMessageDTO.getPayload();
        if (payload == null
                || payload.getRoleId() == null
                || CollectionUtils.isEmpty(payload.getAffectedModuleIds())) {
            return;
        }
        try {
            TokenContextHolder.setCurrentUserToken(changeMessageDTO.getToken());
            changeMessageDTO.setToken(null);
            Set<Long> moduleIdList =
                    redisChannelProcessService.findFinalNotifyModule(
                            changeMessageDTO.getSubjectId(),
                            payload.getAffectedModuleIds(),
                            payload.getRoleId());
            // payload.setAffectedModuleIds(moduleIdList.stream().toList());
            ChangedPayload changedPayload = new ChangedPayload();
            changedPayload.setAffectedModuleIds(moduleIdList.stream().toList());
            changedPayload.setAffectedRoleIds(List.of(payload.getRoleId()));
            changedPayload.setAffectedSubjectIds(List.of(changeMessageDTO.getSubjectId()));
            changeMessageDTO.setPayload(changedPayload);
            CopyOnWriteArrayList<ClientSession> sessions =
                    getAffectedClientSessionListWithMockUser(
                            payload.getRoleId(),
                            payload.getAffectedUserIds(),
                            payload.getAffectedSubjectIds());
            sendMessage(sessions, changeMessageDTO, body);

        } finally {
            TokenContextHolder.clear();
        }
    }
}
