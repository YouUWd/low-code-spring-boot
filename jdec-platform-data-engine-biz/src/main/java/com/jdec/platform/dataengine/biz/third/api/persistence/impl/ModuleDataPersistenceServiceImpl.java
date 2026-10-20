package com.jdec.platform.dataengine.biz.third.api.persistence.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.dataengine.api.dto.model.*;
import com.jdec.platform.dataengine.api.dto.request.DynamicDeleteReq;
import com.jdec.platform.dataengine.api.dto.request.DynamicInsertReq;
import com.jdec.platform.dataengine.api.dto.request.DynamicSaveReq;
import com.jdec.platform.dataengine.biz.constants.CommonConstant;
import com.jdec.platform.dataengine.biz.context.ModuleDataSaveContextHolder;
import com.jdec.platform.dataengine.biz.dto.DataSaveResult;
import com.jdec.platform.dataengine.biz.dto.ModuleDataStoreDTO;
import com.jdec.platform.dataengine.biz.dto.SysModuleCombineInfo;
import com.jdec.platform.dataengine.biz.dto.context.DynamicSaveReqContext;
import com.jdec.platform.dataengine.biz.dto.context.RecordSaveContext;
import com.jdec.platform.dataengine.biz.enums.ApiResponseCodeEnum;
import com.jdec.platform.dataengine.biz.enums.RelationType;
import com.jdec.platform.dataengine.biz.enums.SaveButtonType;
import com.jdec.platform.dataengine.biz.enums.SaveType;
import com.jdec.platform.dataengine.biz.third.api.persistence.DataPersistenceUtil;
import com.jdec.platform.dataengine.biz.third.api.persistence.ModuleDataPersistenceService;
import com.jdec.platform.dataengine.biz.third.api.persistence.tab.BaseSingleTablePersistenceService;
import com.jdec.platform.dataengine.biz.util.JsonUtil;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.datasource.JooqDataContextFactory;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModuleDataPersistenceServiceImpl implements ModuleDataPersistenceService {
    private final JooqDataContextFactory jooqContextFactory;

    private final BaseSingleTablePersistenceService baseSingleTablePersistenceService;

    public Long save(ModuleDataStoreDTO req) {

        boolean isRelatedApproval = req.getIsApproval();
        // 模块是否关联审批链 保存类型
        SaveType finalSaveType = req.getSaveType();
        Map<Long, SysModuleCombineInfo> moduleCacheMap = req.getModuleCacheMap();
        NestedModuleDataDTO nestedStoreData = req.getParamData();

        // 配置上的根模块ID
        Long rootModuleId = req.getRootModuleId();

        // 保存参数上 结构上的 根模块ID
        Long structureRootModuleId = req.getStructureRootModuleId();

        AtomicReference<Long> rootModuleMainTableDataId = new AtomicReference<>();
        Long actionId = null;
        try {
            ModuleDataSaveContextHolder.openDataChangeFlag(finalSaveType);
            actionId = ModuleDataSaveContextHolder.getContextActionId();
            DSLContext txDsl = jooqContextFactory.getContext();
            try {

                Queue<DynamicSaveReqContext> saveReqQueue = new LinkedList<>();

                // 根模块数据 没有父模块和父模块主表ID
                saveReqQueue.add(
                        DynamicSaveReqContext.builder()
                                .nestedModuleData(nestedStoreData)
                                .parentModuleId(null)
                                .parentModuleMainTableDataId(null)
                                .rootModuleId(rootModuleId) // 第一层的 根模块就是自己的模块ID
                                .structureRootModuleId(structureRootModuleId)
                                .rootModuleMainTableDataId(null)
                                .rootModuleMainTableDataVersion(null)
                                .txDsl(txDsl)
                                .saveType(finalSaveType)
                                .isApproval(isRelatedApproval)
                                .build());
                boolean first = true;

                while (!saveReqQueue.isEmpty()) {
                    DynamicSaveReqContext currentReq = saveReqQueue.poll();

                    // 模块数据为空
                    if (currentReq == null
                            || currentReq.getNestedModuleData() == null
                            || CollectionUtil.isEmpty(
                                    currentReq.getNestedModuleData().getRecord())) {
                        continue;
                    }

                    // 保存当前 模块数据 第一次进来  NestedModuleDataDTO.record 只有一条数据
                    for (RecordDataDTO recordDataDTO :
                            currentReq.getNestedModuleData().getRecord()) {

                        // 保存 当前模块一行数据 主表数据只有一行
                        Long resultId = saveModuleData(recordDataDTO, currentReq, moduleCacheMap);

                        // 捕获根模块的 主表记录ID
                        if (first) {
                            rootModuleMainTableDataId.set(resultId);
                        }

                        // 如果当前模块有子模块的数据
                        if ((resultId != null && resultId > 0)
                                && CollectionUtil.isNotEmpty(recordDataDTO.getChildren())) {
                            recordDataDTO.getChildren().stream()
                                    .forEach(
                                            subModule -> {
                                                // 构造保存子模的数据的上下文
                                                saveReqQueue.add(
                                                        DynamicSaveReqContext.builder()
                                                                .nestedModuleData(subModule)
                                                                .parentModuleId(
                                                                        currentReq
                                                                                .getNestedModuleData()
                                                                                .getModuleId())
                                                                .parentModuleMainTableDataId(
                                                                        resultId)
                                                                .rootModuleId(rootModuleId)
                                                                .rootModuleMainTableDataId(
                                                                        rootModuleMainTableDataId
                                                                                .get())
                                                                .txDsl(txDsl)
                                                                .saveType(finalSaveType)
                                                                .build());
                                            });
                        }
                        first = false;
                    }
                }
                // 变更保存 把上根表 上一个版本的数据 标记非活跃
                if (SaveType.APPROVAL_CHANGE.equals(finalSaveType)) {
                    baseSingleTablePersistenceService.inactiveRecordById(
                            txDsl,
                            moduleCacheMap
                                    .get(req.getRootModuleId())
                                    .getModuleInfo()
                                    .getPrimaryTable(),
                            req.getMainTableDataSourceId());
                }
                // 其他sql
            } catch (Exception e) {
                // 抛出异常，jooq自动回滚
                throw new RuntimeException(e);
            }

        } catch (Exception e) {
            throw e;
        } finally {
            ModuleDataSaveContextHolder.clear();
        }
        try {
            log.info("=======" + "\n");
            log.info("=======" + JsonUtil.obj2Json(req.getParamData()));
        } catch (Exception e) {
        }

        return rootModuleMainTableDataId.get();
    }

    @Override
    public Long insertAndGetId(DynamicInsertReq req) {
        DynamicSaveReq saveReq = new DynamicSaveReq();
        saveReq.setBtnType(SaveButtonType.INSERT_AND_GET_ID.getKey());
        NestedModuleDataDTO nestedModuleDataDTO = new NestedModuleDataDTO();
        nestedModuleDataDTO.setModuleId(req.getModuleId());
        RecordDataDTO recordDataDTO = new RecordDataDTO();
        recordDataDTO.setRow(req.getRow());
        nestedModuleDataDTO.setRecord(Collections.singletonList(recordDataDTO));
        saveReq.setNestedModuleData(nestedModuleDataDTO);
        // return save(saveReq);
        return 0L;
    }

    @Override
    public Long deleteById(DynamicDeleteReq req) {
        Set<String> tableNames = DataPersistenceUtil.getTableNameFromWideRow(req.getRow());
        if (CollectionUtil.isEmpty(tableNames)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_REQUEST.getCode(),
                    ApiResponseCodeEnum.BAD_REQUEST.getMessage());
        }
        for (String table : tableNames) {
            DataPersistenceUtil.putTableRowDeletedField(
                    req.getRow(),
                    DataPersistenceUtil.getFullFieldName(
                            table, CommonConstant.TABLE_DEFAULT_DELETED_FIELD));
        }
        DynamicSaveReq saveReq = new DynamicSaveReq();
        saveReq.setBtnType(SaveButtonType.DELETE_BY_ID.getKey());
        NestedModuleDataDTO nestedModuleDataDTO = new NestedModuleDataDTO();
        nestedModuleDataDTO.setModuleId(req.getModuleId());
        RecordDataDTO recordDataDTO = new RecordDataDTO();
        recordDataDTO.setRow(req.getRow());
        nestedModuleDataDTO.setRecord(Collections.singletonList(recordDataDTO));
        saveReq.setNestedModuleData(nestedModuleDataDTO);
        // return save(saveReq);
        return 0L;
    }

    @Override
    public Integer getMaxVersionNoByBusinessNo(String tableName, String businessNo) {
        return baseSingleTablePersistenceService.getMaxVersionNoByBusinessNo(
                null, tableName, businessNo);
    }

    @Override
    public Long getRootModuleMainTableDataId(
            Long id, Long currentModuleId, Map<Long, SysModuleCombineInfo> moduleCacheMap) {
        if (id == null || id <= 0) {
            return null;
        }
        Long parentId = moduleCacheMap.get(currentModuleId).getModuleInfo().getParentId();
        // 根模块直接返回
        if (parentId == null || parentId <= 0) {
            return id;
        }
        // 不是根模块
        Long tempId = id;
        Long tempModuleId = currentModuleId;
        while (tempModuleId != null && tempModuleId > 0) {
            // 虚拟模块直接 往上找
            if (StringUtils.isEmpty(
                    moduleCacheMap.get(tempModuleId).getModuleInfo().getPrimaryTable())) {
                tempId = tempId;
                tempModuleId = moduleCacheMap.get(tempModuleId).getModuleInfo().getParentId();
                continue;
            }
            Map<String, Object> recordMap =
                    baseSingleTablePersistenceService.getRecordById(
                            null,
                            moduleCacheMap.get(tempModuleId).getModuleInfo().getPrimaryTable(),
                            id);

            Object fk =
                    recordMap.get(
                            moduleCacheMap
                                    .get(tempModuleId)
                                    .getMainTableRelationDTO()
                                    .getJoinField());

            tempId = fk instanceof Number ? ((Number) fk).longValue() : null;
            tempModuleId = moduleCacheMap.get(tempModuleId).getModuleInfo().getParentId();
        }
        return tempId;
    }

    @Override
    public Boolean existRecordInNoneTerminationState(
            String tableName, String businessNo, List<Integer> statusList) {
        return baseSingleTablePersistenceService.existRecordInNoneTerminationState(
                        null, tableName, businessNo, statusList)
                != 0;
    }

    private Long saveModuleData(
            RecordDataDTO recordDataDTO,
            DynamicSaveReqContext currentReq,
            Map<Long, SysModuleCombineInfo> moduleCacheMap) {

        // 获取当前要保存模块元数据,从数据上拿
        SysModuleCombineInfo sysModuleCompleteResp =
                moduleCacheMap.get(currentReq.getNestedModuleData().getModuleId());
        //
        if (sysModuleCompleteResp == null) {
            log.error("模块id={}不存在", currentReq.getNestedModuleData().getModuleId());
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getMessage());
        }
        // 虚拟模块
        if (StringUtils.isEmpty(sysModuleCompleteResp.getModuleInfo().getPrimaryTable())) {
            // 虚拟模块数据不用保存 直接返回父模块对应的数据ID
            return currentReq.getParentModuleMainTableDataId();
        }

        // 模块数据
        if (CollectionUtil.isEmpty(recordDataDTO.getRow())) {
            log.error(
                    "模块id={},name={}记录数据为空",
                    sysModuleCompleteResp.getModuleInfo().getModuleId(),
                    sysModuleCompleteResp.getModuleInfo().getModuleName());
            return 0L;
        }
        // 首先 写入当前 模块 主表的数据,主表数据 只有一行
        DataSaveResult primaryDataSaveResult =
                saveModulePrimaryTableData(
                        recordDataDTO, sysModuleCompleteResp, currentReq, moduleCacheMap);
        Long primaryTableId = primaryDataSaveResult.getDataId();

        if (primaryTableId == null || primaryTableId.equals(0L)) {
            return primaryTableId;
        }

        // 在写入当前模块 关联的表数据
        List<DataSaveResult> relatedDataSaveResult =
                saveModuleRelatedTableData(
                        recordDataDTO,
                        sysModuleCompleteResp,
                        primaryTableId,
                        currentReq,
                        moduleCacheMap);
        return primaryTableId;
    }

    /**
     * 写入 模块主表数据
     *
     * @param recordDataDTO 行记录
     * @param sysModuleCompleteResp 模块定义
     * @return
     */
    private DataSaveResult saveModulePrimaryTableData(
            RecordDataDTO recordDataDTO,
            SysModuleCombineInfo sysModuleCompleteResp,
            DynamicSaveReqContext currentReq,
            Map<Long, SysModuleCombineInfo> moduleCacheMap) {

        Map<String, Object> parentLevelTableAssociationMap = new HashMap<>();
        boolean isRootModule = false;
        boolean isTreeRootModule = false;
        com.jdec.platform.config.api.dto.common.TableRelationDTO relationDTO = null;

        // 如果没有父模块ID 说明时是模块定义上时根模块
        if (sysModuleCompleteResp.getModuleInfo().getParentId() == null
                || Long.valueOf(0L).equals(sysModuleCompleteResp.getModuleInfo().getParentId())) {
            isRootModule = true;
        }

        // 首先获取父模块主表 与当前模块的主表 之间的关联关系，一般来说 父模块主表的主键字段 关联子模块的主表某个字段
        // currentReq.getParentModuleId() ==null 说明 在结构上是个根模块,不需要设置 父模块的 主表的ID 为外键
        if (currentReq.getParentModuleId() == null) {
            // 没有父模块，说明是根模块
            isTreeRootModule = true;
        } else {
            relationDTO = sysModuleCompleteResp.getMainTableRelationDTO();
            parentLevelTableAssociationMap.put(
                    relationDTO.getJoinField(), currentReq.getParentModuleMainTableDataId());
        }

        Map<String, Object> dataMap = recordDataDTO.getRow();

        if (CollectionUtil.isEmpty(dataMap)) {
            return DataSaveResult.builder().dataId(0L).build();
        }

        // 主表数据 // 从 wide row 中 过滤出 info 表的字段
        Map<String, Object> primaryTableRowMap =
                DataPersistenceUtil.getTableRowMapFromWideRow(
                        dataMap, sysModuleCompleteResp.getModuleInfo().getPrimaryTable());

        if (CollectionUtil.isEmpty(primaryTableRowMap)) {
            return DataSaveResult.builder().dataId(0L).build();
        }

        Long primaryTableId = DataPersistenceUtil.getTableRowId(primaryTableRowMap);

        RecordSaveContext recordSaveContext =
                RecordSaveContext.builder()
                        .dsl(currentReq.getTxDsl())
                        .tableName(sysModuleCompleteResp.getModuleInfo().getPrimaryTable())
                        .parentLevelTable(null)
                        .record(primaryTableRowMap)
                        .bizDataMap(null)
                        .parentLevelTableAssociationDataMap(parentLevelTableAssociationMap)
                        .parentLevelTableId(currentReq.getParentModuleMainTableDataId())
                        .defaultUserContextDataMap(null)
                        .isInsert(primaryTableId == null || primaryTableId <= 0)
                        .isRootModuleMainTable(isRootModule ? true : false)
                        .isTreeRoot(isTreeRootModule)
                        .rootModuleId(currentReq.getRootModuleId())
                        .rootModuleMainTableDataId(currentReq.getRootModuleMainTableDataId())
                        .sysModuleCompleteResp(sysModuleCompleteResp)
                        .moduleCacheMap(moduleCacheMap)
                        .userId(UserContext.getUserId())
                        .subjectId(AppContext.getSubjectId())
                        .saveType(currentReq.getSaveType())
                        .isApproval(currentReq.isApproval())
                        .build();
        // 首先 写入当前 模块 主表的数据
        DataSaveResult dataSaveResult =
                baseSingleTablePersistenceService.saveRecord(recordSaveContext);
        primaryTableId = dataSaveResult.getDataId();

        if (recordSaveContext.getIsInsert()) {
            DataPersistenceUtil.fillTableRowFieldInWideRow(
                    dataMap,
                    sysModuleCompleteResp.getModuleInfo().getPrimaryTable(),
                    CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD,
                    primaryTableId);
            if (!isTreeRootModule) {
                DataPersistenceUtil.fillTableRowFieldInWideRow(
                        dataMap,
                        sysModuleCompleteResp.getModuleInfo().getPrimaryTable(),
                        relationDTO.getJoinField(),
                        currentReq.getParentModuleMainTableDataId());
            }
        } else {
            if (!isTreeRootModule
                    && !DataPersistenceUtil.isExistFiledInInWideRow(
                            dataMap,
                            sysModuleCompleteResp.getModuleInfo().getPrimaryTable(),
                            relationDTO.getJoinField())) {
                DataPersistenceUtil.fillTableRowFieldInWideRow(
                        dataMap,
                        sysModuleCompleteResp.getModuleInfo().getPrimaryTable(),
                        relationDTO.getJoinField(),
                        currentReq.getParentModuleMainTableDataId());
            }
        }
        //        if (dataSaveResult.getRowChange() != null) {
        //            if (recordDataDTO.getRowChanges() == null) {
        //                recordDataDTO.setRowChanges(new ArrayList<>());
        //                recordDataDTO.getRowChanges().add(dataSaveResult.getRowChange());
        //            } else {
        //                recordDataDTO.getRowChanges().add(dataSaveResult.getRowChange());
        //            }
        //        }

        if (primaryTableId == null) {
            log.error(
                    "模块id={},name={} table={}记录数据写入失败",
                    sysModuleCompleteResp.getModuleInfo().getModuleId(),
                    sysModuleCompleteResp.getModuleInfo().getModuleName(),
                    sysModuleCompleteResp.getModuleInfo().getPrimaryTable());
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_SAVE_DATA_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_SAVE_DATA_ERROR.getMessage());
        }
        return dataSaveResult;
    }

    /**
     * 写入模块 关联表 数据
     *
     * @param recordDataDTO 主表的行记录
     * @param sysModuleCompleteResp
     * @param modulePrimaryTableId 主表行记录id
     * @return
     */
    private List<DataSaveResult> saveModuleRelatedTableData(
            RecordDataDTO recordDataDTO,
            SysModuleCombineInfo sysModuleCompleteResp,
            Long modulePrimaryTableId,
            DynamicSaveReqContext currentReq,
            Map<Long, SysModuleCombineInfo> moduleCacheMap) {
        Set<String> tables = sysModuleCompleteResp.getSubTables();
        List<DataSaveResult> dataSaveResultList = new ArrayList<>();
        if (CollectionUtil.isEmpty(tables)) {
            dataSaveResultList.add(DataSaveResult.builder().dataId(0L).build());
            return dataSaveResultList;
        }

        for (String moduleTable : tables) {
            // 主表与当前表的关联关系
            com.jdec.platform.config.api.dto.common.TableRelationDTO relationDTO =
                    sysModuleCompleteResp.getSubTableRelationMap().get(moduleTable);

            RelationType relationType = RelationType.getRelationType(relationDTO.getRelationType());
            switch (relationType) {
                case ONE_TO_ONE, MANY_TO_ONE -> {
                    // 1:1
                    //// 首先 写入子表数据
                    DataSaveResult dataSaveResult =
                            saveModuleRelatedTableRecordData(
                                    recordDataDTO,
                                    sysModuleCompleteResp,
                                    relationDTO,
                                    modulePrimaryTableId,
                                    currentReq.getTxDsl(),
                                    currentReq);
                    dataSaveResultList.add(dataSaveResult);
                    //                    if (dataSaveResult.getRowChange() != null) {
                    //                        if (recordDataDTO.getRowChanges() == null) {
                    //                            recordDataDTO.setRowChanges(new ArrayList<>());
                    //
                    // recordDataDTO.getRowChanges().add(dataSaveResult.getRowChange());
                    //                        } else {
                    //
                    // recordDataDTO.getRowChanges().add(dataSaveResult.getRowChange());
                    //                        }
                    //                    }
                }
                case ONE_TO_MANY -> {
                    // 1:N
                    // 子表数据 是一个 list
                    //                    List<Map<String, Object>> subTableRecordListMap =
                    //
                    // DataPersistenceUtil.getTableRecordListFromCombinedMap(
                    //                                    dataMap, moduleTable);
                    //
                    //                    for (Map<String, Object> recordMap :
                    // subTableRecordListMap) {
                    //                        // 首先 写入子表数据
                    //                        Map<String, Object> tableRecord =
                    //                                (Map<String, Object>) recordMap.get("data");
                    //                        DataSaveResult dataSaveResult =
                    //                                saveModuleRelatedTableRecordData(
                    //                                        tableRecord,
                    //                                        sysModuleCompleteResp,
                    //                                        relationDTO,
                    //                                        modulePrimaryTableId,
                    //                                        currentReq.getTxDsl());
                    //                        if (dataSaveResult.getRowChange() != null) {
                    //
                    // DataPersistenceUtil.fillTableRecordChangeInRecordMap(
                    //                                    recordMap, dataSaveResult.getRowChange());
                    //                        }
                    //                    }
                }
            }
        }
        return dataSaveResultList;
    }

    /**
     * //写入模块 关联表 一行数据
     *
     * @param recordDataDTO 行记录
     * @param sysModuleCompleteResp 模块定义
     * @param relationDTO 关联表 与主表的 关联关系
     * @param modulePrimaryTableId 主表记录id
     * @return
     */
    private DataSaveResult saveModuleRelatedTableRecordData(
            RecordDataDTO recordDataDTO,
            SysModuleCombineInfo sysModuleCompleteResp,
            com.jdec.platform.config.api.dto.common.TableRelationDTO relationDTO,
            Long modulePrimaryTableId,
            DSLContext dslContext,
            DynamicSaveReqContext currentReq) {

        Map<String, Object> dataMap = recordDataDTO.getRow();

        // 主表数据 // 从 wide row 中 过滤出 模块主表 表的字段
        Map<String, Object> primaryTableRowMap =
                DataPersistenceUtil.getTableRowMapFromWideRow(dataMap, relationDTO.getJoinTable());

        if (CollectionUtil.isEmpty(primaryTableRowMap)) {
            return DataSaveResult.builder().rowChange(null).dataId(0L).build();
        }

        Long subTableId = DataPersistenceUtil.getTableRowId(primaryTableRowMap);
        boolean isInsert = subTableId == null || subTableId <= 0;
        Map<String, Object> subTableParentLevelTableAssociationMap = new HashMap<>();
        if (isInsert) {
            subTableParentLevelTableAssociationMap.put(
                    relationDTO.getJoinField(), modulePrimaryTableId);
        }
        RecordSaveContext subRecordSaveContext =
                RecordSaveContext.builder()
                        .dsl(dslContext)
                        .tableName(relationDTO.getJoinTable())
                        .parentLevelTable(relationDTO.getMainTable())
                        .record(primaryTableRowMap)
                        .bizDataMap(null)
                        .parentLevelTableAssociationDataMap(subTableParentLevelTableAssociationMap)
                        .parentLevelTableId(modulePrimaryTableId)
                        .defaultUserContextDataMap(null)
                        .isInsert(subTableId == null || subTableId <= 0)
                        .isRootModuleMainTable(false)
                        .rootModuleId(currentReq.getRootModuleId())
                        .rootModuleMainTableDataId(currentReq.getRootModuleMainTableDataId())
                        .sysModuleCompleteResp(sysModuleCompleteResp)
                        .saveType(currentReq.getSaveType())
                        .userId(UserContext.getUserId())
                        .subjectId(AppContext.getSubjectId())
                        .build();
        // moduleDataSaveNecessaryFieldFillFactoryService.fillData(subRecordSaveContext);
        //// 首先 写入子表数据
        DataSaveResult dataSaveResult =
                baseSingleTablePersistenceService.saveRecord(subRecordSaveContext);
        subTableId = dataSaveResult.getDataId();
        if (isInsert) {
            DataPersistenceUtil.fillTableRowFieldInWideRow(
                    dataMap,
                    relationDTO.getJoinTable(),
                    CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD,
                    subTableId);
            DataPersistenceUtil.fillTableRowFieldInWideRow(
                    dataMap,
                    relationDTO.getJoinTable(),
                    relationDTO.getJoinField(),
                    modulePrimaryTableId);
        } else {
            if (!DataPersistenceUtil.isExistFiledInInWideRow(
                    dataMap, relationDTO.getJoinTable(), relationDTO.getJoinField())) {
                DataPersistenceUtil.fillTableRowFieldInWideRow(
                        dataMap,
                        relationDTO.getJoinTable(),
                        relationDTO.getJoinField(),
                        modulePrimaryTableId);
            }
        }
        return dataSaveResult;
    }
}
