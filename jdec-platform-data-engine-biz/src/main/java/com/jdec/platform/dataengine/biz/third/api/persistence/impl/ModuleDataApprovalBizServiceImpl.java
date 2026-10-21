package com.jdec.platform.dataengine.biz.third.api.persistence.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.jdec.platform.dataengine.api.dto.model.*;
import com.jdec.platform.dataengine.api.dto.request.ApprovalStatusCallBackReq;
import com.jdec.platform.dataengine.api.dto.response.ApprovalProcessResultResp;
import com.jdec.platform.dataengine.biz.constants.CommonConstant;
import com.jdec.platform.dataengine.biz.dto.SysModuleCombineInfo;
import com.jdec.platform.dataengine.biz.dto.context.ModuleDetailQueryContext;
import com.jdec.platform.dataengine.biz.dto.context.ModuleQueryContext;
import com.jdec.platform.dataengine.biz.dto.context.ModuleSnapShotDataContext;
import com.jdec.platform.dataengine.biz.enums.ApiResponseCodeEnum;
import com.jdec.platform.dataengine.biz.operatelog.util.OperateLogRemarkUtil;
import com.jdec.platform.dataengine.biz.service.*;
import com.jdec.platform.dataengine.biz.third.api.persistence.ModuleDataApprovalBizService;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.datasource.JooqDataContextFactory;
import com.jdec.platform.shared.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModuleDataApprovalBizServiceImpl implements ModuleDataApprovalBizService {

    private final ModuleMetaDataService moduleMetaDataService;

    private final ModuleBizDataService moduleBizDataService;

    private final JooqDataContextFactory jooqDataContextFactory;

    private final ModuleSnapShotDataService moduleSnapShotDataService;

    private final OperateLogRemarkUtil operateLogRemarkUtil;

    /**
     * 审批链状态 回调处理
     *
     * @param req
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public ApprovalProcessResultResp processApprovalStatus(ApprovalStatusCallBackReq req) {
        Map<Long, SysModuleCombineInfo> moduleCacheMap =
                moduleMetaDataService.getModuleMetaDataMapAsync(
                        ModuleQueryContext.builder()
                                .moduleId(req.getModuleId())
                                .projectNo(AppContext.getProjectNo())
                                .subjectId(AppContext.getSubjectId())
                                .roleId(UserContext.getRoleId())
                                .build());
        SysModuleCombineInfo rootModule = moduleCacheMap.get(req.getModuleId());
        if (rootModule == null) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getMessage());
        }
        Boolean isApproval =
                moduleMetaDataService.getApprovalStatusByModuleIdAsync(req.getModuleId());
        DSLContext context = jooqDataContextFactory.getContext();

        Long rootModuleMainTableId = saveModuleApprovalData(req, rootModule, isApproval, context);
        if (rootModuleMainTableId == null || rootModuleMainTableId.equals(0L)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_SAVE_DATA_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_SAVE_DATA_ERROR.getMessage());
        }

        // 获取当前模块数据的快照
        NestedModuleDataDTO result =
                moduleBizDataService.getModuleDetailData(
                        ModuleDetailQueryContext.builder()
                                .moduleId(req.getModuleId())
                                .dataId(req.getDataId())
                                .moduleCacheMap(moduleCacheMap)
                                .projectNo(AppContext.getProjectNo())
                                .subjectId(AppContext.getSubjectId())
                                .loginUser(UserContext.getLoginUser())
                                .build());

        // 不带审批链的 业务 不处理 快照
        if (!isApproval) {
            return ApprovalProcessResultResp.builder().nestedModuleData(result).build();
        }

        // 获取上一个状态的 快照数据
        NestedModuleDataDTO tmpSnapshot =
                moduleSnapShotDataService.getLastStatusModuleDataTempSnapshot(
                        ModuleSnapShotDataContext.builder()
                                .dataId(req.getDataId())
                                .versionNo(req.getVersionNo())
                                .dslContext(context)
                                .rootModuleInfo(rootModule)
                                .rootModuleId(rootModule.getModuleInfo().getModuleId())
                                .build());
        if (req.getIsCurrentStepFinish()
                && (req.getBusinessStatus() == 99 || req.getBusinessStatus() == -99)) {
            // 终态时候 删除快照数据
            moduleSnapShotDataService.deleteModuleDataTempSnapshot(
                    ModuleSnapShotDataContext.builder()
                            .dataId(req.getDataId())
                            .versionNo(req.getVersionNo())
                            .dslContext(context)
                            .rootModuleId(rootModule.getModuleInfo().getModuleId())
                            .build());
            moduleSnapShotDataService.deleteModuleFieldDataSnapshot(
                    ModuleSnapShotDataContext.builder()
                            .dataId(req.getDataId())
                            .versionNo(req.getVersionNo())
                            .dslContext(context)
                            .rootModuleId(rootModule.getModuleInfo().getModuleId())
                            .build());
        } else {
            // 保存每个状态的快照，用于计算 审批的 操作日志
            moduleSnapShotDataService.saveModuleDataTempSnapshot(
                    ModuleSnapShotDataContext.builder()
                            .dataId(req.getDataId())
                            .versionNo(req.getVersionNo())
                            .dslContext(context)
                            .moduleDataDTO(result)
                            .rootModuleInfo(rootModule)
                            .rootModuleId(rootModule.getModuleInfo().getModuleId())
                            .build());
            // 保存字段快照
            // 只有当前步骤完结 才会切分字段快照
            if (req.getIsCurrentStepFinish()) {
                moduleSnapShotDataService.saveModuleFieldDataSnapshot(
                        ModuleSnapShotDataContext.builder()
                                .dataId(req.getDataId())
                                .versionNo(req.getVersionNo())
                                .dslContext(context)
                                .moduleDataDTO(result)
                                .rootModuleInfo(rootModule)
                                .rootModuleId(rootModule.getModuleInfo().getModuleId())
                                .moudleCacheMap(moduleCacheMap)
                                .build());
            }
        }
        // todo  result   tmpSnapshot 要构建 版本间的字段 对比 返回给审批链
        JsonNode remark =
                operateLogRemarkUtil.buildRemark(
                        tmpSnapshot, result, moduleCacheMap, req.getMenuId(), req.getReason());
        ApprovalProcessResultResp resp =
                ApprovalProcessResultResp.builder().nestedModuleData(result).remark(remark).build();
        return resp;
    }

    private Long saveModuleApprovalData(
            ApprovalStatusCallBackReq req,
            SysModuleCombineInfo rootModule,
            Boolean isApproval,
            DSLContext dslContext) {

        Record r =
                dslContext
                        .select(DSL.asterisk())
                        .from(DSL.table(rootModule.getModuleInfo().getPrimaryTable()))
                        .where(DSL.field(DSL.name("id")).eq(req.getDataId()))
                        .fetchOne();
        if (r == null) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_REQUEST.getCode(),
                    "审批状态回调数据ID[" + req.getDataId() + "]无此记录");
        }
        // 带有审批链的状态回调
        if (isApproval) {
            Long sourceId = r.get(DSL.field(DSL.name("source_id")), Long.class);

            Map<Field<Object>, Object> fieldValues = new HashMap<>();
            fieldValues.put(
                    DSL.field(DSL.name(CommonConstant.TABLE_DEFAULT_BUSINESS_STATUS_FIELD)),
                    req.getBusinessStatus());
            fieldValues.put(
                    DSL.field(DSL.name(CommonConstant.TABLE_DEFAULT_UPDATE_BY_FIELD)),
                    UserContext.getUserId());
            fieldValues.put(
                    DSL.field(DSL.name(CommonConstant.TABLE_DEFAULT_UPDATED_DATE_FIELD)),
                    LocalDateTime.now());
            if (req.getIsFirstSubmit() != null
                    && req.getIsFirstSubmit()
                    && req.getRegistrationTime() != null) {
                fieldValues.put(
                        DSL.field(DSL.name(CommonConstant.TABLE_DEFAULT_REGISTRATION_TIME_FIELD)),
                        req.getRegistrationTime());
            }
            // 如果是 取消状态  而且有上一个版本的数据
            if (req.getBusinessStatus() == -99 && (sourceId != null && sourceId > 0)) {
                // 当前版本 要设置成 非活跃
                fieldValues.put(DSL.field(DSL.name(CommonConstant.TABLE_DEFAULT_ACTIVE_FIELD)), 0);
            }
            dslContext
                    .update(DSL.table(rootModule.getModuleInfo().getPrimaryTable()))
                    .set(fieldValues)
                    .where(DSL.field(DSL.name("id")).eq(req.getDataId()))
                    .execute();
            // 如果是 取消状态  而且有上一个版本的数据
            if (req.getBusinessStatus() == -99 && (sourceId != null && sourceId > 0)) {
                // 上一个版本 要活跃
                dslContext
                        .update(DSL.table(rootModule.getModuleInfo().getPrimaryTable()))
                        .set(DSL.field(DSL.name(CommonConstant.TABLE_DEFAULT_ACTIVE_FIELD)), 0)
                        .set(
                                DSL.field(DSL.name(CommonConstant.TABLE_DEFAULT_UPDATE_BY_FIELD)),
                                UserContext.getUserId())
                        .set(
                                DSL.field(
                                        DSL.name(CommonConstant.TABLE_DEFAULT_UPDATED_DATE_FIELD)),
                                LocalDateTime.now())
                        .where(DSL.field(DSL.name("id")).eq(sourceId))
                        .execute();
            }
        } else {
            // 不带审批链 的状态 不用 回调  貌似状态不用 回调

            Map<Field<Object>, Object> fieldValues = new HashMap<>();
            fieldValues.put(
                    DSL.field(DSL.name(CommonConstant.TABLE_DEFAULT_BUSINESS_STATUS_FIELD)),
                    req.getBusinessStatus());
            fieldValues.put(
                    DSL.field(DSL.name(CommonConstant.TABLE_DEFAULT_UPDATE_BY_FIELD)),
                    UserContext.getUserId());
            fieldValues.put(
                    DSL.field(DSL.name(CommonConstant.TABLE_DEFAULT_UPDATED_DATE_FIELD)),
                    LocalDateTime.now());
            if (req.getIsFirstSubmit() != null
                    && req.getIsFirstSubmit()
                    && req.getRegistrationTime() != null) {
                fieldValues.put(
                        DSL.field(DSL.name(CommonConstant.TABLE_DEFAULT_REGISTRATION_TIME_FIELD)),
                        req.getRegistrationTime());
            }
            dslContext
                    .update(DSL.table(rootModule.getModuleInfo().getPrimaryTable()))
                    .set(fieldValues)
                    .where(DSL.field(DSL.name("id")).eq(req.getDataId()))
                    .execute();
        }
        return req.getDataId();
    }
}
