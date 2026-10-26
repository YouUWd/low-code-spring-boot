package com.jdec.platform.dataengine.biz.third.api.persistence.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.collection.ListUtil;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.request.BusinessFormStatusReq;
import com.jdec.platform.dataengine.api.dto.request.DynamicSaveReq;
import com.jdec.platform.dataengine.api.dto.response.DynamicSaveResp;
import com.jdec.platform.dataengine.biz.dto.ModuleDataStoreDTO;
import com.jdec.platform.dataengine.biz.dto.SysModuleCombineInfo;
import com.jdec.platform.dataengine.biz.dto.context.ModuleDetailQueryContext;
import com.jdec.platform.dataengine.biz.dto.context.ModuleQueryContext;
import com.jdec.platform.dataengine.biz.enums.ApiResponseCodeEnum;
import com.jdec.platform.dataengine.biz.enums.SaveType;
import com.jdec.platform.dataengine.biz.service.ModuleBizDataService;
import com.jdec.platform.dataengine.biz.service.ModuleMetaDataService;
import com.jdec.platform.dataengine.biz.third.api.persistence.*;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModuleDataStoreServiceImpl implements ModuleDataStoreService {

    private final ModuleMetaDataService moduleMetaDataService;

    private final ModuleBizDataService moduleBizDataService;

    private final ModuleDataPersistenceService moduleDataPersistenceService;

    private final ModuleDataPreCheckService moduleDataPreCheckService;

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public DynamicSaveResp save(DynamicSaveReq req) {

        // 参数为空，顶层模块ID为空，顶层模块结构为空
        if (req == null
                || req.getNestedModuleData() == null
                || req.getNestedModuleData().getModuleId() == null
                || CollectionUtil.isEmpty(req.getNestedModuleData().getRecord())) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_REQUEST.getCode(),
                    ApiResponseCodeEnum.BAD_REQUEST.getMessage());
        }
        // 这不一定是根模块
        Long structureRootModuleId = req.getNestedModuleData().getModuleId();
        Map<Long, SysModuleCombineInfo> moduleCacheMap =
                moduleMetaDataService.getModuleMetaDataMapAsync(
                        ModuleQueryContext.builder()
                                .moduleId(structureRootModuleId)
                                .projectNo(AppContext.getProjectNo())
                                .subjectId(AppContext.getSubjectId())
                                .roleId(UserContext.getRoleId())
                                .isBuildFieldPermission(true)
                                .isBuildFieldPro(true)
                                .build());
        if (CollectionUtil.isEmpty(moduleCacheMap)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getMessage());
        }
        // 获取根模块ID
        Long rootModuleId =
                moduleCacheMap.get(structureRootModuleId).getModuleTree().getRootModuleId();

        SysModuleCombineInfo rootModule = moduleCacheMap.get(rootModuleId);

        Boolean isApproval = moduleMetaDataService.getApprovalStatusByModuleIdAsync(rootModuleId);
        if (isApproval == null) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getMessage());
        }
        SaveType saveType = SaveType.getSaveType(req.getBtnType());
        if (saveType == null) {
            throw new BusinessException(ApiResponseCodeEnum.BAD_REQUEST.getCode(), "保存类型不能为空");
        }
        // todo 如果待保存的数据不是 从根模块的主表数据开始的，需要进行数据转换寻找父模块的数据

        Long structureRootDataId =
                DataPersistenceUtil.getTableRowIdWithTabName(
                        req.getNestedModuleData().getRecord().get(0).getRow(),
                        moduleCacheMap
                                .get(structureRootModuleId)
                                .getModuleInfo()
                                .getPrimaryTable());
        // 待保存的数据
        NestedModuleDataDTO nestedSaveData = req.getNestedModuleData();

        // 是否是全局的插入
        Boolean isOverallInsert = structureRootDataId == null || structureRootDataId <= 0;
        NestedModuleDataDTO detailData = null;
        Long rootModuleMainTableDataId = null;
        // 更新时先获取一遍详情页
        if (!isOverallInsert) {
            // 如果不是从根模块节点 传参数的
            if (!rootModuleId.equals(structureRootModuleId)) {
                rootModuleMainTableDataId =
                        moduleDataPersistenceService.getRootModuleMainTableDataId(
                                structureRootDataId, structureRootModuleId, moduleCacheMap);
            } else {
                rootModuleMainTableDataId = structureRootDataId;
            }
            detailData =
                    moduleBizDataService.getModuleDetailDataAsync(
                            ModuleDetailQueryContext.builder()
                                    .moduleId(rootModuleId)
                                    .dataId(rootModuleMainTableDataId)
                                    .versionNo(null)
                                    .moduleCacheMap(moduleCacheMap)
                                    .projectNo(AppContext.getProjectNo())
                                    .subjectId(AppContext.getSubjectId())
                                    .loginUser(UserContext.getLoginUser())
                                    .build());
        }

        ModuleDataStoreDTO moduleDataStoreDTO = ModuleDataStoreDTO.builder().build();
        switch (saveType) {
            case SAVE -> {
                // 普通保存,
                // todo 校验字段权限是否可编辑可写入
                moduleDataPreCheckService.prePermissionCheck(
                        nestedSaveData, detailData, isOverallInsert, moduleCacheMap);
                // 全局插入
                if (isOverallInsert) {
                    Integer versionNo = 1;
                    // 填充版本号
                    ModuleTreeDataUtils.fillApprovalData(
                            nestedSaveData, versionNo, isApproval, moduleCacheMap);
                    // 从填充的数据中获取版本号和业务编号
                    String businessNo =
                            DataPersistenceUtil.getTableRowBusinessNoWithTabName(
                                    nestedSaveData.getRecord().get(0).getRow(),
                                    rootModule.getModuleInfo().getPrimaryTable());
                    moduleDataStoreDTO.setMainTableBusinessNo(businessNo);
                    moduleDataStoreDTO.setMainTableVersionNo(versionNo);

                } else {
                    // 全局更新,要处理删除数据的情况 实时保存的不需要处理
                    ModuleTreeDataUtils.mergeDeletionsNestedModuleData(
                            nestedSaveData, detailData, moduleCacheMap);
                    // 全局更新数据,先从主表拿出 版本号
                    String businessNo =
                            DataPersistenceUtil.getTableRowBusinessNoWithTabName(
                                    detailData.getRecord().get(0).getRow(),
                                    rootModule.getModuleInfo().getPrimaryTable());
                    Integer versionNo =
                            DataPersistenceUtil.getTableRowVersionWithTabName(
                                    detailData.getRecord().get(0).getRow(),
                                    rootModule.getModuleInfo().getPrimaryTable());
                    // 用根模块的主表版本号 填充插入行的版本号
                    ModuleTreeDataUtils.fillApprovalData(
                            nestedSaveData, versionNo, isApproval, moduleCacheMap);
                    moduleDataStoreDTO.setMainTableBusinessNo(businessNo);
                    moduleDataStoreDTO.setMainTableVersionNo(versionNo);
                }
            }
            case APPROVAL_REALTIME_SAVE -> {
                // 实时新增,修改 // 实时新增 是个全局更新 必须要有 detailData
                // todo 校验字段权限是否可编辑可写入
                moduleDataPreCheckService.prePermissionCheck(
                        nestedSaveData, detailData, isOverallInsert, moduleCacheMap);

                // 全局更新数据,先从主表拿出 版本号
                String businessNo =
                        DataPersistenceUtil.getTableRowBusinessNoWithTabName(
                                detailData.getRecord().get(0).getRow(),
                                rootModule.getModuleInfo().getPrimaryTable());
                Integer versionNo =
                        DataPersistenceUtil.getTableRowVersionWithTabName(
                                detailData.getRecord().get(0).getRow(),
                                rootModule.getModuleInfo().getPrimaryTable());
                // 用根模块的主表版本号 填充插入行的版本号
                ModuleTreeDataUtils.fillApprovalData(
                        nestedSaveData, versionNo, isApproval, moduleCacheMap);
                moduleDataStoreDTO.setMainTableBusinessNo(businessNo);
                moduleDataStoreDTO.setMainTableVersionNo(versionNo);
            }
            case APPROVAL_CHANGE -> {
                // todo 校验字段权限是否可编辑可写入
                moduleDataPreCheckService.prePermissionCheck(
                        nestedSaveData, detailData, isOverallInsert, moduleCacheMap);
                // 变更插入 根模块 主表的业务编号
                String businessNo =
                        DataPersistenceUtil.getTableRowBusinessNoWithTabName(
                                detailData.getRecord().get(0).getRow(),
                                rootModule.getModuleInfo().getPrimaryTable());
                Long sourceId =
                        DataPersistenceUtil.getTableRowIdWithTabName(
                                detailData.getRecord().get(0).getRow(),
                                rootModule.getModuleInfo().getPrimaryTable());
                // 无论如何变更保存，原来的 单据号和ID都要存在
                if (StringUtils.isEmpty(businessNo) || sourceId == null || sourceId <= 0) {
                    throw new BusinessException(
                            ApiResponseCodeEnum.BAD_REQUEST.getCode(), "变更保存时，根模块主表业务编号和ID不能为空");
                }
                // 变更提交原始 版本
                Integer sourceVersionNo =
                        DataPersistenceUtil.getTableRowVersionWithTabName(
                                detailData.getRecord().get(0).getRow(),
                                rootModule.getModuleInfo().getPrimaryTable());
                if (sourceVersionNo == null || sourceVersionNo <= 0) {
                    throw new BusinessException(
                            ApiResponseCodeEnum.BAD_REQUEST.getCode(), "变更保存时，根模块主表源版本号不能为空");
                }

                // 这个单据号是否有在草稿状态和在审批流程中的 单子
                // 99,-99 是终态，其他是非终态
                Boolean exist =
                        moduleDataPersistenceService.existRecordInNoneTerminationState(
                                rootModule.getModuleInfo().getPrimaryTable(),
                                businessNo,
                                ListUtil.toList(99, -99));
                if (exist) {
                    throw new BusinessException(
                            ApiResponseCodeEnum.BAD_REQUEST.getCode(), "变更保存时，根模块主表业务编号已存在在流程的单据");
                }
                // 变更保存都是 全局插入操作
                isOverallInsert = true;
                // 已数据库中的detail数据为基准，合并前端提交过来的数据nestedSaveData
                nestedSaveData =
                        ModuleTreeDataUtils.mergeNestedModuleData(
                                detailData, nestedSaveData, moduleCacheMap);
                // 变更插入 剔除ID 等相关的字段
                ModuleTreeDataUtils.cleanNestedModuleData(nestedSaveData, moduleCacheMap);
                // 获取最大版本号 计算下一个版本号
                Integer maxVersionNo =
                        moduleDataPersistenceService.getMaxVersionNoByBusinessNo(
                                rootModule.getModuleInfo().getPrimaryTable(), businessNo);
                Integer versionNo = maxVersionNo == null ? 0 : maxVersionNo + 1;

                // 填充状态，版本号，sourceId,补充新增行的business_no
                ModuleTreeDataUtils.fillApprovalDataByChange(
                        nestedSaveData, versionNo, sourceId, isApproval, moduleCacheMap);

                moduleDataStoreDTO.setMainTableBusinessNo(businessNo);
                moduleDataStoreDTO.setMainTableVersionNo(versionNo);
                moduleDataStoreDTO.setMainTableSourceVersionNo(sourceVersionNo);
                moduleDataStoreDTO.setMainTableDataSourceId(sourceId);
            }
            case APPROVAL_SAVE -> {

                // 审批通过,审批驳回,审批取消 保存  那详情页中最新的 引用字段值，去刷新一遍数据库
                // 不需要校验字段权限
                if (detailData == null) {
                    throw new BusinessException(
                            ApiResponseCodeEnum.BAD_REQUEST.getCode(), "审批保存时详情页数据不能为空");
                }
                nestedSaveData =
                        ModuleTreeDataUtils.copyExternalReferenceData(detailData, moduleCacheMap);

                String businessNo =
                        DataPersistenceUtil.getTableRowBusinessNoWithTabName(
                                detailData.getRecord().get(0).getRow(),
                                rootModule.getModuleInfo().getPrimaryTable());
                Integer versionNo =
                        DataPersistenceUtil.getTableRowVersionWithTabName(
                                detailData.getRecord().get(0).getRow(),
                                rootModule.getModuleInfo().getPrimaryTable());
                moduleDataStoreDTO.setMainTableBusinessNo(businessNo);
                moduleDataStoreDTO.setMainTableVersionNo(versionNo);
            }
        }

        moduleDataStoreDTO.setParamData(nestedSaveData);
        moduleDataStoreDTO.setDetailData(detailData);
        moduleDataStoreDTO.setSaveType(saveType);
        moduleDataStoreDTO.setIsApproval(isApproval);
        moduleDataStoreDTO.setModuleCacheMap(moduleCacheMap);
        moduleDataStoreDTO.setIsOverallInsert(isOverallInsert);
        moduleDataStoreDTO.setRootModuleId(rootModuleId);
        moduleDataStoreDTO.setStructureRootModuleId(structureRootModuleId);

        Long dataId = moduleDataPersistenceService.save(moduleDataStoreDTO);

        DynamicSaveResp resp = new DynamicSaveResp();
        if (rootModuleMainTableDataId == null) {
            resp.setDataId(dataId);
        } else {
            resp.setDataId(rootModuleMainTableDataId);
        }
        if (saveType == SaveType.APPROVAL_REALTIME_SAVE) {
            Long data =
                    ModuleTreeDataUtils.getLeafRowDataId(
                            moduleDataStoreDTO.getParamData(), moduleCacheMap);
            resp.setChildDataId(data);
        }
        resp.setModuleId(rootModuleId);
        resp.setBusinessNo(moduleDataStoreDTO.getMainTableBusinessNo());
        resp.setVersionNo(moduleDataStoreDTO.getMainTableVersionNo());
        resp.setData(moduleDataStoreDTO.getParamData());
        return resp;
    }

    @Override
    public Integer modifyBusinessFormStatus(BusinessFormStatusReq req) {
        if (req == null
                || req.getModuleId() == null
                || StringUtils.isEmpty(req.getBusinessNo())
                || req.getOriginalStatus() == null
                || req.getTargetStatus() == null) {
            throw new BusinessException(ApiResponseCodeEnum.BAD_REQUEST.getCode(), "参数不能为空");
        }
        Map<Long, SysModuleCombineInfo> moduleCacheMap =
                moduleMetaDataService.getModuleMetaDataMapAsync(
                        ModuleQueryContext.builder()
                                .moduleId(req.getModuleId())
                                .projectNo(AppContext.getProjectNo())
                                .subjectId(AppContext.getSubjectId())
                                .roleId(UserContext.getRoleId())
                                .build());
        String rootMainTable =
                moduleCacheMap
                        .get(req.getModuleId())
                        .getModuleTree()
                        .getRootModule()
                        .getPrimaryTable();

        return moduleDataPersistenceService.modifyBusinessFormStatus(
                rootMainTable, req.getBusinessNo(), req.getOriginalStatus(), req.getTargetStatus());
    }
}
