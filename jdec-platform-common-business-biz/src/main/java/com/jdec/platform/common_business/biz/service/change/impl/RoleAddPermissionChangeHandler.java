package com.jdec.platform.common_business.biz.service.change.impl;

import com.jdec.platform.common_business.biz.dto.ChangedPayload;
import com.jdec.platform.common_business.biz.dto.ConfigChangeMessageDTO;
import com.jdec.platform.common_business.biz.dto.RoleModulePermissionChangedPayload;
import com.jdec.platform.common_business.biz.enums.ConfigCenterChangeType;
import com.jdec.platform.common_business.biz.service.change.PermissionChangeMessageHandle;
import com.jdec.platform.common_business.biz.websocket.ClientSession;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Slf4j
public class RoleAddPermissionChangeHandler implements PermissionChangeMessageHandle {

    private final Set<ConfigCenterChangeType> referToChangeTypeSet =
            Set.of(ConfigCenterChangeType.ROLE_ADD);

    @Override
    public Boolean isReferToChangeType(ConfigCenterChangeType changeType) {
        return referToChangeTypeSet.contains(changeType);
    }

    @Override
    public void handle(
            ConfigChangeMessageDTO changeMessageDTO,
            ConfigCenterChangeType changeType,
            String body) {
        RoleModulePermissionChangedPayload payload =
                (RoleModulePermissionChangedPayload) changeMessageDTO.getPayload();
        if (payload == null || payload.getRoleId() == null) {
            return;
        }
        changeMessageDTO.setToken(null);
        ChangedPayload changedPayload = new ChangedPayload();
        changedPayload.setAffectedRoleIds(List.of(payload.getRoleId()));
        changedPayload.setAffectedSubjectIds(payload.getAffectedSubjectIds());

        //        if (CollectionUtil.isNotEmpty(payload.getAffectedUserIds())) {
        //            Set<Long> affectUserSet =
        // getLastAffectedUserIds(payload.getAffectedUserIds());
        //            changeMessageDTO.setPayload(changedPayload);
        //            sendMessageByRoleAndUser(
        //                    payload.getRoleId(), affectUserSet.stream().toList(),
        // changeMessageDTO, body);
        //        } else {
        //            CopyOnWriteArrayList<ClientSession> sessions =
        //                    new CopyOnWriteArrayList(
        //                            SessionHolder.keyToSessionMap.get(payload.getRoleId()));
        //            changeMessageDTO.setPayload(changedPayload);
        //            sendMessage(sessions, changeMessageDTO, body);
        //        }
        changeMessageDTO.setPayload(changedPayload);
        CopyOnWriteArrayList<ClientSession> sessions =
                getAffectedClientSessionListWithRoleChange(
                        payload.getRoleId(),
                        payload.getAffectedUserIds(),
                        payload.getAffectedSubjectIds());
        sendMessage(sessions, changeMessageDTO, body);
    }
}
