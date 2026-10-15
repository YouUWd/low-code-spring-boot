package com.jdec.platform.common.biz.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.approval.api.IApprovalEngine;
import com.jdec.platform.approval.api.event.ApprovalEvent;
import com.jdec.platform.common.api.dto.request.ApprovalReq;
import com.jdec.platform.common.biz.service.CommonApprovalService;
import com.jdec.platform.config.api.SysStatusApi;
import com.jdec.platform.config.api.dto.response.ModuleApprovalChainConfigResp;
import com.jdec.platform.config.open.api.OpenMenuApi;
import com.jdec.platform.dataengine.api.DataEngineApi;
import com.jdec.platform.dataengine.api.dto.request.VersionListQueryReq;
import com.jdec.platform.dataengine.api.dto.response.CreatedUserInfoResp;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/** 审批与流程公共聚合服务实现 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommonApprovalServiceImpl implements CommonApprovalService {

    private final IApprovalEngine approvalEngine;
    private final OpenMenuApi openMenuApi;
    private final DataEngineApi dataEngineApi;
    private final ApplicationEventPublisher eventPublisher;
    private final SysStatusApi sysStatusApi;
    private final ObjectMapper objectMapper;

    @Override
    public void approvalBusiness(Long menuId, ApprovalReq req) {
        if (menuId == null) {
            throw new IllegalArgumentException("菜单 ID 不能为空");
        }

        // 1. 直接通过 OpenMenuApi 解析菜单绑定的根模块 ID
        Long rootModuleId = openMenuApi.getModuleIdByMenuId(menuId);
        if (rootModuleId == null) {
            throw new BusinessException("菜单 [" + menuId + "] 未关联或绑定有效的根模块");
        }

        ApprovalEvent approvalEvent = new ApprovalEvent();
        BeanUtils.copyProperties(req, approvalEvent);
        approvalEvent.setApplicantId(UserContext.getMockUserId());
        approvalEvent.setCurrentUserId(UserContext.getRealUserId());
        approvalEvent.setRoleId(UserContext.getRoleId());
        approvalEvent.setSubjectId(AppContext.getSubjectId());
        approvalEvent.setMenuId(menuId);
        approvalEvent.setModuleId(rootModuleId);
        approvalEvent.setOldRecordId(req.getRecordId());

        eventPublisher.publishEvent(approvalEvent);
    }

    @Override
    public void approvalSave(Long moduleId, ApprovalReq req) {
        ApprovalEvent approvalEvent = new ApprovalEvent();
        BeanUtils.copyProperties(req, approvalEvent);
        approvalEvent.setApplicantId(UserContext.getMockUserId());
        approvalEvent.setCurrentUserId(UserContext.getRealUserId());
        approvalEvent.setRoleId(UserContext.getRoleId());
        approvalEvent.setSubjectId(AppContext.getSubjectId());
        approvalEvent.setModuleId(moduleId);
        approvalEvent.setOldRecordId(req.getRecordId());

        eventPublisher.publishEvent(approvalEvent);
    }

    @Override
    public Map<String, Object> getDetailHead(Long menuId, Long recordId, String btnType) {
        if (menuId == null) {
            throw new IllegalArgumentException("菜单 ID 不能为空");
        }
        // 1. 直接通过 OpenMenuApi 解析菜单绑定的根模块 ID
        Long rootModuleId = openMenuApi.getModuleIdByMenuId(menuId);
        if (rootModuleId == null) {
            throw new BusinessException("菜单 [" + menuId + "] 未关联或绑定有效的根模块");
        }
        Map<String, Object> recordData = dataEngineApi.getRecordData(rootModuleId, recordId);
        if (Objects.isNull(recordData)) {
            throw new BusinessException(608, "业务记录异常");
        }
        boolean approvalChainStatus = approvalEngine.getApprovalChainStatus(rootModuleId, recordId);
        Map<String, Object> result = new HashMap<>();
        // 单据编号
        result.put("businessNumber", recordData.get("business_no"));
        // 版本号列表
        VersionListQueryReq versionListQueryReq =
                VersionListQueryReq.builder()
                        .id(recordId)
                        .moduleId(rootModuleId)
                        .businessNo(recordData.get("business_no").toString())
                        .btnType(btnType)
                        .build();
        ;
        result.put("versionList", dataEngineApi.getVersionList(versionListQueryReq));
        // 当前状态
        Map<String, Object> approvalStatus = new HashMap<>();
        if (Objects.nonNull(btnType)
                && (btnType.equals("change") || btnType.equals("change-change"))) {
            ModuleApprovalChainConfigResp.StatusNode statusNode =
                    sysStatusApi.getStatusByValue(1, rootModuleId);
            if (Objects.isNull(statusNode)) {
                throw new BusinessException(601, "模块状态配置异常");
            }
            approvalStatus.put("id", statusNode.getId());
            approvalStatus.put("statusValue", statusNode.getStatusValue());
            approvalStatus.put("title", statusNode.getTitle());
            approvalStatus.put("statusBackground", statusNode.getStatusBackground());
            approvalStatus.put("statusFontColor", statusNode.getStatusFontColor());
        } else {
            ModuleApprovalChainConfigResp.StatusNode statusNode =
                    sysStatusApi.getStatusByValue(
                            (Integer) recordData.get("business_status"), rootModuleId);
            approvalStatus.put("id", statusNode.getId());
            approvalStatus.put("statusValue", statusNode.getStatusValue());
            approvalStatus.put("title", statusNode.getTitle());
            approvalStatus.put("statusBackground", statusNode.getStatusBackground());
            approvalStatus.put("statusFontColor", statusNode.getStatusFontColor());
        }
        // 当前版本号
        result.put("versionNo", recordData.get("version_no"));
        result.put("approvalStatus", approvalStatus);
        // 创建人
        CreatedUserInfoResp userInfoResp =
                dataEngineApi.getCreatedUserInfo(
                        rootModuleId, recordId, recordData.get("business_no").toString());

        result.putAll(objectMapper.convertValue(userInfoResp, HashMap.class));
        // 是否草稿
        result.put("approvalChainStatus", approvalChainStatus);
        if (approvalChainStatus) {
            result.putAll(approvalEngine.getApprovalData(rootModuleId, recordId, btnType));
        } else {
            // 是否可以保存提交
            Long userId =
                    Objects.nonNull(UserContext.getMockUserId())
                            ? UserContext.getMockUserId()
                            : UserContext.getRealUserId();
            if (recordData.get("created_by").equals(userId)
                    && recordData.get("business_status").equals(1)) result.put("canEdit", 1);
            else result.put("canEdit", 0);
        }
        return result;
    }

    @Override
    public Long BusinessNextId(Long menuId) {
        if (menuId == null) {
            throw new IllegalArgumentException("菜单 ID 不能为空");
        }

        // 1. 直接通过 OpenMenuApi 解析菜单绑定的根模块 ID
        Long rootModuleId = openMenuApi.getModuleIdByMenuId(menuId);
        if (rootModuleId == null) {
            throw new BusinessException("菜单 [" + menuId + "] 未关联或绑定有效的根模块");
        }

        return approvalEngine.getBusinessNextId(rootModuleId);
    }

    @Override
    public List<Map<String, Object>> getApprovalStep(Long menuId, Long id) {
        if (menuId == null) {
            throw new IllegalArgumentException("菜单 ID 不能为空");
        }

        // 1. 直接通过 OpenMenuApi 解析菜单绑定的根模块 ID
        Long rootModuleId = openMenuApi.getModuleIdByMenuId(menuId);
        if (rootModuleId == null) {
            throw new BusinessException("菜单 [" + menuId + "] 未关联或绑定有效的根模块");
        }

        return approvalEngine.getApprovalStep(rootModuleId, id);
    }
}
