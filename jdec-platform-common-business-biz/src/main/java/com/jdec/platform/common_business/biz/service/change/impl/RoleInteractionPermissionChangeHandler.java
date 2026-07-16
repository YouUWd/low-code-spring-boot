package com.jdec.platform.common_business.biz.service.change.impl;

import com.jdec.platform.common_business.biz.dto.ConfigChangeMessageDTO;
import com.jdec.platform.common_business.biz.enums.ConfigCenterChangeType;
import com.jdec.platform.common_business.biz.service.change.PermissionChangeMessageHandle;

public class RoleInteractionPermissionChangeHandler implements PermissionChangeMessageHandle {
    @Override
    public Boolean isReferToChangeType(ConfigCenterChangeType changeType) {
        return null;
    }

    /**
     * 交互权限 变更消息处理
     *
     * @param changeMessageDTO
     * @param changeType
     * @param body
     */
    @Override
    public void handle(
            ConfigChangeMessageDTO changeMessageDTO,
            ConfigCenterChangeType changeType,
            String body) {}
}
