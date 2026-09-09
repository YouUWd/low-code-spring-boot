package com.jdec.platform.dataengine.biz.third.api.persistence;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.config.api.dto.common.ModuleTableDTO;
import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp;
import com.jdec.platform.dataengine.api.dto.request.DynamicSaveReq;
import com.jdec.platform.dataengine.biz.common.ResultSetConstantKey;
import com.jdec.platform.dataengine.biz.dsl.JooqContextFactory;
import com.jdec.platform.dataengine.biz.dto.TableRelationDTO;
import com.jdec.platform.dataengine.biz.dto.context.DynamicSaveReqContext;
import com.jdec.platform.dataengine.biz.dto.context.ModuleDataCheckContext;
import com.jdec.platform.dataengine.biz.dto.context.ModuleDataPostContext;
import com.jdec.platform.dataengine.biz.dto.context.RecordSaveContext;
import com.jdec.platform.dataengine.biz.enums.ApiResponseCodeEnum;
import com.jdec.platform.dataengine.biz.enums.RelationType;
import com.jdec.platform.dataengine.biz.service.MetadataCacheService;
import com.jdec.platform.dataengine.biz.service.TableRelationService;
import com.jdec.platform.dataengine.biz.third.api.persistence.check.ModuleDataSaveCheckFactoryService;
import com.jdec.platform.dataengine.biz.third.api.persistence.fill.ModuleDataSaveNecessaryFieldFillFactoryService;
import com.jdec.platform.dataengine.biz.third.api.persistence.post.ModuleDataSavePostFactoryService;
import com.jdec.platform.dataengine.biz.third.api.persistence.tab.BaseSingleTablePersistenceService;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModuleDataPersistenceService {
    private final MetadataCacheService metadataCacheService;
    private final JooqContextFactory jooqContextFactory;

    private final TableRelationService tableRelationService;

    private final ModuleDataSaveCheckFactoryService moduleDataSaveCheckFactoryService;

    private final ModuleDataSavePostFactoryService moduleDataSavePostFactoryService;

    private final ModuleDataSaveNecessaryFieldFillFactoryService
            moduleDataSaveNecessaryFieldFillFactoryService;

    private final BaseSingleTablePersistenceService baseSingleTablePersistenceService;

    public Long save(DynamicSaveReq req) {
        moduleDataSaveCheckFactoryService.check(ModuleDataCheckContext.builder().build());

        Queue<DynamicSaveReqContext> saveReqQueue = new LinkedList<>();
        saveReqQueue.add(
                DynamicSaveReqContext.builder()
                        .req(req)
                        .parentModuleId(null)
                        .parentModulePrimaryTableId(null)
                        .build());
        boolean first = true;

        Long rootModulePrimaryId = null;

        while (!saveReqQueue.isEmpty()) {
            DynamicSaveReqContext currentReq = saveReqQueue.poll();

            // 保存当前模块数据
            Long resultId = save(currentReq);

            // 捕获根模块的 主表记录ID
            if (first) {
                rootModulePrimaryId = resultId;
            }

            // 构造保存子模的数据的上下文
            if (CollectionUtil.isNotEmpty(currentReq.getReq().getSubModules())) {
                for (DynamicSaveReq subModule : currentReq.getReq().getSubModules()) {
                    saveReqQueue.add(
                            DynamicSaveReqContext.builder()
                                    .req(subModule)
                                    .parentModuleId(currentReq.getReq().getModuleId())
                                    .parentModulePrimaryTableId(resultId)
                                    .build());
                }
            }
            first = false;
        }
        moduleDataSavePostFactoryService.postProcess(ModuleDataPostContext.builder().build());
        return rootModulePrimaryId;
    }

    private Long save(DynamicSaveReqContext currentReq) {
        // 获取模块元数据
        SysModuleCompleteResp sysModuleCompleteResp =
                metadataCacheService.getModuleComplete(currentReq.getReq().getModuleId());

        // 模块数据
        Map<String, Object> moduleData = currentReq.getReq().getModuleData();

        // 模块记录数据,多行
        List<Map<String, Object>> moduleRecordData =
                (List<Map<String, Object>>)
                        moduleData.get(ResultSetConstantKey.RESULT_SET_KEY_RECORDS);

        if (CollectionUtil.isEmpty(moduleRecordData)) {
            log.error(
                    "模块id={},name={}记录数据为空",
                    sysModuleCompleteResp.getModule().getId(),
                    sysModuleCompleteResp.getModule().getModuleName());
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_SAVE_DATA_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_SAVE_DATA_ERROR.getMessage());
        }

        Long resultId = null;
        boolean first = true;

        //
        for (Map<String, Object> recordData : moduleRecordData) {
            // 一行数据
            Map<String, Object> dataMap =
                    (Map<String, Object>) recordData.get(ResultSetConstantKey.RESULT_SET_KEY_DATA);

            if (CollectionUtil.isEmpty(dataMap)) {
                log.error(
                        "模块id={},name={}记录数据为空",
                        sysModuleCompleteResp.getModule().getId(),
                        sysModuleCompleteResp.getModule().getModuleName());
                throw new BusinessException(
                        ApiResponseCodeEnum.BAD_MODULE_SAVE_DATA_ERROR.getCode(),
                        ApiResponseCodeEnum.BAD_MODULE_SAVE_DATA_ERROR.getMessage());
            }
            // 首先 写入当前 模块 主表的数据
            Long primaryTableId =
                    saveModulePrimaryTableData(
                            dataMap,
                            sysModuleCompleteResp,
                            currentReq.getParentModuleId(),
                            currentReq.getParentModulePrimaryTableId());
            if (first) {
                resultId = primaryTableId;
            }

            // 当写入当前模块 关联的表数据
            saveModuleRelatedTableData(dataMap, sysModuleCompleteResp, primaryTableId);

            first = false;
        }
        return resultId;
    }

    /**
     * 写入 模块主表数据
     *
     * @param dataMap 行记录
     * @param sysModuleCompleteResp 模块定义
     * @param parentModuleId 父模块的ID
     * @param parentModulePrimaryTableId 父模块 主表的记录ID
     * @return
     */
    private Long saveModulePrimaryTableData(
            Map<String, Object> dataMap,
            SysModuleCompleteResp sysModuleCompleteResp,
            Long parentModuleId,
            Long parentModulePrimaryTableId) {

        Map<String, Object> parentLevelTableAssociationMap = new HashMap<>();
        SysModuleCompleteResp parentModuleCompleteResp = null;

        // 首先获取父模块主表 与当前模块的主表 之间的关联关系，一般来说 父模块主表的主键字段 关联子模块的主表主表字段
        if (parentModuleId == null) {
            // 没有父模块，说明是根模块
        } else {
            parentModuleCompleteResp = metadataCacheService.getModuleComplete(parentModuleId);
            TableRelationDTO relationDTO =
                    tableRelationService.getTableRelation(
                            parentModuleCompleteResp.getModule().getPrimaryTable(),
                            sysModuleCompleteResp.getModule().getPrimaryTable());
            parentLevelTableAssociationMap.put(
                    relationDTO.getJoinField(), parentModulePrimaryTableId);
        }

        // 主表数据
        Map<String, Object> primaryTableRecordMap =
                DataPersistenceUtil.getTableRecordMap(
                        dataMap, sysModuleCompleteResp.getModule().getPrimaryTable());

        Long primaryTableId = DataPersistenceUtil.getRecordId(primaryTableRecordMap);

        RecordSaveContext recordSaveContext =
                RecordSaveContext.builder()
                        .dsl(jooqContextFactory.getContext())
                        .tableName(sysModuleCompleteResp.getModule().getPrimaryTable())
                        .parentLevelTable(
                                parentModuleCompleteResp != null
                                        ? parentModuleCompleteResp.getModule().getPrimaryTable()
                                        : null)
                        .record(primaryTableRecordMap)
                        .bizDataMap(null)
                        .parentLevelTableAssociationDataMap(parentLevelTableAssociationMap)
                        .parentLevelTableId(parentModulePrimaryTableId)
                        .defaultUserContextDataMap(null)
                        .isInsert(primaryTableId == null || primaryTableId <= 0)
                        .build();
        moduleDataSaveNecessaryFieldFillFactoryService.fillData(recordSaveContext);
        // 首先 写入当前 模块 主表的数据
        primaryTableId = baseSingleTablePersistenceService.saveRecord(recordSaveContext);

        if (primaryTableId == null) {
            log.error(
                    "模块id={},name={} table={}记录数据写入失败",
                    sysModuleCompleteResp.getModule().getId(),
                    sysModuleCompleteResp.getModule().getModuleName(),
                    sysModuleCompleteResp.getModule().getPrimaryTable());
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_SAVE_DATA_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_SAVE_DATA_ERROR.getMessage());
        }
        return primaryTableId;
    }

    /**
     * 写入模块 关联表 数据
     *
     * @param dataMap 主表的行记录
     * @param sysModuleCompleteResp
     * @param modulePrimaryTableId 主表行记录id
     * @return
     */
    private Long saveModuleRelatedTableData(
            Map<String, Object> dataMap,
            SysModuleCompleteResp sysModuleCompleteResp,
            Long modulePrimaryTableId) {
        List<ModuleTableDTO> moduleTables = sysModuleCompleteResp.getModuleTables();
        for (ModuleTableDTO moduleTable : moduleTables) {
            // 主表与当前表的关联关系
            TableRelationDTO relationDTO =
                    tableRelationService.getTableRelation(
                            sysModuleCompleteResp.getModule().getPrimaryTable(),
                            moduleTable.getTableName());

            RelationType relationType = RelationType.getRelationType(relationDTO.getRelationType());
            switch (relationType) {
                case ONE_TO_ONE, MANY_TO_ONE -> {
                    // 1:1
                    // 子表数据 是一行
                    Map<String, Object> subTableRecordMap =
                            DataPersistenceUtil.getTableRecordMap(
                                    dataMap, moduleTable.getTableName());
                    //// 首先 写入子表数据
                    saveModuleRelatedTableRecordData(
                            subTableRecordMap,
                            sysModuleCompleteResp,
                            relationDTO,
                            modulePrimaryTableId);
                }
                case ONE_TO_MANY -> {
                    // 1:N
                    // 子表数据 是一个 list
                    List<Map<String, Object>> subTableRecordListMap =
                            DataPersistenceUtil.getTableRecordListMap(
                                    dataMap, moduleTable.getTableName());

                    for (Map<String, Object> recordMap : subTableRecordListMap) {
                        // 首先 写入子表数据
                        saveModuleRelatedTableRecordData(
                                recordMap,
                                sysModuleCompleteResp,
                                relationDTO,
                                modulePrimaryTableId);
                    }
                }
            }
        }
        return null;
    }

    /**
     * //写入模块 关联表 一行数据
     *
     * @param recordMap 行记录
     * @param sysModuleCompleteResp 模块定义
     * @param relationDTO 关联表 与主表的 关联关系
     * @param modulePrimaryTableId 主表记录id
     * @return
     */
    private Long saveModuleRelatedTableRecordData(
            Map<String, Object> recordMap,
            SysModuleCompleteResp sysModuleCompleteResp,
            TableRelationDTO relationDTO,
            Long modulePrimaryTableId) {

        Long subTableId = DataPersistenceUtil.getRecordId(recordMap);
        Map<String, Object> subTableParentLevelTableAssociationMap = new HashMap<>();
        subTableParentLevelTableAssociationMap.put(
                relationDTO.getJoinField(), modulePrimaryTableId);
        RecordSaveContext subRecordSaveContext =
                RecordSaveContext.builder()
                        .dsl(jooqContextFactory.getContext())
                        .tableName(relationDTO.getJoinTable())
                        .parentLevelTable(relationDTO.getMainTable())
                        .record(recordMap)
                        .bizDataMap(null)
                        .parentLevelTableAssociationDataMap(subTableParentLevelTableAssociationMap)
                        .parentLevelTableId(modulePrimaryTableId)
                        .defaultUserContextDataMap(null)
                        .isInsert(subTableId == null || subTableId <= 0)
                        .build();
        moduleDataSaveNecessaryFieldFillFactoryService.fillData(subRecordSaveContext);
        //// 首先 写入子表数据
        subTableId = baseSingleTablePersistenceService.saveRecord(subRecordSaveContext);
        return subTableId;
    }
}
