package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp;

public interface SysModuleQueryApi {
    SysModuleCompleteResp getModuleCompleteByCode(
            String projectNo, Long subjectId, String moduleCode);
}
