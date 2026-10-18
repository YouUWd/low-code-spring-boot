package com.jdec.platform.dataengine.biz.third.api.persistence.impl;

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

    private final ModuleDataChangeRowConvertService moduleDataChangeRowConvertService;

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
        DSLContext context = jooqDataContextFactory.getContext();

        Long rootModuleMainTableId = saveModuleApprovalData(req, rootModule, context);
        if (rootModuleMainTableId == null || rootModuleMainTableId.equals(0L)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_SAVE_DATA_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_SAVE_DATA_ERROR.getMessage());
        }

        // 获取当前模块数据的快照
        NestedModuleDataDTO result =
                moduleBizDataService.getModuleDetailDataAsync(
                        ModuleDetailQueryContext.builder()
                                .moduleId(req.getModuleId())
                                .dataId(req.getDataId())
                                .moduleCacheMap(moduleCacheMap)
                                .projectNo(AppContext.getProjectNo())
                                .subjectId(AppContext.getSubjectId())
                                .loginUser(UserContext.getLoginUser())
                                .build());

        // 如果是终态 保存模块数据的 版本快照

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
        //
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

        // todo  result   tmpSnapshot 要构建 版本间的字段 对比 返回给审批链
        String remark =
                operateLogRemarkUtil.buildRemark(
                        tmpSnapshot, result, moduleCacheMap, req.getMenuId(), req.getReason());
        ApprovalProcessResultResp resp =
                ApprovalProcessResultResp.builder().nestedModuleData(result).remark(remark).build();
        return resp;
    }

    private Long saveModuleApprovalData(
            ApprovalStatusCallBackReq req, SysModuleCombineInfo rootModule, DSLContext dslContext) {

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
                            DSL.field(DSL.name(CommonConstant.TABLE_DEFAULT_UPDATED_DATE_FIELD)),
                            LocalDateTime.now())
                    .where(DSL.field(DSL.name("id")).eq(sourceId))
                    .execute();
        }
        return req.getDataId();
    }
}
