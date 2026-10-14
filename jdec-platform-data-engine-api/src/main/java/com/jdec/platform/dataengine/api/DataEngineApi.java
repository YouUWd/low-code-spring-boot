package com.jdec.platform.dataengine.api;

import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.request.*;
import com.jdec.platform.dataengine.api.dto.response.*;
import java.util.List;

public interface DataEngineApi {
    DynamicQueryResp query(DynamicQueryReq req);

    NestedModuleDataDTO getDetail(DynamicDetailQueryReq dynamicDetailQueryReq);

    DynamicSaveResp save(DynamicSaveReq req);

    // 审批链处理 业务状态 回调接口
    ApprovalProcessResultResp processApprovalStatus(ApprovalStatusCallBackReq req);

    // 查询版本列表
    List<VersionListResp> getVersionList(VersionListQueryReq versionListQueryReq);

    Long insertAndGetId(DynamicInsertReq req);

    Long deleteById(DynamicDeleteReq req);

    CreatedUserInfoResp getCreatedUserInfo(Long moduleId, Long id, String businessNo);
}
