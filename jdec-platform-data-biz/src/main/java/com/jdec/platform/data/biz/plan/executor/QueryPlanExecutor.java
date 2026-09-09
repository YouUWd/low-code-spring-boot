package com.jdec.platform.data.biz.plan.executor;

import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.biz.dsl.JooqContextFactory;
import com.jdec.platform.data.biz.plan.assembler.TreeResultAssembler;
import com.jdec.platform.data.biz.plan.model.PhysicalFieldSpec;
import com.jdec.platform.data.biz.plan.model.QueryNodePlan;
import com.jdec.platform.data.biz.plan.model.QueryPlan;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Result;
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

    private final JooqContextFactory jooqContextFactory;
    private final TreeResultAssembler treeResultAssembler;

    /** 执行整树查询计划 (纯数据引擎，返回分页数据页) */
    public DataPage<Map<String, Object>> execute(QueryPlan plan) {
        if (plan == null || plan.getRootNodePlan() == null) {
            return DataPage.empty(1, 20);
        }

        QueryNodePlan rootPlan = plan.getRootNodePlan();
        String primaryTable = rootPlan.getPrimaryTable();
        DSLContext dsl = jooqContextFactory.getContext();

        // 0. 防御空主表 (如未配置的虚拟模块)
        if (primaryTable == null || primaryTable.isBlank()) {
            return DataPage.empty(rootPlan.getPageNo(), rootPlan.getPageSize());
        }

        // ==================== Stage 0: 根节点扫描与分页 ====================
        // 1. 构建主表精确 COUNT 查询 (若有伴生表同样需 LEFT JOIN 保证伴生表条件解析正常)
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

        long total = countStep.where(rootPlan.getCondition()).fetchOne(0, Long.class);

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
            Map<String, Object> row = new LinkedHashMap<>();
            // 1. 提取物理主键 id (作为内部临时技术锚点，供 Stage 1 批抓取与 Stage 2 装配)
            Object idVal = null;
            try {
                idVal = r.get(DSL.field(DSL.name(primaryTable, "id")));
                if (idVal == null) {
                    idVal = r.get("id");
                }
            } catch (Exception ignored) {
            }
            if (idVal != null) {
                row.put("_row_id", idVal);
            }

            // 2. 根模块命名空间包装: 主表实体始终保留主键id作为唯一身份标识
            Map<String, Object> rootModSpace = new LinkedHashMap<>();
            Map<String, Object> primaryTableSpace = new LinkedHashMap<>();
            if (idVal != null) {
                primaryTableSpace.put("id", idVal);
            }
            rootModSpace.put(primaryTable, primaryTableSpace);

            if (rootPlan.getProjectedFields() != null && !rootPlan.getProjectedFields().isEmpty()) {
                for (PhysicalFieldSpec spec : rootPlan.getProjectedFields()) {
                    if (spec.getColumnName() != null) {
                        String tbl =
                                spec.getTableName() != null ? spec.getTableName() : primaryTable;
                        @SuppressWarnings("unchecked")
                        Map<String, Object> tableMap =
                                (Map<String, Object>)
                                        rootModSpace.computeIfAbsent(
                                                tbl, k -> new LinkedHashMap<>());
                        try {
                            String alias =
                                    tbl.equalsIgnoreCase(primaryTable)
                                            ? spec.getColumnName()
                                            : tbl + "_" + spec.getColumnName();
                            Object val = null;
                            try {
                                val = r.get(alias);
                            } catch (Exception e) {
                                val = r.get(spec.getColumnName());
                            }
                            tableMap.put(spec.getColumnName(), val);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            row.put(String.valueOf(rootPlan.getModuleId()), rootModSpace);
            rootRows.add(row);
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

        // 彻底移除顶层临时暴露的内部技术键 _row_id
        for (Map<String, Object> row : rootRows) {
            row.remove("_row_id");
        }

        return DataPage.<Map<String, Object>>builder()
                .pageNo(pageNo)
                .pageSize(pageSize)
                .total(total)
                .records(rootRows)
                .build();
    }

    /** 递归分批批量拉取各层子模块/子表数据 (利用 fk IN (...)) */
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

            if (childTable == null
                    || childTable.isBlank()
                    || fkField == null
                    || fkField.isBlank()) {
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
                if (pkVal != null) {
                    crow.put("id", pkVal);
                    crow.put("_row_id", pkVal);
                }

                // 临时注入内部技术外键（供 Stage 2 树装配器按外键分组）
                try {
                    Object fkVal = r.get(fkField);
                    if (fkVal != null) {
                        crow.put("_fk_" + fkField, fkVal);
                    }
                } catch (Exception ignored) {
                }

                // 投影字段严格按照 PhysicalFieldSpec 声明注入，支持多表结构分发
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
                            // 如果是伴生表字段，既放在 crow[col] 中（若无冲突），也按照表名分拆放入 crow[table][col]
                            crow.put(spec.getColumnName(), val);
                            if (!tbl.equalsIgnoreCase(childTable)) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> compTableMap =
                                        (Map<String, Object>)
                                                crow.computeIfAbsent(
                                                        tbl, k -> new LinkedHashMap<>());
                                compTableMap.put(spec.getColumnName(), val);
                            }
                        }
                    }
                }
                childRows.add(crow);
                if (pkVal instanceof Number pkNum) {
                    childPrimaryIds.add(pkNum.longValue());
                }
            }

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
}
