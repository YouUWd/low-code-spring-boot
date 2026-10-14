package com.jdec.platform.data.biz.plan.compiler;

import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.common.ProjectTableRelationsDTO;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import com.jdec.platform.data.api.dto.request.PhysicalFilterItem;
import com.jdec.platform.data.api.dto.request.PhysicalSortItem;
import com.jdec.platform.data.api.dto.request.TableDetailQueryReq;
import com.jdec.platform.data.api.dto.request.TableOptionReq;
import com.jdec.platform.data.api.dto.request.TablePageQueryReq;
import com.jdec.platform.data.api.dto.request.TableQueryReq;
import com.jdec.platform.data.api.dto.request.TableSpec;
import com.jdec.platform.data.biz.plan.PlanMeta;
import com.jdec.platform.data.biz.plan.model.AttachedTableSpec;
import com.jdec.platform.data.biz.plan.model.PhysicalFieldSpec;
import com.jdec.platform.data.biz.plan.model.QueryNodePlan;
import com.jdec.platform.data.biz.plan.model.QueryPlan;
import com.jdec.platform.shared.context.AppContext;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * 结构化查询计划编译器 (QueryPlanCompiler)
 *
 * <p>职责：
 *
 * <ul>
 *   <li>1. 递归校验并加载模块树各节点的元数据配置；
 *   <li>2. 严格以物理表名/物理列名为基准，解析投影规格与过滤/排序条件；
 *   <li>3. 构建强类型安全的 SQL 过滤 Condition 与 OrderField 序列；
 *   <li>4. 自动推导子模块外键连接关系（parentForeignKey），产出确定性的 QueryPlan。
 * </ul>
 *
 * <p>本类不依赖 fieldId，也**不自行读取元数据**：模块树与项目表关系由调用方以 {@link PlanMeta} 快照传入，保证一次操作内所有节点都由同一份快照编译出来。
 *
 * <p>白名单校验、过滤条件编译与表间关系解析分别下沉到 {@link TableQueryValidator}、 {@link FilterConditionCompiler}、{@link
 * ModuleRelationResolver}，本类只负责把三者装配成节点计划树。
 */
@Slf4j
@Component
public class QueryPlanCompiler {

    /** 编译物理表候选项查询请求为单列投影与物理过滤上下文的 QueryPlan */
    public QueryPlan compile(TableOptionReq req, PlanMeta meta) {
        if (req == null) {
            throw new IllegalArgumentException("候选项请求不能为空");
        }
        if (req.getModuleId() == null
                || req.getColumnName() == null
                || req.getColumnName().isBlank()) {
            throw new IllegalArgumentException("候选项请求的模块 ID 与列名均不能为空");
        }

        ModuleTreeDTO tree = meta != null ? meta.moduleTree() : null;
        String table = req.getTableName();
        if (table == null || table.isBlank()) {
            if (tree != null && tree.getRootModule() != null) {
                table = ModuleRelationResolver.resolvePrimaryTable(tree.getRootModule());
            }
        }

        List<TableSpec> tables = new ArrayList<>();
        if (table != null && !table.isBlank()) {
            Long targetModuleId = req.getModuleId();
            if (tree != null && tree.getModuleMap() != null) {
                for (ModuleInfo m : tree.getModuleMap().values()) {
                    if (m != null
                            && m.getPrimaryTable() != null
                            && m.getPrimaryTable().equalsIgnoreCase(table.trim())) {
                        targetModuleId = m.getModuleId();
                        break;
                    }
                }
            }
            tables.add(TableSpec.of(targetModuleId, table, req.getColumnName().trim()));
        }

        TableQueryReq queryReq =
                TableQueryReq.builder()
                        .moduleId(req.getModuleId())
                        .tables(tables)
                        .filters(req.getFilters())
                        .build();
        return compile(queryReq, meta);
    }

    /** 编译物理表单据详情精准查询请求为 QueryPlan */
    public QueryPlan compileDetail(TableDetailQueryReq req, PlanMeta meta) {
        if (req == null) {
            throw new IllegalArgumentException("物理表详情查询请求不能为空");
        }
        if (req.getModuleId() == null || req.getId() == null) {
            throw new IllegalArgumentException("模块 ID 和单据 ID 不能为空");
        }

        ModuleTreeDTO tree = meta != null ? meta.moduleTree() : null;
        if (tree == null || tree.getRootModule() == null) {
            throw new IllegalArgumentException("模块 ID [" + req.getModuleId() + "] 不存在或未配置元数据");
        }

        // id 过滤条件与后续投影/校验必须取自同一份元数据快照，故直接复用入参 meta
        String primaryTable = ModuleRelationResolver.resolvePrimaryTable(tree.getRootModule());
        List<PhysicalFilterItem> mergedFilters = new ArrayList<>();
        mergedFilters.add(PhysicalFilterItem.eq(primaryTable, "id", req.getId()));
        if (req.getFilters() != null && !req.getFilters().isEmpty()) {
            mergedFilters.addAll(req.getFilters());
        }

        TableQueryReq queryReq =
                TableDetailQueryReq.builder()
                        .moduleId(req.getModuleId())
                        .id(req.getId())
                        .tables(req.getTables())
                        .filters(mergedFilters)
                        .sorts(req.getSorts())
                        .build();

        return compile(queryReq, meta);
    }

    /** 编译基于物理表与字段的查询请求为 QueryPlan */
    public QueryPlan compile(TableQueryReq req, PlanMeta meta) {
        if (req == null) {
            throw new IllegalArgumentException("物理表查询请求不能为空");
        }
        Long rootModuleId = req.getModuleId();
        if (rootModuleId == null) {
            throw new IllegalArgumentException("查询主模块 ID 不能为空");
        }

        ModuleTreeDTO tree = meta != null ? meta.moduleTree() : null;
        if (tree == null || tree.getRootModule() == null) {
            throw new IllegalArgumentException("模块 ID [" + rootModuleId + "] 不存在或未配置元数据");
        }
        TableQueryValidator.validate(req, tree);

        ProjectTableRelationsDTO projectRelations = meta != null ? meta.projectRelations() : null;
        QueryNodePlan rootPlan =
                compileTableNode(rootModuleId, null, null, req, projectRelations, tree);
        return QueryPlan.builder().rootNodePlan(rootPlan).build();
    }

    public record ParentRelationInfo(
            String parentKey,
            String childForeignKey,
            String parentPrimaryTable,
            String relationType) {}

    /**
     * 从单向定义的 tableRelations 中严格推导外键关联字段名与父表主键关联名
     *
     * <p>第一性原理：TableRelations 仅单向定义 (main_table 为父表，join_table 为从表，join_field 为从表外键物理列)。
     * 若直接父模块为虚拟模块 (无主表)，则自动递归向上查找最近的有物理主表的祖先模块进行关联。
     */
    private ParentRelationInfo resolveParentRelation(
            String childPrimaryTable,
            ModuleInfo childMeta,
            ModuleInfo parentMeta,
            ProjectTableRelationsDTO projectRelations,
            ModuleTreeDTO tree) {
        if (childPrimaryTable == null || childPrimaryTable.isBlank() || parentMeta == null) {
            return new ParentRelationInfo("id", null, null, "1:N");
        }

        // 1. 定位有效的物理父模块 (若直接父模块为无主表的虚拟模块，则向上递归查找最近的物理祖先模块)
        ModuleInfo physicalParentMeta = parentMeta;
        String parentPrimary = ModuleRelationResolver.resolvePrimaryTable(physicalParentMeta);
        if (parentPrimary.isBlank()) {
            physicalParentMeta =
                    ModuleRelationResolver.findNearestPhysicalParentMeta(
                            parentMeta, new ArrayList<>(), tree);
            if (physicalParentMeta != null) {
                parentPrimary = ModuleRelationResolver.resolvePrimaryTable(physicalParentMeta);
            }
        }

        // 向上回溯到顶层仍未找到任何配置物理主表的父模块 (如根模块即为纯虚拟模块)
        if (physicalParentMeta == null || parentPrimary.isBlank()) {
            return new ParentRelationInfo("id", null, null, "1:N");
        }

        // 2. 匹配单向关联 (含父子同表的 1:1 垂直拆分特例)
        TableRelationDTO matchedRel =
                ModuleRelationResolver.resolveDirectRelation(
                        parentPrimary, childPrimaryTable, projectRelations);

        if (matchedRel != null
                && matchedRel.getJoinField() != null
                && !matchedRel.getJoinField().isBlank()) {
            String pKey = "id";
            if (matchedRel.getMainField() != null && !matchedRel.getMainField().isBlank()) {
                pKey = matchedRel.getMainField().trim();
            }
            return new ParentRelationInfo(
                    pKey,
                    matchedRel.getJoinField().trim(),
                    parentPrimary,
                    ModuleRelationResolver.relationType(matchedRel, "1:N"));
        }

        // 3. 严格自洽校验：杜绝任何未配置关系的隐式推测
        Long parentModId = physicalParentMeta.getModuleId();
        Long childModId = childMeta != null ? childMeta.getModuleId() : null;
        throw new IllegalStateException(
                String.format(
                        "元数据关联关系未自洽：未在 sys_table_relation 中找到物理父表 [%s](模块 %s) 到子表 [%s](模块 %s) 的单向关联定义(main_table -> join_table)",
                        parentPrimary, parentModId, childPrimaryTable, childModId));
    }

    /** 递归穿透虚拟模块，收集所有挂载在其下的物理子计划 */
    private List<QueryNodePlan> collectPhysicalDescendantPlans(QueryNodePlan plan) {
        List<QueryNodePlan> result = new ArrayList<>();
        if (plan == null) {
            return result;
        }
        if (plan.getPrimaryTable() != null && !plan.getPrimaryTable().isBlank()) {
            result.add(plan);
        } else if (plan.getChildren() != null) {
            for (QueryNodePlan child : plan.getChildren()) {
                result.addAll(collectPhysicalDescendantPlans(child));
            }
        }
        return result;
    }

    private PhysicalFieldSpec toPhysicalFieldSpec(ModuleFieldDTO f, String defaultTable) {
        String tbl =
                (f.getTableName() != null && !f.getTableName().isBlank())
                        ? f.getTableName()
                        : defaultTable;
        return PhysicalFieldSpec.builder()
                .tableName(tbl)
                .columnName(f.getColumnName())
                .displayName(f.getDisplayName())
                .sortOrder(f.getSortOrder())
                .build();
    }

    /** 编译软删除与租户隔离等基础底座条件 */
    private Condition compileBaseConditions(ModuleInfo meta, String primaryTable) {
        List<Condition> conditions = new ArrayList<>();
        conditions.add(DSL.field(DSL.name(primaryTable, "deleted")).eq((byte) 0));

        Long subjectId = AppContext.getSubjectId();
        boolean hasSubjectId =
                meta.getFields() != null
                        && meta.getFields().stream()
                                .anyMatch(
                                        f ->
                                                primaryTable.equalsIgnoreCase(f.getTableName())
                                                        && "subject_id"
                                                                .equalsIgnoreCase(
                                                                        f.getColumnName()));
        if (hasSubjectId && subjectId != null && subjectId > 0) {
            conditions.add(DSL.field(DSL.name(primaryTable, "subject_id")).eq(subjectId));
        }
        return DSL.and(conditions);
    }

    // ==================== 物理表/字段直接编译内核 (TableSpec 驱动) ====================

    private QueryNodePlan compileTableNode(
            Long currentModuleId,
            Long parentModuleId,
            ModuleInfo parentMeta,
            TableQueryReq req,
            ProjectTableRelationsDTO projectRelations,
            ModuleTreeDTO tree) {
        ModuleInfo meta = tree.getModule(currentModuleId);
        if (meta == null) {
            throw new IllegalArgumentException("模块 ID [" + currentModuleId + "] 不存在或未在当前模块树中配置元数据");
        }

        String primaryTable = ModuleRelationResolver.resolvePrimaryTable(meta);
        ParentRelationInfo parentRel =
                resolveParentRelation(primaryTable, meta, parentMeta, projectRelations, tree);
        String parentFk = parentRel.childForeignKey();
        String parentKey = parentRel.parentKey();
        boolean isVirtual = primaryTable == null || primaryTable.isBlank();

        // 1. 查找当前模块及伴生表的 TableSpec 投影规格
        List<TableSpec> tableSpecs = req.getTables();
        boolean isFullTreeMode = tableSpecs == null || tableSpecs.isEmpty();

        // 2. 编译投影字段 (PhysicalFieldSpec)
        // 本模块字段字典声明过的物理表集合：JOIN 只对「本模块确实要展示的表」生效，避免拉进无用表。
        Set<String> declaredTables = new HashSet<>();
        if (meta.getFields() != null) {
            for (ModuleFieldDTO f : meta.getFields()) {
                if (f.getTableName() != null && !f.getTableName().isBlank()) {
                    declaredTables.add(f.getTableName().trim().toLowerCase());
                }
            }
        }
        List<PhysicalFieldSpec> primaryProjectedFields = new ArrayList<>();
        if (!isVirtual) {
            // 主表投影
            TableSpec mainSpec = findTableSpec(currentModuleId, primaryTable, tableSpecs);
            if (isFullTreeMode || mainSpec != null) {
                List<String> requestedCols = mainSpec != null ? mainSpec.getColumns() : null;
                primaryProjectedFields.addAll(
                        compilePhysicalTableProjection(meta, primaryTable, requestedCols));
            }

            // 全字段模式 + 存在附加表 ⇒ 必须显式列出「主表 + 附加表」的声明字段，不能走 SELECT *。
            // 原因：SELECT * 下记录里附加表列名不带表前缀，执行器无投影规格时的回退分支会把它们
            // 全部塞进【主表】空间（如 course.prerequisite），破坏「行键 = 物理表名」的契约。
            if (isFullTreeMode) {
                List<String> attachedTables =
                        attachedTablesOf(primaryTable, projectRelations, tree, declaredTables);
                if (!attachedTables.isEmpty()) {
                    primaryProjectedFields.addAll(
                            compilePhysicalTableProjection(
                                    meta, primaryTable, declaredColumnsOf(meta, primaryTable)));
                    for (String attached : attachedTables) {
                        primaryProjectedFields.addAll(
                                compilePhysicalTableProjection(
                                        meta, attached, declaredColumnsOf(meta, attached)));
                    }
                }
            }

            // 伴生表投影（垂直拆分：relation_type='1:1' 且 join_table 未被模块树认领）
            if (projectRelations != null && projectRelations.getForwardRelations() != null) {
                List<TableRelationDTO> fwdRels =
                        projectRelations.getForwardRelations().get(primaryTable.toLowerCase());
                if (fwdRels != null) {
                    for (TableRelationDTO rel : fwdRels) {
                        if (ModuleRelationResolver.isCompanionRelation(rel, tree)) {
                            String compTable = rel.getJoinTable();
                            if (compTable != null && !compTable.isBlank()) {
                                TableSpec compSpec =
                                        findTableSpec(
                                                currentModuleId, compTable.trim(), tableSpecs);
                                if ((isFullTreeMode || compSpec != null)
                                        && declaredTables.contains(
                                                compTable.trim().toLowerCase())) {
                                    List<String> compCols =
                                            compSpec != null ? compSpec.getColumns() : null;
                                    primaryProjectedFields.addAll(
                                            compilePhysicalTableProjection(
                                                    meta, compTable.trim(), compCols));
                                }
                            }
                        }
                    }
                }
            }

            // 上游表投影（多对一：本表是 join_table，main_table 未被模块树认领）
            if (projectRelations != null && projectRelations.getReverseRelations() != null) {
                List<TableRelationDTO> revRels =
                        projectRelations.getReverseRelations().get(primaryTable.toLowerCase());
                if (revRels != null) {
                    for (TableRelationDTO rel : revRels) {
                        if (ModuleRelationResolver.isUpstreamRelation(rel, primaryTable, tree)) {
                            String upTable = rel.getMainTable().trim();
                            TableSpec upSpec = findTableSpec(currentModuleId, upTable, tableSpecs);
                            if ((isFullTreeMode || upSpec != null)
                                    && declaredTables.contains(upTable.toLowerCase())) {
                                List<String> upCols = upSpec != null ? upSpec.getColumns() : null;
                                primaryProjectedFields.addAll(
                                        compilePhysicalTableProjection(meta, upTable, upCols));
                            }
                        }
                    }
                }
            }
        }

        // 3. 过滤条件编译 (将归属于当前模块/主表/伴生表的 PhysicalFilterItem 编译为 Condition)
        Condition userFilterCondition =
                isVirtual
                        ? null
                        : FilterConditionCompiler.compileTableFilters(
                                currentModuleId, primaryTable, req.getFilters(), projectRelations);

        // 4. 排序编译
        List<OrderField<?>> orderFields =
                isVirtual
                        ? Collections.emptyList()
                        : compileTableSorts(currentModuleId, primaryTable, req.getSorts());

        // 5. 递归编译子节点
        List<QueryNodePlan> childPlans = new ArrayList<>();
        List<Long> childModuleIds = tree.getChildModuleIds(currentModuleId);
        if (childModuleIds != null && !childModuleIds.isEmpty()) {
            for (Long childMid : childModuleIds) {
                ModuleInfo childMeta = tree.getModule(childMid);
                if (childMeta != null) {
                    String childTable = ModuleRelationResolver.resolvePrimaryTable(childMeta);
                    boolean shouldIncludeChild = isFullTreeMode;
                    if (!isFullTreeMode) {
                        shouldIncludeChild =
                                isTableOrDescendantInSpecs(childMid, tableSpecs, tree)
                                        || isModuleOrDescendantInFilters(
                                                childMid, req.getFilters(), tree);
                    }
                    if (shouldIncludeChild) {
                        QueryNodePlan cp =
                                compileTableNode(
                                        childMid,
                                        currentModuleId,
                                        meta,
                                        req,
                                        projectRelations,
                                        tree);
                        childPlans.add(cp);
                    }
                }
            }
        }

        // 6. 附加 LEFT JOIN 规格：伴生表（垂直拆分 1:1，沿关系向下）+ 上游表（多对一 N:1，沿关系向上）
        List<AttachedTableSpec> attachedJoins = new ArrayList<>();
        if (!isVirtual) {
            attachedJoins.addAll(
                    resolveCompanionJoinsForTable(
                            primaryTable,
                            primaryProjectedFields,
                            projectRelations,
                            tree,
                            declaredTables));
            attachedJoins.addAll(
                    resolveUpstreamJoinsForTable(
                            primaryTable,
                            primaryProjectedFields,
                            projectRelations,
                            tree,
                            declaredTables));
        }

        // 7. 自底向上子孙节点过滤条件上卷 (Semi-Join / EXISTS)
        List<Condition> rollupConditions = new ArrayList<>();
        if (!isVirtual) {
            List<QueryNodePlan> physicalChildren = new ArrayList<>();
            for (QueryNodePlan cp : childPlans) {
                physicalChildren.addAll(collectPhysicalDescendantPlans(cp));
            }

            for (QueryNodePlan childPlan : physicalChildren) {
                if (childPlan.getEffectiveFilterCondition() != null) {
                    String childTable = childPlan.getPrimaryTable();
                    if (childTable != null && childTable.equalsIgnoreCase(primaryTable)) {
                        rollupConditions.add(childPlan.getEffectiveFilterCondition());
                    } else if (childTable != null && childPlan.getParentForeignKey() != null) {
                        String pK =
                                childPlan.getParentKey() != null ? childPlan.getParentKey() : "id";
                        String cFk = childPlan.getParentForeignKey();
                        Condition existsCondition =
                                DSL.exists(
                                        DSL.selectOne()
                                                .from(DSL.table(DSL.name(childTable)))
                                                .where(
                                                        DSL.field(DSL.name(childTable, cFk))
                                                                .eq(
                                                                        DSL.field(
                                                                                DSL.name(
                                                                                        primaryTable,
                                                                                        pK)))
                                                                .and(
                                                                        DSL.field(
                                                                                        DSL.name(
                                                                                                childTable,
                                                                                                "deleted"))
                                                                                .eq((byte) 0))
                                                                .and(
                                                                        childPlan
                                                                                .getEffectiveFilterCondition())));
                        rollupConditions.add(existsCondition);
                    }
                }
            }
        }

        Condition effectiveFilterCondition = null;
        if (userFilterCondition != null && !rollupConditions.isEmpty()) {
            effectiveFilterCondition = userFilterCondition.and(DSL.and(rollupConditions));
        } else if (userFilterCondition != null) {
            effectiveFilterCondition = userFilterCondition;
        } else if (!rollupConditions.isEmpty()) {
            effectiveFilterCondition = DSL.and(rollupConditions);
        }

        Condition baseCondition = isVirtual ? null : compileBaseConditions(meta, primaryTable);
        Condition finalCombinedCondition;
        if (baseCondition != null && effectiveFilterCondition != null) {
            finalCombinedCondition = baseCondition.and(effectiveFilterCondition);
        } else if (baseCondition != null) {
            finalCombinedCondition = baseCondition;
        } else {
            finalCombinedCondition = effectiveFilterCondition;
        }

        Integer pNo =
                (currentModuleId.equals(req.getModuleId()) && req instanceof TablePageQueryReq p)
                        ? p.getPageNo()
                        : 1;
        Integer pSize =
                (currentModuleId.equals(req.getModuleId()) && req instanceof TablePageQueryReq p)
                        ? p.getPageSize()
                        : (req instanceof TableDetailQueryReq ? 1 : 20);

        // 8. 判定当前节点是否需要拉取实体数据 (fetchData)
        // 规则：全树模式下恒拉取；指定 tableSpecs 模式下，仅当本节点显式在 tableSpecs 中声明时才拉取实体数据。
        // 若未声明，则仅用于上卷过滤 (EXISTS) 或下拉选项跨表路径推导，跳过 Stage 1 的 Batch Fetch 二次回查。
        boolean shouldFetchData =
                isFullTreeMode || isTableOrDescendantInSpecs(currentModuleId, tableSpecs, tree);

        return QueryNodePlan.builder()
                .moduleId(currentModuleId)
                .moduleMeta(meta)
                .primaryTable(primaryTable)
                .parentForeignKey(parentFk)
                .parentKey(parentKey)
                .parentPrimaryTable(parentRel.parentPrimaryTable())
                .parentRelationType(parentRel.relationType())
                .effectiveFilterCondition(effectiveFilterCondition)
                .pageNo(pNo)
                .pageSize(pSize)
                .projectedFields(primaryProjectedFields)
                .condition(finalCombinedCondition)
                .orderFields(orderFields)
                .attachedJoins(attachedJoins)
                .fetchData(shouldFetchData)
                .children(childPlans)
                .build();
    }

    private List<PhysicalFieldSpec> compilePhysicalTableProjection(
            ModuleInfo meta, String table, List<String> requestedColumns) {
        List<PhysicalFieldSpec> specs = new ArrayList<>();
        boolean isAllCols =
                requestedColumns == null
                        || requestedColumns.isEmpty()
                        || requestedColumns.contains("*");

        Map<String, ModuleFieldDTO> colMap = new LinkedHashMap<>();
        if (meta != null && meta.getFields() != null) {
            for (ModuleFieldDTO f : meta.getFields()) {
                String tbl =
                        (f.getTableName() != null && !f.getTableName().isBlank())
                                ? f.getTableName()
                                : ModuleRelationResolver.resolvePrimaryTable(meta);
                if (table.equalsIgnoreCase(tbl) && f.getColumnName() != null) {
                    colMap.put(f.getColumnName().toLowerCase(), f);
                }
            }
        }

        if (isAllCols) {
            // 全列查询模式 (未指定列或指定了 *)：返回空列表，由底层执行器直接生成并下发 SELECT * FROM table
            return Collections.emptyList();
        }

        for (String col : requestedColumns) {
            if (col != null && !col.isBlank() && !"*".equals(col.trim())) {
                String cleanCol =
                        col.contains(".")
                                ? col.substring(col.lastIndexOf('.') + 1).trim()
                                : col.trim();
                ModuleFieldDTO f = colMap.get(cleanCol.toLowerCase());
                if (f != null) {
                    specs.add(toPhysicalFieldSpec(f, table));
                } else {
                    specs.add(
                            PhysicalFieldSpec.builder()
                                    .tableName(table)
                                    .columnName(cleanCol)
                                    .build());
                }
            }
        }
        return specs;
    }

    private List<AttachedTableSpec> resolveCompanionJoinsForTable(
            String primaryTable,
            List<PhysicalFieldSpec> projectedFields,
            ProjectTableRelationsDTO projectRelations,
            ModuleTreeDTO tree,
            Set<String> declaredTables) {
        if (primaryTable == null
                || primaryTable.isBlank()
                || projectRelations == null
                || projectRelations.getForwardRelations() == null) {
            return Collections.emptyList();
        }

        List<TableRelationDTO> relations =
                projectRelations.getForwardRelations().get(primaryTable.toLowerCase());
        if (relations == null || relations.isEmpty()) {
            return Collections.emptyList();
        }

        // 投影规格为空 = 全字段模式（未指定表或指定了 *）：需要 JOIN 全部伴生表；
        // 显式投影时只 JOIN 真正被投影到列的伴生表，避免「只查主表列却 JOIN 全部伴生表」的无谓开销。
        boolean includeAllCompanions = projectedFields == null || projectedFields.isEmpty();
        Set<String> neededCompanionTables = new HashSet<>();
        if (!includeAllCompanions) {
            for (PhysicalFieldSpec spec : projectedFields) {
                if (spec.getTableName() != null
                        && !spec.getTableName().equalsIgnoreCase(primaryTable)) {
                    neededCompanionTables.add(spec.getTableName().toLowerCase());
                }
            }
        }

        List<AttachedTableSpec> joins = new ArrayList<>();
        for (TableRelationDTO rel : relations) {
            if (ModuleRelationResolver.isCompanionRelation(rel, tree)) {
                String compTable = rel.getJoinTable();
                if (compTable != null && !compTable.isBlank()) {
                    String compKey = compTable.trim().toLowerCase();
                    if (!declaredTables.contains(compKey)) {
                        continue;
                    }
                    if (includeAllCompanions || neededCompanionTables.contains(compKey)) {
                        String fk =
                                (rel.getJoinField() != null && !rel.getJoinField().isBlank())
                                        ? rel.getJoinField().trim()
                                        : "id";
                        String pk =
                                (rel.getMainField() != null && !rel.getMainField().isBlank())
                                        ? rel.getMainField().trim()
                                        : "id";
                        joins.add(
                                AttachedTableSpec.builder()
                                        .sourceTable(primaryTable)
                                        .sourceField(pk)
                                        .targetTable(compTable.trim())
                                        .targetField(fk)
                                        .joinKind("1:1")
                                        .build());
                    }
                }
            }
        }
        return joins;
    }

    /**
     * 编译「上游表」（多对一）的 LEFT JOIN 规格。
     *
     * <p>与伴生表方向相反：伴生表是沿关系向下的垂直拆分，上游表是沿关系向上取父行。school 的 {@code teacher -> course} 表示 {@code
     * course.teacher_id = teacher.id}，因此 ON 条件里 本表出外键、目标表出主键（伴生表恰好相反）。两者都复用 {@link
     * AttachedTableSpec} 的通用形式 {@code source.sourceField = target.targetField}，执行侧无需区分。
     */
    private List<AttachedTableSpec> resolveUpstreamJoinsForTable(
            String primaryTable,
            List<PhysicalFieldSpec> projectedFields,
            ProjectTableRelationsDTO projectRelations,
            ModuleTreeDTO tree,
            Set<String> declaredTables) {
        if (primaryTable == null
                || primaryTable.isBlank()
                || projectRelations == null
                || projectRelations.getReverseRelations() == null) {
            return Collections.emptyList();
        }

        List<TableRelationDTO> relations =
                projectRelations.getReverseRelations().get(primaryTable.toLowerCase());
        if (relations == null || relations.isEmpty()) {
            return Collections.emptyList();
        }

        boolean includeAllUpstream = projectedFields == null || projectedFields.isEmpty();
        Set<String> neededUpstreamTables = new HashSet<>();
        if (!includeAllUpstream) {
            for (PhysicalFieldSpec spec : projectedFields) {
                if (spec.getTableName() != null
                        && !spec.getTableName().equalsIgnoreCase(primaryTable)) {
                    neededUpstreamTables.add(spec.getTableName().toLowerCase());
                }
            }
        }

        List<AttachedTableSpec> joins = new ArrayList<>();
        for (TableRelationDTO rel : relations) {
            if (!ModuleRelationResolver.isUpstreamRelation(rel, primaryTable, tree)) {
                continue;
            }
            String upTable = rel.getMainTable().trim().toLowerCase();
            // 上游表必须出现在本模块的字段字典里，否则 JOIN 进来纯属浪费
            if (!declaredTables.contains(upTable)) {
                continue;
            }
            if (!includeAllUpstream && !neededUpstreamTables.contains(upTable)) {
                continue;
            }
            joins.add(
                    AttachedTableSpec.builder()
                            .sourceTable(primaryTable)
                            .sourceField(rel.getJoinField().trim())
                            .targetTable(rel.getMainTable().trim())
                            .targetField(rel.getMainField().trim())
                            .joinKind("N:1")
                            .build());
        }
        return joins;
    }

    /**
     * 列出会被 LEFT JOIN 进来的附加表（伴生表 + 上游表）。
     *
     * <p>仅用于全字段模式下判断「是否必须放弃 {@code SELECT *}」。刻意不依赖投影规格：全字段模式本来还没有投影规格， 此处按「关系 + 字段字典声明」判定，与两个
     * resolve 方法的全字段分支保持同一口径。
     */
    private static List<String> attachedTablesOf(
            String primaryTable,
            ProjectTableRelationsDTO projectRelations,
            ModuleTreeDTO tree,
            Set<String> declaredTables) {
        List<String> result = new ArrayList<>();
        if (primaryTable == null || primaryTable.isBlank() || projectRelations == null) {
            return result;
        }

        Map<String, List<TableRelationDTO>> forward = projectRelations.getForwardRelations();
        if (forward != null) {
            List<TableRelationDTO> fwdRels = forward.get(primaryTable.toLowerCase());
            if (fwdRels != null) {
                for (TableRelationDTO rel : fwdRels) {
                    if (ModuleRelationResolver.isCompanionRelation(rel, tree)
                            && rel.getJoinTable() != null
                            && declaredTables.contains(rel.getJoinTable().trim().toLowerCase())) {
                        result.add(rel.getJoinTable().trim());
                    }
                }
            }
        }

        Map<String, List<TableRelationDTO>> reverse = projectRelations.getReverseRelations();
        if (reverse != null) {
            List<TableRelationDTO> revRels = reverse.get(primaryTable.toLowerCase());
            if (revRels != null) {
                for (TableRelationDTO rel : revRels) {
                    if (ModuleRelationResolver.isUpstreamRelation(rel, primaryTable, tree)
                            && declaredTables.contains(rel.getMainTable().trim().toLowerCase())) {
                        result.add(rel.getMainTable().trim());
                    }
                }
            }
        }
        return result;
    }

    /** 取模块字段字典中某张物理表声明的列名（保持字段顺序）。 */
    private static List<String> declaredColumnsOf(ModuleInfo meta, String table) {
        List<String> columns = new ArrayList<>();
        if (meta == null || meta.getFields() == null || table == null || table.isBlank()) {
            return columns;
        }
        for (ModuleFieldDTO f : meta.getFields()) {
            if (f.getColumnName() == null || f.getColumnName().isBlank()) {
                continue;
            }
            String tbl =
                    (f.getTableName() != null && !f.getTableName().isBlank())
                            ? f.getTableName()
                            : ModuleRelationResolver.resolvePrimaryTable(meta);
            if (table.equalsIgnoreCase(tbl)) {
                columns.add(f.getColumnName());
            }
        }
        return columns;
    }

    private TableSpec findTableSpec(
            Long currentModuleId, String tableName, List<TableSpec> tableSpecs) {
        if (tableName == null
                || tableName.isBlank()
                || tableSpecs == null
                || tableSpecs.isEmpty()) {
            return null;
        }
        String cleanTable = tableName.trim();
        TableSpec fallback = null;
        for (TableSpec ts : tableSpecs) {
            if (ts == null || ts.getTableName() == null || ts.getTableName().isBlank()) {
                continue;
            }
            if (cleanTable.equalsIgnoreCase(ts.getTableName().trim())) {
                if (ts.getModuleId() != null && currentModuleId != null) {
                    if (ts.getModuleId().equals(currentModuleId)) {
                        return ts;
                    }
                } else if (fallback == null) {
                    fallback = ts;
                }
            }
        }
        return fallback;
    }

    private boolean isTableOrDescendantInSpecs(
            Long moduleId, List<TableSpec> tableSpecs, ModuleTreeDTO tree) {
        if (moduleId == null || tree == null || tableSpecs == null || tableSpecs.isEmpty()) {
            return false;
        }
        ModuleInfo meta = tree.getModule(moduleId);
        if (meta != null) {
            String pt = ModuleRelationResolver.resolvePrimaryTable(meta);
            if (pt != null && !pt.isBlank()) {
                for (TableSpec ts : tableSpecs) {
                    if (ts == null) {
                        continue;
                    }
                    if (ts.getModuleId() != null) {
                        if (ts.getModuleId().equals(moduleId)
                                && ts.getTableName() != null
                                && pt.equalsIgnoreCase(ts.getTableName().trim())) {
                            return true;
                        }
                    } else if (ts.getTableName() != null
                            && pt.equalsIgnoreCase(ts.getTableName().trim())) {
                        return true;
                    }
                }
            }
        }
        List<Long> children = tree.getChildModuleIds(moduleId);
        if (children != null) {
            for (Long cid : children) {
                if (isTableOrDescendantInSpecs(cid, tableSpecs, tree)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isModuleOrDescendantInFilters(
            Long moduleId, List<PhysicalFilterItem> filters, ModuleTreeDTO tree) {
        if (moduleId == null || tree == null || filters == null || filters.isEmpty()) {
            return false;
        }
        ModuleInfo meta = tree.getModule(moduleId);
        if (meta != null) {
            String pt = ModuleRelationResolver.resolvePrimaryTable(meta);
            for (PhysicalFilterItem f : filters) {
                if (f == null) {
                    continue;
                }
                if (f.getModuleId() != null && f.getModuleId().equals(moduleId)) {
                    return true;
                }
                if (pt != null
                        && !pt.isBlank()
                        && f.getTableName() != null
                        && pt.equalsIgnoreCase(f.getTableName().trim())) {
                    return true;
                }
            }
        }
        List<Long> children = tree.getChildModuleIds(moduleId);
        if (children != null) {
            for (Long cid : children) {
                if (isModuleOrDescendantInFilters(cid, filters, tree)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<OrderField<?>> compileTableSorts(
            Long targetModuleId, String primaryTable, List<PhysicalSortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return List.of(DSL.field(DSL.name(primaryTable, "id")).desc());
        }
        List<OrderField<?>> orderFields = new ArrayList<>();
        for (PhysicalSortItem s : sorts) {
            if (s != null && s.getColumnName() != null && !s.getColumnName().isBlank()) {
                if (s.getModuleId() != null) {
                    if (targetModuleId != null && !s.getModuleId().equals(targetModuleId)) {
                        continue;
                    }
                } else if (targetModuleId != null) {
                    // 若未传 moduleId，则仅在主表匹配时生效
                    String tbl =
                            (s.getTableName() != null && !s.getTableName().isBlank())
                                    ? s.getTableName().trim()
                                    : primaryTable;
                    if (!primaryTable.equalsIgnoreCase(tbl)) {
                        continue;
                    }
                }
                String tbl =
                        (s.getTableName() != null && !s.getTableName().isBlank())
                                ? s.getTableName().trim()
                                : primaryTable;
                Field<Object> field = DSL.field(DSL.name(tbl, s.getColumnName().trim()));
                if ("ASC".equalsIgnoreCase(s.getDirection())) {
                    orderFields.add(field.asc());
                } else {
                    orderFields.add(field.desc());
                }
            }
        }
        if (orderFields.isEmpty()) {
            orderFields.add(DSL.field(DSL.name(primaryTable, "id")).desc());
        }
        return orderFields;
    }
}
