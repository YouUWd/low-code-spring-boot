package com.jdec.platform.dataengine.api;

import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.request.ApprovalProcessReq;
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
     * @param moduleIds 待查询模块id列表
     * @param id 详情id
     * @param btnType 查看入口按钮类别
     * @param topQuery 是否顶层查询，顶层查询会查询模块及其树上的所有子模块。非顶层查询会查询顶层模块到模块id列表指定的路径上的模块数据
     * @return
     */
    DetailDataResp<NestedModuleDataDTO> getDetail(
            List<Long> moduleIds,
            Long id,
            String btnType,
            Boolean topQuery,
            List<Long> filterFields);

    Long save(DynamicSaveReq req);

    // 审批链信息查询方法
    ApprovalStepInfo getApprovalStepInfo(ApprovalProcessReq approvalProcessReq);

    // 版本内及版本间对比查询方法
    VersionCompareInfo getVersionCompareInfo(Long primaryKeyId);
}
