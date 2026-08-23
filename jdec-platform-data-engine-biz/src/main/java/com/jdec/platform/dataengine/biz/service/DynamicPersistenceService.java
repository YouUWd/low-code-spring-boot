package com.jdec.platform.dataengine.biz.service;

import com.jdec.platform.config.api.dto.common.ModuleTableDTO;
import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp;
import com.jdec.platform.dataengine.api.dto.request.DynamicSaveReq;
import com.jdec.platform.dataengine.biz.dsl.JooqContextFactory;
import com.jdec.platform.shared.context.AppContext;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 动态主子表物理持久化服务 (不生成快照) */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicPersistenceService {

    private final MetadataCacheService metadataCacheService;
    private final PermissionFilterService permissionFilterService;
    private final JooqContextFactory jooqContextFactory;

    /** 保存主子表当前态物理记录 */
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Long save(DynamicSaveReq req) {
        Long moduleId = req.getModuleId();
        SysModuleCompleteResp completeResp = metadataCacheService.getModuleComplete(moduleId);
        String primaryTable = completeResp.getModule().getPrimaryTable();
        Map<String, Object> tables = req.getTables();

        if (tables == null || !tables.containsKey(primaryTable)) {
            throw new IllegalArgumentException("待保存数据中必须包含主表 [" + primaryTable + "] 的数据");
        }

        DSLContext dsl = jooqContextFactory.getContext();
        Object primaryObj = tables.get(primaryTable);
        if (!(primaryObj instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("主表 [" + primaryTable + "] 数据格式错误，应为单个对象 Map");
        }
        Map<String, Object> primaryRecord = (Map<String, Object>) primaryObj;

        // 1. 保存/更新主表数据
        permissionFilterService.validateWritableFields(
                moduleId, primaryTable, new ArrayList<>(primaryRecord.keySet()));
        Long primaryId = saveSingleRecord(dsl, primaryTable, primaryRecord);

        // 2. 级联保存各关联从表
        if (completeResp.getModuleTables() != null) {
            for (ModuleTableDTO tableDto : completeResp.getModuleTables()) {
                String tableName = tableDto.getTableName();
                if (!tables.containsKey(tableName)) {
                    continue;
                }

                Object tableDataObj = tables.get(tableName);
                String relationType = tableDto.getRelationType();
                String rightFkField = tableDto.getJoinRightField(); // 外键关联字段, 如 student_id

                if ("1:N".equalsIgnoreCase(relationType)
                        || "ONE_TO_MANY".equalsIgnoreCase(relationType)) {
                    // 1:N 从表多行批量维护
                    if (tableDataObj instanceof List<?> recordsList) {
                        for (Object itemObj : recordsList) {
                            if (itemObj instanceof Map<?, ?> itemMap) {
                                Map<String, Object> item = (Map<String, Object>) itemMap;
                                item.put(rightFkField, primaryId);
                                saveSingleRecord(dsl, tableName, item);
                            }
                        }
                    }
                } else {
                    // 1:1 或 N:1 从表单行维护
                    if (tableDataObj instanceof Map<?, ?> relMap) {
                        Map<String, Object> relRecord = (Map<String, Object>) relMap;
                        if (!relRecord.isEmpty()) {
                            relRecord.put(rightFkField, primaryId);
                            saveSingleRecord(dsl, tableName, relRecord);
                        }
                    }
                }
            }
        }

        log.info(
                "动态主子表保存成功: moduleId={}, primaryTable={}, primaryId={}",
                moduleId,
                primaryTable,
                primaryId);
        return primaryId;
    }

    /** 维护单行数据 (有 ID 则 Update，无 ID 则 Insert) */
    private Long saveSingleRecord(DSLContext dsl, String tableName, Map<String, Object> record) {
        if (record == null || record.isEmpty()) {
            return null;
        }

        // 自动补充主体信息
        if (AppContext.getSubjectId() != null
                && AppContext.getSubjectId() > 0
                && !record.containsKey("subject_id")) {
            record.put("subject_id", AppContext.getSubjectId());
        }

        Object idObj = record.get("id");
        Long id = idObj instanceof Number num ? num.longValue() : null;

        Map<Field<Object>, Object> fieldValues = new HashMap<>();
        for (Map.Entry<String, Object> entry : record.entrySet()) {
            if ("id".equalsIgnoreCase(entry.getKey())) {
                continue;
            }
            fieldValues.put(DSL.field(DSL.name(entry.getKey())), entry.getValue());
        }

        if (id != null && id > 0) {
            // Update
            dsl.update(DSL.table(DSL.name(tableName)))
                    .set(fieldValues)
                    .where(DSL.field(DSL.name("id")).eq(id))
                    .execute();
            return id;
        } else {
            // Insert 并获取自增主键
            Record inserted =
                    dsl.insertInto(DSL.table(DSL.name(tableName)))
                            .set(fieldValues)
                            .returning(DSL.field(DSL.name("id")))
                            .fetchOne();

            if (inserted != null) {
                Object newId = inserted.getValue("id");
                if (newId instanceof Number num) {
                    return num.longValue();
                }
            }
            return id;
        }
    }
}
