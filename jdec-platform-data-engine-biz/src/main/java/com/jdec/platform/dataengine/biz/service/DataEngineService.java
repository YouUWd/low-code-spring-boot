package com.jdec.platform.dataengine.biz.service;

import com.jdec.platform.dataengine.api.DataEngineApi;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.request.*;
import com.jdec.platform.dataengine.api.dto.response.ApprovalProcessResultResp;
import com.jdec.platform.dataengine.api.dto.response.DynamicQueryResp;
import com.jdec.platform.dataengine.api.dto.response.VersionListResp;
import com.jdec.platform.dataengine.biz.third.api.persistence.ModuleDataApprovalBizService;
import com.jdec.platform.dataengine.biz.third.api.persistence.ModuleDataPersistenceService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
@RequiredArgsConstructor
public class DataEngineService implements DataEngineApi {

    private final DynamicQueryService dynamicQueryService;
    private final ModuleDataPersistenceService moduleDataPersistenceService;
    private final ModuleDataApprovalBizService moduleDataApprovalBizService;

    @Override
    public DynamicQueryResp query(DynamicQueryReq req) {
        return dynamicQueryService.query(req);
    }

    @Override
    public NestedModuleDataDTO getDetail(DynamicDetailQueryReq dynamicDetailQueryReq) {

        List<NestedModuleDataDTO> detailDatas =
                dynamicQueryService.getDetail(
                        dynamicDetailQueryReq, dynamicDetailQueryReq.getBtnType());

        return CollectionUtils.isEmpty(detailDatas) ? null : detailDatas.get(0);
    }

    @Override
    public Long save(DynamicSaveReq req) {
        return moduleDataPersistenceService.save(req);
    }

    @Override
    public ApprovalProcessResultResp processApprovalStatus(ApprovalStatusCallBackReq req) {
        return moduleDataApprovalBizService.processApprovalStatus(req);
    }

    @Override
    public List<VersionListResp> getVersionList(VersionListQueryReq versionListQueryReq) {
        return dynamicQueryService.getVersionList(versionListQueryReq);
    }

    @Override
    public Long insertAndGetId(DynamicInsertReq req) {
        return moduleDataPersistenceService.insertAndGetId(req);
    }

    @Override
    public Long deleteById(DynamicDeleteReq req) {
        return moduleDataPersistenceService.deleteById(req);
    }
}
