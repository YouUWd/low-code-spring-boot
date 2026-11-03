package com.jdec.platform.common.biz.notify.service.impl;

import com.jdec.platform.common.biz.notify.service.AbstractPermissionChangeHandler;
import com.jdec.platform.shared.enums.ChangeType;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 审批流程更新（审批链新增、提交、通过、驳回、取消）配置变更处理 */
@Component
public class ApprovalChangeHandler extends AbstractPermissionChangeHandler {

    @Override
    protected Set<ChangeType> supportedTypes() {
        return Set.of(ChangeType.APPROVAL_UPDATE);
    }

    @Override
    protected Strategy strategy() {
        return Strategy.SUBJECT_BROADCAST;
    }
}
