package com.jdec.platform.dataengine.biz.third.api.persistence.tab.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.jdec.platform.dataengine.biz.constants.CommonConstant;
import com.jdec.platform.dataengine.biz.context.ModuleDataSaveContextHolder;
import com.jdec.platform.dataengine.biz.dto.context.RecordSaveContext;
import com.jdec.platform.dataengine.biz.entity.ModuleDataChangeContent;
import com.jdec.platform.dataengine.biz.enums.ApiResponseCodeEnum;
import com.jdec.platform.dataengine.biz.mapper.ModuleDataChangeContentMapper;
import com.jdec.platform.dataengine.biz.third.api.persistence.DataPersistenceUtil;
import com.jdec.platform.dataengine.biz.third.api.persistence.tab.BaseSingleTablePersistenceService;
import com.jdec.platform.dataengine.biz.util.JsonUtil;
import com.jdec.platform.shared.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
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

    @Override
    public Long insertRecord(RecordSaveContext context) {
        Map<Field<Object>, Object> fieldValues = getRecordFieldValues(context);

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
                saveModuleDataChangeContent(context, null, insertedMap, num.longValue());
                return num.longValue();
            }
        }
        return null;
    }

    @Override
    public Long updateRecord(RecordSaveContext context) {
        Long id = DataPersistenceUtil.getRecordId(context.getRecord());
        Map<Field<Object>, Object> fieldValues = getRecordFieldValues(context);

        Record oldMap =
                context.getDsl()
                        .select(DSL.asterisk())
                        .from(DSL.table(DSL.name(context.getTableName())))
                        .where(
                                DSL.field(DSL.name(CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD))
                                        .eq(id))
                        .fetchOne();
        // Update
        context.getDsl()
                .update(DSL.table(DSL.name(context.getTableName())))
                .set(fieldValues)
                .where(DSL.field(DSL.name(CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD)).eq(id))
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
        saveModuleDataChangeContent(context, oldMap, newMap, id);
        return id;
    }

    @Override
    public Long saveRecord(RecordSaveContext context) {
        if (CollectionUtil.isEmpty(context.getRecord())) {
            return null;
        }
        Long id = DataPersistenceUtil.getRecordId(context.getRecord());
        if (id != null && id > 0) {
            return updateRecord(context);
        } else {
            return insertRecord(context);
        }
    }

    private Map<Field<Object>, Object> getRecordFieldValues(RecordSaveContext context) {
        Map<Field<Object>, Object> fieldValues = new HashMap<>();
        // 表单数据
        for (Map.Entry<String, Object> entry : context.getRecord().entrySet()) {
            if (CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD.equalsIgnoreCase(entry.getKey())) {
                continue;
            }
            if (CollectionUtil.isNotEmpty(context.getDefaultExcludeFields())
                    && context.getDefaultExcludeFields().contains(entry.getKey())) {
                continue;
            }
            fieldValues.put(
                    DSL.field(DSL.name(StrUtil.toUnderlineCase(entry.getKey()))), entry.getValue());
        }
        // 表单数据 共有的 一些业务字段数据
        if (CollectionUtil.isNotEmpty(context.getBizDataMap())) {
            for (Map.Entry<String, Object> entry : context.getBizDataMap().entrySet()) {
                if (CollectionUtil.isEmpty(context.getDefaultExcludeFields())
                        || context.getDefaultExcludeFields().contains(entry.getKey())) {
                    continue;
                }
                log.info("fill biz data field {}={}", entry.getKey(), entry.getValue());
                fieldValues.put(
                        DSL.field(DSL.name(StrUtil.toUnderlineCase(entry.getKey()))),
                        entry.getValue());
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
                        entry.getValue());
            }
        }
        // 自动补充默认的字段信息 用户上下文 数据
        if (CollectionUtil.isNotEmpty(context.getDefaultUserContextDataMap())) {
            for (Map.Entry<String, Object> entry :
                    context.getDefaultUserContextDataMap().entrySet()) {
                if (CollectionUtil.isNotEmpty(context.getDefaultExcludeFields())
                        && context.getDefaultExcludeFields().contains(entry.getKey())) {
                    continue;
                }
                log.info("fill user context field {}={}", entry.getKey(), entry.getValue());
                fieldValues.put(
                        DSL.field(DSL.name(StrUtil.toUnderlineCase(entry.getKey()))),
                        entry.getValue());
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
        changeContent.setModuleId(context.getSysModuleCompleteResp().getModule().getId());
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
}
