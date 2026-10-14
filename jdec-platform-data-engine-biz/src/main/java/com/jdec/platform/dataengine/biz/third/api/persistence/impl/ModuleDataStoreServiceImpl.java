package com.jdec.platform.dataengine.biz.third.api.persistence.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
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
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModuleDataStoreServiceImpl implements ModuleDataStoreService {

    private final ModuleMetaDataService moduleMetaDataService;

    private final ModuleBizDataService moduleBizDataService;

    private final ModuleDataPersistenceService moduleDataPersistenceService;

    private final ModuleDataPreStoreService moduleDataPreStoreService;

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

        Long structureRootDataId =
                DataPersistenceUtil.getTableRowIdWithTabName(
                        req.getNestedModuleData().getRecord().get(0).getRow(),
                        moduleCacheMap
                                .get(structureRootModuleId)
                                .getModuleInfo()
                                .getPrimaryTable());

        Boolean isOverallInsert = structureRootDataId == null || structureRootDataId <= 0;
        NestedModuleDataDTO detailData = null;
        if (!isOverallInsert) {
            detailData =
                    moduleBizDataService.getModuleDetailDataAsync(
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

        ModuleDataStoreDTO moduleDataStoreDTO = ModuleDataStoreDTO.builder().build();
        NestedModuleDataDTO nestedStoreData = req.getNestedModuleData();
        ;
        // 非变更保存
        if (saveType != SaveType.APPROVAL_CHANGE) {
            if (isOverallInsert) {
                Integer versionNo = 1;
                // 填充版本号
                ModuleTreeDataUtils.fillApprovalData(
                        nestedStoreData, versionNo, isApproval, moduleCacheMap);
                // 从填充的数据中获取版本号
                String businessNo =
                        DataPersistenceUtil.getTableRowBusinessNoWithTabName(
                                nestedStoreData.getRecord().get(0).getRow(),
                                rootModule.getModuleInfo().getPrimaryTable());
                moduleDataStoreDTO.setMainTableBusinessNo(businessNo);
                moduleDataStoreDTO.setMainTableVersionNo(versionNo);

            } else {
                // 全局更新数据,先从主表拿出 版本号
                String businessNo =
                        DataPersistenceUtil.getTableRowBusinessNoWithTabName(
                                detailData.getRecord().get(0).getRow(),
                                rootModule.getModuleInfo().getPrimaryTable());
                Integer versionNo =
                        DataPersistenceUtil.getTableRowVersionWithTabName(
                                detailData.getRecord().get(0).getRow(),
                                rootModule.getModuleInfo().getPrimaryTable());
                // 填充插入行的版本号
                ModuleTreeDataUtils.fillApprovalData(
                        req.getNestedModuleData(), versionNo, isApproval, moduleCacheMap);
                moduleDataStoreDTO.setMainTableBusinessNo(businessNo);
                moduleDataStoreDTO.setMainTableVersionNo(versionNo);
            }

        } else {
            //            ModuleTreeNodeDataDTO detailData =
            //
            // ModuleTreeDataUtils.copyToModuleTreeNodeData(dataStoreDTO.getDetailData());
            //            ModuleTreeNodeDataDTO paramData =
            //
            // ModuleTreeDataUtils.copyToModuleTreeNodeData(dataStoreDTO.getParamData());
            //            moduleTreeNodeDataDTO =
            //                    ModuleTreeDataUtils.mergeModuleTreeNodeData(detailData,
            // paramData);
        }
        moduleDataStoreDTO.setParamData(req.getNestedModuleData());
        moduleDataStoreDTO.setDetailData(detailData);
        moduleDataStoreDTO.setSaveType(saveType);
        moduleDataStoreDTO.setIsApproval(isApproval);
        moduleDataStoreDTO.setModuleCacheMap(moduleCacheMap);
        moduleDataStoreDTO.setIsOverallInsert(isOverallInsert);
        moduleDataStoreDTO.setRootModuleId(rootModuleId);

        Long dataId = moduleDataPersistenceService.save(moduleDataStoreDTO);

        DynamicSaveResp resp = new DynamicSaveResp();
        resp.setDataId(dataId);
        resp.setModuleId(rootModuleId);
        resp.setBusinessNo(moduleDataStoreDTO.getMainTableBusinessNo());
        resp.setVersionNo(moduleDataStoreDTO.getMainTableVersionNo());
        resp.setData(moduleDataStoreDTO.getParamData());
        return resp;
    }
}
