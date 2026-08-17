package com.jdec.platform.common_business.api;

import com.jdec.platform.common_business.api.dto.request.ApprovalModuleSaveReq;
import com.jdec.platform.common_business.api.dto.request.ApprovalOperationReq;
import com.jdec.platform.common_business.api.dto.request.MenuSaveReq;
import com.jdec.platform.common_business.api.dto.request.ScheduledTaskCallBackReq;
import java.util.List;

public interface DynamicDataSaveServiceApi {

    Object editMultiMenuModuleData(Long subjectId, Long roleId, List<MenuSaveReq> req);

    Object createMultiMenuModuleData(Long subjectId, Long roleId, List<MenuSaveReq> req);

    // 带审批链的创建
    Object createMultiMenuModuleDataWithApproval(
            Long subjectId, Long roleId, ApprovalModuleSaveReq req);

    Object editMultiMenuModuleDataWithApproval(
            Long subjectId,
            Long roleId,
            List<MenuSaveReq> req,
            ApprovalOperationReq approvalOperation);

    Object autoApproval(Long subjectId, Long roleId, ScheduledTaskCallBackReq req);
}
