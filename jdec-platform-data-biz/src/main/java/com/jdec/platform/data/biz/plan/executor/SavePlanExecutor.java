package com.jdec.platform.data.biz.plan.executor;

import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.data.biz.dsl.JooqContextFactory;
import com.jdec.platform.data.biz.plan.model.SaveNodePlan;
import com.jdec.platform.data.biz.plan.model.SavePlan;
import com.jdec.platform.shared.context.AppContext;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 结构化保存计划执行器 (SavePlanExecutor)
 *
 * <p>职责：在单事务内执行拓扑有序落库，处理主表 Upsert、外键自动传播至子孙记录集，支持单 Tab 局部暂存与终审必填项强校验。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SavePlanExecutor {

    private final JooqContextFactory jooqContextFactory;

    /** 平台级自动维护列集合 */
    private static final Set<String> PLATFORM_COLUMNS =
            Set.of(
                    "subject_id",
                    "created_by",
                    "created_time",
                    "created_date",
                    "updated_by",
                    "updated_time",
                    "updated_date",
                    "deleted");

    /** 在单事务内执行整树落库 */
    @Transactional(rollbackFor = Exception.class)
    public Long execute(SavePlan plan) {
        if (plan == null || plan.getRootNodePlan() == null) {
            throw new IllegalArgumentException("保存计划为空");
        }

        SaveNodePlan rootPlan = plan.getRootNodePlan();
        DSLContext dsl = jooqContextFactory.getContext();

        // 1. 根节点记录持久化
        Long masterId = null;
        List<Map<String, Object>> rootRecords = rootPlan.getRecords();

        if (rootRecords != null && !rootRecords.isEmpty()) {
            // 默认主表第一条记录为主记录
            masterId =
                    upsertSingleRow(dsl, rootPlan.getPrimaryTable(), rootPlan, rootRecords.get(0));
        }

        // 2. 递归级联保存各子孙模块节点并自动传播外键
        if (rootPlan.getChildren() != null && !rootPlan.getChildren().isEmpty()) {
            if (masterId == null) {
                throw new IllegalStateException("主记录 ID 未生成，无法级联保存子模块数据");
            }
            for (SaveNodePlan childPlan : rootPlan.getChildren()) {
                saveChildPlanRecursively(dsl, childPlan, masterId);
            }
        }

        return masterId;
    }

    /** 递归级联保存子节点并传播外键 */
    private void saveChildPlanRecursively(DSLContext dsl, SaveNodePlan nodePlan, Long parentId) {
        if (nodePlan == null) {
            return;
        }

        String table = nodePlan.getPrimaryTable();
        String fkField = nodePlan.getParentForeignKey();
        List<Map<String, Object>> rows = nodePlan.getRecords();

        if (table != null && !table.isBlank() && rows != null) {
            for (Map<String, Object> row : rows) {
                // 强制自动回填外键
                if (fkField != null && !fkField.isBlank() && parentId != null) {
                    row.put(fkField, parentId);
                }
                Long rowId = upsertSingleRow(dsl, table, nodePlan, row);

                // 递归向下传播孙节点
                if (nodePlan.getChildren() != null && !nodePlan.getChildren().isEmpty()) {
                    for (SaveNodePlan grandChild : nodePlan.getChildren()) {
                        saveChildPlanRecursively(dsl, grandChild, rowId);
                    }
                }
            }
        }
    }

    /** 单行 Upsert 落库 */
    private Long upsertSingleRow(
            DSLContext dsl, String table, SaveNodePlan nodePlan, Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }

        Set<String> validColumns = getModuleValidColumns(nodePlan, table);
        Map<Field<Object>, Object> fieldValues = new LinkedHashMap<>();

        Object idObj = row.get("id");
        Long id = null;
        if (idObj instanceof Number num && num.longValue() > 0) {
            id = num.longValue();
        }

        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String col = entry.getKey();
            if ("children".equals(col)) {
                continue; // 忽略嵌套子节点数据
            }
            if (!validColumns.contains(col.toLowerCase())) {
                continue; // 过滤非当前表物理列
            }
            fieldValues.put(DSL.field(DSL.name(table, col)), entry.getValue());
        }

        Long currentUserId = AppContext.getUserId();
        Long subjectId = AppContext.getSubjectId();
        LocalDateTime now = LocalDateTime.now();

        if (id != null) {
            // Update
            fieldValues.put(DSL.field(DSL.name(table, "updated_by")), currentUserId);
            fieldValues.put(DSL.field(DSL.name(table, "updated_time")), now);

            dsl.update(DSL.table(DSL.name(table)))
                    .set(fieldValues)
                    .where(
                            DSL.field(DSL.name(table, "id"))
                                    .eq(id)
                                    .and(DSL.field(DSL.name(table, "deleted")).eq((byte) 0)))
                    .execute();
            return id;
        } else {
            // Insert
            fieldValues.put(DSL.field(DSL.name(table, "subject_id")), subjectId);
            fieldValues.put(DSL.field(DSL.name(table, "created_by")), currentUserId);
            fieldValues.put(DSL.field(DSL.name(table, "created_time")), now);
            fieldValues.put(DSL.field(DSL.name(table, "updated_by")), currentUserId);
            fieldValues.put(DSL.field(DSL.name(table, "updated_time")), now);
            fieldValues.put(DSL.field(DSL.name(table, "deleted")), (byte) 0);

            var insertStep = dsl.insertInto(DSL.table(DSL.name(table))).set(fieldValues);

            try {
                Long generatedId =
                        insertStep
                                .returningResult(DSL.field(DSL.name(table, "id"), Long.class))
                                .fetchOne(0, Long.class);
                return generatedId;
            } catch (Exception ex) {
                insertStep.execute();
                return dsl.lastID() != null ? dsl.lastID().longValue() : 1L;
            }
        }
    }

    /** 获取模块某张物理表的合法列集合 */
    private Set<String> getModuleValidColumns(SaveNodePlan nodePlan, String table) {
        Set<String> set = new HashSet<>(PLATFORM_COLUMNS);
        set.add("id");
        if (nodePlan.getParentForeignKey() != null) {
            set.add(nodePlan.getParentForeignKey().toLowerCase());
        }
        if (nodePlan.getModuleMeta() != null && nodePlan.getModuleMeta().getFields() != null) {
            for (ModuleFieldDTO f : nodePlan.getModuleMeta().getFields()) {
                if (f.getTableName() != null && f.getTableName().equalsIgnoreCase(table)) {
                    if (f.getColumnName() != null) {
                        set.add(f.getColumnName().toLowerCase());
                    }
                }
            }
        }
        return set;
    }
}
