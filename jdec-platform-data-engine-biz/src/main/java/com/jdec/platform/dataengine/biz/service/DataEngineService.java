package com.jdec.platform.dataengine.biz.service;

import com.jdec.platform.dataengine.api.DataEngineApi;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.request.*;
import com.jdec.platform.dataengine.api.dto.response.*;
import com.jdec.platform.dataengine.biz.operatelog.annotation.SaveOperateLog;
import com.jdec.platform.dataengine.biz.third.api.persistence.ModuleDataApprovalBizService;
import com.jdec.platform.dataengine.biz.third.api.persistence.ModuleDataStoreService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
@RequiredArgsConstructor
public class DataEngineService implements DataEngineApi {

    private final DynamicQueryService dynamicQueryService;
    private final ModuleDataStoreService moduleDataStoreService;
    private final ModuleDataApprovalBizService moduleDataApprovalBizService;

    @Override
    public NestedModuleDataDTO getDetail(DynamicDetailQueryReq dynamicDetailQueryReq) {

        List<NestedModuleDataDTO> detailDatas =
                dynamicQueryService.getDetail(
                        dynamicDetailQueryReq, dynamicDetailQueryReq.getBtnType());

        return detailDatas.get(0);
    }

    @Override
    @SaveOperateLog
    public DynamicSaveResp save(DynamicSaveReq req) {
        return moduleDataStoreService.save(req);
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
    public CreatedUserInfoResp getCreatedUserInfo(Long moduleId, Long id, String businessNo) {
        return dynamicQueryService.getCreatedUserInfo(moduleId, id, businessNo);
    }

    @Override
    public Map<String, Object> getRecordData(Long moduleId, Long id) {
        List<Map<String, Object>> dataList =
                dynamicQueryService.getRecordDatas(
                        moduleId, Arrays.asList(id), new ArrayList<>(), null);
        return CollectionUtils.isEmpty(dataList) ? null : dataList.get(0);
    }

    @Override
    public Integer modifyBusinessFormStatus(BusinessFormStatusReq req) {
        return moduleDataStoreService.modifyBusinessFormStatus(req);
    }
}
