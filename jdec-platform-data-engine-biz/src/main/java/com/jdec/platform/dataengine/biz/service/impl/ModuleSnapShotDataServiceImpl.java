package com.jdec.platform.dataengine.biz.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.dataengine.biz.constants.CommonConstant;
import com.jdec.platform.dataengine.biz.dto.SysModuleCombineInfo;
import com.jdec.platform.dataengine.biz.dto.context.DynamicSaveReqContext;
import com.jdec.platform.dataengine.biz.dto.context.ModuleSnapShotDataContext;
import com.jdec.platform.dataengine.biz.entity.ModuleDataSnapshotContent;
import com.jdec.platform.dataengine.biz.service.ModuleSnapShotDataService;
import com.jdec.platform.dataengine.biz.third.api.persistence.DataPersistenceUtil;
import com.jdec.platform.dataengine.biz.util.JsonUtil;
import com.jdec.platform.shared.datasource.JooqDataContextFactory;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@AllArgsConstructor
public class ModuleSnapShotDataServiceImpl implements ModuleSnapShotDataService {

    private final JooqDataContextFactory jooqDataContextFactory;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveModuleDataPermanentSnapshot(ModuleSnapShotDataContext context) {
        Object versionNo =
                DataPersistenceUtil.getTableRowFieldValue(
                        context.getModuleDataDTO().getRecord().get(0).getRow(),
                        DataPersistenceUtil.getFullFieldName(
                                context.getRootModuleInfo().getModuleInfo().getPrimaryTable(),
                                CommonConstant.TABLE_DEFAULT_VERSION_NO_FIELD));
        Object businessStatus =
                DataPersistenceUtil.getTableRowFieldValue(
                        context.getModuleDataDTO().getRecord().get(0).getRow(),
                        DataPersistenceUtil.getFullFieldName(
                                context.getRootModuleInfo().getModuleInfo().getPrimaryTable(),
                                CommonConstant.TABLE_DEFAULT_BUSINESS_STATUS_FIELD));

        ModuleDataSnapshotContent moduleDataSnapshotContent = new ModuleDataSnapshotContent();
        try {
            moduleDataSnapshotContent.setRootModuleId(
                    context.getRootModuleInfo().getModuleInfo().getModuleId());
            moduleDataSnapshotContent.setRootTableDataId(context.getDataId());
            moduleDataSnapshotContent.setRootTableDataVersion((Integer) versionNo); // Changed from
            moduleDataSnapshotContent.setBusinessStatus((Integer) businessStatus);
            moduleDataSnapshotContent.setContent(JsonUtil.obj2Json(context.getModuleDataDTO()));
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
        DSLContext dslContext =
                context.getDslContext() != null
                        ? context.getDslContext()
                        : jooqDataContextFactory.getContext();
        // Insert 并获取自增主键
        Record inserted =
                dslContext.insertInto(dynamicTable).set(fieldValues).returning(pkField).fetchOne();

        return inserted.getValue(CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD, Long.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NestedModuleDataDTO getLastStatusModuleDataTempSnapshot(
            ModuleSnapShotDataContext context) {
        String tableName = CommonConstant.TABLE_NAME_MODULE_DATA_TMP_SNAPSHOT_CONTENT;
        DSLContext dslContext =
                context.getDslContext() != null
                        ? context.getDslContext()
                        : jooqDataContextFactory.getContext();
        Record oldRecord =
                dslContext
                        .select(DSL.asterisk())
                        .from(DSL.table(tableName))
                        .where(
                                DSL.field(DSL.name("root_module_id"))
                                        .eq(
                                                context.getRootModuleInfo()
                                                        .getModuleInfo()
                                                        .getModuleId()))
                        .and(DSL.field(DSL.name("root_table_data_id")).eq(context.getDataId()))
                        .and(
                                DSL.field(DSL.name("root_table_data_version"))
                                        .eq(context.getVersionNo()))
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveModuleDataTempSnapshot(ModuleSnapShotDataContext context) {
        Object versionNo =
                DataPersistenceUtil.getTableRowFieldValue(
                        context.getModuleDataDTO().getRecord().get(0).getRow(),
                        DataPersistenceUtil.getFullFieldName(
                                context.getRootModuleInfo().getModuleInfo().getPrimaryTable(),
                                CommonConstant.TABLE_DEFAULT_VERSION_NO_FIELD));
        Object businessStatus =
                DataPersistenceUtil.getTableRowFieldValue(
                        context.getModuleDataDTO().getRecord().get(0).getRow(),
                        DataPersistenceUtil.getFullFieldName(
                                context.getRootModuleInfo().getModuleInfo().getPrimaryTable(),
                                CommonConstant.TABLE_DEFAULT_BUSINESS_STATUS_FIELD));

        ModuleDataSnapshotContent moduleDataSnapshotContent = new ModuleDataSnapshotContent();
        try {
            moduleDataSnapshotContent.setRootModuleId(
                    context.getRootModuleInfo().getModuleInfo().getModuleId());
            moduleDataSnapshotContent.setRootTableDataId(context.getDataId());
            moduleDataSnapshotContent.setRootTableDataVersion((Integer) versionNo); // Changed from
            moduleDataSnapshotContent.setBusinessStatus((Integer) businessStatus);
            moduleDataSnapshotContent.setContent(JsonUtil.obj2Json(context.getModuleDataDTO()));
            moduleDataSnapshotContent.setComparison(null);
            moduleDataSnapshotContent.setCreateDate(LocalDateTime.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Map<Field<Object>, Object> fieldValues =
                getRecordFieldValues(moduleDataSnapshotContent, true);

        String tableName = CommonConstant.TABLE_NAME_MODULE_DATA_TMP_SNAPSHOT_CONTENT;
        DSLContext dslContext =
                context.getDslContext() != null
                        ? context.getDslContext()
                        : jooqDataContextFactory.getContext();

        Record oldRecord =
                dslContext
                        .select(DSL.asterisk())
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
                    dslContext
                            .insertInto(dynamicTable)
                            .set(fieldValues)
                            .returning(pkField)
                            .fetchOne();
            return inserted.getValue(CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD, Long.class);
        } else {

            int effect =
                    dslContext
                            .update(DSL.table(tableName))
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
            return oldRecord.get(CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD, Long.class);
        }
    }

    @Override
    public int deleteModuleDataTempSnapshot(ModuleSnapShotDataContext context) {
        String tableName = CommonConstant.TABLE_NAME_MODULE_DATA_TMP_SNAPSHOT_CONTENT;
        DSLContext dslContext =
                context.getDslContext() != null
                        ? context.getDslContext()
                        : jooqDataContextFactory.getContext();
        int effect =
                dslContext
                        .deleteFrom(DSL.table(tableName))
                        .where(DSL.field(DSL.name("root_module_id")).eq(context.getRootModuleId()))
                        .and(DSL.field(DSL.name("root_table_data_id")).eq(context.getDataId()))
                        .execute();
        return effect;
    }

    @Override
    public Long saveModuleFieldDataSnapshot(ModuleSnapShotDataContext context) {
        Long rootModuleId = context.getRootModuleId();
        Long rootTableDataId = context.getDataId();
        Integer rootTableDataVersion = context.getVersionNo();

        Queue<DynamicSaveReqContext> saveReqQueue = new LinkedList<>();
        // 构建根节点数据
        saveReqQueue.add(
                DynamicSaveReqContext.builder()
                        .nestedModuleData(context.getModuleDataDTO())
                        .parentModuleId(null)
                        .parentModuleMainTableDataId(null)
                        .rootModuleId(rootModuleId) //
                        .rootModuleMainTableDataId(rootTableDataId)
                        .rootModuleMainTableDataVersion(rootTableDataVersion)
                        .build());
        boolean first = true;

        DSLContext dslContext =
                context.getDslContext() != null
                        ? context.getDslContext()
                        : jooqDataContextFactory.getContext();

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
                saveModuleFieldValueSnapshot(
                        recordDataDTO, currentReq, context.getMoudleCacheMap(), dslContext);

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

    @Override
    public int deleteModuleFieldDataSnapshot(ModuleSnapShotDataContext context) {
        String tableName = CommonConstant.TABLE_NAME_MODULE_DATA_FIELD_CHANGE_CONTENT;
        DSLContext dslContext =
                context.getDslContext() != null
                        ? context.getDslContext()
                        : jooqDataContextFactory.getContext();
        int effect =
                dslContext
                        .deleteFrom(DSL.table(tableName))
                        .where(DSL.field(DSL.name("root_module_id")).eq(context.getRootModuleId()))
                        .and(DSL.field(DSL.name("root_table_data_id")).eq(context.getDataId()))
                        .execute();
        return effect;
    }

    private Object saveModuleFieldValueSnapshot(
            RecordDataDTO recordDataDTO,
            DynamicSaveReqContext currentReq,
            Map<Long, SysModuleCombineInfo> moduleCacheMap,
            DSLContext context) {

        Long currentModuleId = currentReq.getNestedModuleData().getModuleId();

        SysModuleCombineInfo currentModule = moduleCacheMap.get(currentModuleId);

        String primaryTable = currentModule.getModuleInfo().getPrimaryTable();
        if (StringUtils.isEmpty(primaryTable)) {
            return null;
        }

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
                            .from(
                                    DSL.table(
                                            CommonConstant
                                                    .TABLE_NAME_MODULE_DATA_FIELD_CHANGE_CONTENT))
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
                            .and(DSL.field(DSL.name("data_id")).eq(id))
                            .and(DSL.field(DSL.name("field_id")).eq(field))
                            .fetchOne();

            // 如果没有 s1 快照 直接保存 s1快照
            if (oldRecord == null) {

                // 如果没有值，不保存
                if (currentValue != null) {
                    context.insertInto(
                                    DSL.table(
                                            CommonConstant
                                                    .TABLE_NAME_MODULE_DATA_FIELD_CHANGE_CONTENT))
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
                            .set(DSL.field(DSL.name("data_id")), id)
                            .set(DSL.field(DSL.name("field_id")), field)
                            .set(
                                    DSL.field(DSL.name("first_snapshot")),
                                    DataPersistenceUtil.getFieldTxtValue(rowMap.get(field)))
                            .set(DSL.field(DSL.name("created_date")), LocalDateTime.now())
                            .execute();
                }
            } else {

                Long recordId =
                        oldRecord.getValue(
                                CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD, Long.class);

                // S2 是否为空
                String firstSnapShot =
                        oldRecord.get(DSL.field(DSL.name("first_snapshot")), String.class);
                String secondSnapShot =
                        oldRecord.get(DSL.field(DSL.name("second_snapshot")), String.class);
                if (secondSnapShot == null) {
                    // 当前值 与 S1 快照 不相等, 保存 S2 快照
                    if (!DataPersistenceUtil.isEqualValue(currentValue, firstSnapShot)) {
                        context.update(
                                        DSL.table(
                                                CommonConstant
                                                        .TABLE_NAME_MODULE_DATA_FIELD_CHANGE_CONTENT))
                                .set(DSL.field(DSL.name("second_snapshot")), currentValue)
                                .set(DSL.field(DSL.name("created_date")), LocalDateTime.now())
                                .where(DSL.field(DSL.name("id")).eq(recordId))
                                .execute();
                    }
                } else {
                    if (!DataPersistenceUtil.isEqualValue(currentValue, secondSnapShot)) {
                        context.update(
                                        DSL.table(
                                                CommonConstant
                                                        .TABLE_NAME_MODULE_DATA_FIELD_CHANGE_CONTENT))
                                .set(DSL.field(DSL.name("first_snapshot")), secondSnapShot)
                                .set(DSL.field(DSL.name("second_snapshot")), currentValue)
                                .set(DSL.field(DSL.name("created_date")), LocalDateTime.now())
                                .where(DSL.field(DSL.name("id")).eq(recordId))
                                .execute();
                    }
                }
            }
        }
        return null;
    }
}
