package com.jdec.platform.common.biz.service.impl;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.map.MapUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.approval.api.IApprovalEngine;
import com.jdec.platform.approval.api.event.ApprovalEvent;
import com.jdec.platform.common.api.CommonBusinessApi;
import com.jdec.platform.common.api.dto.request.ApprovalReq;
import com.jdec.platform.common.biz.service.CommonApprovalService;
import com.jdec.platform.config.api.SysApprovalChainConfigApi;
import com.jdec.platform.config.api.SysStatusApi;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.response.ModuleApprovalChainConfigResp;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.config.open.api.OpenMenuApi;
import com.jdec.platform.dataengine.api.DataEngineApi;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.dataengine.api.dto.request.DynamicSaveReq;
import com.jdec.platform.dataengine.api.dto.request.VersionListQueryReq;
import com.jdec.platform.dataengine.api.dto.response.CreatedUserInfoResp;
import com.jdec.platform.dataengine.api.dto.response.DynamicSaveResp;
import com.jdec.platform.dataengine.api.dto.response.VersionListResp;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.datasource.ProjectProperties;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.validation.SubmitCheckRouter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 审批与流程公共聚合服务实现 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommonApprovalServiceImpl implements CommonApprovalService, CommonBusinessApi {

    private final IApprovalEngine approvalEngine;
    private final OpenMenuApi openMenuApi;
    private final DataEngineApi dataEngineApi;
    private final ApplicationEventPublisher eventPublisher;
    private final SysStatusApi sysStatusApi;
    private final ObjectMapper objectMapper;
    private final SysApprovalChainConfigApi sysApprovalChainConfigApi;
    private final ProjectProperties projectProperties;
    private final ConfigApi configApi;
    private final SubmitCheckRouter submitCheckRouter;

    // 审批单据
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
        ModuleTreeDTO moduleTreeDTO =
                configApi.getModuleTree(
                        AppContext.getProjectNo(), AppContext.getSubjectId(), rootModuleId);
        NestedModuleDataDTO nestedModuleDataDTO =
                buildRootNestedModuleData(moduleTreeDTO, req.getRecordId());
        DynamicSaveReq dynamicSaveReq =
                DynamicSaveReq.builder()
                        .nestedModuleData(nestedModuleDataDTO)
                        .btnType("approval_save")
                        .build();
        dataEngineApi.save(dynamicSaveReq);

        ApprovalEvent approvalEvent = new ApprovalEvent();
        BeanUtils.copyProperties(req, approvalEvent);
        approvalEvent.setApplicantId(
                Objects.nonNull(UserContext.getMockUserId())
                        ? UserContext.getMockUserId()
                        : UserContext.getRealUserId());
        approvalEvent.setCurrentUserId(UserContext.getRealUserId());
        approvalEvent.setRoleId(UserContext.getRoleId());
        approvalEvent.setSubjectId(AppContext.getSubjectId());
        approvalEvent.setMenuId(menuId);
        approvalEvent.setModuleId(rootModuleId);
        approvalEvent.setOldRecordId(req.getRecordId());

        eventPublisher.publishEvent(approvalEvent);
    }

    private NestedModuleDataDTO buildRootNestedModuleData(
            ModuleTreeDTO moduleTreeDTO, Long dataId) {
        return NestedModuleDataDTO.builder()
                .moduleId(moduleTreeDTO.getRootModuleId())
                .record(
                        ListUtil.of(
                                RecordDataDTO.builder()
                                        .row(
                                                MapUtil.of(
                                                        moduleTreeDTO
                                                                        .getRootModule()
                                                                        .getPrimaryTable()
                                                                + ".id",
                                                        dataId))
                                        .build()))
                .build();
    }

    // 保存草稿
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long businessSave(DynamicSaveReq req) {
        // 保存前校验：无审批链保存，以及有审批链的新增保存（主单ID没有值），落库前基于前端提交的参数执行
        //        checkSaveBeforeStore(req);
        DynamicSaveResp result = dataEngineApi.save(req);
        if (Objects.nonNull(req.getBtnType()) && req.getBtnType().equals("realTime_save"))
            return result.getChildDataId();
        ApprovalReq approvalReq = new ApprovalReq();
        approvalReq.setRecordId(result.getDataId());
        approvalReq.setBusinessNumber(result.getBusinessNo());
        approvalReq.setButton("save");
        approvalSave(result.getModuleId(), approvalReq);
        return result.getDataId();
    }

    /**
     * 保存前的校验。
     *
     * <p>无审批链保存直接生效，落库前需要按前端提交的模块数据校验；有审批链保存属于草稿，编辑草稿（主模块第一条记录已有主单ID）允许数据不完整，
     * 仅新增（主单ID没有值）时按无审批链保存同样的规则校验。 实时保存（{@code
     * realTime_save}）是详情页中暂存子表行，不作为正式保存校验。当前单据的校验数据取自前端参数，查重会读取其他已保存对象作为比对基准。
     */
    private void checkSaveBeforeStore(DynamicSaveReq req) {
        if (req == null
                || req.getNestedModuleData() == null
                || req.getNestedModuleData().getModuleId() == null
                || "realTime_save".equals(req.getBtnType())) {
            return;
        }
        NestedModuleDataDTO nestedModuleData = req.getNestedModuleData();
        Boolean approvalEnable =
                sysApprovalChainConfigApi.getApprovalStatus(
                        List.of(nestedModuleData.getModuleId()));
        // 有审批链保存：编辑草稿允许数据不完整，新增（主单ID没有值）按无审批链保存同样的规则校验
        if (Boolean.TRUE.equals(approvalEnable) && hasMainRecordId(nestedModuleData)) {
            return;
        }
        submitCheckRouter.checkOnSave(nestedModuleData);
    }

    /**
     * 主模块第一条记录是否已有主单ID（主表 {@code id}）。
     *
     * <p>主单ID没有值代表新增：有审批链新增保存需要按无审批链保存同样的规则校验，已有主单ID的编辑保存不做校验。 主表名取自模块树配置，取不到时按新增处理。
     */
    private boolean hasMainRecordId(NestedModuleDataDTO nestedModuleData) {
        return hasMainRecordId(moduleTreeOf(nestedModuleData), nestedModuleData);
    }

    /** 主模块第一条记录是否已有主单ID（主表 {@code id}）。 */
    private boolean hasMainRecordId(
            ModuleTreeDTO moduleTree, NestedModuleDataDTO nestedModuleData) {
        List<RecordDataDTO> records = nestedModuleData.getRecord();
        if (records == null || records.isEmpty() || records.get(0) == null) {
            return false;
        }
        Map<String, Object> row = records.get(0).getRow();
        if (row == null || row.isEmpty()) {
            return false;
        }
        String primaryTable = moduleTree == null ? null : moduleTree.getPrimaryTable();
        if (primaryTable == null || primaryTable.isBlank()) {
            return false;
        }
        return isPositiveId(row.get(primaryTable + ".id"));
    }

    /** 解析前端提交结构对应的模块树；模块ID为空或配置不存在时返回 null。 */
    private ModuleTreeDTO moduleTreeOf(NestedModuleDataDTO nestedModuleData) {
        if (nestedModuleData == null || nestedModuleData.getModuleId() == null) {
            return null;
        }
        return configApi.getModuleTree(
                AppContext.getProjectNo(),
                AppContext.getSubjectId(),
                nestedModuleData.getModuleId());
    }

    /** 主单ID：空值、空串、非数字或非正数都视为没有值。 */
    private static boolean isPositiveId(Object value) {
        if (value instanceof Number number) {
            return number.longValue() > 0;
        }
        if (value == null) {
            return false;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return false;
        }
        try {
            return Long.parseLong(text) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // 提交单据
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long businessSubmit(DynamicSaveReq req) {
        boolean realTimeSave =
                Objects.nonNull(req.getBtnType()) && req.getBtnType().equals("realTime_save");
        // 提交前判断主单ID：有主单ID时保存后按主单ID加载数据库整单数据校验；
        // 主单ID没有值时从数据库查不到整单数据，改为基于前端提交的模块数据校验
        ModuleTreeDTO moduleTree = realTimeSave ? null : moduleTreeOf(req.getNestedModuleData());
        boolean mainRecordExists =
                moduleTree != null && hasMainRecordId(moduleTree, req.getNestedModuleData());
        if (!realTimeSave && !mainRecordExists && req.getNestedModuleData() != null) {
            // 前端未提交的模块按该模块校验全部不通过处理，所有需要校验的模块都通过后才允许提交
            Long rootModuleId =
                    moduleTree == null || moduleTree.getRootModuleId() == null
                            ? req.getNestedModuleData().getModuleId()
                            : moduleTree.getRootModuleId();
            submitCheckRouter.checkOnSubmit(rootModuleId, req.getNestedModuleData());
        }
        DynamicSaveResp result = dataEngineApi.save(req);
        if (realTimeSave) return result.getDataId();
        if (mainRecordExists) {
            submitCheckRouter.check(
                    result.getModuleId(),
                    result.getDataId(),
                    result.getBusinessNo(),
                    result.getVersionNo());
        }
        ApprovalReq approvalReq = new ApprovalReq();
        approvalReq.setRecordId(result.getDataId());
        approvalReq.setBusinessNumber(result.getBusinessNo());
        approvalReq.setButton("submit");
        approvalSave(result.getModuleId(), approvalReq);
        return result.getDataId();
    }

    public void approvalSave(Long moduleId, ApprovalReq req) {
        ApprovalEvent approvalEvent = new ApprovalEvent();
        BeanUtils.copyProperties(req, approvalEvent);
        approvalEvent.setApplicantId(UserContext.getMockUserId());
        approvalEvent.setCurrentUserId(UserContext.getRealUserId());
        approvalEvent.setRoleId(UserContext.getRoleId());
        approvalEvent.setSubjectId(AppContext.getSubjectId());
        approvalEvent.setModuleId(moduleId);
        approvalEvent.setOldRecordId(req.getRecordId());
        approvalEngine.handleApproval(approvalEvent);
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
        Map<String, Object> result = new HashMap<>();
        // 模块是否挂审批链
        boolean approvalEnable = sysApprovalChainConfigApi.getApprovalStatus(List.of(rootModuleId));
        if (recordId == 0) {
            result.put("approvalEnable", approvalEnable);
            return result;
        }
        result.put("approvalEnable", approvalEnable);
        Map<String, Object> recordData = dataEngineApi.getRecordData(rootModuleId, recordId);
        if (Objects.isNull(recordData)) {
            throw new BusinessException(608, "业务记录异常");
        }
        boolean approvalChainStatus = approvalEngine.getApprovalChainStatus(rootModuleId, recordId);
        // 单据编号
        result.put("businessNumber", recordData.get("business_no"));
        // 创建人
        CreatedUserInfoResp userInfoResp =
                dataEngineApi.getCreatedUserInfo(
                        rootModuleId, recordId, recordData.get("business_no").toString());

        result.putAll(objectMapper.convertValue(userInfoResp, HashMap.class));
        if (approvalEnable) {
            // 版本号列表
            VersionListQueryReq versionListQueryReq =
                    VersionListQueryReq.builder()
                            .id(recordId)
                            .moduleId(rootModuleId)
                            .businessNo(recordData.get("business_no").toString())
                            .btnType(btnType)
                            .build();
            List<VersionListResp> versionList = dataEngineApi.getVersionList(versionListQueryReq);
            result.put("versionList", versionList);
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
            // 是否草稿
            result.put("approvalChainStatus", approvalChainStatus);
            result.putAll(approvalEngine.getApprovalData(rootModuleId, recordId, btnType));
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
