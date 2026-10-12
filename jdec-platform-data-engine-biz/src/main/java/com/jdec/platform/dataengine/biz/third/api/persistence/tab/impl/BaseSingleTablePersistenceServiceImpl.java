package com.jdec.platform.dataengine.biz.third.api.persistence.tab.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.response.SysFieldResp;
import com.jdec.platform.dataengine.api.dto.model.FieldChangeDTO;
import com.jdec.platform.dataengine.api.dto.model.RowChangeDTO;
import com.jdec.platform.dataengine.biz.constants.CommonConstant;
import com.jdec.platform.dataengine.biz.context.ModuleDataSaveContextHolder;
import com.jdec.platform.dataengine.biz.dto.DataSaveResult;
import com.jdec.platform.dataengine.biz.dto.context.RecordSaveContext;
import com.jdec.platform.dataengine.biz.entity.ModuleDataChangeContent;
import com.jdec.platform.dataengine.biz.entity.ModuleDataFieldChangeContent;
import com.jdec.platform.dataengine.biz.enums.ApiResponseCodeEnum;
import com.jdec.platform.dataengine.biz.enums.SaveType;
import com.jdec.platform.dataengine.biz.service.IModuleDataFieldChangeContentService;
import com.jdec.platform.dataengine.biz.third.api.persistence.DataPersistenceUtil;
import com.jdec.platform.dataengine.biz.third.api.persistence.check.ModuleDataSaveCheckFactoryService;
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

    private final IModuleDataFieldChangeContentService iModuleDataFieldChangeContentService;

    private final ModuleDataSaveNecessaryFieldFillFactoryService
            moduleDataSaveNecessaryFieldFillFactoryService;

    private final ModuleDataSaveCheckFactoryService moduleDataSaveCheckFactoryService;

    private final Set<String> UPDATE_DEFAULT_EXCLUDE_FIELD =
            Set.of("id", "subject_id", "business_no", "version_no", "created_by", "created_date");

    private final Set<String> CHANGE_DEFAULT_SET_NULL_EXCLUDE_FIELD =
            Set.of(
                    "id",
                    "subject_id",
                    "business_no",
                    "business_status",
                    "version_no",
                    "created_by",
                    "updated_by",
                    "created_date",
                    "updated_date");

    private final Set<String> INSERT_DEFAULT_EXCLUDE_FIELD =
            Set.of(
                    "id",
                    "subject_id",
                    "business_no",
                    "business_status",
                    "version_no",
                    "created_by",
                    "updated_by",
                    "created_date",
                    "updated_date");

    @Override
    public DataSaveResult insertRecord(RecordSaveContext context) {

        if (CollectionUtil.isEmpty(context.getRecord())) {
            return DataSaveResult.builder().dataId(0L).build();
        }
        // 校验字段权限, 校验不通过 直接抛异常
        moduleDataSaveCheckFactoryService.check(context);

        // 填充 必要的 业务字段
        moduleDataSaveNecessaryFieldFillFactoryService.fillData(context);

        // 设置 新增时不需要填充 的字段
        context.setDefaultExcludeFields(INSERT_DEFAULT_EXCLUDE_FIELD);
        Map<Field<Object>, Object> fieldValues = getInsertRecordFieldValues(context);

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

        if (inserted == null) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_SAVE_DATA_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_SAVE_DATA_ERROR.getMessage());
        }

        // 获取 插入的ID
        Object newId = inserted.getValue(CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD);

        if (newId == null) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_SAVE_DATA_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_SAVE_DATA_ERROR.getMessage());
        }
        Number num = (Number) newId;
        if (context.getRootModuleMainTableDataId() == null) {
            context.setRootModuleMainTableDataId(num.longValue());
        }

        RowChangeDTO rowChange = null;
        // 非审批链业务 正常保存 记录下变更日志
        if (!context.getIsApproval() && SaveType.SAVE.equals(context.getSaveType())) {
            rowChange =
                    DataPersistenceUtil.convertInsertRecordToRowChangeDTO(
                            context.getRecord(),
                            context.getTableName(),
                            context.getSysModuleCompleteResp()
                                    .getFieldMapByTable()
                                    .get(context.getTableName()),
                            DataPersistenceUtil.getModuleTableUniqueNameField(
                                    context.getSysModuleCompleteResp().getModuleInfo().getFields(),
                                    context.getTableName()));
        }
        return DataSaveResult.builder().dataId(num.longValue()).rowChange(rowChange).build();
    }

    @Override
    public DataSaveResult updateRecord(RecordSaveContext context) {
        Long id = DataPersistenceUtil.getTableRowId(context.getRecord());
        // 表单数据只有ID ，不用更新
        if (DataPersistenceUtil.isOnlyHasId(context.getRecord())) {
            return DataSaveResult.builder().dataId(id).build();
        }

        // 获取删除 标记
        Boolean deletedFalg = false;
        Integer deletedField = DataPersistenceUtil.getTableRowDeletedField(context.getRecord());
        if (deletedField != null && Integer.valueOf(1).equals(deletedField)) {
            deletedFalg = true;
        }

        Record oldMap =
                context.getDsl()
                        .select(DSL.asterisk())
                        .from(DSL.table(DSL.name(context.getTableName())))
                        .where(
                                DSL.field(DSL.name(CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD))
                                        .eq(id))
                        .fetchOne();
        if (oldMap == null) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_SAVE_DATA_ERROR.getCode(),
                    "您要编辑的数据表【" + context.getTableName() + "】的数据【" + id + "】不存在");
        }

        // 变更保存 需要读取旧数据
        context.setReadOutRecord(DataPersistenceUtil.convertRecordToMap(oldMap));

        // 权限校验
        if (deletedFalg) {
            // 删除校验 申请权限
            if (CollectionUtil.isEmpty(context.getSysModuleCompleteResp().getWriteFields())) {
                throw new BusinessException(
                        ApiResponseCodeEnum.BAD_MODULE_FIELD_APPLY_RIGHT_ERROR.getCode(),
                        "您没有表【" + context.getTableName() + "】的数据删除权限");
            }
        } else {
            // 校验字段权限, 校验不通过 直接抛异常
            moduleDataSaveCheckFactoryService.check(context);
        }

        context.setDefaultExcludeFields(UPDATE_DEFAULT_EXCLUDE_FIELD);
        moduleDataSaveNecessaryFieldFillFactoryService.fillData(context);

        Map<Field<Object>, Object> fieldValues = getUpdateRecordFieldValues(context);

        // Update 不返回任何字段
        context.getDsl()
                .update(DSL.table(DSL.name(context.getTableName())))
                .set(fieldValues)
                .where(DSL.field(DSL.name(CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD)).eq(id))
                .execute();
        if (context.getRootModuleMainTableDataId() == null) {
            context.setRootModuleMainTableDataId(id);
        }
        RowChangeDTO rowChange = null;
        // 非审批链业务 正常保存 记录下变更日志
        if (!context.getIsApproval() && SaveType.SAVE.equals(context.getSaveType())) {
            rowChange =
                    DataPersistenceUtil.convertUpdateRecordToRowChangeDTO(
                            DataPersistenceUtil.convertRecordToMap(oldMap),
                            context.getRecord(),
                            context.getTableName(),
                            context.getSysModuleCompleteResp()
                                    .getFieldMapByTable()
                                    .get(context.getTableName()),
                            DataPersistenceUtil.getModuleTableUniqueNameField(
                                    context.getSysModuleCompleteResp().getModuleInfo().getFields(),
                                    context.getTableName()),
                            deletedFalg);
        }
        return DataSaveResult.builder().dataId(id).rowChange(rowChange).build();
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

    private Map<Field<Object>, Object> getInsertRecordFieldValues(RecordSaveContext context) {

        Map<String, ModuleFieldDTO> fieldMap =
                context.getSysModuleCompleteResp().getFieldMapByTable().get(context.getTableName());
        Map<String, SysFieldResp> allFields =
                context.getSysModuleCompleteResp()
                        .getAllFieldsByTable()
                        .get(context.getTableName());
        Map<Field<Object>, Object> fieldValues = new HashMap<>();
        // Insert时候 表单数据都要 写入
        for (Map.Entry<String, Object> entry : context.getRecord().entrySet()) {

            if (CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD.equalsIgnoreCase(entry.getKey())) {
                continue;
            }
            if (CollectionUtil.isNotEmpty(context.getDefaultExcludeFields())
                    && context.getDefaultExcludeFields().contains(entry.getKey())) {
                continue;
            }

            // 非表结构定义字段 不写入
            if (CollectionUtil.isNotEmpty(allFields) && !allFields.containsKey(entry.getKey())) {
                continue;
            }
            fieldValues.put(
                    DSL.field(DSL.name(StrUtil.toUnderlineCase(entry.getKey()))),
                    DataPersistenceUtil.getFieldWriteValue(entry.getValue()));
        }
        // 表单数据 共有的 一些业务字段数据 business_no ,business_status等
        if (CollectionUtil.isNotEmpty(context.getBizDataMap())) {
            for (Map.Entry<String, Object> entry : context.getBizDataMap().entrySet()) {
                // 非表结构定义字段 不写入
                if (CollectionUtil.isNotEmpty(allFields)
                        && !allFields.containsKey(entry.getKey())) {
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
                // 非表结构定义字段 不写入
                if (CollectionUtil.isNotEmpty(allFields)
                        && !allFields.containsKey(entry.getKey())) {
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
                // 非表结构定义字段 不写入
                if (CollectionUtil.isNotEmpty(allFields)
                        && !allFields.containsKey(entry.getKey())) {
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

    private Map<Field<Object>, Object> getUpdateRecordFieldValues(RecordSaveContext context) {

        Map<String, ModuleFieldDTO> fieldMap =
                context.getSysModuleCompleteResp().getFieldMapByTable().get(context.getTableName());
        Map<String, SysFieldResp> allFields =
                context.getSysModuleCompleteResp()
                        .getAllFieldsByTable()
                        .get(context.getTableName());
        Map<Field<Object>, Object> fieldValues = new HashMap<>();

        Set<String> setFields = new HashSet<>();
        // 表单数据 都是要更新的
        for (Map.Entry<String, Object> entry : context.getRecord().entrySet()) {
            // 非 update 字段 不更新
            if (CollectionUtil.isNotEmpty(context.getDefaultExcludeFields())
                    && context.getDefaultExcludeFields().contains(entry.getKey())) {
                continue;
            }
            // 非表结构定义字段 不写入
            if (CollectionUtil.isNotEmpty(allFields) && !allFields.containsKey(entry.getKey())) {
                continue;
            }

            fieldValues.put(
                    DSL.field(DSL.name(StrUtil.toUnderlineCase(entry.getKey()))),
                    DataPersistenceUtil.getFieldWriteValue(entry.getValue()));
            setFields.add(entry.getKey());

            //            switch (context.getSaveType()) {
            //                case NORMAL_SAVE,
            //                        APPROVAL_SAVE,
            //                        APPROVAL_REALTIME,
            //                        INSERT_AND_GET_ID,
            //                        DELETE_BY_ID,
            //                        APPROVAL_INSERT_AND_GET_ID,
            //                        APPROVAL_DELETE_BY_ID -> {
            //                    if (CollectionUtil.isNotEmpty(fieldMap)
            //                            && !fieldMap.containsKey(entry.getKey())) {
            //                        log.info(
            //                                "fill form data table={} has no field={}",
            //                                context.getTableName(),
            //                                entry.getKey());
            //                        continue;
            //                    }
            //
            //                }
            //                case APPROVAL_CHANGE -> {
            //                    if (CollectionUtil.isNotEmpty(fieldMap)
            //                            && !fieldMap.containsKey(entry.getKey())) {
            //                        log.info(
            //                                "fill form data table={} field={} null value",
            //                                context.getTableName(),
            //                                entry.getKey());
            //                        fieldValues.put(
            //
            // DSL.field(DSL.name(StrUtil.toUnderlineCase(entry.getKey()))), null);
            //                    } else {
            //                        fieldValues.put(
            //
            // DSL.field(DSL.name(StrUtil.toUnderlineCase(entry.getKey()))),
            //
            // DataPersistenceUtil.getFieldWriteValue(entry.getValue()));
            //                    }
            //                }
            //            }
        }
        // 表单数据 共有的 一些业务字段数据 更新时 业务字段数据，从表单中获取
        //        if (CollectionUtil.isNotEmpty(context.getBizDataMap())) {
        //            for (Map.Entry<String, Object> entry : context.getBizDataMap().entrySet()) {
        //                if (CollectionUtil.isNotEmpty(fieldMap) &&
        // !fieldMap.containsKey(entry.getKey())) {
        //                    log.info(
        //                            "fill biz data table={} has no field={}",
        //                            context.getTableName(),
        //                            entry.getKey());
        //                    continue;
        //                }
        //                if (CollectionUtil.isNotEmpty(context.getDefaultExcludeFields())
        //                        && context.getDefaultExcludeFields().contains(entry.getKey())) {
        //                    continue;
        //                }
        //                log.info("fill biz data field {}={}", entry.getKey(), entry.getValue());
        //                fieldValues.put(
        //                        DSL.field(DSL.name(StrUtil.toUnderlineCase(entry.getKey()))),
        //                        DataPersistenceUtil.getFieldWriteValue(entry.getValue()));
        //            }
        //        }
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
                setFields.add(entry.getKey());
            }
        }
        // 自动补充默认的字段信息 用户上下文 数据
        if (CollectionUtil.isNotEmpty(context.getDefaultUserContextDataMap())) {
            for (Map.Entry<String, Object> entry :
                    context.getDefaultUserContextDataMap().entrySet()) {
                if (CollectionUtil.isNotEmpty(allFields)
                        && !allFields.containsKey(entry.getKey())) {
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
                setFields.add(entry.getKey());
            }
        }

        // 对于变更保存的 要吧一些 非勾选的字段 表单中没有的字段 set null
        if (context.getSaveType() == SaveType.APPROVAL_CHANGE) {
            for (String field : allFields.keySet()) {
                // 这些字段是 不能 set null || 上面已经设置了字段 不能 set null
                if (CHANGE_DEFAULT_SET_NULL_EXCLUDE_FIELD.contains(field)
                        || setFields.contains(field)) {
                    continue;
                }
                if (!fieldMap.containsKey(field)) {
                    fieldValues.put(DSL.field(DSL.name(StrUtil.toUnderlineCase(field))), null);
                }
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
        changeContent.setModuleId(context.getSysModuleCompleteResp().getModuleInfo().getModuleId());
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
                changeContent.setModuleId(
                        context.getSysModuleCompleteResp().getModuleInfo().getModuleId());
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
                changeContent.setCreatedDate(LocalDateTime.now());
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
