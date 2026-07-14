package com.jdec.platform.common_business.api;

import com.jdec.platform.common_business.api.dto.request.MenuSaveReq;
import com.jdec.platform.common_business.api.dto.request.ModuleSaveReq;
import com.jdec.platform.shared.model.ApiResponse;
import java.util.List;

public interface DynamicDataSaveServiceApi {

    Object createMultiModuleData(Long subjectId, Long roleId, List<ModuleSaveReq> req);

    Object editMultiModuleData(Long subjectId, Long roleId, List<ModuleSaveReq> req);

    ApiResponse editMultiMenuModuleData(Long subjectId, Long roleId, List<MenuSaveReq> req);

    ApiResponse createMultiMenuModuleData(Long subjectId, Long roleId, List<MenuSaveReq> req);
}
