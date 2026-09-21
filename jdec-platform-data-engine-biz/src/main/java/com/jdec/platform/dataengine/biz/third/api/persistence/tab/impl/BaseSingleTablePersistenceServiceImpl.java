package com.jdec.platform.dataengine.biz.third.api.persistence.tab.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.jdec.platform.dataengine.api.dto.model.FieldChangeDTO;
import com.jdec.platform.dataengine.api.dto.model.ModuleFieldDTO;
import com.jdec.platform.dataengine.api.dto.model.RowChangeDTO;
import com.jdec.platform.dataengine.biz.constants.CommonConstant;
import com.jdec.platform.dataengine.biz.context.ModuleDataSaveContextHolder;
import com.jdec.platform.dataengine.biz.dto.DataSaveResult;
import com.jdec.platform.dataengine.biz.dto.context.RecordSaveContext;
import com.jdec.platform.dataengine.biz.entity.ModuleDataChangeContent;
import com.jdec.platform.dataengine.biz.entity.ModuleDataFieldChangeContent;
import com.jdec.platform.dataengine.biz.enums.ApiResponseCodeEnum;
import com.jdec.platform.dataengine.biz.mapper.ModuleDataChangeContentMapper;
import com.jdec.platform.dataengine.biz.service.IModuleDataFieldChangeContentService;
import com.jdec.platform.dataengine.biz.third.api.persistence.DataPersistenceUtil;
import com.jdec.platform.dataengine.biz.third.api.persistence.fill.ModuleDataSaveNecessaryFieldFillFactoryService;
import com.jdec.platform.dataengine.biz.third.api.persistence.tab.BaseSingleTablePersistenceService;
import com.jdec.platform.dataengine.biz.util.JsonUtil;
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

@Service
@Slf4j
@RequiredArgsConstructor
public class BaseSingleTablePersistenceServiceImpl implements BaseSingleTablePersistenceService {

    private final ModuleDataChangeContentMapper moduleDataChangeContentMapper;

    private final IModuleDataFieldChangeContentService iModuleDataFieldChangeContentService;

    private final ModuleDataSaveNecessaryFieldFillFactoryService
            moduleDataSaveNecessaryFieldFillFactoryService;

    @Override
    public DataSaveResult insertRecord(RecordSaveContext context) {
        if (CollectionUtil.isEmpty(context.getRecord())) {
            return DataSaveResult.builder().dataId(0L).build();
        }
        context.setDefaultExcludeFields(new HashSet<>());
        moduleDataSaveNecessaryFieldFillFactoryService.fillData(context);
        Map<Field<Object>, Object> fieldValues =
                getRecordFieldValues(
                        context,
                        DataPersistenceUtil.getModuleTableFieldMap(
                                context.getSysModuleCompleteResp().getFieldList(),
                                context.getTableName()));

        String tableName = context.getTableName();
        // 手动定义表和主键字段，标记IDENTITY
        // 1. 构造【带自增标记】的数据类型
        DataType<Long> autoIncLong = SQLDataType.BIGINT.identity(true);

        // 2. 用这个DataType创建field，等价旧版 .identity(true)
        Field<Long> pkField =
                DSL.field(DSL.name(CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD), autoIncLong);

        Table<?> dynamicTable = DSL.table(DSL.name(tableName));
        // Insert 并获取自增主键
        Record inserted =
                context.getDsl()
                        .insertInto(dynamicTable)
                        .set(fieldValues)
                        .returning(pkField)
                        .fetchOne();
        //
        //        Long pkk =
        //                context.getDsl()
        //                        .select(DSL.field("last_insert_id()", Long.class))
        //                        .fetchOneInto(Long.class);

        if (inserted != null) {
            Object newId = inserted.getValue(CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD);
            if (newId instanceof Number num) {
                Record insertedMap =
                        context.getDsl()
                                .select(DSL.asterisk())
                                .from(DSL.table(DSL.name(context.getTableName())))
                                .where(
                                        DSL.field(
                                                        DSL.name(
                                                                CommonConstant
                                                                        .TABLE_DEFAULT_PRIMARY_KEY_FIELD))
                                                .eq(newId))
                                .fetchOne();
                if (context.getRootModuleMainTableDataId() == null) {
                    context.setRootModuleMainTableDataId(num.longValue());
                }
                // saveModuleDataChangeContent(context, null, insertedMap, num.longValue());
                RowChangeDTO rowChange =
                        DataPersistenceUtil.convertInsertRecordToRowChangeDTO(
                                context.getRecord(),
                                context.getTableName(),
                                DataPersistenceUtil.getModuleTableFieldMap(
                                        context.getSysModuleCompleteResp().getFieldList(),
                                        context.getTableName()),
                                DataPersistenceUtil.getModuleTableUniqueNameField(
                                        context.getSysModuleCompleteResp().getFieldList(),
                                        context.getTableName()));
                saveModuleDataFieldChangeContent(context, rowChange, num.longValue());
                return DataSaveResult.builder()
                        .dataId(num.longValue())
                        .rowChange(rowChange)
                        .build();
            }
        }
        return null;
    }

    @Override
    public DataSaveResult updateRecord(RecordSaveContext context) {
        Long id = DataPersistenceUtil.getTableRowId(context.getRecord());
        if (DataPersistenceUtil.isOnlyHasId(context.getRecord())) {
            return DataSaveResult.builder().dataId(id).build();
        }
        Boolean deletedFalg = false;

        Integer deletedField = DataPersistenceUtil.getTableRowDeletedField(context.getRecord());
        if (deletedField != null && deletedField == 1) {
            deletedFalg = true;
        }

        context.setDefaultExcludeFields(
                Set.of(
                        "subject_id",
                        "business_no",
                        "version_no",
                        "module_id",
                        "created_by",
                        "created_date"));
        moduleDataSaveNecessaryFieldFillFactoryService.fillData(context);

        Map<Field<Object>, Object> fieldValues =
                getRecordFieldValues(
                        context,
                        DataPersistenceUtil.getModuleTableFieldMap(
                                context.getSysModuleCompleteResp().getFieldList(),
                                context.getTableName()));

        Record oldMap =
                context.getDsl()
                        .select(DSL.asterisk())
                        .from(DSL.table(DSL.name(context.getTableName())))
                        .where(
                                DSL.field(DSL.name(CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD))
                                        .eq(id))
                        .fetchOne();

        RowChangeDTO rowChangeDTO =
                DataPersistenceUtil.convertUpdateRecordToRowChangeDTO(
                        DataPersistenceUtil.convertRecordToMap(oldMap),
                        context.getRecord(),
                        context.getTableName(),
                        DataPersistenceUtil.getModuleTableFieldMap(
                                context.getSysModuleCompleteResp().getFieldList(),
                                context.getTableName()),
                        DataPersistenceUtil.getModuleTableUniqueNameField(
                                context.getSysModuleCompleteResp().getFieldList(),
                                context.getTableName()),
                        deletedFalg);

        // Update 不返回任何字段
        context.getDsl()
                .update(DSL.table(DSL.name(context.getTableName())))
                .set(fieldValues)
                .where(DSL.field(DSL.name(CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD)).eq(id))
                // .returning(DSL.asterisk())
                .execute();

        Record newMap =
                context.getDsl()
                        .select(DSL.asterisk())
                        .from(DSL.table(DSL.name(context.getTableName())))
                        .where(
                                DSL.field(DSL.name(CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD))
                                        .eq(id))
                        .fetchOne();
        if (context.getRootModuleMainTableDataId() == null) {
            context.setRootModuleMainTableDataId(id);
        }
        // saveModuleDataChangeContent(context, oldMap, newMap, id);
        saveModuleDataFieldChangeContent(context, rowChangeDTO, id);
        return DataSaveResult.builder().dataId(id).rowChange(rowChangeDTO).build();
    }

    @Override
    public DataSaveResult saveRecord(RecordSaveContext context) {
        if (CollectionUtil.isEmpty(context.getRecord())) {
            return null;
        }
        if (context.getIsInsert()) {
            return insertRecord(context);
        } else {
            return updateRecord(context);
        }
    }

    private Map<Field<Object>, Object> getRecordFieldValues(
            RecordSaveContext context, Map<String, ModuleFieldDTO> fieldMap) {
        Map<Field<Object>, Object> fieldValues = new HashMap<>();
        // 表单数据
        for (Map.Entry<String, Object> entry : context.getRecord().entrySet()) {
            if (CollectionUtil.isNotEmpty(fieldMap) && !fieldMap.containsKey(entry.getKey())) {
                log.info(
                        "fill form data table={} has no field={}",
                        context.getTableName(),
                        entry.getKey());
                continue;
            }

            if (CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD.equalsIgnoreCase(entry.getKey())) {
                continue;
            }
            if (CollectionUtil.isNotEmpty(context.getDefaultExcludeFields())
                    && context.getDefaultExcludeFields().contains(entry.getKey())) {
                continue;
            }

            fieldValues.put(
                    DSL.field(DSL.name(StrUtil.toUnderlineCase(entry.getKey()))),
                    DataPersistenceUtil.getFieldWriteValue(entry.getValue()));
        }
        // 表单数据 共有的 一些业务字段数据
        if (CollectionUtil.isNotEmpty(context.getBizDataMap())) {
            for (Map.Entry<String, Object> entry : context.getBizDataMap().entrySet()) {
                if (CollectionUtil.isNotEmpty(fieldMap) && !fieldMap.containsKey(entry.getKey())) {
                    log.info(
                            "fill biz data table={} has no field={}",
                            context.getTableName(),
                            entry.getKey());
                    continue;
                }
                if (CollectionUtil.isNotEmpty(context.getDefaultExcludeFields())
                        && context.getDefaultExcludeFields().contains(entry.getKey())) {
                    continue;
                }
                log.info("fill biz data field {}={}", entry.getKey(), entry.getValue());
                fieldValues.put(
                        DSL.field(DSL.name(StrUtil.toUnderlineCase(entry.getKey()))),
                        DataPersistenceUtil.getFieldWriteValue(entry.getValue()));
            }
        }
        // 关联上级表 一些字段 数据
        if (CollectionUtil.isNotEmpty(context.getParentLevelTableAssociationDataMap())) {
            for (Map.Entry<String, Object> entry :
                    context.getParentLevelTableAssociationDataMap().entrySet()) {
                if (CollectionUtil.isNotEmpty(context.getDefaultExcludeFields())
                        && context.getDefaultExcludeFields().contains(entry.getKey())) {
                    continue;
                }
                log.info("fill parent module field {}={}", entry.getKey(), entry.getValue());
                fieldValues.put(
                        DSL.field(DSL.name(StrUtil.toUnderlineCase(entry.getKey()))),
                        DataPersistenceUtil.getFieldWriteValue(entry.getValue()));
            }
        }
        // 自动补充默认的字段信息 用户上下文 数据
        if (CollectionUtil.isNotEmpty(context.getDefaultUserContextDataMap())) {
            for (Map.Entry<String, Object> entry :
                    context.getDefaultUserContextDataMap().entrySet()) {
                if (CollectionUtil.isNotEmpty(fieldMap) && !fieldMap.containsKey(entry.getKey())) {
                    log.info(
                            "fill user context table={} has no field={}",
                            context.getTableName(),
                            entry.getKey());
                    continue;
                }
                if (CollectionUtil.isNotEmpty(context.getDefaultExcludeFields())
                        && context.getDefaultExcludeFields().contains(entry.getKey())) {
                    continue;
                }
                log.info("fill user context field {}={}", entry.getKey(), entry.getValue());
                fieldValues.put(
                        DSL.field(DSL.name(StrUtil.toUnderlineCase(entry.getKey()))),
                        DataPersistenceUtil.getFieldWriteValue(entry.getValue()));
            }
        }
        return fieldValues;
    }

    private void saveModuleDataChangeContent(
            RecordSaveContext context, Record before, Record after, Long dataId) {
        ModuleDataChangeContent changeContent = new ModuleDataChangeContent();
        changeContent.setId(null);
        changeContent.setActionId(ModuleDataSaveContextHolder.getContextActionId());
        changeContent.setRootModuleId(context.getRootModuleId());
        changeContent.setMainTableDataId(context.getRootModuleMainTableDataId());
        changeContent.setMainTableDataVersion(context.getRootModuleMainTableDataVersion());
        changeContent.setModuleId(context.getSysModuleCompleteResp().getModuleId());
        changeContent.setTableName(context.getTableName());
        changeContent.setDataId(dataId);
        changeContent.setParentDataId(context.getParentLevelTableId());
        Map dataMap = new HashMap();
        dataMap.put("before", DataPersistenceUtil.convertRecordToMap(before));
        dataMap.put("after", DataPersistenceUtil.convertRecordToMap(after));
        try {
            String jsonStr = JsonUtil.obj2Json(dataMap);
            changeContent.setContent(jsonStr);
        } catch (Exception e) {
            throw new BusinessException(
                    ApiResponseCodeEnum.SERVER_ERROR.getCode(),
                    ApiResponseCodeEnum.SERVER_ERROR.getMessage(),
                    e);
        }
        changeContent.setApprovalFlag(1);
        changeContent.setActionType(context.getIsInsert() ? 1 : 2);
        changeContent.setCreateDate(LocalDateTime.now());

        Map<Field<Object>, Object> fieldValues = new HashMap<>();

        fieldValues.put(
                DSL.field(DSL.name(StrUtil.toUnderlineCase("action_id"))),
                changeContent.getActionId());
        fieldValues.put(
                DSL.field(DSL.name(StrUtil.toUnderlineCase("root_module_id"))),
                changeContent.getRootModuleId());
        fieldValues.put(
                DSL.field(DSL.name(StrUtil.toUnderlineCase("main_table_data_id"))),
                changeContent.getMainTableDataId());
        fieldValues.put(
                DSL.field(DSL.name(StrUtil.toUnderlineCase("main_table_data_version"))),
                changeContent.getMainTableDataVersion());
        fieldValues.put(
                DSL.field(DSL.name(StrUtil.toUnderlineCase("module_id"))),
                changeContent.getModuleId());
        fieldValues.put(
                DSL.field(DSL.name(StrUtil.toUnderlineCase("table_name"))),
                changeContent.getTableName());
        fieldValues.put(
                DSL.field(DSL.name(StrUtil.toUnderlineCase("data_id"))), changeContent.getDataId());
        fieldValues.put(
                DSL.field(DSL.name(StrUtil.toUnderlineCase("parent_data_id"))),
                changeContent.getParentDataId());
        fieldValues.put(
                DSL.field(DSL.name(StrUtil.toUnderlineCase("content"))),
                changeContent.getContent());
        fieldValues.put(
                DSL.field(DSL.name(StrUtil.toUnderlineCase("approval_flag"))),
                changeContent.getApprovalFlag());
        fieldValues.put(
                DSL.field(DSL.name(StrUtil.toUnderlineCase("action_type"))),
                changeContent.getActionType());
        fieldValues.put(
                DSL.field(DSL.name(StrUtil.toUnderlineCase("create_date"))),
                changeContent.getCreateDate());

        String tableName = context.getTableName();
        // 手动定义表和主键字段，标记IDENTITY
        // 1. 构造【带自增标记】的数据类型
        DataType<Long> autoIncLong = SQLDataType.BIGINT.identity(true);

        // 2. 用这个DataType创建field，等价旧版 .identity(true)
        Field<Long> pkField =
                DSL.field(DSL.name(CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD), autoIncLong);

        Table<?> dynamicTable = DSL.table(DSL.name("module_data_change_content"));
        // Insert 并获取自增主键
        Record inserted =
                context.getDsl()
                        .insertInto(dynamicTable)
                        .set(fieldValues)
                        .returning(pkField)
                        .fetchOne();
    }

    private void saveModuleDataFieldChangeContent(
            RecordSaveContext context, RowChangeDTO rowChange, Long dataId) {
        if (rowChange == null) {
            return;
        }

        if (CollectionUtil.isNotEmpty(rowChange.getColumns())) {

            List<ModuleDataFieldChangeContent> insertList = new ArrayList<>();
            List<ModuleDataFieldChangeContent> updateList = new ArrayList<>();

            for (FieldChangeDTO fieldChangeDTO : rowChange.getColumns()) {
                //                LambdaQueryWrapper<ModuleDataFieldChangeContent> queryWrapper =
                //                        new LambdaQueryWrapper<>();
                //                queryWrapper.eq(ModuleDataFieldChangeContent::getDataId, dataId);
                //                queryWrapper.eq(ModuleDataFieldChangeContent::getTableName,
                // context.getTableName());
                //                queryWrapper.eq(
                //                        ModuleDataFieldChangeContent::getFieldId,
                // fieldChangeDTO.getField());
                //
                //                ModuleDataFieldChangeContent old =
                //
                // iModuleDataFieldChangeContentService.queryOne(queryWrapper);

                //                if (old == null) {
                //
                //                } else {
                //                    Map dataMap = new HashMap();
                //                    dataMap.put("old", fieldChangeDTO.getOld());
                //                    dataMap.put("newer", fieldChangeDTO.getNewer());
                //                    try {
                //                        String jsonStr = JsonUtil.obj2Json(dataMap);
                //                        old.setContent(jsonStr);
                //                    } catch (Exception e) {
                //                        throw new BusinessException(
                //                                ApiResponseCodeEnum.SERVER_ERROR.getCode(),
                //                                ApiResponseCodeEnum.SERVER_ERROR.getMessage(),
                //                                e);
                //                    }
                //                    old.setCreateDate(LocalDateTime.now());
                //                    updateList.add(old);
                //                }
                ModuleDataFieldChangeContent changeContent = new ModuleDataFieldChangeContent();
                changeContent.setId(null);
                changeContent.setActionId(ModuleDataSaveContextHolder.getContextActionId());
                changeContent.setRootModuleId(context.getRootModuleId());
                changeContent.setRootTableDataId(context.getRootModuleMainTableDataId());
                changeContent.setRootTableDataVersion(context.getRootModuleMainTableDataVersion());
                changeContent.setModuleId(context.getSysModuleCompleteResp().getModuleId());
                changeContent.setTableName(context.getTableName());
                changeContent.setDataId(dataId);
                changeContent.setFieldId(fieldChangeDTO.getField());
                // changeContent.setContent(null);

                Map dataMap = new HashMap();
                dataMap.put("old", fieldChangeDTO.getOld());
                dataMap.put("newer", fieldChangeDTO.getNewer());
                try {
                    String jsonStr = JsonUtil.obj2Json(dataMap);
                    changeContent.setContent(jsonStr);
                } catch (Exception e) {
                    throw new BusinessException(
                            ApiResponseCodeEnum.SERVER_ERROR.getCode(),
                            ApiResponseCodeEnum.SERVER_ERROR.getMessage(),
                            e);
                }
                changeContent.setApprovalFlag(1);
                changeContent.setActionType(context.getIsInsert() ? 1 : 2);
                changeContent.setCreateDate(LocalDateTime.now());
                insertList.add(changeContent);
            }

            if (CollectionUtil.isNotEmpty(insertList)) {
                iModuleDataFieldChangeContentService.batchInsert(insertList);
            }
            if (CollectionUtil.isNotEmpty(updateList)) {
                iModuleDataFieldChangeContentService.batchUpdate(updateList);
            }
        }
    }
}
