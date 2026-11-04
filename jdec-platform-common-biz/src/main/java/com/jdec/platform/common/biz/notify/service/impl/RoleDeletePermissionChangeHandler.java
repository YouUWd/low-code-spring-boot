package com.jdec.platform.common.biz.notify.service.impl;

import com.jdec.platform.common.biz.notify.service.AbstractPermissionChangeHandler;
import com.jdec.platform.shared.enums.ChangeType;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 角色删除（删除角色或编辑角色时移除用户/主体）配置变更处理 */
@Component
public class RoleDeletePermissionChangeHandler extends AbstractPermissionChangeHandler {

    @Override
    protected Set<ChangeType> supportedTypes() {
        return Set.of(ChangeType.ROLE_DELETE);
    }

    @Override
    protected Strategy strategy() {
        return Strategy.ROLE_REMOVE;
    }
}
