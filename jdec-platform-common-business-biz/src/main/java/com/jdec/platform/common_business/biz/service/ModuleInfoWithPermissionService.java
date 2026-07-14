package com.jdec.platform.common_business.biz.service;

import com.jdec.platform.common_business.api.dto.common.ModuleFilterInfo;
import com.jdec.platform.common_business.biz.dto.FormField;
import com.jdec.platform.common_business.biz.dto.RightInfoVO;
import com.jdec.platform.shared.model.ApiResponse;
import java.util.List;

/** 获取过滤权限后的模块信息，筛选掉不在权限中的TableHeader和ModuleField * */
public interface ModuleInfoWithPermissionService {
    ModuleFilterInfo filterModuleInfo(
            Long subjectId, Long roleId, Long moduleId, String btnType, List<Long> moduleSkip);

    ApiResponse<RightInfoVO> getModuleApplyFieldList(
            Long subjectId, Long roleId, Long moduleId, String btn_type);

    List<FormField> getFormFieldData(
            ModuleFilterInfo moduleFilterInfo, boolean isFilterUnWriteableField);
}
