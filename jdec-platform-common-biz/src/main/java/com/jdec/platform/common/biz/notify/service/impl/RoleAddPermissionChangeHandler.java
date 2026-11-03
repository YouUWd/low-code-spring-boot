package com.jdec.platform.common.biz.notify.service.impl;

import com.jdec.platform.common.biz.notify.service.AbstractPermissionChangeHandler;
import com.jdec.platform.shared.enums.ChangeType;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 角色新增（新增角色或编辑角色时新增用户/主体）配置变更处理 */
@Component
public class RoleAddPermissionChangeHandler extends AbstractPermissionChangeHandler {

    @Override
    protected Set<ChangeType> supportedTypes() {
        return Set.of(ChangeType.ROLE_ADD);
    }

    @Override
    protected Strategy strategy() {
        return Strategy.LOGIN_USER;
    }
}
