package com.jdec.platform.dataengine.api;

import com.jdec.platform.dataengine.api.dto.request.ApprovalProcessReq;
import com.jdec.platform.dataengine.api.dto.request.DynamicQueryReq;
import com.jdec.platform.dataengine.api.dto.request.DynamicSaveReq;
import com.jdec.platform.dataengine.api.dto.response.ApprovalStepInfo;
import com.jdec.platform.dataengine.api.dto.response.DynamicDetailResp;
import com.jdec.platform.dataengine.api.dto.response.DynamicQueryResp;
import com.jdec.platform.dataengine.api.dto.response.VersionCompareInfo;

public interface DataEngineApi {
    DynamicQueryResp query(DynamicQueryReq req);

    DynamicDetailResp getDetail(Long moduleId, Long id, String btnType);

    Long save(DynamicSaveReq req);

    // 审批链信息查询方法
    ApprovalStepInfo getApprovalStepInfo(ApprovalProcessReq approvalProcessReq);

    // 版本内及版本间对比查询方法
    VersionCompareInfo getVersionCompareInfo(Long primaryKeyId);
}
