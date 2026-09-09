package com.jdec.platform.dataengine.biz.third.api.impl;

import com.jdec.platform.dataengine.biz.dto.FormButton;
import com.jdec.platform.dataengine.biz.dto.ModuleFilterInfo;
import com.jdec.platform.dataengine.biz.third.api.module.ModuleInfoWithPermissionService;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ModuleInfoWithPermissionServiceImpl implements ModuleInfoWithPermissionService {
    @Override
    public ModuleFilterInfo filterModuleInfo(
            Long subjectId, Long roleId, Long moduleId, String btnType, boolean withApproval) {
        return null;
    }

    @Override
    public Set<FormButton> getListOperateBtn(
            Long subjectId, Long roleId, Long moduleId, String btnType, boolean withApproval) {
        return Set.of();
    }

    @Override
    public FormButton getCreateBtn(Long subjectId, Long roleId, Long moduleId) {
        return null;
    }

    @Override
    public Set<FormButton> getDeatailBottomBtn(
            Long subjectId, Long roleId, Long moduleId, String btnType, boolean withApproval) {
        return Set.of();
    }

    @Override
    public void fillIdAndJoinIdField(ModuleFilterInfo moduleFilterInfo) {}
}
