package com.jdec.platform.common_business.biz.dto;

import com.jdec.platform.common_business.api.dto.common.ModuleFilterInfo;
import com.jdec.platform.common_business.api.dto.request.ModuleSaveReq;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApprovalMsgDTO implements Serializable {
    private Long subjectId;

    private List<Map<String, Object>> processResult;

    private ModuleFilterInfo moduleFilterInfo;

    private List<ModuleSaveReq> moduleSaveReqList;

    private Long dataId;

    private Boolean autoApproval;
}
