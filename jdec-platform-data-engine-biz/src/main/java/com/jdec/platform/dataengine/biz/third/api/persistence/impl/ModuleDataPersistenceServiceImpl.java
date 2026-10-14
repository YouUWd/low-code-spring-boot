package com.jdec.platform.dataengine.biz.third.api.persistence.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.dataengine.api.dto.model.*;
import com.jdec.platform.dataengine.api.dto.request.DynamicDeleteReq;
import com.jdec.platform.dataengine.api.dto.request.DynamicInsertReq;
import com.jdec.platform.dataengine.api.dto.request.DynamicSaveReq;
import com.jdec.platform.dataengine.api.dto.response.DynamicSaveResp;
import com.jdec.platform.dataengine.biz.constants.CommonConstant;
import com.jdec.platform.dataengine.biz.context.ModuleDataSaveContextHolder;
import com.jdec.platform.dataengine.biz.dto.DataSaveResult;
import com.jdec.platform.dataengine.biz.dto.ModuleDataStoreDTO;
import com.jdec.platform.dataengine.biz.dto.SysModuleCombineInfo;
import com.jdec.platform.dataengine.biz.dto.context.DynamicSaveReqContext;
import com.jdec.platform.dataengine.biz.dto.context.ModuleDataPostContext;
import com.jdec.platform.dataengine.biz.dto.context.RecordSaveContext;
import com.jdec.platform.dataengine.biz.enums.ApiResponseCodeEnum;
import com.jdec.platform.dataengine.biz.enums.RelationType;
import com.jdec.platform.dataengine.biz.enums.SaveButtonType;
import com.jdec.platform.dataengine.biz.enums.SaveType;
import com.jdec.platform.dataengine.biz.third.api.persistence.DataPersistenceUtil;
import com.jdec.platform.dataengine.biz.third.api.persistence.ModuleDataPersistenceService;
import com.jdec.platform.dataengine.biz.third.api.persistence.post.ModuleDataSavePostFactoryService;
import com.jdec.platform.dataengine.biz.third.api.persistence.tab.BaseSingleTablePersistenceService;
import com.jdec.platform.dataengine.biz.util.JsonUtil;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.RequestContextSnapshotHolder;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.datasource.JooqDataContextFactory;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModuleDataPersistenceServiceImpl implements ModuleDataPersistenceService {
    private final JooqDataContextFactory jooqContextFactory;

    private final ModuleDataSavePostFactoryService moduleDataSavePostFactoryService;

    private final BaseSingleTablePersistenceService baseSingleTablePersistenceService;

    public Long save(ModuleDataStoreDTO req) {

        boolean isRelatedApproval = req.getIsApproval();
        // 模块是否关联审批链 保存类型
        SaveType finalSaveType = req.getSaveType();
        Map<Long, SysModuleCombineInfo> moduleCacheMap = req.getModuleCacheMap();
        NestedModuleDataDTO nestedStoreData = req.getParamData();
        DynamicSaveResp resp = new DynamicSaveResp();
        AtomicReference<Long> rootModuleMainTableDataId = new AtomicReference<>();
        Long actionId = null;
        try {
            ModuleDataSaveContextHolder.openDataChangeFlag(finalSaveType);
            actionId = ModuleDataSaveContextHolder.getContextActionId();

            jooqContextFactory
                    .getContext()
                    .transaction(
                            ctx -> {
                                DSLContext txDsl = DSL.using(ctx);
                                try {

                                    Queue<DynamicSaveReqContext> saveReqQueue = new LinkedList<>();

                                    Long rootModuleId = nestedStoreData.getModuleId();

                                    // 根模块数据 没有父模块和父模块主表ID
                                    saveReqQueue.add(
                                            DynamicSaveReqContext.builder()
                                                    .nestedModuleData(nestedStoreData)
                                                    .parentModuleId(null)
                                                    .parentModulePrimaryTableId(null)
                                                    .rootModuleId(rootModuleId) // 第一层的 根模块就是自己的模块ID
                                                    .rootModuleMainTableDataId(null)
                                                    .rootModuleMainTableDataVersion(null)
                                                    .rootModuleMainTableDataStatus(null)
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
                                                        currentReq
                                                                .getNestedModuleData()
                                                                .getRecord())) {
                                            continue;
                                        }

                                        Long currentModuleId =
                                                currentReq.getNestedModuleData().getModuleId();

                                        if (currentReq.getParentModuleId() != null
                                                && currentReq.getParentModuleId() > 0) {
                                            SysModuleCombineInfo sysModuleCombineInfo =
                                                    moduleCacheMap.get(currentModuleId);
                                            // 虚模块不处理
                                            if (StringUtils.isNotEmpty(
                                                    sysModuleCombineInfo
                                                            .getModuleInfo()
                                                            .getPrimaryTable())) {
                                                // 当前父模块父数据ID 关联的 所有子模块子表的ID
                                                List<Long> dbIds =
                                                        baseSingleTablePersistenceService
                                                                .getRecordIdsByFk(
                                                                        txDsl,
                                                                        sysModuleCombineInfo
                                                                                .getModuleInfo()
                                                                                .getPrimaryTable(),
                                                                        sysModuleCombineInfo
                                                                                .getMainTableRelationDTO()
                                                                                .getJoinField(),
                                                                        List.of(
                                                                                currentReq
                                                                                        .getParentModulePrimaryTableId()));

                                                Set<Long> storeIds = new HashSet();
                                                for (RecordDataDTO recordDataDTO :
                                                        currentReq
                                                                .getNestedModuleData()
                                                                .getRecord()) {
                                                    if (CollectionUtil.isNotEmpty(
                                                            recordDataDTO.getRow())) {
                                                        Boolean deleteFlag =
                                                                DataPersistenceUtil
                                                                        .getTableRowDeletedFlagWithTabName(
                                                                                recordDataDTO
                                                                                        .getRow(),
                                                                                sysModuleCombineInfo
                                                                                        .getModuleInfo()
                                                                                        .getPrimaryTable());
                                                        // 显示删除的数据 走整体的保存逻辑
                                                        if (deleteFlag) {
                                                            continue;
                                                        }
                                                        Long id =
                                                                DataPersistenceUtil
                                                                        .getTableRowIdWithTabName(
                                                                                recordDataDTO
                                                                                        .getRow(),
                                                                                sysModuleCombineInfo
                                                                                        .getModuleInfo()
                                                                                        .getPrimaryTable());
                                                        if (id != null && id > 0) {
                                                            storeIds.add(id);
                                                        }
                                                    }
                                                }
                                                if (CollectionUtil.isNotEmpty(dbIds)) {
                                                    List<Long> deleteIds =
                                                            dbIds.stream()
                                                                    .filter(
                                                                            x ->
                                                                                    x != null
                                                                                            && !storeIds
                                                                                                    .contains(
                                                                                                            x))
                                                                    .collect(Collectors.toList());
                                                    if (CollectionUtil.isNotEmpty(deleteIds)) {
                                                        deleteOrphanModuleData(
                                                                deleteIds,
                                                                currentModuleId,
                                                                moduleCacheMap,
                                                                txDsl);
                                                    }
                                                }
                                            }
                                        }

                                        // 保存当前 模块数据 第一次进来  NestedModuleDataDTO.record 只有一条数据
                                        for (RecordDataDTO recordDataDTO :
                                                currentReq.getNestedModuleData().getRecord()) {

                                            // 保存 当前模块一行数据 主表数据只有一行
                                            Long resultId =
                                                    saveModuleData(
                                                            recordDataDTO,
                                                            currentReq,
                                                            moduleCacheMap);

                                            // 捕获根模块的 主表记录ID
                                            if (first) {
                                                rootModuleMainTableDataId.set(resultId);
                                            }

                                            // 如果当前模块有子模块的数据
                                            if ((resultId != null && resultId > 0)
                                                    && CollectionUtil.isNotEmpty(
                                                            recordDataDTO.getChildren())) {
                                                recordDataDTO.getChildren().stream()
                                                        .forEach(
                                                                subModule -> {
                                                                    // 构造保存子模的数据的上下文
                                                                    saveReqQueue.add(
                                                                            DynamicSaveReqContext
                                                                                    .builder()
                                                                                    .nestedModuleData(
                                                                                            subModule)
                                                                                    .parentModuleId(
                                                                                            currentReq
                                                                                                    .getNestedModuleData()
                                                                                                    .getModuleId())
                                                                                    .parentModulePrimaryTableId(
                                                                                            resultId)
                                                                                    .rootModuleId(
                                                                                            rootModuleId)
                                                                                    .rootModuleMainTableDataId(
                                                                                            rootModuleMainTableDataId
                                                                                                    .get())
                                                                                    .txDsl(txDsl)
                                                                                    .saveType(
                                                                                            finalSaveType)
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
                            });

            if (!isRelatedApproval && SaveType.SAVE.equals(finalSaveType)) {
                moduleDataSavePostFactoryService.postProcess(
                        ModuleDataPostContext.builder()
                                .actionId(actionId)
                                .subjectId(AppContext.getSubjectId())
                                .projectNo(AppContext.getProjectNo())
                                .realUserId(UserContext.getRealUserId())
                                .userId(UserContext.getUserId())
                                .mockUserId(UserContext.getMockUserId())
                                .roleId(UserContext.getRoleId())
                                .saveType(finalSaveType)
                                .nestedModuleDataDTO(nestedStoreData)
                                .changeMap(ModuleDataSaveContextHolder.getRowChange())
                                .moduleCacheMap(moduleCacheMap)
                                .requestContextSnapshot(RequestContextSnapshotHolder.get())
                                .loginUser(UserContext.getLoginUser())
                                .build());
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

    private Long saveModuleData(
            RecordDataDTO recordDataDTO,
            DynamicSaveReqContext currentReq,
            Map<Long, SysModuleCombineInfo> moduleCacheMap) {

        // 获取模块元数据
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
            return currentReq.getParentModulePrimaryTableId();
        }

        // 模块数据
        if (CollectionUtil.isEmpty(recordDataDTO.getRow())) {
            log.error(
                    "模块id={},name={}记录数据为空",
                    sysModuleCompleteResp.getModuleInfo().getModuleId(),
                    sysModuleCompleteResp.getModuleInfo().getModuleName());
            //            throw new BusinessException(
            //                    ApiResponseCodeEnum.BAD_MODULE_SAVE_DATA_ERROR.getCode(),
            //                    ApiResponseCodeEnum.BAD_MODULE_SAVE_DATA_ERROR.getMessage());
            return 0L;
        }
        // 首先 写入当前 模块 主表的数据,主表数据 只有一行
        DataSaveResult primaryDataSaveResult =
                saveModulePrimaryTableData(recordDataDTO, sysModuleCompleteResp, currentReq);
        Long primaryTableId = primaryDataSaveResult.getDataId();

        if (primaryTableId == null || primaryTableId.equals(0L)) {
            return primaryTableId;
        }

        // 在写入当前模块 关联的表数据
        List<DataSaveResult> relatedDataSaveResult =
                saveModuleRelatedTableData(
                        recordDataDTO, sysModuleCompleteResp, primaryTableId, currentReq);

        // 合并两张表的变更记录
        if (!currentReq.isApproval() && SaveType.SAVE.equals(currentReq.getSaveType())) {
            List<RowChangeDTO> rowChangeDTOList = new ArrayList<>();
            if (CollectionUtil.isNotEmpty(relatedDataSaveResult)) {
                for (DataSaveResult dataSaveResult : relatedDataSaveResult) {
                    if (dataSaveResult.getRowChange() != null) {
                        rowChangeDTOList.add(dataSaveResult.getRowChange());
                    }
                }
            }
            RowChangeDTO combinedRowChange =
                    DataPersistenceUtil.combineRowChanges(
                            primaryDataSaveResult.getRowChange(), rowChangeDTOList);
            // recordDataDTO.setRowChanges(combinedRowChange);
            ModuleDataSaveContextHolder.addRowChange(
                    sysModuleCompleteResp.getModuleInfo().getModuleId(),
                    primaryTableId,
                    combinedRowChange);
        }
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
            DynamicSaveReqContext currentReq) {

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
                    relationDTO.getJoinField(), currentReq.getParentModulePrimaryTableId());
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
                        .parentLevelTableId(currentReq.getParentModulePrimaryTableId())
                        .defaultUserContextDataMap(null)
                        .isInsert(primaryTableId == null || primaryTableId <= 0)
                        .isRootModuleMainTable(isRootModule ? true : false)
                        .isTreeRoot(isTreeRootModule)
                        .rootModuleId(currentReq.getRootModuleId())
                        .rootModuleMainTableDataId(currentReq.getRootModuleMainTableDataId())
                        .sysModuleCompleteResp(sysModuleCompleteResp)
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
                        currentReq.getParentModulePrimaryTableId());
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
                        currentReq.getParentModulePrimaryTableId());
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
            DynamicSaveReqContext currentReq) {
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

    private void deleteOrphanModuleData(
            List<Long> dataIds,
            Long moduleId,
            Map<Long, SysModuleCombineInfo> moduleCacheMap,
            DSLContext dslContext) {
        SysModuleCombineInfo sysModuleCombineInfo = moduleCacheMap.get(moduleId);
        // 先删除 父当前模块数据
        baseSingleTablePersistenceService.deleteByIds(
                dslContext, sysModuleCombineInfo.getModuleInfo().getPrimaryTable(), dataIds);
        deleteRelatedModuleData(dataIds, moduleId, moduleCacheMap, dslContext);
        // 子模块
        List<Long> childrenModuleIds =
                sysModuleCombineInfo.getModuleTree().getChildModuleIds(moduleId);
        if (CollectionUtil.isNotEmpty(childrenModuleIds)) {
            for (Long childModuleId : childrenModuleIds) {
                deleteChildrenModuleDataByParentIdsRecursion(
                        dataIds, childModuleId, moduleCacheMap, dslContext);
            }
        }
    }

    private void deleteChildrenModuleDataByParentIdsRecursion(
            List<Long> parentIds,
            Long moduleId,
            Map<Long, SysModuleCombineInfo> moduleCacheMap,
            DSLContext dslContext) {
        SysModuleCombineInfo sysModuleCombineInfo = moduleCacheMap.get(moduleId);
        // 子模块ID
        List<Long> childrenModuleIds =
                sysModuleCombineInfo.getModuleTree().getChildModuleIds(moduleId);

        // 虚拟模块 直接透传到下级模块删除
        if (StringUtils.isEmpty(sysModuleCombineInfo.getModuleInfo().getPrimaryTable())) {
            if (CollectionUtil.isNotEmpty(childrenModuleIds)) {
                for (Long childModuleId : childrenModuleIds) {
                    deleteChildrenModuleDataByParentIdsRecursion(
                            parentIds, childModuleId, moduleCacheMap, dslContext);
                }
            }
        }
        // 子表 待删除的 数据ID
        List<Long> dataIds =
                baseSingleTablePersistenceService.getRecordIdsByFk(
                        dslContext,
                        sysModuleCombineInfo.getModuleInfo().getPrimaryTable(),
                        sysModuleCombineInfo.getMainTableRelationDTO().getJoinField(),
                        parentIds);
        if (CollectionUtil.isNotEmpty(dataIds)) {
            // 删除子表数据
            baseSingleTablePersistenceService.deleteByIds(
                    dslContext, sysModuleCombineInfo.getModuleInfo().getPrimaryTable(), dataIds);
            deleteRelatedModuleData(dataIds, moduleId, moduleCacheMap, dslContext);
            if (CollectionUtil.isNotEmpty(childrenModuleIds)) {
                for (Long childModuleId : childrenModuleIds) {
                    deleteChildrenModuleDataByParentIdsRecursion(
                            dataIds, childModuleId, moduleCacheMap, dslContext);
                }
            }
        }
    }

    /**
     * 删除模块关联表的数据
     *
     * @param mainDataIds 模块主表的数据ID
     * @param moduleId
     * @param moduleCacheMap
     * @param dslContext
     */
    private void deleteRelatedModuleData(
            List<Long> mainDataIds,
            Long moduleId,
            Map<Long, SysModuleCombineInfo> moduleCacheMap,
            DSLContext dslContext) {
        SysModuleCombineInfo sysModuleCombineInfo = moduleCacheMap.get(moduleId);
        if (CollectionUtil.isNotEmpty(sysModuleCombineInfo.getSubTables())) {
            for (String subTable : sysModuleCombineInfo.getSubTables()) {
                // 子表中关联的字段
                com.jdec.platform.config.api.dto.common.TableRelationDTO subTableRelationDTO =
                        sysModuleCombineInfo.getSubTableRelationMap().get(subTable);
                if (subTableRelationDTO != null) {
                    // 子表的ID
                    List<Long> subTabDataIds =
                            baseSingleTablePersistenceService.getRecordIdsByFk(
                                    dslContext,
                                    subTable,
                                    subTableRelationDTO.getJoinField(),
                                    mainDataIds);
                    if (CollectionUtil.isNotEmpty(subTabDataIds)) {
                        baseSingleTablePersistenceService.deleteByIds(
                                dslContext, subTable, subTabDataIds);
                    }
                }
            }
        }
    }
}
