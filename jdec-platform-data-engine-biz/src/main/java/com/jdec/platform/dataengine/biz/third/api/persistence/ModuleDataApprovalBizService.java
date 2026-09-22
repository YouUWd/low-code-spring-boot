package com.jdec.platform.dataengine.biz.third.api.persistence;

import com.jdec.platform.dataengine.api.dto.model.ModuleNodeDTO;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.dataengine.api.dto.model.SysModuleInfo;
import com.jdec.platform.dataengine.api.dto.request.ApprovalStatusCallBackReq;
import com.jdec.platform.dataengine.api.dto.request.DynamicDetailQueryReq;
import com.jdec.platform.dataengine.api.dto.request.DynamicSaveReq;
import com.jdec.platform.dataengine.biz.config.service.DataModuleConfigService;
import com.jdec.platform.dataengine.biz.constants.CommonConstant;
import com.jdec.platform.dataengine.biz.entity.ModuleDataSnapshotContent;
import com.jdec.platform.dataengine.biz.enums.ApiResponseCodeEnum;
import com.jdec.platform.dataengine.biz.service.DynamicQueryService;
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

    public NestedModuleDataDTO processApprovalStatus(ApprovalStatusCallBackReq req) {

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
        // todo  result   要构建 版本间的字段 对比 返回给审批链
        return result;
    }
}
