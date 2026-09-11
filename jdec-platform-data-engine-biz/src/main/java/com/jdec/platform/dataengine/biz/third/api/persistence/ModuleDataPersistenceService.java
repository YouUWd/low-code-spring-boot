package com.jdec.platform.dataengine.biz.third.api.persistence;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.config.api.dto.response.SysModuleResp;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.dataengine.api.dto.model.TabTreeNodeDTO;
import com.jdec.platform.dataengine.api.dto.request.DynamicSaveReq;
import com.jdec.platform.dataengine.biz.dsl.JooqContextFactory;
import com.jdec.platform.dataengine.biz.dto.TableRelationDTO;
import com.jdec.platform.dataengine.biz.dto.context.DynamicSaveReqContext;
import com.jdec.platform.dataengine.biz.dto.context.ModuleDataCheckContext;
import com.jdec.platform.dataengine.biz.dto.context.ModuleDataPostContext;
import com.jdec.platform.dataengine.biz.dto.context.RecordSaveContext;
import com.jdec.platform.dataengine.biz.enums.ApiResponseCodeEnum;
import com.jdec.platform.dataengine.biz.enums.RelationType;
import com.jdec.platform.dataengine.biz.service.MetadataCacheService;
import com.jdec.platform.dataengine.biz.third.api.persistence.check.ModuleDataSaveCheckFactoryService;
import com.jdec.platform.dataengine.biz.third.api.persistence.fill.ModuleDataSaveNecessaryFieldFillFactoryService;
import com.jdec.platform.dataengine.biz.third.api.persistence.post.ModuleDataSavePostFactoryService;
import com.jdec.platform.dataengine.biz.third.api.persistence.tab.BaseSingleTablePersistenceService;
import com.jdec.platform.dataengine.biz.util.TableRelationUtils;
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

    private final ModuleDataSaveCheckFactoryService moduleDataSaveCheckFactoryService;

    private final ModuleDataSavePostFactoryService moduleDataSavePostFactoryService;

    private final ModuleDataSaveNecessaryFieldFillFactoryService
            moduleDataSaveNecessaryFieldFillFactoryService;

    private final BaseSingleTablePersistenceService baseSingleTablePersistenceService;

    public Long save(DynamicSaveReq req) {
        moduleDataSaveCheckFactoryService.check(ModuleDataCheckContext.builder().build());

        req.setRoot(TabTreeNodeDTO.buildRoot());

        Queue<DynamicSaveReqContext> saveReqQueue = new LinkedList<>();

        Long rootModuleId = req.getNestedModuleData().getModuleId();

        // 根模块数据 没有父模块和父模块主表ID
        saveReqQueue.add(
                DynamicSaveReqContext.builder()
                        .nestedModuleData(req.getNestedModuleData())
                        .parentModuleId(null)
                        .parentModulePrimaryTableId(null)
                        .rootModuleId(rootModuleId) // 第一层的 根模块就是自己的模块ID
                        .mainTableDataId(null)
                        .tabTreeNode(req.getRoot())
                        .build());
        boolean first = true;

        Long rootModulePrimaryId = null;

        while (!saveReqQueue.isEmpty()) {
            DynamicSaveReqContext currentReq = saveReqQueue.poll();

            // 模块数据为空
            if (currentReq == null
                    || currentReq.getNestedModuleData() == null
                    || CollectionUtil.isEmpty(currentReq.getNestedModuleData().getRecord())) {
                continue;
            }

            // 保存当前 模块数据 第一次进来  NestedModuleDataDTO.record 只有一条数据
            for (RecordDataDTO recordDataDTO : currentReq.getNestedModuleData().getRecord()) {

                // 保存 当前模块一行数据 主表数据只有一行
                Long resultId = saveModuleData(recordDataDTO, currentReq);

                // 捕获根模块的 主表记录ID
                if (first) {
                    rootModulePrimaryId = resultId;
                }

                // 如果当前模块有子模块的数据
                if (CollectionUtil.isEmpty(recordDataDTO.getSubModule())) {
                    recordDataDTO.getSubModule().stream()
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
                                                        .parentModulePrimaryTableId(resultId)
                                                        .tabTreeNode(req.getRoot())
                                                        .build());
                                    });
                }
                first = false;
            }
        }
        moduleDataSavePostFactoryService.postProcess(ModuleDataPostContext.builder().build());
        return rootModulePrimaryId;
    }

    private Long saveModuleData(RecordDataDTO recordDataDTO, DynamicSaveReqContext currentReq) {
        // 获取模块元数据
        SysModuleResp sysModuleCompleteResp =
                metadataCacheService.getModuleComplete(
                        currentReq.getNestedModuleData().getModuleId());

        // 模块数据
        if (CollectionUtil.isEmpty(recordDataDTO.getCombinedRow())) {
            log.error(
                    "模块id={},name={}记录数据为空",
                    sysModuleCompleteResp.getModule().getId(),
                    sysModuleCompleteResp.getModule().getModuleName());
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_SAVE_DATA_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_SAVE_DATA_ERROR.getMessage());
        }
        // 首先 写入当前 模块 主表的数据,主表数据 只有一行
        Long primaryTableId =
                saveModulePrimaryTableData(
                        recordDataDTO.getCombinedRow(), sysModuleCompleteResp, currentReq);

        // 在写入当前模块 关联的表数据
        saveModuleRelatedTableData(
                recordDataDTO.getCombinedRow(), sysModuleCompleteResp, primaryTableId, currentReq);

        return primaryTableId;
    }

    /**
     * 写入 模块主表数据
     *
     * @param dataMap 行记录
     * @param sysModuleCompleteResp 模块定义
     * @return
     */
    private Long saveModulePrimaryTableData(
            Map<String, Object> dataMap,
            SysModuleResp sysModuleCompleteResp,
            DynamicSaveReqContext currentReq) {

        Map<String, Object> parentLevelTableAssociationMap = new HashMap<>();
        // 首先获取父模块主表 与当前模块的主表 之间的关联关系，一般来说 父模块主表的主键字段 关联子模块的主表主表字段
        if (currentReq.getParentModuleId() == null) {
            // 没有父模块，说明是根模块
        } else {
            TableRelationDTO relationDTO =
                    TableRelationUtils.getTableRelationBySubTable(
                            currentReq.getTabTreeNode(),
                            sysModuleCompleteResp.getModule().getPrimaryTable());
            parentLevelTableAssociationMap.put(
                    relationDTO.getJoinField(), currentReq.getParentModulePrimaryTableId());
        }

        // 主表数据
        Map<String, Object> primaryTableRecordMap =
                DataPersistenceUtil.getTableRecordFromCombinedMap(
                        dataMap, sysModuleCompleteResp.getModule().getPrimaryTable());

        Long primaryTableId = DataPersistenceUtil.getRecordId(primaryTableRecordMap);

        RecordSaveContext recordSaveContext =
                RecordSaveContext.builder()
                        .dsl(jooqContextFactory.getContext())
                        .tableName(sysModuleCompleteResp.getModule().getPrimaryTable())
                        .parentLevelTable(null)
                        .record(primaryTableRecordMap)
                        .bizDataMap(null)
                        .parentLevelTableAssociationDataMap(parentLevelTableAssociationMap)
                        .parentLevelTableId(currentReq.getParentModulePrimaryTableId())
                        .defaultUserContextDataMap(null)
                        .isInsert(primaryTableId == null || primaryTableId <= 0)
                        .rootModuleId(currentReq.getRootModuleId())
                        .mainTableDataId(currentReq.getMainTableDataId())
                        .sysModuleCompleteResp(sysModuleCompleteResp)
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
            SysModuleResp sysModuleCompleteResp,
            Long modulePrimaryTableId,
            DynamicSaveReqContext currentReq) {
        Set<String> tables =
                metadataCacheService.getModuleTables(sysModuleCompleteResp.getModule().getId());
        // List<ModuleTableDTO> moduleTables = tables.stream().map(x->)

        for (String moduleTable : tables) {
            // 主表与当前表的关联关系
            TableRelationDTO relationDTO =
                    TableRelationUtils.getTableRelationBySubTable(
                            currentReq.getTabTreeNode(), moduleTable);

            RelationType relationType = RelationType.getRelationType(relationDTO.getRelationType());
            switch (relationType) {
                case ONE_TO_ONE, MANY_TO_ONE -> {
                    // 1:1
                    // 子表数据 是一行
                    Map<String, Object> subTableRecordMap =
                            DataPersistenceUtil.getTableRecordFromCombinedMap(dataMap, moduleTable);
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
                            DataPersistenceUtil.getTableRecordListFromCombinedMap(
                                    dataMap, moduleTable);

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
            SysModuleResp sysModuleCompleteResp,
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
