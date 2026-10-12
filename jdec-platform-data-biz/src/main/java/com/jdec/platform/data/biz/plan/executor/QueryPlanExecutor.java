package com.jdec.platform.data.biz.plan.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.common.DataRow;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.biz.plan.assembler.TreeResultAssembler;
import com.jdec.platform.data.biz.plan.model.AttachedTableSpec;
import com.jdec.platform.data.biz.plan.model.PhysicalFieldSpec;
import com.jdec.platform.data.biz.plan.model.QueryNodePlan;
import com.jdec.platform.data.biz.plan.model.QueryPlan;
import com.jdec.platform.shared.datasource.JooqDataContextFactory;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSON;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SelectConditionStep;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * 结构化查询计划执行器 (QueryPlanExecutor)
 *
 * <p>基于两阶段执行模型：
 *
 * <ul>
 *   <li>Stage 0 (Root Scan)：执行主表分页 SQL，计算精确 COUNT 与当前页主键 ID 集合；
 *   <li>Stage 1 (Batch Fetch)：以极少数批次 SQL (fk IN (...)) 批量拉取所有子孙模块数据，杜绝 N+1 递归；
 *   <li>Stage 2 (Assembler)：调用 TreeResultAssembler 在内存中聚合缝合为行内自相似嵌套树。
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryPlanExecutor {

    private final JooqDataContextFactory jooqDataContextFactory;
    private final TreeResultAssembler treeResultAssembler;

    /** 执行计数查询计划 (返回满足条件的记录总数) */
    public Long executeCount(QueryPlan plan) {
        if (plan == null || plan.getRootNodePlan() == null) {
            return 0L;
        }

        QueryNodePlan rootPlan = plan.getRootNodePlan();
        String primaryTable = rootPlan.getPrimaryTable();
        if (primaryTable == null || primaryTable.isBlank()) {
            return 0L;
        }

        DSLContext dsl = jooqDataContextFactory.getContext();
        SelectJoinStep<?> countStep =
                applyAttachedJoins(
                        dsl.selectCount().from(DSL.table(DSL.name(primaryTable))),
                        rootPlan.getAttachedJoins());

        Long total = countStep.where(rootPlan.getCondition()).fetchOne(0, Long.class);
        return total != null ? total : 0L;
    }

    /** 执行整树查询计划 (纯数据引擎，返回分页数据区块) */
    public DataPage<DataBlock> execute(QueryPlan plan) {
        if (plan == null || plan.getRootNodePlan() == null) {
            return DataPage.empty(1, 20);
        }

        QueryNodePlan rootPlan = plan.getRootNodePlan();
        String primaryTable = rootPlan.getPrimaryTable();
        DSLContext dsl = jooqDataContextFactory.getContext();

        // 0. 防御空主表 (如未配置的虚拟模块)
        if (primaryTable == null || primaryTable.isBlank()) {
            return DataPage.empty(rootPlan.getPageNo(), rootPlan.getPageSize());
        }

        // ==================== Stage 0: 根节点扫描与分页 ====================
        // 1. 构建主表精确 COUNT 查询 (若有伴生表同样需 LEFT JOIN 保证伴生表条件解析正常)
        long total = executeCount(plan);

        int pageNo =
                (rootPlan.getPageNo() != null && rootPlan.getPageNo() > 0)
                        ? rootPlan.getPageNo()
                        : 1;
        int pageSize =
                (rootPlan.getPageSize() != null && rootPlan.getPageSize() > 0)
                        ? rootPlan.getPageSize()
                        : 20;
        int offset = (pageNo - 1) * pageSize;

        // 2. 构建根查询物理投影字段列表
        List<Field<?>> selectFields =
                buildSelectFields(primaryTable, rootPlan.getProjectedFields());

        SelectJoinStep<Record> selectStep =
                selectFields.isEmpty()
                        ? dsl.select(DSL.asterisk()).from(DSL.table(DSL.name(primaryTable)))
                        : dsl.select(selectFields).from(DSL.table(DSL.name(primaryTable)));

        // 2.1 若根节点配置有附加表 (伴生 1:1 / 上游 N:1)，执行安全 LEFT JOIN
        selectStep = applyAttachedJoins(selectStep, rootPlan.getAttachedJoins());

        // 3. 执行主表分页查询
        Result<Record> records =
                selectStep
                        .where(rootPlan.getCondition())
                        .orderBy(rootPlan.getOrderFields())
                        .limit(pageSize)
                        .offset(offset)
                        .fetch();

        List<Long> primaryIds = new ArrayList<>();
        List<DataRow> rootRows = new ArrayList<>();
        for (Record r : records) {
            DataRow rootRow = extractRow(r, primaryTable, rootPlan.getProjectedFields(), null);
            rootRows.add(rootRow);
            Object pk = TreeResultAssembler.extractRowPrimaryId(rootRow, primaryTable);
            if (pk instanceof Number num) {
                primaryIds.add(num.longValue());
            }
        }

        // ==================== Stage 1: 批量拉取子模块数据 (Batch Fetch) ====================
        Map<QueryNodePlan, List<DataRow>> rawDataByNode = new IdentityHashMap<>();
        if (!primaryIds.isEmpty()
                && rootPlan.getChildren() != null
                && !rootPlan.getChildren().isEmpty()) {
            batchFetchChildNodesRecursively(dsl, primaryIds, rootPlan.getChildren(), rawDataByNode);
        }

        // ==================== Stage 2: 内存装配聚合根行内树 ====================
        if (!rootRows.isEmpty()
                && rootPlan.getChildren() != null
                && !rootPlan.getChildren().isEmpty()) {
            treeResultAssembler.assembleChildrenRecursively(
                    rootRows, rootPlan.getModuleId(), rootPlan.getChildren(), rawDataByNode);
        }

        DataBlock block = DataBlock.of(rootPlan.getModuleId(), rootRows);
        return DataPage.<DataBlock>builder()
                .pageNo(pageNo)
                .pageSize(pageSize)
                .total(total)
                .data(block)
                .build();
    }

    /** 递归分批批量拉取各层子模块/子表数据 (利用 fk IN (:parentIds)) */
    private void batchFetchChildNodesRecursively(
            DSLContext dsl,
            List<Long> parentIds,
            List<QueryNodePlan> childPlans,
            Map<QueryNodePlan, List<DataRow>> rawDataByNode) {

        if (parentIds == null
                || parentIds.isEmpty()
                || childPlans == null
                || childPlans.isEmpty()) {
            return;
        }

        for (QueryNodePlan childPlan : childPlans) {
            String childTable = childPlan.getPrimaryTable();
            String fkField = childPlan.getParentForeignKey();
            log.debug(
                    "[QueryPlanExecutor] 批量拉取子节点数据: moduleId={}, primaryTable={}, fkField={}, parentIdsCount={}",
                    childPlan.getModuleId(),
                    childTable,
                    fkField,
                    parentIds.size());

            if (childTable == null
                    || childTable.isBlank()
                    || fkField == null
                    || fkField.isBlank()) {
                // 🌟 若当前子节点是虚拟模块 (无物理主表)，且包含下级子计划，
                // 则将当前父级实体主键列表 parentIds 原样向下透传，递归抓取其下级模块数据
                if ((childTable == null || childTable.isBlank())
                        && childPlan.getChildren() != null
                        && !childPlan.getChildren().isEmpty()) {
                    log.debug(
                            "[QueryPlanExecutor] 节点 moduleId={} 为虚拟模块, 透传 parentIds: {} 递归拉取子节点数: {}",
                            childPlan.getModuleId(),
                            parentIds,
                            childPlan.getChildren().size());
                    batchFetchChildNodesRecursively(
                            dsl, parentIds, childPlan.getChildren(), rawDataByNode);
                } else {
                    log.warn(
                            "[QueryPlanExecutor] 节点 moduleId={} 跳过拉取 (childTable={}, fkField={}, hasChildren={})",
                            childPlan.getModuleId(),
                            childTable,
                            fkField,
                            childPlan.getChildren() != null && !childPlan.getChildren().isEmpty());
                }
                continue;
            }

            // 1. 构建物理投影字段列表 (包含主键、技术外键及伴生表字段)
            List<Field<?>> selectFields =
                    buildSelectFields(childTable, childPlan.getProjectedFields(), fkField);

            SelectJoinStep<Record> queryStep =
                    selectFields.isEmpty()
                            ? dsl.select(DSL.asterisk()).from(DSL.table(DSL.name(childTable)))
                            : dsl.select(selectFields).from(DSL.table(DSL.name(childTable)));

            // 2. 若子节点有附加表 (伴生 1:1 / 上游 N:1)，执行安全 LEFT JOIN
            queryStep = applyAttachedJoins(queryStep, childPlan.getAttachedJoins());

            // 3. 分批拉取 (Batch Partitioning, 每批最多 500 个 parentId, 防止超长 IN 参数导致数据库异常或性能下降)
            List<DataRow> childRows = new ArrayList<>();
            List<Long> childPrimaryIds = new ArrayList<>();
            int batchSize = 500;
            for (int i = 0; i < parentIds.size(); i += batchSize) {
                List<Long> batchIds =
                        parentIds.subList(i, Math.min(i + batchSize, parentIds.size()));
                var query = queryStep.where(DSL.field(DSL.name(childTable, fkField)).in(batchIds));

                if (childPlan.getCondition() != null
                        && !DSL.noCondition().equals(childPlan.getCondition())) {
                    query = query.and(childPlan.getCondition());
                } else {
                    query = query.and(DSL.field(DSL.name(childTable, "deleted")).eq((byte) 0));
                }

                Result<Record> childRecords = query.orderBy(childPlan.getOrderFields()).fetch();
                for (Record r : childRecords) {
                    DataRow crow =
                            extractRow(r, childTable, childPlan.getProjectedFields(), fkField);
                    childRows.add(crow);
                    Object pk = TreeResultAssembler.extractRowPrimaryId(crow, childTable);
                    if (pk instanceof Number pkNum) {
                        childPrimaryIds.add(pkNum.longValue());
                    }
                }
            }

            log.debug(
                    "[QueryPlanExecutor] 节点批量拉取完成: moduleId={}, childTable={}, 捕获子行数={}, 产生下级候选主键数={}",
                    childPlan.getModuleId(),
                    childTable,
                    childRows.size(),
                    childPrimaryIds.size());
            rawDataByNode.put(childPlan, childRows);

            // 如果有孙模块/孙表，以当前从表的主键集合继续批量抓取孙表数据
            if (!childPrimaryIds.isEmpty()
                    && childPlan.getChildren() != null
                    && !childPlan.getChildren().isEmpty()) {
                batchFetchChildNodesRecursively(
                        dsl, childPrimaryIds, childPlan.getChildren(), rawDataByNode);
            }
        }
    }

    /** 将物理投影规格转换为 jOOQ Field 列表 (支持伴生表列名别名隔离，且保证必须包含主键 id) */
    private List<Field<?>> buildSelectFields(String primaryTable, List<PhysicalFieldSpec> specs) {
        return buildSelectFields(primaryTable, specs, null);
    }

    /** 将物理投影规格转换为 jOOQ Field 列表 (支持伴生表列名别名隔离，且保证必须包含主键 id 与技术外键 fkField) */
    private List<Field<?>> buildSelectFields(
            String primaryTable, List<PhysicalFieldSpec> specs, String fkField) {
        // 若未指定投影规格（如全字段投影回退场景），返回空列表触发 SELECT * 全字段扫描
        if (specs == null || specs.isEmpty()) {
            return Collections.emptyList();
        }

        List<Field<?>> fields = new ArrayList<>();
        Set<String> added = new HashSet<>();

        // 0. 主键 id 是系统级锚点，必须包含在 SELECT 中
        if (primaryTable != null && !primaryTable.isBlank()) {
            fields.add(DSL.field(DSL.name(primaryTable, "id")).as("id"));
            added.add("id");

            // 若存在技术外键字段（如从表关联父表的外键），确保纳入 SELECT 供装配器匹配
            if (fkField != null && !fkField.isBlank() && added.add(fkField.toLowerCase())) {
                fields.add(DSL.field(DSL.name(primaryTable, fkField)).as(fkField));
            }
        }
        for (PhysicalFieldSpec spec : specs) {
            String tbl = spec.getTableName() != null ? spec.getTableName() : primaryTable;
            String col = spec.getColumnName();
            if (col == null || col.isBlank()) {
                continue;
            }

            // 主表字段直接使用 col 作为别名，伴生表字段使用 tbl_col 作为别名，防止重名列碰撞
            String alias = tbl.equalsIgnoreCase(primaryTable) ? col : tbl + "_" + col;
            if (added.add(alias.toLowerCase())) {
                fields.add(DSL.field(DSL.name(tbl, col)).as(alias));
            }
        }
        return fields;
    }

    /** 物理表驱动的字段候选值去重查询 (直接基于物理表名与列名，脱离 fieldId) */
    public List<Object> executeDistinctOptions(
            QueryPlan plan, String tableName, String columnName, String direction, Integer limit) {
        if (plan == null || plan.getRootNodePlan() == null) {
            return Collections.emptyList();
        }

        QueryNodePlan rootPlan = plan.getRootNodePlan();
        String rootPrimaryTable = rootPlan.getPrimaryTable();
        if (rootPrimaryTable == null || rootPrimaryTable.isBlank()) {
            return Collections.emptyList();
        }

        String targetTable =
                (tableName != null && !tableName.isBlank()) ? tableName.trim() : rootPrimaryTable;
        String column = (columnName != null) ? columnName.trim() : null;
        if (column == null || column.isBlank()) {
            return Collections.emptyList();
        }

        List<QueryNodePlan> pathToTarget = new ArrayList<>();
        List<QueryNodePlan> pathResult = new ArrayList<>();
        findPathToTable(rootPlan, targetTable, pathToTarget, pathResult);

        QueryNodePlan targetNode =
                pathResult.isEmpty() ? rootPlan : pathResult.get(pathResult.size() - 1);
        String targetPrimaryTable = targetNode.getPrimaryTable();

        DSLContext dsl = jooqDataContextFactory.getContext();
        Field<Object> distinctField = DSL.field(DSL.name(targetTable, column));

        int maxLimit = (limit != null && limit > 0) ? Math.min(limit, 500) : 100;

        // 目标节点即根节点时 (pathResult 为空或仅含根) targetNode == rootPlan
        SelectJoinStep<?> selectStep =
                applyAttachedJoins(
                        dsl.selectDistinct(distinctField)
                                .from(DSL.table(DSL.name(targetPrimaryTable))),
                        targetNode.getAttachedJoins());

        Condition combinedCond = DSL.noCondition();

        if (pathResult.size() > 1) {
            Set<String> joinedTables = new HashSet<>();
            joinedTables.add(targetPrimaryTable.toLowerCase());

            // 目标表属于从属/子孙模块节点：沿路径向上反向 INNER JOIN 到根节点（如 member_contact -> member -> info）
            // 注意：中间节点可能是虚拟模块（primaryTable 为空），此时子节点已直接跨越虚拟层级关联到上层物理父表 (child.parentPrimaryTable)
            for (int i = pathResult.size() - 1; i >= 0; i--) {
                QueryNodePlan child = pathResult.get(i);
                String childTable = child.getPrimaryTable();
                String parentTable = child.getParentPrimaryTable();
                if (childTable == null || childTable.isBlank()) {
                    continue;
                }
                if (parentTable != null
                        && !parentTable.isBlank()
                        && joinedTables.add(parentTable.toLowerCase())) {
                    String cFk =
                            child.getParentForeignKey() != null
                                    ? child.getParentForeignKey()
                                    : "id";
                    String pKey = child.getParentKey() != null ? child.getParentKey() : "id";
                    selectStep =
                            (SelectJoinStep<?>)
                                    selectStep
                                            .innerJoin(DSL.table(DSL.name(parentTable)))
                                            .on(
                                                    DSL.field(DSL.name(childTable, cFk))
                                                            .eq(
                                                                    DSL.field(
                                                                            DSL.name(
                                                                                    parentTable,
                                                                                    pKey))))
                                            .and(
                                                    DSL.field(DSL.name(parentTable, "deleted"))
                                                            .eq((byte) 0));
                }
            }

            // 对路径上所有已引入的物理表节点补充其 attachedJoins (如伴生表/上游表)
            for (QueryNodePlan nodeOnPath : pathResult) {
                if (nodeOnPath.getPrimaryTable() != null
                        && joinedTables.contains(nodeOnPath.getPrimaryTable().toLowerCase())
                        && nodeOnPath.getAttachedJoins() != null) {
                    selectStep = applyAttachedJoins(selectStep, nodeOnPath.getAttachedJoins());
                }
            }

            // 收集从目标节点到祖先节点路径上的所有过滤条件
            for (QueryNodePlan nodeOnPath : pathResult) {
                if (nodeOnPath.getCondition() != null) {
                    combinedCond = combinedCond.and(nodeOnPath.getCondition());
                }
            }
        } else {
            if (targetNode.getCondition() != null) {
                combinedCond = combinedCond.and(targetNode.getCondition());
            }
        }

        combinedCond = combinedCond.and(distinctField.isNotNull());

        SelectConditionStep<?> conditionStep = selectStep.where(combinedCond);
        Result<?> records;
        if (direction != null && !direction.isBlank()) {
            records =
                    "DESC".equalsIgnoreCase(direction.trim())
                            ? conditionStep.orderBy(distinctField.desc()).limit(maxLimit).fetch()
                            : conditionStep.orderBy(distinctField.asc()).limit(maxLimit).fetch();
        } else {
            records = conditionStep.limit(maxLimit).fetch();
        }
        return extractDistinctOptionValues(records, direction);
    }

    private void findPathToTable(
            QueryNodePlan current,
            String targetTable,
            List<QueryNodePlan> currentPath,
            List<QueryNodePlan> result) {
        if (current == null || !result.isEmpty()) return;
        currentPath.add(current);
        if (targetTable == null
                || targetTable.isBlank()
                || (current.getPrimaryTable() != null
                        && current.getPrimaryTable().equalsIgnoreCase(targetTable))) {
            result.addAll(currentPath);
            return;
        }
        if (current.getAttachedJoins() != null) {
            for (var cj : current.getAttachedJoins()) {
                if (cj.getTargetTable() != null
                        && cj.getTargetTable().equalsIgnoreCase(targetTable)) {
                    result.addAll(currentPath);
                    return;
                }
            }
        }
        if (current.getChildren() != null) {
            for (QueryNodePlan child : current.getChildren()) {
                findPathToTable(child, targetTable, new ArrayList<>(currentPath), result);
            }
        }
    }

    /** 从 SELECT DISTINCT 结果集中提取候选值，自动解析与展平 JSON 数组并进行内存去重（支持按需可选排序） */
    private List<Object> extractDistinctOptionValues(Result<?> records, String direction) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Object> uniqueValues = new LinkedHashSet<>();
        for (Record r : records) {
            Object rawVal = r.get(0);
            if (rawVal == null) {
                continue;
            }
            Object parsed = parseIfJson(rawVal);
            if (parsed instanceof Iterable<?> iterable) {
                for (Object item : iterable) {
                    if (item != null) {
                        uniqueValues.add(item);
                    }
                }
            } else {
                uniqueValues.add(parsed);
            }
        }

        List<Object> result = new ArrayList<>(uniqueValues);
        if (direction != null && !direction.isBlank()) {
            Comparator<Object> comparator =
                    (a, b) -> {
                        String sa =
                                (a instanceof Map || a instanceof Collection)
                                        ? a.toString()
                                        : String.valueOf(a);
                        String sb =
                                (b instanceof Map || b instanceof Collection)
                                        ? b.toString()
                                        : String.valueOf(b);
                        return sa.compareTo(sb);
                    };
            if ("DESC".equalsIgnoreCase(direction.trim())) {
                result.sort(comparator.reversed());
            } else {
                result.sort(comparator);
            }
        }
        return result;
    }

    /**
     * 为查询步骤叠加附加表 (伴生垂直拆分 1:1 / 上游多对一 N:1) 的安全 LEFT JOIN，统一附加 deleted = 0 约束。
     *
     * <p>两种方向共用 {@code source.sourceField = target.targetField} 这一通用形式：伴生表是本表出主键、
     * 目标表出外键；上游表是本表出外键、目标表出主键。
     */
    @SuppressWarnings("unchecked")
    private static <R extends Record> SelectJoinStep<R> applyAttachedJoins(
            SelectJoinStep<R> step, List<AttachedTableSpec> attachedJoins) {
        if (attachedJoins == null || attachedJoins.isEmpty()) {
            return step;
        }
        SelectJoinStep<R> result = step;
        for (AttachedTableSpec joinSpec : attachedJoins) {
            String tgtTable = joinSpec.getTargetTable();
            String tgtField = joinSpec.getTargetField();
            String srcTable = joinSpec.getSourceTable();
            String srcField = joinSpec.getSourceField();
            result =
                    (SelectJoinStep<R>)
                            result.leftJoin(DSL.table(DSL.name(tgtTable)))
                                    .on(
                                            DSL.field(DSL.name(srcTable, srcField))
                                                    .eq(DSL.field(DSL.name(tgtTable, tgtField))))
                                    .and(DSL.field(DSL.name(tgtTable, "deleted")).eq((byte) 0));
        }
        return result;
    }

    /**
     * 将一条物理记录抽取为同构 DataRow：主表空间先落主键 id，再按投影规格（或全列回退）填充字段。
     *
     * @param fkField 非空时额外注入内部技术外键 {@code _fk_xxx}，供装配阶段按父键分组（装配完成后剥离）
     */
    private DataRow extractRow(
            Record r, String table, List<PhysicalFieldSpec> specs, String fkField) {
        DataRow row = new DataRow();
        Object pkVal = null;
        try {
            pkVal = r.get(DSL.field(DSL.name(table, "id")));
            if (pkVal == null) {
                pkVal = r.get("id");
            }
        } catch (Exception ignored) {
        }

        Map<String, Object> tableSpace = new LinkedHashMap<>();
        if (pkVal != null) {
            tableSpace.put("id", pkVal);
        }
        row.putTable(table, tableSpace);

        // 临时注入内部技术外键（供装配器按外键分组，装配完成后自动剥离）
        if (fkField != null && !fkField.isBlank()) {
            try {
                Object fkVal = r.get(fkField);
                if (fkVal != null) {
                    row.getTables().put("_fk_" + fkField, fkVal);
                }
            } catch (Exception ignored) {
            }
        }

        if (specs != null && !specs.isEmpty()) {
            for (PhysicalFieldSpec spec : specs) {
                if (spec.getColumnName() == null) {
                    continue;
                }
                String tbl = spec.getTableName() != null ? spec.getTableName() : table;
                String alias =
                        tbl.equalsIgnoreCase(table)
                                ? spec.getColumnName()
                                : tbl + "_" + spec.getColumnName();
                Object val = null;
                try {
                    val = r.get(alias);
                } catch (Exception e) {
                    try {
                        val = r.get(spec.getColumnName());
                    } catch (Exception ignored) {
                    }
                }
                val = parseIfJson(val);
                Map<String, Object> targetTableMap = row.getTable(tbl);
                if (targetTableMap == null) {
                    targetTableMap = new LinkedHashMap<>();
                    row.putTable(tbl, targetTableMap);
                }
                targetTableMap.put(spec.getColumnName(), val);
            }
        } else {
            // 无字段元数据回退：读取记录中全部可用列并注入当前物理表空间
            for (Field<?> f : r.fields()) {
                String col = f.getName();
                if ("id".equalsIgnoreCase(col) || col.startsWith("_fk_")) {
                    continue;
                }
                tableSpace.put(col, parseIfJson(r.get(f)));
            }
        }
        return row;
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 自动将 JSON 字符串或 jOOQ JSON 对象转换为结构化的 List/Map，保持返回值纯净 */
    private Object parseIfJson(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof JSON jooqJson) {
            String jsonStr = jooqJson.data();
            if (jsonStr == null || jsonStr.isBlank()) {
                return null;
            }
            try {
                return MAPPER.readValue(jsonStr, Object.class);
            } catch (Exception e) {
                return jsonStr;
            }
        }
        if (val instanceof String str) {
            String trimmed = str.trim();
            if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
                    || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                try {
                    return MAPPER.readValue(trimmed, Object.class);
                } catch (Exception ignored) {
                    return val;
                }
            }
        }
        return val;
    }
}
