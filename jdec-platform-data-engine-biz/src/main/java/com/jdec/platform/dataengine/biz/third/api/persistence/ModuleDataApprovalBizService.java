package com.jdec.platform.dataengine.biz.third.api.persistence;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.dataengine.api.dto.model.*;
import com.jdec.platform.dataengine.api.dto.request.ApprovalStatusCallBackReq;
import com.jdec.platform.dataengine.api.dto.request.DynamicDetailQueryReq;
import com.jdec.platform.dataengine.api.dto.request.DynamicSaveReq;
import com.jdec.platform.dataengine.api.dto.response.ApprovalProcessResultResp;
import com.jdec.platform.dataengine.biz.config.service.DataModuleConfigService;
import com.jdec.platform.dataengine.biz.constants.CommonConstant;
import com.jdec.platform.dataengine.biz.entity.ModuleDataFieldChangeContent;
import com.jdec.platform.dataengine.biz.entity.ModuleDataSnapshotContent;
import com.jdec.platform.dataengine.biz.enums.ApiResponseCodeEnum;
import com.jdec.platform.dataengine.biz.service.DynamicQueryService;
import com.jdec.platform.dataengine.biz.service.IModuleDataFieldChangeContentService;
import com.jdec.platform.dataengine.biz.service.IModuleDataSnapshotContentService;
import com.jdec.platform.dataengine.biz.util.JsonUtil;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModuleDataApprovalBizService {

    private final DataModuleConfigService moduleConfigService;

    private final ModuleDataPersistenceService moduleDataPersistenceService;

    private final DynamicQueryService dynamicQueryService;

    private final IModuleDataSnapshotContentService iModuleDataSnapshotContentService;

    private final IModuleDataFieldChangeContentService iModuleDataFieldChangeContentService;

    public ApprovalProcessResultResp processApprovalStatus(ApprovalStatusCallBackReq req) {

        SysModuleInfo rootModule =
                moduleConfigService.getModuleCompleteById(
                        AppContext.getProjectNo(), AppContext.getSubjectId(), req.getModuleId());
        if (rootModule == null) {
            log.error("模块id={}不存在", req.getModuleId());
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getMessage());
        }
        Map<String, Object> row = new HashMap<>();
        row.put(
                DataPersistenceUtil.getFullFieldName(
                        rootModule.getPrimaryTable(),
                        CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD),
                req.getDataId());
        row.put(
                DataPersistenceUtil.getFullFieldName(
                        rootModule.getPrimaryTable(),
                        CommonConstant.TABLE_DEFAULT_BUSINESS_STATUS_FIELD),
                req.getBusinessStatus());

        RecordDataDTO recordDataDTO = new RecordDataDTO();
        recordDataDTO.setRow(row);
        List<RecordDataDTO> recordDataDTOList = new ArrayList<>();
        recordDataDTOList.add(recordDataDTO);
        NestedModuleDataDTO nestedModuleDataDTO = new NestedModuleDataDTO();
        nestedModuleDataDTO.setModuleId(rootModule.getModuleId());
        nestedModuleDataDTO.setRecord(recordDataDTOList);

        DynamicSaveReq saveReq = new DynamicSaveReq();
        saveReq.setNestedModuleData(nestedModuleDataDTO);
        // 更更新 业务数据的 审批状态
        Long rootModuleMainTableId = moduleDataPersistenceService.save(saveReq);

        // 查询 根模块及其子模的 信息
        Set<Long> filterField = new HashSet<>();
        rootModule.getFieldList().stream().map(x -> x.getId()).forEach(filterField::add);
        for (ModuleNodeDTO moduleNode : rootModule.getModuleNodes()) {
            SysModuleInfo sysModuleInfo =
                    moduleConfigService.getModuleCompleteById(
                            AppContext.getProjectNo(),
                            AppContext.getSubjectId(),
                            moduleNode.getId());
            sysModuleInfo.getFieldList().stream().map(x -> x.getId()).forEach(filterField::add);
        }
        // 终态需要切 快照保存
        NestedModuleDataDTO result = null;
        DynamicDetailQueryReq queryReq = new DynamicDetailQueryReq();
        queryReq.setModuleId(rootModule.getModuleId());
        queryReq.setId(req.getDataId());
        queryReq.setVersion(req.getVersionNo());
        queryReq.setFilterFields(new ArrayList<>(filterField));
        queryReq.setShowVersionCompare(false);
        if (req.getIsTermination() != null && req.getIsTermination()) {

            List<NestedModuleDataDTO> nestedModuleDataDTOList =
                    dynamicQueryService.getDetail(queryReq, null);

            result = nestedModuleDataDTOList.get(0);
            Object versionNo =
                    DataPersistenceUtil.getTableRowFieldValue(
                            nestedModuleDataDTOList.get(0).getRecord().get(0).getRow(),
                            DataPersistenceUtil.getFullFieldName(
                                    rootModule.getPrimaryTable(),
                                    CommonConstant.TABLE_DEFAULT_VERSION_NO_FIELD));
            Object businessStatus =
                    DataPersistenceUtil.getTableRowFieldValue(
                            nestedModuleDataDTOList.get(0).getRecord().get(0).getRow(),
                            DataPersistenceUtil.getFullFieldName(
                                    rootModule.getPrimaryTable(),
                                    CommonConstant.TABLE_DEFAULT_BUSINESS_STATUS_FIELD));

            // todo 这里面保存快照时需要构建 版本间的 对比
            ModuleDataSnapshotContent moduleDataSnapshotContent = new ModuleDataSnapshotContent();
            try {
                moduleDataSnapshotContent.setRootModuleId(rootModule.getModuleId());
                moduleDataSnapshotContent.setRootTableDataId(req.getDataId());
                moduleDataSnapshotContent.setRootTableDataVersion(
                        (Integer) versionNo); // Changed from
                // moduleDataSnapshotContent.setRootTableDataVersion((Integer) versionNo);
                moduleDataSnapshotContent.setBusinessStatus((Integer) businessStatus);
                moduleDataSnapshotContent.setContent(
                        JsonUtil.obj2Json(nestedModuleDataDTOList.get(0)));
                moduleDataSnapshotContent.setComparison(null); // todo
                moduleDataSnapshotContent.setCreateDate(LocalDateTime.now());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            iModuleDataSnapshotContentService.save(moduleDataSnapshotContent);
        } else {
            List<NestedModuleDataDTO> nestedModuleDataDTOList =
                    dynamicQueryService.getDetail(queryReq, null);
            result = nestedModuleDataDTOList.get(0);
        }

        LambdaQueryWrapper<ModuleDataFieldChangeContent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModuleDataFieldChangeContent::getRootModuleId, rootModule.getModuleId());
        wrapper.eq(ModuleDataFieldChangeContent::getRootTableDataId, req.getDataId());
        wrapper.eq(ModuleDataFieldChangeContent::getRootModuleId, req.getVersionNo());

        List<ModuleDataFieldChangeContent> changeContentList =
                iModuleDataFieldChangeContentService.queryList(wrapper);

        // todo  result   要构建 版本间的字段 对比 返回给审批链
        ApprovalProcessResultResp resp =
                ApprovalProcessResultResp.builder().nestedModuleData(result).build();

        return resp;
    }

    private List<ModuleChangeDTO> buildModuleChangeDTO(
            NestedModuleDataDTO nestedModuleDataDTO, Map<Long, SysModuleInfo> moduleCacheMap) {

        Long moduleId = nestedModuleDataDTO.getModuleId();
        SysModuleInfo sysModule = null;
        if (moduleCacheMap.containsKey(moduleId)) {
            sysModule = moduleCacheMap.get(moduleId);
        } else {
            sysModule =
                    moduleConfigService.getModuleCompleteById(
                            AppContext.getProjectNo(), AppContext.getSubjectId(), moduleId);
        }
        List<ModuleChangeDTO> result = new ArrayList<>();
        ModuleChangeDTO moduleChangeDTO = ModuleChangeDTO.builder().build();
        moduleChangeDTO.setMId(sysModule.getModuleId());
        moduleChangeDTO.setName(sysModule.getModuleName());

        if (CollectionUtil.isEmpty(nestedModuleDataDTO.getRecord())) {
            result.add(moduleChangeDTO);
            return result;
        }

        for (RecordDataDTO recordDataDTO : nestedModuleDataDTO.getRecord()) {

            // 这一行没有变更日志
            if (CollectionUtil.isEmpty(recordDataDTO.getRowChanges())) {

                ActionChangeDTO Action = new ActionChangeDTO();

                RowChangeDTO r = new RowChangeDTO();

                if (CollectionUtil.isEmpty(recordDataDTO.getChildren())) {
                    r.setChildren(new ArrayList<>());
                    List<RowChangeDTO> child = new ArrayList<>();
                    child.add(r);
                    Action.setU(child);
                    moduleChangeDTO.setActions(Action);
                    result.add(moduleChangeDTO);
                    return result;
                }
            }

            RowChangeDTO rowChangeDTO = recordDataDTO.getRowChanges().get(0);
        }
        result.add(moduleChangeDTO);

        return result;

        ////
        // moduleChangeDTO.setActions(buildActionChangeDTO(nestedModuleDataDTO,sysModule,moduleCacheMap));

    }
}
