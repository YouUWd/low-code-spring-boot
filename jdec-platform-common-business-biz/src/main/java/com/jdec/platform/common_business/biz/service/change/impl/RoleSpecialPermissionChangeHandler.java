package com.jdec.platform.common_business.biz.service.change.impl;

import com.jdec.platform.common_business.biz.common.CommonConstant;
import com.jdec.platform.common_business.biz.configcenter.client.ConfigCenterApiClient;
import com.jdec.platform.common_business.biz.context.TokenContextHolder;
import com.jdec.platform.common_business.biz.dto.ChangedPayload;
import com.jdec.platform.common_business.biz.dto.ConfigChangeMessageDTO;
import com.jdec.platform.common_business.biz.dto.RoleModulePermissionChangedPayload;
import com.jdec.platform.common_business.biz.dto.configcenter.requst.CheckUserSpecialPermissionReq;
import com.jdec.platform.common_business.biz.dto.configcenter.response.CheckUserSpecialPermissionResp;
import com.jdec.platform.common_business.biz.dto.configcenter.response.ConfigApiResponse;
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
public class RoleSpecialPermissionChangeHandler implements PermissionChangeMessageHandle {

    private final Set<ConfigCenterChangeType> referToChangeTypeSet =
            Set.of(ConfigCenterChangeType.ROLE_SPECIAL_PERMISSION_CHANGED);

    private final ConfigCenterApiClient configCenterApiClient;

    @Override
    public Boolean isReferToChangeType(ConfigCenterChangeType changeType) {
        return referToChangeTypeSet.contains(changeType);
    }

    /**
     * 特殊权限（模拟权限） 变更消息处理
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
        if (payload == null || payload.getRoleId() == null) {
            return;
        }
        // changeMessageDTO.setToken(null);
        ChangedPayload changedPayload = new ChangedPayload();
        changedPayload.setAffectedRoleIds(List.of(payload.getRoleId()));
        // changedPayload.setAffectedSubjectIds(payload.getAffectedSubjectIds());

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
                getAffectedClientSessionListWithLoginUser(
                        payload.getRoleId(),
                        payload.getAffectedUserIds(),
                        payload.getAffectedSubjectIds());
        try {
            TokenContextHolder.setCurrentUserToken(changeMessageDTO.getToken());
            sendMessage(sessions, changeMessageDTO, body);
        } finally {
            TokenContextHolder.clear();
        }
    }

    @Override
    public void customMessage(ClientSession session, ConfigChangeMessageDTO changeMessageDTO) {
        changeMessageDTO.setToken(null);
        ChangedPayload changedPayload = (ChangedPayload) changeMessageDTO.getPayload();
        // 这里使用的是，登录用户正在使用的主题
        Long subjectId = session.getSubjectId();
        log.debug("查询用户id:{} , 主体id:{}的模拟权限。", session.getUserId(), subjectId);
        ConfigApiResponse<CheckUserSpecialPermissionResp> checkUserSpecialResponse =
                configCenterApiClient.checkUserSpecialPermission(
                        subjectId,
                        CheckUserSpecialPermissionReq.builder()
                                .userId(session.getUserId())
                                .rightCode(CommonConstant.CONFIG_CENTER_SIMULATE_LOGIN_RIGHT_CODE)
                                .build());
        log.info(
                "get subjectId={},userId={}  simulate_login  result={}",
                subjectId,
                session.getUserId(),
                checkUserSpecialResponse != null ? checkUserSpecialResponse.toString() : null);
        Boolean simulateLogin = false;
        if (ConfigApiResponse.isSuccessResponse(checkUserSpecialResponse)
                && checkUserSpecialResponse.getData() != null
                && checkUserSpecialResponse.getData().getHasPermission() != null
                && checkUserSpecialResponse.getData().getHasPermission()) {
            log.debug(
                    "调用配置中心查询模拟权限成功，模拟权限为{}",
                    checkUserSpecialResponse.getData().getHasPermission());
            simulateLogin = true;
        }
        changedPayload.setSimulateLogin(simulateLogin);
        changedPayload.setAffectedSubjectIds(List.of(subjectId));
    }
}
