package com.jdec.platform.dataengine.biz.third.api.persistence.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.jdec.platform.dataengine.api.dto.model.*;
import com.jdec.platform.dataengine.api.dto.request.ApprovalStatusCallBackReq;
import com.jdec.platform.dataengine.api.dto.request.DynamicDetailQueryReq;
import com.jdec.platform.dataengine.api.dto.request.DynamicSaveReq;
import com.jdec.platform.dataengine.api.dto.response.ApprovalProcessResultResp;
import com.jdec.platform.dataengine.biz.constants.CommonConstant;
import com.jdec.platform.dataengine.biz.dsl.JooqContextFactory;
import com.jdec.platform.dataengine.biz.dto.SysModuleCombineInfo;
import com.jdec.platform.dataengine.biz.dto.context.DynamicSaveReqContext;
import com.jdec.platform.dataengine.biz.dto.context.ModuleQueryContext;
import com.jdec.platform.dataengine.biz.entity.ModuleDataSnapshotContent;
import com.jdec.platform.dataengine.biz.enums.ApiResponseCodeEnum;
import com.jdec.platform.dataengine.biz.service.*;
import com.jdec.platform.dataengine.biz.third.api.persistence.DataPersistenceUtil;
import com.jdec.platform.dataengine.biz.third.api.persistence.ModuleDataApprovalBizService;
import com.jdec.platform.dataengine.biz.third.api.persistence.ModuleDataPersistenceService;
import com.jdec.platform.dataengine.biz.util.JsonUtil;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModuleDataApprovalBizServiceImpl implements ModuleDataApprovalBizService {

    private final ModuleMetaDataService moduleMetaDataService;

    private final ModuleDataPersistenceService moduleDataPersistenceService;

    private final DynamicQueryService dynamicQueryService;

    private final JooqContextFactory jooqContextFactory;

    /**
     * 审批链状态 回调处理
     *
     * @param req
     * @return
     */
    public ApprovalProcessResultResp processApprovalStatus(ApprovalStatusCallBackReq req) {

        Map<Long, SysModuleCombineInfo> moduleCacheMap =
                moduleMetaDataService.getModuleMetaDataMap(
                        ModuleQueryContext.builder()
                                .moduleId(req.getModuleId())
                                .projectNo(AppContext.getProjectNo())
                                .subjectId(AppContext.getSubjectId())
                                .roleId(UserContext.getRoleId())
                                .build());
        SysModuleCombineInfo rootModule = moduleCacheMap.get(req.getModuleId());
        if (rootModule == null) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getMessage());
        }
        DSLContext context = jooqContextFactory.getContext();

        Long rootModuleMainTableId = saveModuleApprovalData(req, rootModule);
        if (rootModuleMainTableId == null || rootModuleMainTableId.equals(0L)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_SAVE_DATA_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_SAVE_DATA_ERROR.getMessage());
        }

        // 获取当前模块数据的快照
        NestedModuleDataDTO result = getCurrentModuleDataSnapshot(req, rootModule, moduleCacheMap);

        // 获取上一个状态的 快照数据
        NestedModuleDataDTO tmpSnapshot = getModuleDataTempSnapshot(req, rootModule, context);

        // 保存字段快照
        //
        saveModuleFieldDataSnapshot(req, rootModule, result, context, moduleCacheMap);

        // 保存每个状态的快照，用于计算 审批的 操作日志
        saveModuleDataTempSnapshot(req, rootModule, result, context);

        // 如果是终态 保存模块数据的 版本快照
        if (req.getIsTermination() != null && req.getIsTermination()) {
            saveModuleDataPermanentSnapshot(req, rootModule, result, context);
        }

        // todo  result   tmpSnapshot 要构建 版本间的字段 对比 返回给审批链
        ApprovalProcessResultResp resp =
                ApprovalProcessResultResp.builder().nestedModuleData(result).build();
        return resp;
    }

    private Long saveModuleApprovalData(
            ApprovalStatusCallBackReq req, SysModuleCombineInfo rootModule) {
        Map<String, Object> row = new HashMap<>();
        row.put(
                DataPersistenceUtil.getFullFieldName(
                        rootModule.getModuleInfo().getPrimaryTable(),
                        CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD),
                req.getDataId());
        row.put(
                DataPersistenceUtil.getFullFieldName(
                        rootModule.getModuleInfo().getPrimaryTable(),
                        CommonConstant.TABLE_DEFAULT_BUSINESS_STATUS_FIELD),
                req.getBusinessStatus());

        // 第一次提交，要更新 登记时间
        if (req.getIsFirstSubmit() != null && req.getIsFirstSubmit()) {
            row.put(
                    DataPersistenceUtil.getFullFieldName(
                            rootModule.getModuleInfo().getPrimaryTable(),
                            CommonConstant.TABLE_DEFAULT_REGISTRATION_TIME_FIELD),
                    req.getRegistrationTime());
        }

        RecordDataDTO recordDataDTO = new RecordDataDTO();
        recordDataDTO.setRow(row);
        List<RecordDataDTO> recordDataDTOList = new ArrayList<>();
        recordDataDTOList.add(recordDataDTO);
        NestedModuleDataDTO nestedModuleDataDTO = new NestedModuleDataDTO();
        nestedModuleDataDTO.setModuleId(rootModule.getModuleInfo().getModuleId());
        nestedModuleDataDTO.setRecord(recordDataDTOList);
        DynamicSaveReq saveReq = new DynamicSaveReq();
        saveReq.setNestedModuleData(nestedModuleDataDTO);
        // 更更新 业务数据的 审批状态
        Long rootModuleMainTableId = moduleDataPersistenceService.save(saveReq);
        return rootModuleMainTableId;
    }

    private NestedModuleDataDTO getCurrentModuleDataSnapshot(
            ApprovalStatusCallBackReq req,
            SysModuleCombineInfo rootModule,
            Map<Long, SysModuleCombineInfo> moduleCacheMap) {
        // 查询 根模块及其子模的 信息
        Set<Long> filterField = new HashSet<>();
        // 把所有模块的字段加进去
        for (Long mId : moduleCacheMap.keySet()) {
            SysModuleCombineInfo sysModuleInfo = moduleCacheMap.get(mId);
            sysModuleInfo.getModuleInfo().getFields().stream()
                    .map(x -> x.getId())
                    .forEach(filterField::add);
        }
        // 终态需要切 快照保存
        NestedModuleDataDTO result = null;
        DynamicDetailQueryReq queryReq = new DynamicDetailQueryReq();
        queryReq.setModuleId(rootModule.getModuleInfo().getModuleId());
        queryReq.setId(req.getDataId());
        queryReq.setVersion(req.getVersionNo());
        queryReq.setFilterFields(new ArrayList<>(filterField));
        queryReq.setShowVersionCompare(false);

        List<NestedModuleDataDTO> nestedModuleDataDTOList =
                dynamicQueryService.getDetail(queryReq, null);
        result = nestedModuleDataDTOList.get(0);
        return result;
    }

    /**
     * 保存模块数据永久的快照,每一个版本的终态 ，会保存一个
     *
     * @param req
     * @param rootModule
     * @param nestedModuleDataDTO
     * @return
     */
    private Object saveModuleDataPermanentSnapshot(
            ApprovalStatusCallBackReq req,
            SysModuleCombineInfo rootModule,
            NestedModuleDataDTO nestedModuleDataDTO,
            DSLContext context) {
        Object versionNo =
                DataPersistenceUtil.getTableRowFieldValue(
                        nestedModuleDataDTO.getRecord().get(0).getRow(),
                        DataPersistenceUtil.getFullFieldName(
                                rootModule.getModuleInfo().getPrimaryTable(),
                                CommonConstant.TABLE_DEFAULT_VERSION_NO_FIELD));
        Object businessStatus =
                DataPersistenceUtil.getTableRowFieldValue(
                        nestedModuleDataDTO.getRecord().get(0).getRow(),
                        DataPersistenceUtil.getFullFieldName(
                                rootModule.getModuleInfo().getPrimaryTable(),
                                CommonConstant.TABLE_DEFAULT_BUSINESS_STATUS_FIELD));

        ModuleDataSnapshotContent moduleDataSnapshotContent = new ModuleDataSnapshotContent();
        try {
            moduleDataSnapshotContent.setRootModuleId(rootModule.getModuleInfo().getModuleId());
            moduleDataSnapshotContent.setRootTableDataId(req.getDataId());
            moduleDataSnapshotContent.setRootTableDataVersion((Integer) versionNo); // Changed from
            moduleDataSnapshotContent.setBusinessStatus((Integer) businessStatus);
            moduleDataSnapshotContent.setContent(JsonUtil.obj2Json(nestedModuleDataDTO));
            moduleDataSnapshotContent.setComparison(null);
            moduleDataSnapshotContent.setCreateDate(LocalDateTime.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Map<Field<Object>, Object> fieldValues =
                getRecordFieldValues(moduleDataSnapshotContent, true);

        String tableName = CommonConstant.TABLE_NAME_MODULE_DATA_SNAPSHOT_CONTENT;
        // 手动定义表和主键字段，标记IDENTITY
        // 1. 构造【带自增标记】的数据类型
        DataType<Long> autoIncLong = SQLDataType.BIGINT.identity(true);

        // 2. 用这个DataType创建field，等价旧版 .identity(true)
        Field<Long> pkField =
                DSL.field(DSL.name(CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD), autoIncLong);

        Table<?> dynamicTable = DSL.table(DSL.name(tableName));
        // Insert 并获取自增主键
        Record inserted =
                context.insertInto(dynamicTable).set(fieldValues).returning(pkField).fetchOne();

        return inserted.getValue(CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD);
    }

    /**
     * 保存模块数据临时的快照,每个状态回保存 ，会保存一个
     *
     * @param req
     * @param rootModule
     * @param nestedModuleDataDTO
     * @return
     */
    private Object saveModuleDataTempSnapshot(
            ApprovalStatusCallBackReq req,
            SysModuleCombineInfo rootModule,
            NestedModuleDataDTO nestedModuleDataDTO,
            DSLContext context) {
        Object versionNo =
                DataPersistenceUtil.getTableRowFieldValue(
                        nestedModuleDataDTO.getRecord().get(0).getRow(),
                        DataPersistenceUtil.getFullFieldName(
                                rootModule.getModuleInfo().getPrimaryTable(),
                                CommonConstant.TABLE_DEFAULT_VERSION_NO_FIELD));
        Object businessStatus =
                DataPersistenceUtil.getTableRowFieldValue(
                        nestedModuleDataDTO.getRecord().get(0).getRow(),
                        DataPersistenceUtil.getFullFieldName(
                                rootModule.getModuleInfo().getPrimaryTable(),
                                CommonConstant.TABLE_DEFAULT_BUSINESS_STATUS_FIELD));

        ModuleDataSnapshotContent moduleDataSnapshotContent = new ModuleDataSnapshotContent();
        try {
            moduleDataSnapshotContent.setRootModuleId(rootModule.getModuleInfo().getModuleId());
            moduleDataSnapshotContent.setRootTableDataId(req.getDataId());
            moduleDataSnapshotContent.setRootTableDataVersion((Integer) versionNo); // Changed from
            moduleDataSnapshotContent.setBusinessStatus((Integer) businessStatus);
            moduleDataSnapshotContent.setContent(JsonUtil.obj2Json(nestedModuleDataDTO));
            moduleDataSnapshotContent.setComparison(null);
            moduleDataSnapshotContent.setCreateDate(LocalDateTime.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Map<Field<Object>, Object> fieldValues =
                getRecordFieldValues(moduleDataSnapshotContent, true);

        String tableName = CommonConstant.TABLE_NAME_MODULE_DATA_TMP_SNAPSHOT_CONTENT;

        Record oldRecord =
                context.select(DSL.asterisk())
                        .from(DSL.table(tableName))
                        .where(
                                DSL.field(DSL.name("root_module_id"))
                                        .eq(moduleDataSnapshotContent.getRootModuleId()))
                        .and(
                                DSL.field(DSL.name("root_table_data_id"))
                                        .eq(moduleDataSnapshotContent.getRootTableDataId()))
                        .and(
                                DSL.field(DSL.name("root_table_data_version"))
                                        .eq(moduleDataSnapshotContent.getRootTableDataVersion()))
                        .fetchOne();
        // 没有 就新增
        if (oldRecord == null) {
            // 手动定义表和主键字段，标记IDENTITY
            // 1. 构造【带自增标记】的数据类型
            DataType<Long> autoIncLong = SQLDataType.BIGINT.identity(true);

            // 2. 用这个DataType创建field，等价旧版 .identity(true)
            Field<Long> pkField =
                    DSL.field(
                            DSL.name(CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD), autoIncLong);

            Table<?> dynamicTable = DSL.table(DSL.name(tableName));
            // Insert 并获取自增主键
            Record inserted =
                    context.insertInto(dynamicTable).set(fieldValues).returning(pkField).fetchOne();
            return inserted.getValue(CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD);
        } else {

            int effect =
                    context.update(DSL.table(tableName))
                            .set(fieldValues)
                            .where(
                                    DSL.field(DSL.name("root_module_id"))
                                            .eq(moduleDataSnapshotContent.getRootModuleId()))
                            .and(
                                    DSL.field(DSL.name("root_table_data_id"))
                                            .eq(moduleDataSnapshotContent.getRootTableDataId()))
                            .and(
                                    DSL.field(DSL.name("root_table_data_version"))
                                            .eq(
                                                    moduleDataSnapshotContent
                                                            .getRootTableDataVersion()))
                            .execute();
            return effect;
        }
    }

    // 获取模块 状态的 快照数据
    private NestedModuleDataDTO getModuleDataTempSnapshot(
            ApprovalStatusCallBackReq req, SysModuleCombineInfo rootModule, DSLContext context) {

        String tableName = CommonConstant.TABLE_NAME_MODULE_DATA_TMP_SNAPSHOT_CONTENT;
        Record oldRecord =
                context.select(DSL.asterisk())
                        .from(DSL.table(tableName))
                        .where(
                                DSL.field(DSL.name("root_module_id"))
                                        .eq(rootModule.getModuleInfo().getModuleId()))
                        .and(DSL.field(DSL.name("root_table_data_id")).eq(req.getDataId()))
                        .and(DSL.field(DSL.name("root_table_data_version")).eq(req.getVersionNo()))
                        .fetchOne();
        if (oldRecord == null) {
            return null;
        }
        if (oldRecord.get(DSL.field(DSL.name("content"))) == null) {
            return null;
        }

        NestedModuleDataDTO result = null;
        try {
            result =
                    JsonUtil.json2Obj(
                            oldRecord.get(DSL.field(DSL.name("content")), String.class),
                            new TypeReference<NestedModuleDataDTO>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    private Object saveModuleFieldDataSnapshot(
            ApprovalStatusCallBackReq req,
            SysModuleCombineInfo rootModule,
            NestedModuleDataDTO nestedModuleDataDTO,
            DSLContext context,
            Map<Long, SysModuleCombineInfo> moduleCacheMap) {

        if (nestedModuleDataDTO == null
                || CollectionUtil.isEmpty(nestedModuleDataDTO.getRecord())) {
            return null;
        }

        Long rootModuleId = req.getModuleId();
        Long rootTableDataId = req.getDataId();
        Integer rootTableDataVersion = req.getVersionNo();

        Queue<DynamicSaveReqContext> saveReqQueue = new LinkedList<>();
        // 构建根节点数据
        saveReqQueue.add(
                DynamicSaveReqContext.builder()
                        .nestedModuleData(nestedModuleDataDTO)
                        .parentModuleId(null)
                        .parentModulePrimaryTableId(null)
                        .rootModuleId(rootModuleId) //
                        .rootModuleMainTableDataId(rootTableDataId)
                        .rootModuleMainTableDataVersion(rootTableDataVersion)
                        .rootModuleMainTableDataStatus(null)
                        .build());
        boolean first = true;

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
                saveModuleFieldValueSnapshot(recordDataDTO, currentReq, moduleCacheMap, context);

                // 如果当前模块有子模块的数据
                if (CollectionUtil.isNotEmpty(recordDataDTO.getChildren())) {
                    recordDataDTO.getChildren().stream()
                            .forEach(
                                    subModule -> {
                                        // 构造保存子模的数据的上下文
                                        saveReqQueue.add(
                                                DynamicSaveReqContext.builder()
                                                        .nestedModuleData(subModule)
                                                        .rootModuleId(rootModuleId) //
                                                        .rootModuleMainTableDataId(rootTableDataId)
                                                        .rootModuleMainTableDataVersion(
                                                                rootTableDataVersion)
                                                        .build());
                                    });
                }
                first = false;
            }
        }
        return null;
    }

    private Object saveModuleFieldValueSnapshot(
            RecordDataDTO recordDataDTO,
            DynamicSaveReqContext currentReq,
            Map<Long, SysModuleCombineInfo> moduleCacheMap,
            DSLContext context) {

        Long currentModuleId = currentReq.getNestedModuleData().getModuleId();

        SysModuleCombineInfo currentModule = moduleCacheMap.get(currentModuleId);

        String primaryTable = currentModule.getModuleInfo().getPrimaryTable();

        Map<String, Object> rowMap =
                DataPersistenceUtil.getTableRowMapFromWideRow(recordDataDTO.getRow(), primaryTable);

        Long id = DataPersistenceUtil.getTableRowId(rowMap);

        for (String field : rowMap.keySet()) {

            if (CommonConstant.NONE_DIFF_FIELD_SET.contains(field)) {
                continue;
            }

            String currentValue = DataPersistenceUtil.getFieldTxtValue(rowMap.get(field));

            Record oldRecord =
                    context.select(DSL.asterisk())
                            .from(DSL.table(primaryTable))
                            .where(
                                    DSL.field(DSL.name("root_module_id"))
                                            .eq(currentReq.getRootModuleId()))
                            .and(
                                    DSL.field(DSL.name("root_table_data_id"))
                                            .eq(currentReq.getRootModuleMainTableDataId()))
                            .and(
                                    DSL.field(DSL.name("root_table_data_version"))
                                            .eq(currentReq.getRootModuleMainTableDataVersion()))
                            .and(DSL.field(DSL.name("module_id")).eq(currentModuleId))
                            .and(DSL.field(DSL.name("table_name")).eq(primaryTable))
                            .and(DSL.field(DSL.name("id")).eq(id))
                            .and(DSL.field(DSL.name("field_id")).eq(field))
                            .fetchOne();

            Long recordId =
                    oldRecord.getValue(CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD, Long.class);

            // 如果没有 s1 快照 直接保存 s1快照
            if (oldRecord == null) {

                // 如果没有值，不保存
                if (currentValue != null) {
                    context.insertInto(DSL.table(primaryTable))
                            .set(
                                    DSL.field(DSL.name("root_module_id")),
                                    currentReq.getRootModuleId())
                            .set(
                                    DSL.field(DSL.name("root_table_data_id")),
                                    currentReq.getRootModuleMainTableDataId())
                            .set(
                                    DSL.field(DSL.name("root_table_data_version")),
                                    currentReq.getRootModuleMainTableDataVersion())
                            .set(DSL.field(DSL.name("module_id")), currentModuleId)
                            .set(DSL.field(DSL.name("table_name")), primaryTable)
                            .set(DSL.field(DSL.name("id")), id)
                            .set(DSL.field(DSL.name("field_id")), field)
                            .set(
                                    DSL.field(DSL.name("first_snapshot")),
                                    DataPersistenceUtil.getFieldWriteValue(rowMap.get(field)))
                            .execute();
                }
            } else {

                // S2 是否为空
                String firstSnapShot =
                        oldRecord.get(DSL.field(DSL.name("first_snapshot")), String.class);
                String secondSnapShot =
                        oldRecord.get(DSL.field(DSL.name("second_snapshot")), String.class);
                if (secondSnapShot == null) {
                    // 当前值 与 S1 快照 不相等, 保存 S2 快照
                    if (!DataPersistenceUtil.isEqualValue(currentValue, firstSnapShot)) {
                        context.update(DSL.table(primaryTable))
                                .set(DSL.field(DSL.name("second_snapshot")), currentValue)
                                .where(DSL.field(DSL.name("id")).eq(recordId))
                                .execute();
                    }
                } else {
                    if (!DataPersistenceUtil.isEqualValue(currentValue, secondSnapShot)) {
                        context.update(DSL.table(primaryTable))
                                .set(DSL.field(DSL.name("first_snapshot")), secondSnapShot)
                                .set(DSL.field(DSL.name("second_snapshot")), currentValue)
                                .where(DSL.field(DSL.name("id")).eq(recordId))
                                .execute();
                    }
                }
            }
        }
        return null;
    }

    private Map<Field<Object>, Object> getRecordFieldValues(
            ModuleDataSnapshotContent content, boolean isInsert) {
        Map<Field<Object>, Object> result = new HashMap<>();
        result.put(DSL.field(DSL.name("root_module_id")), content.getRootModuleId());
        result.put(DSL.field(DSL.name("root_table_data_id")), content.getRootTableDataId());
        result.put(
                DSL.field(DSL.name("root_table_data_version")), content.getRootTableDataVersion());
        result.put(DSL.field(DSL.name("business_status")), content.getBusinessStatus());
        result.put(DSL.field(DSL.name("content")), content.getContent());
        result.put(DSL.field(DSL.name("comparison")), content.getComparison());
        result.put(DSL.field(DSL.name("created_date")), content.getCreateDate());
        return result;
    }

    private List<ModuleChangeDTO> buildModuleChangeDTO(
            NestedModuleDataDTO nestedModuleDataDTO, Map<Long, SysModuleInfo> moduleCacheMap) {

        Long moduleId = nestedModuleDataDTO.getModuleId();
        SysModuleInfo sysModule = null;
        if (moduleCacheMap.containsKey(moduleId)) {
            sysModule = moduleCacheMap.get(moduleId);
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
            //            if (CollectionUtil.isEmpty(recordDataDTO.getRowChanges())) {
            //
            //                ActionChangeDTO Action = new ActionChangeDTO();
            //
            //                RowChangeDTO r = new RowChangeDTO();
            //
            //                if (CollectionUtil.isEmpty(recordDataDTO.getChildren())) {
            //                    r.setChildren(new ArrayList<>());
            //                    List<RowChangeDTO> child = new ArrayList<>();
            //                    child.add(r);
            //                    Action.setU(child);
            //                    moduleChangeDTO.setActions(Action);
            //                    result.add(moduleChangeDTO);
            //                    return result;
            //                }
            //            }

            // RowChangeDTO rowChangeDTO = recordDataDTO.getRowChanges().get(0);
        }
        result.add(moduleChangeDTO);

        return result;

        ////
        // moduleChangeDTO.setActions(buildActionChangeDTO(nestedModuleDataDTO,sysModule,moduleCacheMap));

    }
}
