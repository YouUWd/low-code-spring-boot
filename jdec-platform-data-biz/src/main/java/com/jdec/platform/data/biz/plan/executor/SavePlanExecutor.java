package com.jdec.platform.data.biz.plan.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.data.api.dto.common.DataRow;
import com.jdec.platform.data.biz.plan.model.SaveNodePlan;
import com.jdec.platform.data.biz.plan.model.SavePlan;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.datasource.JooqDataContextFactory;
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

    private final JooqDataContextFactory jooqDataContextFactory;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 在单事务内执行整树落库并返回根记录 ID */
    @Transactional(rollbackFor = Exception.class)
    public Long execute(SavePlan plan) {
        Map<String, Object> idMap = doSaveInTransaction(plan);
        if (plan != null
                && plan.getRootNodePlan() != null
                && plan.getRootNodePlan().getModuleId() != null) {
            String modKey = String.valueOf(plan.getRootNodePlan().getModuleId());
            Object rootVal = idMap.get(modKey);
            String primaryTable = plan.getRootNodePlan().getPrimaryTable();
            return extractIdFromIsomorphicNode(rootVal, primaryTable);
        }
        return null;
    }

    private Long extractIdFromIsomorphicNode(Object nodeVal, String primaryTable) {
        if (nodeVal instanceof Long id) {
            return id;
        }
        if (nodeVal instanceof List<?> list && !list.isEmpty()) {
            return extractIdFromIsomorphicNode(list.get(0), primaryTable);
        }
        if (nodeVal instanceof Map<?, ?> map) {
            if (map.get("id") instanceof Number num) {
                return num.longValue();
            }
            if (primaryTable != null && map.get(primaryTable) instanceof Map<?, ?> tableMap) {
                if (tableMap.get("id") instanceof Number num) {
                    return num.longValue();
                }
            }
            for (Object v : map.values()) {
                if (v instanceof Map<?, ?> tableMap && tableMap.get("id") instanceof Number num) {
                    return num.longValue();
                }
            }
        }
        return null;
    }

    /**
     * 在单事务内执行整树落库并收集返回与请求/详情 100% 读写同构的 ID 结构 示例： { "1001": { "info": { "id": 100 }, "1002": [ {
     * "basic_info": { "id": 1 } } ] } }
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> executeAndCollectIds(SavePlan plan) {
        return doSaveInTransaction(plan);
    }

    /**
     * 整树落库的共享实现。
     *
     * <p>刻意做成**非事务**的私有方法：两个公开入口各自开启事务，避免 {@code execute()} 自调用 {@code executeAndCollectIds()} 时内层
     * {@code @Transactional} 因代理失效而被忽略（语义误导）。
     */
    private Map<String, Object> doSaveInTransaction(SavePlan plan) {
        if (plan == null || plan.getRootNodePlan() == null) {
            throw new IllegalArgumentException("保存计划为空");
        }

        SaveNodePlan rootPlan = plan.getRootNodePlan();
        DSLContext dsl = jooqDataContextFactory.getContext();

        // 1. 根节点记录持久化
        String primaryTable = rootPlan.getPrimaryTable();
        List<Map<String, Object>> rootRecords = rootPlan.getRecords();
        List<Map<String, Object>> rootRowResults = new ArrayList<>();
        Long masterId = null;

        if (rootRecords != null && !rootRecords.isEmpty()) {
            List<DataRow> sourceRows = rootPlan.getSourceDataRows();
            for (int i = 0; i < rootRecords.size(); i++) {
                Map<String, Object> rootRecord = rootRecords.get(i);
                DataRow sourceRow =
                        (sourceRows != null && i < sourceRows.size()) ? sourceRows.get(i) : null;
                Map<String, Object> rootRowMap = new LinkedHashMap<>();
                Long generatedId =
                        upsertSingleRow(dsl, primaryTable, rootPlan, rootRecord, sourceRow, null);
                if (generatedId != null) {
                    if (masterId == null) {
                        masterId = generatedId;
                    }
                    if (primaryTable != null && !primaryTable.isBlank()) {
                        rootRowMap.put(primaryTable, Map.of("id", generatedId));
                    } else {
                        rootRowMap.put("id", generatedId);
                    }
                }

                // 递归级联保存各子孙模块节点并挂载到当前根行对象
                List<SaveNodePlan> rootRowChildren =
                        (rootPlan.getRowChildren() != null && i < rootPlan.getRowChildren().size())
                                ? rootPlan.getRowChildren().get(i)
                                : rootPlan.getChildren();
                if (rootRowChildren != null && !rootRowChildren.isEmpty()) {
                    Long currentParentId = generatedId != null ? generatedId : masterId;
                    if (currentParentId == null) {
                        throw new IllegalStateException("主记录 ID 未生成，无法级联保存子模块数据");
                    }
                    for (SaveNodePlan childPlan : rootRowChildren) {
                        Object childResult =
                                saveChildPlanRecursively(dsl, childPlan, currentParentId);
                        if (childPlan.getModuleId() != null && childResult != null) {
                            rootRowMap.put(String.valueOf(childPlan.getModuleId()), childResult);
                        }
                    }
                }

                rootRowResults.add(rootRowMap);
            }
        } else if (rootPlan.getChildren() != null && !rootPlan.getChildren().isEmpty()) {
            // 没有根行记录但有子模块（容错处理）
            Map<String, Object> rootRowMap = new LinkedHashMap<>();
            for (SaveNodePlan childPlan : rootPlan.getChildren()) {
                Object childResult = saveChildPlanRecursively(dsl, childPlan, null);
                if (childPlan.getModuleId() != null && childResult != null) {
                    rootRowMap.put(String.valueOf(childPlan.getModuleId()), childResult);
                }
            }
            rootRowResults.add(rootRowMap);
        }

        Map<String, Object> resultMap = new LinkedHashMap<>();
        if (rootPlan.getModuleId() != null) {
            String modKey = String.valueOf(rootPlan.getModuleId());
            if (rootPlan.isMany()) {
                resultMap.put(modKey, rootRowResults);
            } else {
                resultMap.put(
                        modKey,
                        rootRowResults.isEmpty() ? Collections.emptyMap() : rootRowResults.get(0));
            }
        }

        return resultMap;
    }

    /** 递归级联保存子节点并传播外键，同时返回同构的主键结构 (1:1 返回 Map<String, Object>, 1:N 返回 List<Map<String, Object>>) */
    private Object saveChildPlanRecursively(DSLContext dsl, SaveNodePlan nodePlan, Long parentId) {
        if (nodePlan == null) {
            return null;
        }

        String table = nodePlan.getPrimaryTable();
        String fkField = nodePlan.getParentForeignKey();
        List<Map<String, Object>> rows = nodePlan.getRecords();

        // 虚拟模块无物理主表：不执行单行落库，直接将 parentId 透传给下级子模块
        if (table == null || table.isBlank()) {
            Map<String, Object> virtualMap = new LinkedHashMap<>();
            if (nodePlan.getChildren() != null && !nodePlan.getChildren().isEmpty()) {
                for (SaveNodePlan child : nodePlan.getChildren()) {
                    Object childRes = saveChildPlanRecursively(dsl, child, parentId);
                    if (child.getModuleId() != null && childRes != null) {
                        virtualMap.put(String.valueOf(child.getModuleId()), childRes);
                    }
                }
            }
            return virtualMap;
        }

        // ==================== 1:N 孤儿差集自动软删除 (Orphan Removal) ====================
        if (nodePlan.isMany()
                && fkField != null
                && !fkField.isBlank()
                && parentId != null
                && !"id".equalsIgnoreCase(fkField)) {
            // 1. 提取当前请求中该子模块提交的有效已有 ID 集合（不包括显式标记为 deleted=1 的记录）
            Set<Long> incomingValidIds = new HashSet<>();
            if (rows != null) {
                for (Map<String, Object> r : rows) {
                    if (r == null) {
                        continue;
                    }
                    Object deletedObj = r.get("deleted");
                    if (deletedObj == null && r.get(table) instanceof Map<?, ?> tm) {
                        deletedObj = tm.get("deleted");
                    }
                    if (isDeletedFlag(deletedObj)) {
                        continue;
                    }
                    Object idObj = r.get("id");
                    if (idObj == null && r.get(table) instanceof Map<?, ?> tm) {
                        idObj = tm.get("id");
                    }
                    if (idObj instanceof Number num && num.longValue() > 0) {
                        incomingValidIds.add(num.longValue());
                    }
                }
            }

            // 2. 查询数据库中该父记录 (parentId) 下当前存活的所有有效子记录 ID (deleted = 0)
            List<Long> dbLiveIds = Collections.emptyList();
            try {
                dbLiveIds =
                        dsl.select(DSL.field(DSL.name(table, "id"), Long.class))
                                .from(DSL.table(DSL.name(table)))
                                .where(
                                        DSL.field(DSL.name(table, fkField))
                                                .eq(parentId)
                                                .and(
                                                        DSL.field(DSL.name(table, "deleted"))
                                                                .eq((byte) 0)))
                                .fetch(0, Long.class);
            } catch (Exception ex) {
                log.warn("查询物理表 [{}] 关联父外键 [{}] 存活记录失败: {}", table, fkField, ex.getMessage());
            }

            // 3. 计算差集：在数据库中存活，但在前端提交的最新数组中被移除的孤儿记录
            if (dbLiveIds != null && !dbLiveIds.isEmpty()) {
                List<Long> orphanIds =
                        dbLiveIds.stream()
                                .filter(id -> id != null && !incomingValidIds.contains(id))
                                .toList();

                if (!orphanIds.isEmpty()) {
                    Long currentUserId = UserContext.getUserId();
                    LocalDateTime now = LocalDateTime.now();
                    log.info(
                            "1:N 子表 [{}] 自动执行孤儿记录软删除: parentId={}, fkField={}, orphanIds={}",
                            table,
                            parentId,
                            fkField,
                            orphanIds);
                    dsl.update(DSL.table(DSL.name(table)))
                            .set(DSL.field(DSL.name(table, "deleted")), (byte) 1)
                            .set(DSL.field(DSL.name(table, "updated_by")), currentUserId)
                            .set(DSL.field(DSL.name(table, "updated_date")), now)
                            .where(
                                    DSL.field(DSL.name(table, "id"))
                                            .in(orphanIds)
                                            .and(
                                                    DSL.field(DSL.name(table, "deleted"))
                                                            .eq((byte) 0)))
                            .execute();

                    // 级联软删除当前孤儿记录的所有下级物理子模块数据，防止孙表记录游离
                    cascadeDeleteOrphanDescendants(
                            dsl, nodePlan.getChildren(), orphanIds, currentUserId, now);
                }
            }
        }

        List<Map<String, Object>> rowResults = new ArrayList<>();
        if (rows != null) {
            List<DataRow> sourceRows = nodePlan.getSourceDataRows();
            for (int i = 0; i < rows.size(); i++) {
                Map<String, Object> row = rows.get(i);
                DataRow sourceRow =
                        (sourceRows != null && i < sourceRows.size()) ? sourceRows.get(i) : null;
                Map<String, Object> childRowMap = new LinkedHashMap<>();
                // 强制自动回填外键
                if (fkField != null && !fkField.isBlank() && parentId != null) {
                    row.put(fkField, parentId);
                    backfillForeignKey(sourceRow, table, fkField, parentId);
                }
                Long rowId = upsertSingleRow(dsl, table, nodePlan, row, sourceRow, parentId);
                if (rowId != null) {
                    childRowMap.put(table, Map.of("id", rowId));
                }

                // 递归向下传播孙节点 (仅针对当前行拥有的子计划，避免 1:N 孙表交叉污染)
                List<SaveNodePlan> rowChildren =
                        (nodePlan.getRowChildren() != null && i < nodePlan.getRowChildren().size())
                                ? nodePlan.getRowChildren().get(i)
                                : nodePlan.getChildren();
                if (rowChildren != null && !rowChildren.isEmpty()) {
                    Long currentId = rowId != null ? rowId : parentId;
                    for (SaveNodePlan grandChild : rowChildren) {
                        Object grandChildRes = saveChildPlanRecursively(dsl, grandChild, currentId);
                        if (grandChild.getModuleId() != null && grandChildRes != null) {
                            childRowMap.put(
                                    String.valueOf(grandChild.getModuleId()), grandChildRes);
                        }
                    }
                }
                rowResults.add(childRowMap);
            }
        }

        if (nodePlan.isMany()) {
            return rowResults;
        } else {
            return rowResults.isEmpty() ? Collections.emptyMap() : rowResults.get(0);
        }
    }

    /** 回填主键到 DataRow 对应的表对象中 */
    private void backfillId(DataRow sourceRow, String table, Long id) {
        if (sourceRow == null || id == null) {
            return;
        }
        if (table != null && !table.isBlank()) {
            sourceRow.putValue(table, "id", id);
        } else {
            sourceRow.setTable("id", id);
        }
    }

    /** 回填外键到 DataRow 对应的表对象中 */
    private void backfillForeignKey(
            DataRow sourceRow, String table, String fkField, Long parentId) {
        if (sourceRow == null || fkField == null || fkField.isBlank() || parentId == null) {
            return;
        }
        if (table != null && !table.isBlank()) {
            sourceRow.putValue(table, fkField, parentId);
        } else {
            sourceRow.setTable(fkField, parentId);
        }
    }

    /** 单行 Upsert / Soft-Delete 落库 */
    private Long upsertSingleRow(
            DSLContext dsl,
            String table,
            SaveNodePlan nodePlan,
            Map<String, Object> row,
            DataRow sourceRow,
            Long parentId) {
        if (row == null || row.isEmpty()) {
            return null;
        }

        Object idObj = row.get("id");
        if (idObj == null && table != null && row.get(table) instanceof Map<?, ?> tm) {
            idObj = tm.get("id");
        }
        Long id = null;
        if (idObj instanceof Number num && num.longValue() > 0) {
            id = num.longValue();
        }

        // 提取是否标记删除
        Object deletedObj = row.get("deleted");
        if (deletedObj == null && table != null && row.get(table) instanceof Map<?, ?> tm) {
            deletedObj = tm.get("deleted");
        }
        boolean isDeleted = isDeletedFlag(deletedObj);

        Long currentUserId = UserContext.getUserId();
        Long subjectId = AppContext.getSubjectId();
        LocalDateTime now = LocalDateTime.now();

        // 1. 如果显式标记为删除
        if (isDeleted) {
            if (id != null) {
                // 针对数据库中已存在的记录执行逻辑软删除 (deleted = 1)
                dsl.update(DSL.table(DSL.name(table)))
                        .set(DSL.field(DSL.name(table, "deleted")), (byte) 1)
                        .set(DSL.field(DSL.name(table, "updated_by")), currentUserId)
                        .set(DSL.field(DSL.name(table, "updated_date")), now)
                        .where(
                                DSL.field(DSL.name(table, "id"))
                                        .eq(id)
                                        .and(DSL.field(DSL.name(table, "deleted")).eq((byte) 0)))
                        .execute();
                backfillId(sourceRow, table, id);
                return id;
            } else {
                // 纯前端临时新建、未入库前即删除的草稿记录，直接丢弃无需入库
                return null;
            }
        }

        // 2. 组装正常新增/更新字段
        Map<Field<Object>, Object> fieldValues = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String col = entry.getKey();
            Object val = entry.getValue();
            if (col == null
                    || "children".equals(col)
                    || col.matches("\\d+")
                    || (table != null && col.equalsIgnoreCase(table))) {
                continue; // 忽略嵌套子节点数据、子模块数字键及当前主表名自身
            }
            if (id != null && "id".equalsIgnoreCase(col)) {
                continue; // 更新模式下不重复 SET 主键 id
            }
            if ("deleted".equalsIgnoreCase(col)) {
                continue; // deleted 状态由引擎统一维护，避免外部非法覆盖
            }

            // 支持实体中配置的 JSON/JSON 数组字段 (如 nature, cooperation_types 等 List/Map 结构)
            if (val instanceof Map<?, ?> || val instanceof List<?>) {
                try {
                    val = MAPPER.writeValueAsString(val);
                } catch (Exception ex) {
                    log.warn("序列化物理表 [{}] 字段 [{}] 为 JSON 失败: {}", table, col, ex.getMessage());
                }
            }

            fieldValues.put(DSL.field(DSL.name(table, col)), val);
        }

        if (id != null) {
            // Update 模式
            fieldValues.put(DSL.field(DSL.name(table, "updated_by")), currentUserId);
            fieldValues.put(DSL.field(DSL.name(table, "updated_date")), now);

            dsl.update(DSL.table(DSL.name(table)))
                    .set(fieldValues)
                    .where(
                            DSL.field(DSL.name(table, "id"))
                                    .eq(id)
                                    .and(DSL.field(DSL.name(table, "deleted")).eq((byte) 0)))
                    .execute();
            backfillId(sourceRow, table, id);
            return id;
        } else {
            // Insert 模式
            fieldValues.put(
                    DSL.field(DSL.name(table, "subject_id")), subjectId != null ? subjectId : 0L);
            fieldValues.put(DSL.field(DSL.name(table, "created_by")), currentUserId);
            fieldValues.put(DSL.field(DSL.name(table, "created_date")), now);
            fieldValues.put(DSL.field(DSL.name(table, "updated_by")), currentUserId);
            fieldValues.put(DSL.field(DSL.name(table, "updated_date")), now);
            fieldValues.put(DSL.field(DSL.name(table, "deleted")), (byte) 0);

            var insertQuery = dsl.insertInto(DSL.table(DSL.name(table))).set(fieldValues);

            Long generatedId = null;
            try {
                generatedId =
                        insertQuery
                                .returningResult(DSL.field(DSL.name(table, "id"), Long.class))
                                .fetchOne(0, Long.class);
            } catch (Exception ex) {
                log.debug("returningResult 获取自增主键未成功，尝试通过 lastID 回退获取: {}", ex.getMessage());
            }

            if (generatedId == null || generatedId <= 0) {
                try {
                    if (dsl.lastID() != null && dsl.lastID().longValue() > 0) {
                        generatedId = dsl.lastID().longValue();
                    } else {
                        // 通过 SELECT LAST_INSERT_ID() 兜底
                        generatedId =
                                dsl.select(DSL.function("LAST_INSERT_ID", Long.class))
                                        .fetchOne(0, Long.class);
                    }
                } catch (Exception ex) {
                    log.warn("获取最后插入主键失败: {}", ex.getMessage());
                }
            }

            if (generatedId == null || generatedId <= 0) {
                throw new IllegalStateException(
                        String.format("物理表 [%s] 新增数据落库失败：未能获取到生成的有效主键 ID", table));
            }

            row.put("id", generatedId);
            backfillId(sourceRow, table, generatedId);
            return generatedId;
        }
    }

    private boolean isDeletedFlag(Object deletedObj) {
        if (deletedObj == null) {
            return false;
        }
        if (deletedObj instanceof Number num) {
            return num.intValue() == 1;
        }
        if (deletedObj instanceof Boolean bool) {
            return bool;
        }
        if (deletedObj instanceof String str) {
            return "1".equals(str) || "true".equalsIgnoreCase(str);
        }
        return false;
    }

    /** 递归级联软删除孤儿记录的所有下级物理子孙表数据，防止产生游离孙表记录 */
    private void cascadeDeleteOrphanDescendants(
            DSLContext dsl,
            List<SaveNodePlan> childPlans,
            List<Long> parentOrphanIds,
            Long currentUserId,
            LocalDateTime now) {
        if (childPlans == null
                || childPlans.isEmpty()
                || parentOrphanIds == null
                || parentOrphanIds.isEmpty()) {
            return;
        }
        for (SaveNodePlan childPlan : childPlans) {
            String childTable = childPlan.getPrimaryTable();
            String fkField = childPlan.getParentForeignKey();
            if (childTable == null || childTable.isBlank()) {
                // 虚拟模块穿透：直接将当前孤儿 ID 列表透传给下级
                cascadeDeleteOrphanDescendants(
                        dsl, childPlan.getChildren(), parentOrphanIds, currentUserId, now);
                continue;
            }
            if (fkField == null || fkField.isBlank() || "id".equalsIgnoreCase(fkField)) {
                continue;
            }
            try {
                // 查询该孤儿批次对应的直接子记录 ID
                List<Long> childIds =
                        dsl.select(DSL.field(DSL.name(childTable, "id"), Long.class))
                                .from(DSL.table(DSL.name(childTable)))
                                .where(
                                        DSL.field(DSL.name(childTable, fkField))
                                                .in(parentOrphanIds)
                                                .and(
                                                        DSL.field(DSL.name(childTable, "deleted"))
                                                                .eq((byte) 0)))
                                .fetch(0, Long.class);

                if (childIds != null && !childIds.isEmpty()) {
                    log.info(
                            "级联软删除孤儿从表 [{}] 关联数据: fkField={}, 匹配记录数={}",
                            childTable,
                            fkField,
                            childIds.size());
                    dsl.update(DSL.table(DSL.name(childTable)))
                            .set(DSL.field(DSL.name(childTable, "deleted")), (byte) 1)
                            .set(DSL.field(DSL.name(childTable, "updated_by")), currentUserId)
                            .set(DSL.field(DSL.name(childTable, "updated_date")), now)
                            .where(DSL.field(DSL.name(childTable, "id")).in(childIds))
                            .execute();

                    // 继续向下递归级联软删除更深层孙节点
                    cascadeDeleteOrphanDescendants(
                            dsl, childPlan.getChildren(), childIds, currentUserId, now);
                }
            } catch (Exception ex) {
                log.warn("级联软删除从表 [{}] 孤儿记录失败: {}", childTable, ex.getMessage());
            }
        }
    }
}
