package com.jdec.platform.dataengine.api;

import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.request.*;
import com.jdec.platform.dataengine.api.dto.response.ApprovalProcessResultResp;
import com.jdec.platform.dataengine.api.dto.response.DetailDataResp;
import com.jdec.platform.dataengine.api.dto.response.DynamicQueryResp;
import com.jdec.platform.dataengine.api.dto.response.VersionListResp;
import java.util.List;

public interface DataEngineApi {
    DynamicQueryResp query(DynamicQueryReq req);

    DetailDataResp<NestedModuleDataDTO> getDetail(
            DynamicDetailQueryReq dynamicDetailQueryReq, String btnType);

    Long save(DynamicSaveReq req);

    // 审批链处理 业务状态 回调接口
    ApprovalProcessResultResp processApprovalStatus(ApprovalStatusCallBackReq req);

    // 查询版本列表
    List<VersionListResp> getVersionList(VersionListQueryReq versionListQueryReq);
}
