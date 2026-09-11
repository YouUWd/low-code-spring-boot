package com.jdec.platform.dataengine.biz.third.api.persistence.tab.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.jdec.platform.dataengine.biz.constants.CommonConstant;
import com.jdec.platform.dataengine.biz.dto.context.RecordSaveContext;
import com.jdec.platform.dataengine.biz.entity.ModuleDataChangeContent;
import com.jdec.platform.dataengine.biz.mapper.ModuleDataChangeContentMapper;
import com.jdec.platform.dataengine.biz.third.api.persistence.DataPersistenceUtil;
import com.jdec.platform.dataengine.biz.third.api.persistence.tab.BaseSingleTablePersistenceService;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class BaseSingleTablePersistenceServiceImpl implements BaseSingleTablePersistenceService {

    private final ModuleDataChangeContentMapper moduleDataChangeContentMapper;

    @Override
    public Long insertRecord(RecordSaveContext context) {
        Map<Field<Object>, Object> fieldValues = getRecordFieldValues(context);
        // Insert 并获取自增主键
        Record inserted =
                context.getDsl()
                        .insertInto(DSL.table(DSL.name(context.getTableName())))
                        .set(fieldValues)
                        .returning(
                                DSL.field(DSL.name(CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD)))
                        .fetchOne();

        if (inserted != null) {
            Object newId = inserted.getValue(CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD);
            if (newId instanceof Number num) {
                Map<String, Object> insertedMap =
                        context.getDsl()
                                .select(DSL.asterisk())
                                .from(DSL.table(DSL.name(context.getTableName())))
                                .where(
                                        DSL.field(
                                                        DSL.name(
                                                                CommonConstant
                                                                        .TABLE_DEFAULT_PRIMARY_KEY_FIELD))
                                                .eq(newId))
                                .fetchOne()
                                .intoMap();

                return num.longValue();
            }
        }
        return null;
    }

    @Override
    public Long updateRecord(RecordSaveContext context) {
        Long id = DataPersistenceUtil.getRecordId(context.getRecord());
        Map<Field<Object>, Object> fieldValues = getRecordFieldValues(context);

        Map<String, Object> oldMap =
                context.getDsl()
                        .select(DSL.asterisk())
                        .from(DSL.table(DSL.name(context.getTableName())))
                        .where(
                                DSL.field(DSL.name(CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD))
                                        .eq(id))
                        .fetchOne()
                        .intoMap();
        // Update
        context.getDsl()
                .update(DSL.table(DSL.name(context.getTableName())))
                .set(fieldValues)
                .where(DSL.field(DSL.name(CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD)).eq(id))
                .execute();

        Map<String, Object> newMap =
                context.getDsl()
                        .select(DSL.asterisk())
                        .from(DSL.table(DSL.name(context.getTableName())))
                        .where(
                                DSL.field(DSL.name(CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD))
                                        .eq(id))
                        .fetchOne()
                        .intoMap();
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
            if (context.getDefaultExcludeFields().contains(entry.getKey())) {
                continue;
            }
            fieldValues.put(
                    DSL.field(DSL.name(StrUtil.toUnderlineCase(entry.getKey()))), entry.getValue());
        }
        // 表单数据 共有的 一些业务字段数据
        if (CollectionUtil.isNotEmpty(context.getBizDataMap())) {
            for (Map.Entry<String, Object> entry : context.getBizDataMap().entrySet()) {
                if (context.getDefaultExcludeFields().contains(entry.getKey())) {
                    continue;
                }
                fieldValues.put(
                        DSL.field(DSL.name(StrUtil.toUnderlineCase(entry.getKey()))),
                        entry.getValue());
            }
        }
        // 关联上级表 一些字段 数据
        if (CollectionUtil.isNotEmpty(context.getParentLevelTableAssociationDataMap())) {
            for (Map.Entry<String, Object> entry :
                    context.getParentLevelTableAssociationDataMap().entrySet()) {
                if (context.getDefaultExcludeFields().contains(entry.getKey())) {
                    continue;
                }
                fieldValues.put(
                        DSL.field(DSL.name(StrUtil.toUnderlineCase(entry.getKey()))),
                        entry.getValue());
            }
        }
        // 自动补充默认的字段信息 用户上下文 数据
        if (CollectionUtil.isNotEmpty(context.getDefaultUserContextDataMap())) {
            for (Map.Entry<String, Object> entry :
                    context.getDefaultUserContextDataMap().entrySet()) {
                if (context.getDefaultExcludeFields().contains(entry.getKey())) {
                    continue;
                }
                fieldValues.put(
                        DSL.field(DSL.name(StrUtil.toUnderlineCase(entry.getKey()))),
                        entry.getValue());
            }
        }
        return fieldValues;
    }

    private void saveModuleDataChangeContent(
            RecordSaveContext context,
            Map<String, Object> before,
            Map<String, Object> after,
            Long dataId) {

        if (context.getIsInsert()) {
            ModuleDataChangeContent changeContent = new ModuleDataChangeContent();

            changeContent.setId(null);
            changeContent.setActionId(null);
            changeContent.setRootModuleId(context.getRootModuleId());
            changeContent.setMainTableDataId(context.getMainTableDataId());
            changeContent.setModuleId(context.getSysModuleCompleteResp().getModule().getId());
            changeContent.setTableName(context.getTableName());
            changeContent.setDataId(dataId);
            changeContent.setParentDataId(null);
            changeContent.setContent(null);
            changeContent.setApprovalFlag(null);
            changeContent.setActionType(context.getIsInsert() ? 1 : 2);
            changeContent.setCreateDate(LocalDateTime.now());
        }
    }
}
