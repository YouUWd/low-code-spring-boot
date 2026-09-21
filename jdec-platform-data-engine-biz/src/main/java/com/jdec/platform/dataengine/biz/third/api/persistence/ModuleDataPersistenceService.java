package com.jdec.platform.dataengine.biz.third.api.persistence;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.dataengine.api.dto.model.SysModuleInfo;
import com.jdec.platform.dataengine.api.dto.model.TableRelationDTO;
import com.jdec.platform.dataengine.api.dto.request.DynamicSaveReq;
import com.jdec.platform.dataengine.biz.config.service.DataModuleConfigService;
import com.jdec.platform.dataengine.biz.constants.CommonConstant;
import com.jdec.platform.dataengine.biz.context.ModuleDataSaveContextHolder;
import com.jdec.platform.dataengine.biz.dsl.JooqContextFactory;
import com.jdec.platform.dataengine.biz.dto.DataSaveResult;
import com.jdec.platform.dataengine.biz.dto.context.DynamicSaveReqContext;
import com.jdec.platform.dataengine.biz.dto.context.ModuleDataPostContext;
import com.jdec.platform.dataengine.biz.dto.context.RecordSaveContext;
import com.jdec.platform.dataengine.biz.enums.ApiResponseCodeEnum;
import com.jdec.platform.dataengine.biz.enums.RelationType;
import com.jdec.platform.dataengine.biz.enums.SaveType;
import com.jdec.platform.dataengine.biz.service.MetadataCacheService;
import com.jdec.platform.dataengine.biz.third.api.persistence.check.ModuleDataSaveCheckFactoryService;
import com.jdec.platform.dataengine.biz.third.api.persistence.fill.ModuleDataSaveNecessaryFieldFillFactoryService;
import com.jdec.platform.dataengine.biz.third.api.persistence.post.ModuleDataSavePostFactoryService;
import com.jdec.platform.dataengine.biz.third.api.persistence.tab.BaseSingleTablePersistenceService;
import com.jdec.platform.dataengine.biz.util.JsonUtil;
import com.jdec.platform.dataengine.biz.util.TableRelationUtils;
import com.jdec.platform.shared.context.AppContext;
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
public class ModuleDataPersistenceService {
    private final MetadataCacheService metadataCacheService;
    private final JooqContextFactory jooqContextFactory;

    private final ModuleDataSaveCheckFactoryService moduleDataSaveCheckFactoryService;

    private final ModuleDataSavePostFactoryService moduleDataSavePostFactoryService;

    private final ModuleDataSaveNecessaryFieldFillFactoryService
            moduleDataSaveNecessaryFieldFillFactoryService;

    private final BaseSingleTablePersistenceService baseSingleTablePersistenceService;

    private final DataModuleConfigService moduleConfigService;

    public Long save(DynamicSaveReq req) {
        // moduleDataSaveCheckFactoryService.check(ModuleDataCheckContext.builder().build());

        req.setRoot(TableRelationUtils.getRootTabTreeNode());
        AtomicReference<Long> rootModuleMainTableDataId = new AtomicReference<>();
        Long actionId = null;
        try {
            ModuleDataSaveContextHolder.openDataChangeFlag(null);
            actionId = ModuleDataSaveContextHolder.getContextActionId();
            jooqContextFactory
                    .getContext()
                    .transaction(
                            ctx -> {
                                DSLContext txDsl = DSL.using(ctx);
                                try {

                                    Queue<DynamicSaveReqContext> saveReqQueue = new LinkedList<>();

                                    Long rootModuleId = req.getNestedModuleData().getModuleId();

                                    // 根模块数据 没有父模块和父模块主表ID
                                    saveReqQueue.add(
                                            DynamicSaveReqContext.builder()
                                                    .nestedModuleData(req.getNestedModuleData())
                                                    .parentModuleId(null)
                                                    .parentModulePrimaryTableId(null)
                                                    .rootModuleId(rootModuleId) // 第一层的 根模块就是自己的模块ID
                                                    .rootModuleMainTableDataId(null)
                                                    .tabTreeNode(req.getRoot())
                                                    .txDsl(txDsl)
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

                                        // 保存当前 模块数据 第一次进来  NestedModuleDataDTO.record 只有一条数据
                                        for (RecordDataDTO recordDataDTO :
                                                currentReq.getNestedModuleData().getRecord()) {

                                            // 保存 当前模块一行数据 主表数据只有一行
                                            Long resultId =
                                                    saveModuleData(recordDataDTO, currentReq);

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
                                                                                    .tabTreeNode(
                                                                                            req
                                                                                                    .getRoot())
                                                                                    .rootModuleId(
                                                                                            rootModuleId)
                                                                                    .rootModuleMainTableDataId(
                                                                                            rootModuleMainTableDataId
                                                                                                    .get())
                                                                                    .txDsl(txDsl)
                                                                                    .build());
                                                                });
                                            }
                                            first = false;
                                        }
                                    }
                                    // 其他sql
                                } catch (Exception e) {
                                    // 抛出异常，jooq自动回滚
                                    throw new RuntimeException(e);
                                }
                            });
        } catch (Exception e) {
            throw e;
        } finally {
            ModuleDataSaveContextHolder.clear();
        }
        try {
            log.info("=======" + "\n");
            req.setRoot(null);
            log.info("=======" + JsonUtil.obj2Json(req));
        } catch (Exception e) {

        }

        moduleDataSavePostFactoryService.postProcess(
                ModuleDataPostContext.builder()
                        .actionId(actionId)
                        .saveType(SaveType.NORMAL_SAVE)
                        .build());
        return rootModuleMainTableDataId.get();
    }

    private Long saveModuleData(RecordDataDTO recordDataDTO, DynamicSaveReqContext currentReq) {
        // 获取模块元数据
        SysModuleInfo sysModuleCompleteResp =
                moduleConfigService.getModuleCompleteById(
                        AppContext.getProjectNo(),
                        AppContext.getSubjectId(),
                        currentReq.getNestedModuleData().getModuleId());

        //
        if (sysModuleCompleteResp == null) {
            log.error("模块id={}不存在", currentReq.getNestedModuleData().getModuleId());
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getMessage());
        }
        // 虚拟模块
        if (StringUtils.isEmpty(sysModuleCompleteResp.getPrimaryTable())) {
            // 虚拟模块数据不用保存 直接返回父模块对应的数据ID
            return currentReq.getParentModulePrimaryTableId();
        }

        // 模块数据
        if (CollectionUtil.isEmpty(recordDataDTO.getRow())) {
            log.error(
                    "模块id={},name={}记录数据为空",
                    sysModuleCompleteResp.getModuleId(),
                    sysModuleCompleteResp.getModuleName());
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_SAVE_DATA_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_SAVE_DATA_ERROR.getMessage());
        }
        // 首先 写入当前 模块 主表的数据,主表数据 只有一行
        Long primaryTableId =
                saveModulePrimaryTableData(recordDataDTO, sysModuleCompleteResp, currentReq);

        if (primaryTableId == null || primaryTableId.equals(0L)) {
            return primaryTableId;
        }

        // 在写入当前模块 关联的表数据
        saveModuleRelatedTableData(
                recordDataDTO, sysModuleCompleteResp, primaryTableId, currentReq);

        return primaryTableId;
    }

    /**
     * 写入 模块主表数据
     *
     * @param recordDataDTO 行记录
     * @param sysModuleCompleteResp 模块定义
     * @return
     */
    private Long saveModulePrimaryTableData(
            RecordDataDTO recordDataDTO,
            SysModuleInfo sysModuleCompleteResp,
            DynamicSaveReqContext currentReq) {

        Map<String, Object> parentLevelTableAssociationMap = new HashMap<>();
        boolean isRootModule = false;
        TableRelationDTO relationDTO = null;
        // 首先获取父模块主表 与当前模块的主表 之间的关联关系，一般来说 父模块主表的主键字段 关联子模块的主表主表字段
        if (currentReq.getParentModuleId() == null) {
            // 没有父模块，说明是根模块
            isRootModule = true;
        } else {
            relationDTO =
                    TableRelationUtils.getTableRelationBySubTable(
                            currentReq.getTabTreeNode(), sysModuleCompleteResp.getPrimaryTable());
            parentLevelTableAssociationMap.put(
                    relationDTO.getJoinField(), currentReq.getParentModulePrimaryTableId());
        }

        Map<String, Object> dataMap = recordDataDTO.getRow();

        if (CollectionUtil.isEmpty(dataMap)) {
            return 0L;
        }

        // 主表数据 // 从 wide row 中 过滤出 info 表的字段
        Map<String, Object> primaryTableRowMap =
                DataPersistenceUtil.getTableRowMapFromWideRow(
                        dataMap, sysModuleCompleteResp.getPrimaryTable());

        if (CollectionUtil.isEmpty(primaryTableRowMap)) {
            return 0L;
        }

        Long primaryTableId = DataPersistenceUtil.getTableRowId(primaryTableRowMap);

        RecordSaveContext recordSaveContext =
                RecordSaveContext.builder()
                        .dsl(currentReq.getTxDsl())
                        .tableName(sysModuleCompleteResp.getPrimaryTable())
                        .parentLevelTable(null)
                        .record(primaryTableRowMap)
                        .bizDataMap(null)
                        .parentLevelTableAssociationDataMap(parentLevelTableAssociationMap)
                        .parentLevelTableId(currentReq.getParentModulePrimaryTableId())
                        .defaultUserContextDataMap(null)
                        .isInsert(primaryTableId == null || primaryTableId <= 0)
                        .rootModuleId(currentReq.getRootModuleId())
                        .rootModuleMainTableDataId(currentReq.getRootModuleMainTableDataId())
                        .sysModuleCompleteResp(sysModuleCompleteResp)
                        .build();
        // moduleDataSaveNecessaryFieldFillFactoryService.fillData(recordSaveContext);
        // 首先 写入当前 模块 主表的数据
        DataSaveResult dataSaveResult =
                baseSingleTablePersistenceService.saveRecord(recordSaveContext);
        primaryTableId = dataSaveResult.getDataId();

        if (recordSaveContext.getIsInsert()) {
            DataPersistenceUtil.fillTableRowFieldInWideRow(
                    dataMap,
                    sysModuleCompleteResp.getPrimaryTable(),
                    CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD,
                    primaryTableId);
            if (!isRootModule) {
                DataPersistenceUtil.fillTableRowFieldInWideRow(
                        dataMap,
                        sysModuleCompleteResp.getPrimaryTable(),
                        relationDTO.getJoinField(),
                        currentReq.getParentModulePrimaryTableId());
            }
        } else {
            if (!isRootModule
                    && !DataPersistenceUtil.isExistFiledInInWideRow(
                            dataMap,
                            sysModuleCompleteResp.getPrimaryTable(),
                            relationDTO.getJoinField())) {
                DataPersistenceUtil.fillTableRowFieldInWideRow(
                        dataMap,
                        sysModuleCompleteResp.getPrimaryTable(),
                        relationDTO.getJoinField(),
                        currentReq.getParentModulePrimaryTableId());
            }
        }
        if (dataSaveResult.getRowChange() != null) {
            //            DataPersistenceUtil.fillTableRecordChangeInCombinedMap(
            //                    dataMap,
            //                    sysModuleCompleteResp.getPrimaryTable(),
            //                    dataSaveResult.getRowChange());
        }

        if (primaryTableId == null) {
            log.error(
                    "模块id={},name={} table={}记录数据写入失败",
                    sysModuleCompleteResp.getModuleId(),
                    sysModuleCompleteResp.getModuleName(),
                    sysModuleCompleteResp.getPrimaryTable());
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_SAVE_DATA_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_SAVE_DATA_ERROR.getMessage());
        }
        return primaryTableId;
    }

    /**
     * 写入模块 关联表 数据
     *
     * @param recordDataDTO 主表的行记录
     * @param sysModuleCompleteResp
     * @param modulePrimaryTableId 主表行记录id
     * @return
     */
    private Long saveModuleRelatedTableData(
            RecordDataDTO recordDataDTO,
            SysModuleInfo sysModuleCompleteResp,
            Long modulePrimaryTableId,
            DynamicSaveReqContext currentReq) {
        Set<String> tables =
                sysModuleCompleteResp.getFieldList().stream()
                        .map(x -> x.getTableName())
                        .collect(Collectors.toSet());
        tables =
                tables.stream()
                        .filter(x -> !x.equals(sysModuleCompleteResp.getPrimaryTable()))
                        .collect(Collectors.toSet());

        for (String moduleTable : tables) {
            // 主表与当前表的关联关系
            TableRelationDTO relationDTO =
                    TableRelationUtils.getTableRelationBySubTable(
                            currentReq.getTabTreeNode(), moduleTable);

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
                                    currentReq.getTxDsl());
                    if (dataSaveResult.getRowChange() != null) {
                        //
                        // DataPersistenceUtil.fillTableRecordChangeInCombinedMap(
                        //                                dataMap, moduleTable,
                        // dataSaveResult.getRowChange());
                    }
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
        return null;
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
            SysModuleInfo sysModuleCompleteResp,
            TableRelationDTO relationDTO,
            Long modulePrimaryTableId,
            DSLContext dslContext) {

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
        }
        return dataSaveResult;
    }
}
