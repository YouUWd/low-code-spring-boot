package com.jdec.platform.dataengine.api;

import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.request.ApprovalProcessReq;
import com.jdec.platform.dataengine.api.dto.request.ApprovalStatusCallBackReq;
import com.jdec.platform.dataengine.api.dto.request.DynamicQueryReq;
import com.jdec.platform.dataengine.api.dto.request.DynamicSaveReq;
import com.jdec.platform.dataengine.api.dto.response.ApprovalStepInfo;
import com.jdec.platform.dataengine.api.dto.response.DetailDataResp;
import com.jdec.platform.dataengine.api.dto.response.DynamicQueryResp;
import com.jdec.platform.dataengine.api.dto.response.VersionCompareInfo;
import java.util.List;

public interface DataEngineApi {
    DynamicQueryResp query(DynamicQueryReq req);

    /**
     * 模块详情数据查询
     *
     * @param rootModuleId 主模块id
     * @param id 详情id
     * @param btnType 查看入口按钮类别
     * @param filterFields 待查询字段id列表
     * @return
     */
    DetailDataResp<NestedModuleDataDTO> getDetail(
            Long rootModuleId, Long id, String btnType, List<Long> filterFields);

    Long save(DynamicSaveReq req);

    // 审批链信息查询方法
    ApprovalStepInfo getApprovalStepInfo(ApprovalProcessReq approvalProcessReq);

    // 版本内及版本间对比查询方法
    VersionCompareInfo getVersionCompareInfo(Long primaryKeyId);

    // 审批链处理 业务状态 回调接口
    NestedModuleDataDTO processApprovalStatus(ApprovalStatusCallBackReq req);
}
