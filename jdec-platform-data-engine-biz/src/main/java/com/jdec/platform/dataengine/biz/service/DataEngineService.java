package com.jdec.platform.dataengine.biz.service;

import com.jdec.platform.dataengine.api.DataEngineApi;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.request.ApprovalProcessReq;
import com.jdec.platform.dataengine.api.dto.request.DynamicQueryReq;
import com.jdec.platform.dataengine.api.dto.request.DynamicSaveReq;
import com.jdec.platform.dataengine.api.dto.response.ApprovalStepInfo;
import com.jdec.platform.dataengine.api.dto.response.DynamicQueryResp;
import com.jdec.platform.dataengine.api.dto.response.VersionCompareInfo;
import com.jdec.platform.dataengine.biz.third.api.persistence.ModuleDataPersistenceService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DataEngineService implements DataEngineApi {

    private final DynamicQueryService dynamicQueryService;
    private final ModuleDataPersistenceService moduleDataPersistenceService;

    @Override
    public DynamicQueryResp query(DynamicQueryReq req) {
        return dynamicQueryService.query(req);
    }

    @Override
    public List<NestedModuleDataDTO> getDetail(List<Long> moduleIds, Long id, String btnType) {
        return dynamicQueryService.getDetail(moduleIds, id, btnType, false);
    }

    @Override
    public Long save(DynamicSaveReq req) {
        return moduleDataPersistenceService.save(req);
    }

    @Override
    public ApprovalStepInfo getApprovalStepInfo(ApprovalProcessReq approvalProcessReq) {
        return null;
    }

    @Override
    public VersionCompareInfo getVersionCompareInfo(Long primaryKeyId) {
        return null;
    }
}
