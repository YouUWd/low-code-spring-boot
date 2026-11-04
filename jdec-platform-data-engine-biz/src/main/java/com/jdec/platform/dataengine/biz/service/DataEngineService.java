package com.jdec.platform.dataengine.biz.service;

import cn.hutool.core.util.StrUtil;
import com.jdec.platform.dataengine.api.DataEngineApi;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.request.*;
import com.jdec.platform.dataengine.api.dto.response.*;
import com.jdec.platform.dataengine.biz.constants.CommonConstant;
import com.jdec.platform.dataengine.biz.operatelog.annotation.SaveOperateLog;
import com.jdec.platform.dataengine.biz.third.api.persistence.ModuleDataApprovalBizService;
import com.jdec.platform.dataengine.biz.third.api.persistence.ModuleDataStoreService;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
@RequiredArgsConstructor
public class DataEngineService implements DataEngineApi {

    private final DynamicQueryService dynamicQueryService;
    private final ModuleDataStoreService moduleDataStoreService;
    private final ModuleDataApprovalBizService moduleDataApprovalBizService;
    private final SequenceService sequenceService;

    @Override
    public NestedModuleDataDTO getDetail(DynamicDetailQueryReq dynamicDetailQueryReq) {

        List<NestedModuleDataDTO> detailDatas =
                dynamicQueryService.getDetail(dynamicDetailQueryReq);

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
    public CreatedUserInfoResp getCreatedUserInfo(Long userId) {
        return dynamicQueryService.getCreatedUserInfo(userId);
    }

    @Override
    public Map<String, Object> getRecordData(Long moduleId, Long id) {
        List<Map<String, Object>> dataList =
                dynamicQueryService.getRecordDatas(
                        moduleId, Arrays.asList(id), new ArrayList<>(), null);
        return CollectionUtils.isEmpty(dataList) ? new HashMap<>() : dataList.get(0);
    }

    @Override
    public Map<String, Object> getRecordDataByName(String tableName, Long id) {
        List<Map<String, Object>> dataList =
                dynamicQueryService.getRecordDataByName(
                        tableName, Collections.singletonList(id), new ArrayList<>(), null);
        return CollectionUtils.isEmpty(dataList) ? new HashMap<>() : dataList.getFirst();
    }

    @Override
    public Integer modifyBusinessFormStatus(BusinessFormStatusReq req) {
        return moduleDataStoreService.modifyBusinessFormStatus(req);
    }

    @Override
    public ChangeApplySaveResp saveChangeApply(DynamicSaveReq req) {
        return moduleDataStoreService.saveChangeApply(req);
    }

    @Override
    public String getNextDeptNo() {
        Long nextValue = sequenceService.nextSequenceValue(CommonConstant.SEQ_NAME_DEPT_NO);
        String s = StrUtil.padPre(nextValue.toString(), 5, '0');
        return "W" + s;
    }
}
