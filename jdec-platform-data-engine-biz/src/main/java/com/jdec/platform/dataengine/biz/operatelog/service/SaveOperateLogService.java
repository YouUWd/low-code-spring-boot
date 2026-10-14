package com.jdec.platform.dataengine.biz.operatelog.service;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.common.api.MonitorLogApi;
import com.jdec.platform.common.api.dto.log.MonitorLoginInfo;
import com.jdec.platform.common.api.dto.log.MonitorMockInfo;
import com.jdec.platform.common.api.dto.request.MonitorLogSaveReq;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.request.DynamicSaveReq;
import com.jdec.platform.dataengine.api.dto.response.DynamicSaveResp;
import com.jdec.platform.dataengine.biz.dto.SysModuleCombineInfo;
import com.jdec.platform.dataengine.biz.dto.context.ModuleDetailQueryContext;
import com.jdec.platform.dataengine.biz.dto.context.ModuleQueryContext;
import com.jdec.platform.dataengine.biz.enums.SaveType;
import com.jdec.platform.dataengine.biz.operatelog.convert.OperateLogRemarkConverter;
import com.jdec.platform.dataengine.biz.operatelog.dto.OperateLogRemarkDTO;
import com.jdec.platform.dataengine.biz.service.ModuleBizDataService;
import com.jdec.platform.dataengine.biz.service.ModuleMetaDataService;
import com.jdec.platform.dataengine.biz.third.api.persistence.DataPersistenceUtil;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.RequestContextSnapshot;
import com.jdec.platform.shared.context.RequestContextSnapshotHolder;
import com.jdec.platform.shared.context.UserContext;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 无审批链的保存操作日志服务。
 *
 * <p>两段式：{@link #prepare} 在保存前取旧数据（此时库中仍是旧值）；{@link #finish} 在保存成功后对比生成 remark 并落库。
 * 与业务无事务关系，由切面负责异常隔离。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SaveOperateLogService {

    private final ModuleMetaDataService moduleMetaDataService;

    private final ModuleBizDataService moduleBizDataService;

    private final OperateLogRemarkConverter operateLogRemarkConverter;

    private final MonitorLogApi monitorLogApi;

    /** 保存前：判断是否需要记录日志并取旧数据；不需要（非普通保存 / 有审批链 / 参数缺失）返回 null。 */
    public SaveOperateLogContext prepare(DynamicSaveReq req) {
        if (req == null
                || req.getNestedModuleData() == null
                || req.getNestedModuleData().getModuleId() == null
                || CollectionUtil.isEmpty(req.getNestedModuleData().getRecord())) {
            return null;
        }
        // 仅普通保存；变更/实时保存属于审批链
        if (!SaveType.SAVE.equals(SaveType.getSaveType(req.getBtnType()))) {
            return null;
        }
        Long structureRootModuleId = req.getNestedModuleData().getModuleId();
        Map<Long, SysModuleCombineInfo> moduleCacheMap =
                moduleMetaDataService.getModuleMetaDataMapAsync(
                        ModuleQueryContext.builder()
                                .moduleId(structureRootModuleId)
                                .projectNo(AppContext.getProjectNo())
                                .subjectId(AppContext.getSubjectId())
                                .roleId(UserContext.getRoleId())
                                .isBuildFieldPermission(false)
                                .isBuildFieldPro(false)
                                .build());
        if (CollectionUtil.isEmpty(moduleCacheMap)) {
            return null;
        }
        SysModuleCombineInfo structureRootModule = moduleCacheMap.get(structureRootModuleId);
        if (structureRootModule == null || structureRootModule.getModuleInfo() == null) {
            return null;
        }
        Long rootModuleId = structureRootModule.getModuleTree().getRootModuleId();
        Boolean isApproval = moduleMetaDataService.getApprovalStatusByModuleIdAsync(rootModuleId);
        // 有审批链的模块交给既有链路处理
        if (Boolean.TRUE.equals(isApproval)) {
            return null;
        }

        String primaryTable = structureRootModule.getModuleInfo().getPrimaryTable();
        Long structureRootDataId = null;
        if (StringUtils.isNotBlank(primaryTable)) {
            structureRootDataId =
                    DataPersistenceUtil.getTableRowIdWithTabName(
                            req.getNestedModuleData().getRecord().get(0).getRow(), primaryTable);
        }
        NestedModuleDataDTO oldData = null;
        if (structureRootDataId != null && structureRootDataId > 0) {
            oldData =
                    moduleBizDataService.getModuleDetailData(
                            ModuleDetailQueryContext.builder()
                                    .moduleId(structureRootModuleId)
                                    .dataId(structureRootDataId)
                                    .versionNo(null)
                                    .moduleCacheMap(moduleCacheMap)
                                    .projectNo(AppContext.getProjectNo())
                                    .subjectId(AppContext.getSubjectId())
                                    .loginUser(UserContext.getLoginUser())
                                    .build());
        }
        return SaveOperateLogContext.builder()
                .structureRootModuleId(structureRootModuleId)
                .menuId(req.getNestedModuleData().getMenuId())
                .moduleCacheMap(moduleCacheMap)
                .oldData(oldData)
                .build();
    }

    /** 保存成功后：对比生成 remark，写操作日志（type=0）。 */
    public void finish(SaveOperateLogContext ctx, DynamicSaveReq req, DynamicSaveResp resp) {
        if (ctx == null || req == null || req.getNestedModuleData() == null) {
            return;
        }
        OperateLogRemarkDTO remark =
                operateLogRemarkConverter.convert(
                        ctx.getOldData(),
                        req.getNestedModuleData(),
                        ctx.getModuleCacheMap(),
                        ctx.getMenuId());
        if (remark == null || CollectionUtil.isEmpty(remark.getChange())) {
            // 无变更不记录
            return;
        }
        MonitorLogSaveReq logReq = new MonitorLogSaveReq();
        logReq.setType(0);
        logReq.setSubjectId(AppContext.getSubjectId());

        MonitorLoginInfo loginInfo = new MonitorLoginInfo();
        loginInfo.setUserId(UserContext.getRealUserId());
        logReq.setLoginInfo(loginInfo);

        MonitorMockInfo mockInfo = new MonitorMockInfo();
        mockInfo.setUserId(UserContext.getMockUserId());
        mockInfo.setFlag(UserContext.isMockLogin() ? 1 : 0);
        logReq.setMockInfo(mockInfo);

        RequestContextSnapshot snapshot = RequestContextSnapshotHolder.get();
        if (snapshot != null) {
            logReq.setIp(snapshot.getClientIp());
            logReq.setBrowser(snapshot.getBrowser());
            logReq.setPlatForm(snapshot.getPlatform());
        }
        // moduleId + dataId：monitor_operate_index 需要，用于按单据查询操作日志
        logReq.setModuleId(resp != null ? resp.getModuleId() : null);
        logReq.setDataId(resp != null ? resp.getDataId() : null);
        logReq.setRemark(remark);

        monitorLogApi.saveLog(logReq);
    }
}
