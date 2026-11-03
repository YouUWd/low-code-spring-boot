package com.jdec.platform.common.biz.notify.service.impl;

import com.jdec.platform.common.biz.notify.service.AbstractPermissionChangeHandler;
import com.jdec.platform.shared.enums.ChangeType;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 角色交互权限变更配置变更处理 */
@Component
public class RoleInteractionPermissionChangeHandler extends AbstractPermissionChangeHandler {

    @Override
    protected Set<ChangeType> supportedTypes() {
        return Set.of(ChangeType.ROLE_INTERACTION_PERMISSION_CHANGED);
    }
}
