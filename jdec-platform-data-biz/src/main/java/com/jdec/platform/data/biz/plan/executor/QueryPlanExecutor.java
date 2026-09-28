package com.jdec.platform.data.biz.plan.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.biz.plan.assembler.TreeResultAssembler;
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
        SelectJoinStep<?> countStep = dsl.selectCount().from(DSL.table(DSL.name(primaryTable)));
        if (rootPlan.getCompanionJoins() != null && !rootPlan.getCompanionJoins().isEmpty()) {
            for (var joinSpec : rootPlan.getCompanionJoins()) {
                String tgtTable = joinSpec.getTargetTable();
                String tgtField = joinSpec.getTargetField();
                String srcTable = joinSpec.getSourceTable();
                String srcField = joinSpec.getSourceField();
                countStep =
                        (SelectJoinStep<?>)
                                countStep
                                        .leftJoin(DSL.table(DSL.name(tgtTable)))
                                        .on(
                                                DSL.field(DSL.name(srcTable, srcField))
                                                        .eq(
                                                                DSL.field(
                                                                        DSL.name(
                                                                                tgtTable,
                                                                                tgtField))))
                                        .and(DSL.field(DSL.name(tgtTable, "deleted")).eq((byte) 0));
            }
        }

        Long total = countStep.where(rootPlan.getCondition()).fetchOne(0, Long.class);
        return total != null ? total : 0L;
    }

    /** 执行整树查询计划 (纯数据引擎，返回分页数据页) */
    public DataPage<Map<String, Object>> execute(QueryPlan plan) {
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

        // 2.1 若根节点配置有伴生表 (1:1 或 N:1)，执行安全 LEFT JOIN
        if (rootPlan.getCompanionJoins() != null && !rootPlan.getCompanionJoins().isEmpty()) {
            for (var joinSpec : rootPlan.getCompanionJoins()) {
                String tgtTable = joinSpec.getTargetTable();
                String tgtField = joinSpec.getTargetField();
                String srcTable = joinSpec.getSourceTable();
                String srcField = joinSpec.getSourceField();
                selectStep =
                        (SelectJoinStep<Record>)
                                selectStep
                                        .leftJoin(DSL.table(DSL.name(tgtTable)))
                                        .on(
                                                DSL.field(DSL.name(srcTable, srcField))
                                                        .eq(
                                                                DSL.field(
                                                                        DSL.name(
                                                                                tgtTable,
                                                                                tgtField))))
                                        .and(DSL.field(DSL.name(tgtTable, "deleted")).eq((byte) 0));
            }
        }

        // 3. 执行主表分页查询
        Result<Record> records =
                selectStep
                        .where(rootPlan.getCondition())
                        .orderBy(rootPlan.getOrderFields())
                        .limit(pageSize)
                        .offset(offset)
                        .fetch();

        List<Long> primaryIds = new ArrayList<>();
        List<Map<String, Object>> rootRows = new ArrayList<>();
        for (Record r : records) {
            Map<String, Object> rootRow = new LinkedHashMap<>();
            // 1. 提取物理主键 id (作为内部临时技术锚点，供 Stage 1 批抓取与 Stage 2 装配)
            Object idVal = null;
            try {
                idVal = r.get(DSL.field(DSL.name(primaryTable, "id")));
                if (idVal == null) {
                    idVal = r.get("id");
                }
            } catch (Exception ignored) {
            }

            // 2. 主表物理表容器
            Map<String, Object> primaryTableSpace = new LinkedHashMap<>();
            if (idVal != null) {
                primaryTableSpace.put("id", idVal);
            }
            rootRow.put(primaryTable, primaryTableSpace);

            if (rootPlan.getProjectedFields() != null && !rootPlan.getProjectedFields().isEmpty()) {
                for (PhysicalFieldSpec spec : rootPlan.getProjectedFields()) {
                    if (spec.getColumnName() != null) {
                        String tbl =
                                spec.getTableName() != null ? spec.getTableName() : primaryTable;
                        try {
                            String alias =
                                    tbl.equalsIgnoreCase(primaryTable)
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
                            @SuppressWarnings("unchecked")
                            Map<String, Object> tableMap =
                                    (Map<String, Object>)
                                            rootRow.computeIfAbsent(
                                                    tbl, k -> new LinkedHashMap<>());
                            tableMap.put(spec.getColumnName(), val);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            rootRows.add(rootRow);
            if (idVal instanceof Number num) {
                primaryIds.add(num.longValue());
            }
        }

        // ==================== Stage 1: 批量拉取子模块数据 (Batch Fetch) ====================
        Map<QueryNodePlan, List<Map<String, Object>>> rawDataByNode = new IdentityHashMap<>();
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

        return DataPage.<Map<String, Object>>builder()
                .moduleId(rootPlan.getModuleId())
                .pageNo(pageNo)
                .pageSize(pageSize)
                .total(total)
                .records(rootRows)
                .build();
    }

    /**
     * 递归分批批量拉取各层子模块/子表数据 (利用 fk IN (:parentIds))
     *
     * <p>【核心架构设计】： 1. 批量下发 (Batch Fetch)：以上级实体的主键集合为条件执行 {@code fk IN (:parentIds)}，
     * 单次批量捞出所有子实体，彻底杜绝 N+1 次 SQL 递归查询； 2. 虚拟模块透传 (Transparent Virtual
     * Pass-Through)：若当前模块为无物理主表的虚拟逻辑节点， 则直接将上层物理主键集合 {@code parentIds}
     * 原样透传给其下级所有物理子模块，实现跨越虚拟层级的直达查询； 3. 引用索引缓存 (Identity Cache)：拉取出的原始数据按 {@link QueryNodePlan}
     * 实例精确缓存在 {@code Map<QueryNodePlan, List<Map<String, Object>>>} 中，供 Stage 2 内存装配器进行组装。
     */
    private void batchFetchChildNodesRecursively(
            DSLContext dsl,
            List<Long> parentIds,
            List<QueryNodePlan> childPlans,
            Map<QueryNodePlan, List<Map<String, Object>>> rawDataByNode) {

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

            // 2. 若子节点有伴生表 (1:1 或 N:1)，执行安全 LEFT JOIN
            if (childPlan.getCompanionJoins() != null && !childPlan.getCompanionJoins().isEmpty()) {
                for (var joinSpec : childPlan.getCompanionJoins()) {
                    String tgtTable = joinSpec.getTargetTable();
                    String tgtField = joinSpec.getTargetField();
                    String srcTable = joinSpec.getSourceTable();
                    String srcField = joinSpec.getSourceField();
                    queryStep =
                            (SelectJoinStep<Record>)
                                    queryStep
                                            .leftJoin(DSL.table(DSL.name(tgtTable)))
                                            .on(
                                                    DSL.field(DSL.name(srcTable, srcField))
                                                            .eq(
                                                                    DSL.field(
                                                                            DSL.name(
                                                                                    tgtTable,
                                                                                    tgtField))))
                                            .and(
                                                    DSL.field(DSL.name(tgtTable, "deleted"))
                                                            .eq((byte) 0));
                }
            }

            // 构建从表查询 (fk IN (:parentIds) AND deleted = 0 AND 局部条件)
            var query =
                    queryStep
                            .where(DSL.field(DSL.name(childTable, fkField)).in(parentIds))
                            .and(DSL.field(DSL.name(childTable, "deleted")).eq((byte) 0));

            if (childPlan.getCondition() != null
                    && !DSL.noCondition().equals(childPlan.getCondition())) {
                query = query.and(childPlan.getCondition());
            }

            Result<Record> childRecords = query.orderBy(childPlan.getOrderFields()).fetch();
            List<Map<String, Object>> childRows = new ArrayList<>();
            List<Long> childPrimaryIds = new ArrayList<>();

            for (Record r : childRecords) {
                Map<String, Object> crow = new LinkedHashMap<>();
                Object pkVal = null;
                try {
                    pkVal = r.get("id");
                } catch (Exception ignored) {
                }

                // 主从表物理表空间
                Map<String, Object> primaryTableSpace = new LinkedHashMap<>();
                if (pkVal != null) {
                    primaryTableSpace.put("id", pkVal);
                }
                crow.put(childTable, primaryTableSpace);

                // 临时注入内部技术外键（供 Stage 2 树装配器按外键分组，装配完成后自动剥离）
                try {
                    Object fkVal = r.get(fkField);
                    if (fkVal != null) {
                        crow.put("_fk_" + fkField, fkVal);
                    }
                } catch (Exception ignored) {
                }

                // 投影字段严格按照 PhysicalFieldSpec 声明注入物理表空间
                if (childPlan.getProjectedFields() != null) {
                    for (PhysicalFieldSpec spec : childPlan.getProjectedFields()) {
                        if (spec.getColumnName() != null) {
                            String tbl =
                                    spec.getTableName() != null ? spec.getTableName() : childTable;
                            String alias =
                                    tbl.equalsIgnoreCase(childTable)
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
                            @SuppressWarnings("unchecked")
                            Map<String, Object> targetTableMap =
                                    (Map<String, Object>)
                                            crow.computeIfAbsent(tbl, k -> new LinkedHashMap<>());
                            targetTableMap.put(spec.getColumnName(), val);
                        }
                    }
                }
                childRows.add(crow);
                if (pkVal instanceof Number pkNum) {
                    childPrimaryIds.add(pkNum.longValue());
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

        if (specs != null && !specs.isEmpty()) {
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
        }
        return fields;
    }

    /** 执行字段候选值去重查询 (保证选项严格属于符合当前分页过滤条件的候选集) */
    public List<String> executeDistinctOptions(
            QueryPlan plan, Long targetFieldId, String direction) {
        if (plan == null || plan.getRootNodePlan() == null) {
            return Collections.emptyList();
        }

        QueryNodePlan rootPlan = plan.getRootNodePlan();
        String rootPrimaryTable = rootPlan.getPrimaryTable();
        if (rootPrimaryTable == null || rootPrimaryTable.isBlank()) {
            return Collections.emptyList();
        }

        // 递归从节点树中寻找目标字段所属的 QueryNodePlan 及物理字段定义
        List<QueryNodePlan> pathToTarget = new ArrayList<>();
        findPathToField(rootPlan, targetFieldId, pathToTarget);

        QueryNodePlan targetNode =
                pathToTarget.isEmpty() ? rootPlan : pathToTarget.get(pathToTarget.size() - 1);
        PhysicalFieldSpec targetSpec = null;
        if (targetNode.getProjectedFields() != null) {
            for (PhysicalFieldSpec spec : targetNode.getProjectedFields()) {
                if (Objects.equals(spec.getFieldId(), targetFieldId)) {
                    targetSpec = spec;
                    break;
                }
            }
        }

        if (targetSpec == null || targetSpec.getColumnName() == null) {
            return Collections.emptyList();
        }

        String targetPrimaryTable = targetNode.getPrimaryTable();
        String targetTable =
                (targetSpec.getTableName() != null && !targetSpec.getTableName().isBlank())
                        ? targetSpec.getTableName()
                        : targetPrimaryTable;
        String column = targetSpec.getColumnName();

        if (targetTable == null || targetTable.isBlank() || column == null || column.isBlank()) {
            return Collections.emptyList();
        }

        DSLContext dsl = jooqDataContextFactory.getContext();
        Field<Object> distinctField = DSL.field(DSL.name(targetTable, column));

        // 1. 如果目标字段属于根节点 (根主表或根伴生表)，查询逻辑与主表分页 COUNT 逻辑完全对齐
        if (pathToTarget.size() <= 1) {
            SelectJoinStep<?> selectStep =
                    dsl.selectDistinct(distinctField).from(DSL.table(DSL.name(rootPrimaryTable)));

            // 根伴生表 LEFT JOIN
            if (rootPlan.getCompanionJoins() != null && !rootPlan.getCompanionJoins().isEmpty()) {
                for (var joinSpec : rootPlan.getCompanionJoins()) {
                    String tgtTable = joinSpec.getTargetTable();
                    String tgtField = joinSpec.getTargetField();
                    String srcTable = joinSpec.getSourceTable();
                    String srcField = joinSpec.getSourceField();
                    selectStep =
                            (SelectJoinStep<?>)
                                    selectStep
                                            .leftJoin(DSL.table(DSL.name(tgtTable)))
                                            .on(
                                                    DSL.field(DSL.name(srcTable, srcField))
                                                            .eq(
                                                                    DSL.field(
                                                                            DSL.name(
                                                                                    tgtTable,
                                                                                    tgtField))))
                                            .and(
                                                    DSL.field(DSL.name(tgtTable, "deleted"))
                                                            .eq((byte) 0));
                }
            }

            Condition cond = rootPlan.getCondition();
            if (cond == null) {
                cond = DSL.noCondition();
            }
            cond = cond.and(distinctField.isNotNull());

            SelectConditionStep<?> conditionStep = selectStep.where(cond);
            Result<?> records;
            if (direction != null && !direction.isBlank()) {
                records =
                        "DESC".equalsIgnoreCase(direction.trim())
                                ? conditionStep.orderBy(distinctField.desc()).fetch()
                                : conditionStep.orderBy(distinctField.asc()).fetch();
            } else {
                records = conditionStep.fetch();
            }
            return extractDistinctOptionValues(records, direction);
        }

        // 2. 如果目标字段属于子节点 (1:N 从表或子模块)，从子表出发 SELECT DISTINCT targetField，
        // 并通过 Semi-Join (EXISTS) 关联符合 rootPlan 全部约束的根实体记录
        SelectJoinStep<?> selectStep =
                dsl.selectDistinct(distinctField).from(DSL.table(DSL.name(targetPrimaryTable)));

        // 目标子节点的伴生表 LEFT JOIN
        if (targetNode.getCompanionJoins() != null && !targetNode.getCompanionJoins().isEmpty()) {
            for (var joinSpec : targetNode.getCompanionJoins()) {
                String tgtTable = joinSpec.getTargetTable();
                String tgtField = joinSpec.getTargetField();
                String srcTable = joinSpec.getSourceTable();
                String srcField = joinSpec.getSourceField();
                selectStep =
                        (SelectJoinStep<?>)
                                selectStep
                                        .leftJoin(DSL.table(DSL.name(tgtTable)))
                                        .on(
                                                DSL.field(DSL.name(srcTable, srcField))
                                                        .eq(
                                                                DSL.field(
                                                                        DSL.name(
                                                                                tgtTable,
                                                                                tgtField))))
                                        .and(DSL.field(DSL.name(tgtTable, "deleted")).eq((byte) 0));
            }
        }

        Condition childCond = targetNode.getCondition();
        if (childCond == null) {
            childCond = DSL.noCondition();
        }
        childCond = childCond.and(distinctField.isNotNull());

        // 向上逐跳回溯构建递归 EXISTS 链，穿透虚拟模块并正确关联到符合 root 过滤条件的主数据
        List<QueryNodePlan> physicalPath = new ArrayList<>();
        for (QueryNodePlan pNode : pathToTarget) {
            if (pNode != null
                    && pNode.getPrimaryTable() != null
                    && !pNode.getPrimaryTable().isBlank()) {
                physicalPath.add(pNode);
            }
        }

        if (physicalPath.size() > 1) {
            SelectJoinStep<?> rootMatchStep =
                    dsl.selectOne().from(DSL.table(DSL.name(rootPrimaryTable)));
            if (rootPlan.getCompanionJoins() != null && !rootPlan.getCompanionJoins().isEmpty()) {
                for (var joinSpec : rootPlan.getCompanionJoins()) {
                    String tgtTable = joinSpec.getTargetTable();
                    String tgtField = joinSpec.getTargetField();
                    String srcTable = joinSpec.getSourceTable();
                    String srcField = joinSpec.getSourceField();
                    rootMatchStep =
                            (SelectJoinStep<?>)
                                    rootMatchStep
                                            .leftJoin(DSL.table(DSL.name(tgtTable)))
                                            .on(
                                                    DSL.field(DSL.name(srcTable, srcField))
                                                            .eq(
                                                                    DSL.field(
                                                                            DSL.name(
                                                                                    tgtTable,
                                                                                    tgtField))))
                                            .and(
                                                    DSL.field(DSL.name(tgtTable, "deleted"))
                                                            .eq((byte) 0));
                }
            }

            Condition rootCond =
                    rootPlan.getCondition() != null ? rootPlan.getCondition() : DSL.noCondition();

            // 递归链构建
            Condition existsChain =
                    buildRecursiveExistsChain(
                            dsl, physicalPath, 0, physicalPath.size() - 1, rootMatchStep, rootCond);
            if (existsChain != null) {
                childCond = childCond.and(existsChain);
            }
        }

        SelectConditionStep<?> childConditionStep = selectStep.where(childCond);
        Result<?> records;
        if (direction != null && !direction.isBlank()) {
            records =
                    "DESC".equalsIgnoreCase(direction.trim())
                            ? childConditionStep.orderBy(distinctField.desc()).fetch()
                            : childConditionStep.orderBy(distinctField.asc()).fetch();
        } else {
            records = childConditionStep.fetch();
        }
        return extractDistinctOptionValues(records, direction);
    }

    /** 兼容无 direction 参数的重载方法 */
    public List<String> executeDistinctOptions(QueryPlan plan, Long targetFieldId) {
        return executeDistinctOptions(plan, targetFieldId, null);
    }

    /** 从 SELECT DISTINCT 结果集中提取候选值，自动解析与展平 JSON 数组并进行内存去重（支持按需可选排序） */
    private List<String> extractDistinctOptionValues(Result<?> records, String direction) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> uniqueValues = new LinkedHashSet<>();
        for (Record r : records) {
            Object rawVal = r.get(0);
            if (rawVal == null) {
                continue;
            }
            Object parsed = parseIfJson(rawVal);
            if (parsed instanceof Iterable<?> iterable) {
                for (Object item : iterable) {
                    if (item != null) {
                        String s = String.valueOf(item).trim();
                        if (!s.isEmpty()) {
                            uniqueValues.add(s);
                        }
                    }
                }
            } else {
                String s = String.valueOf(parsed).trim();
                if (!s.isEmpty()) {
                    uniqueValues.add(s);
                }
            }
        }

        List<String> result = new ArrayList<>(uniqueValues);
        if (direction != null && !direction.isBlank()) {
            if ("DESC".equalsIgnoreCase(direction.trim())) {
                result.sort(Collections.reverseOrder());
            } else {
                Collections.sort(result);
            }
        }
        return result;
    }

    /**
     * 递归构建从根物理节点逐跳向下到目标物理节点的 EXISTS 关联链
     *
     * <p>例如路径为 [101(student), 105(student_course), 108(score_item)]: 生成: EXISTS (SELECT 1 FROM
     * student LEFT JOIN ... WHERE rootCond AND EXISTS (SELECT 1 FROM student_course WHERE
     * student.id = student_course.student_id AND student_course.id = score_item.course_id))
     */
    private Condition buildRecursiveExistsChain(
            DSLContext dsl,
            List<QueryNodePlan> physicalPath,
            int currentIndex,
            int targetIndex,
            SelectJoinStep<?> rootMatchStep,
            Condition rootCond) {
        if (currentIndex < 0 || currentIndex >= targetIndex) {
            return null;
        }

        QueryNodePlan currentPhysicalNode = physicalPath.get(currentIndex);
        QueryNodePlan nextPhysicalNode = physicalPath.get(currentIndex + 1);

        String currentTable = currentPhysicalNode.getPrimaryTable();
        String nextTable = nextPhysicalNode.getPrimaryTable();

        String parentKey =
                nextPhysicalNode.getParentKey() != null ? nextPhysicalNode.getParentKey() : "id";
        String childFk = nextPhysicalNode.getParentForeignKey();

        if (childFk == null || childFk.isBlank() || currentTable == null || nextTable == null) {
            return null;
        }

        // 当前层与下一层的父子关联: currentTable.parentKey = nextTable.childFk
        Condition linkToNextCondition =
                DSL.field(DSL.name(currentTable, parentKey))
                        .eq(DSL.field(DSL.name(nextTable, childFk)));

        if (currentIndex == 0) {
            // 顶层根节点
            Condition fullRootCondition = rootCond != null ? rootCond : DSL.noCondition();

            if (currentIndex + 1 == targetIndex) {
                // 只有一层 (直接子模块): EXISTS (SELECT 1 FROM root WHERE root.id = target.fk AND
                // rootCond)
                fullRootCondition = fullRootCondition.and(linkToNextCondition);
            } else {
                // 深度 >= 2: 将 linkToNextCondition 和后续更深层的 EXISTS 嵌套一并构建在下一层
                Condition nextChain =
                        buildRecursiveExistsChain(
                                dsl,
                                physicalPath,
                                currentIndex + 1,
                                targetIndex,
                                null,
                                linkToNextCondition);
                if (nextChain != null) {
                    fullRootCondition = fullRootCondition.and(nextChain);
                }
            }

            return DSL.exists(rootMatchStep.where(fullRootCondition));
        } else {
            // 中间节点
            SelectJoinStep<?> intermediateStep =
                    dsl.selectOne().from(DSL.table(DSL.name(currentTable)));
            if (currentPhysicalNode.getCompanionJoins() != null
                    && !currentPhysicalNode.getCompanionJoins().isEmpty()) {
                for (var joinSpec : currentPhysicalNode.getCompanionJoins()) {
                    String tgtTable = joinSpec.getTargetTable();
                    String tgtField = joinSpec.getTargetField();
                    String srcTable = joinSpec.getSourceTable();
                    String srcField = joinSpec.getSourceField();
                    intermediateStep =
                            (SelectJoinStep<?>)
                                    intermediateStep
                                            .leftJoin(DSL.table(DSL.name(tgtTable)))
                                            .on(
                                                    DSL.field(DSL.name(srcTable, srcField))
                                                            .eq(
                                                                    DSL.field(
                                                                            DSL.name(
                                                                                    tgtTable,
                                                                                    tgtField))))
                                            .and(
                                                    DSL.field(DSL.name(tgtTable, "deleted"))
                                                            .eq((byte) 0));
                }
            }

            // 当前节点的自身条件 (含 deleted=0) + 上级传下来的父子关联 (rootCond: 即父节点.id = 当前中间节点.fk)
            Condition currentCond =
                    currentPhysicalNode.getCondition() != null
                            ? currentPhysicalNode.getCondition()
                            : DSL.noCondition();
            if (rootCond != null && !DSL.noCondition().equals(rootCond)) {
                currentCond = currentCond.and(rootCond);
            }

            if (currentIndex + 1 == targetIndex) {
                // 到达最后一跳: 当前中间表直接关联目标表 (例如 student_course.id = score_item.course_id)
                currentCond = currentCond.and(linkToNextCondition);
            } else {
                // 还有更深层级: 递归向下构建，并将当前与下一跳的关联条件传入作为其 rootCond
                Condition nextInnerExists =
                        buildRecursiveExistsChain(
                                dsl,
                                physicalPath,
                                currentIndex + 1,
                                targetIndex,
                                null,
                                linkToNextCondition);
                if (nextInnerExists != null) {
                    currentCond = currentCond.and(nextInnerExists);
                }
            }

            return DSL.exists(intermediateStep.where(currentCond));
        }
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

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
                return objectMapper.readValue(jsonStr, Object.class);
            } catch (Exception e) {
                return jsonStr;
            }
        }
        if (val instanceof String str) {
            String trimmed = str.trim();
            if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
                    || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                try {
                    return objectMapper.readValue(trimmed, Object.class);
                } catch (Exception ignored) {
                    return val;
                }
            }
        }
        return val;
    }

    private boolean findPathToField(
            QueryNodePlan node, Long targetFieldId, List<QueryNodePlan> path) {
        if (node == null) {
            return false;
        }
        path.add(node);
        if (node.getProjectedFields() != null) {
            for (PhysicalFieldSpec spec : node.getProjectedFields()) {
                if (Objects.equals(spec.getFieldId(), targetFieldId)) {
                    return true;
                }
            }
        }
        if (node.getChildren() != null) {
            for (QueryNodePlan child : node.getChildren()) {
                if (findPathToField(child, targetFieldId, path)) {
                    return true;
                }
            }
        }
        path.remove(path.size() - 1);
        return false;
    }
}
