package com.jdec.platform.common_business.api;

import com.jdec.platform.common_business.api.dto.request.OpLogReq;
import com.jdec.platform.common_business.api.dto.response.OpLogResp;
import java.util.List;

public interface OperatorLogServiceApi {
    List<OpLogResp> getModuleDataChangeLog(Long subjectId, Long roleId, OpLogReq req);
}
