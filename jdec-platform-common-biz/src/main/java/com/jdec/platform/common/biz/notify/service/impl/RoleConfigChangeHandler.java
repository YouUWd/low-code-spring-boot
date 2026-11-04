package com.jdec.platform.common.biz.notify.service.impl;

import com.jdec.platform.common.biz.notify.service.AbstractPermissionChangeHandler;
import com.jdec.platform.shared.enums.ChangeType;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 角色配置变更（角色基础信息变更）配置变更处理 */
@Component
public class RoleConfigChangeHandler extends AbstractPermissionChangeHandler {

    @Override
    protected Set<ChangeType> supportedTypes() {
        return Set.of(ChangeType.ROLE_CONFIG_CHANGED);
    }

    @Override
    protected Strategy strategy() {
        return Strategy.LOGIN_USER;
    }
}
