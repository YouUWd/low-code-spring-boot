package com.jdec.platform.data.biz.plan.compiler;

import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.common.ProjectTableRelationsDTO;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import com.jdec.platform.data.api.dto.request.DetailQueryReq;
import com.jdec.platform.data.api.dto.request.OptionReq;
import com.jdec.platform.data.api.dto.request.PageQueryReq;
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.data.api.dto.request.SortItem;
import com.jdec.platform.data.api.model.predicate.ColumnRef;
import com.jdec.platform.data.api.model.predicate.ComparisonPredicate;
import com.jdec.platform.data.api.model.predicate.CompoundPredicate;
import com.jdec.platform.data.api.model.predicate.FunctionExpr;
import com.jdec.platform.data.api.model.predicate.Predicate;
import com.jdec.platform.data.api.model.predicate.RawSqlPredicate;
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
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * 结构化查询计划编译器 (QueryPlanCompiler)
 *
 * <p>基于自相似业务视图树 (View Tree) 驱动：
 *
 * <ul>
 *   <li>1. 接收自相似 QueryReq 视图树 (View Tree)，与 ModuleTreeDTO 树进行 1:1 正向对齐装配；
 *   <li>2. 各 View 节点作用域天然物理隔离，彻底消歧同名表被多个子模块引用的问题；
 *   <li>3. 依据 MySQL 标准谓词 AST 编译 WHERE Condition，支持布尔复合、集合运算与自底向上 Semi-Join EXISTS 上卷；
 *   <li>4. 支持单主表、1:1 伴生表、N:1 上游字典表的统一 table.column 投影推导。
 * </ul>
 */
@Slf4j
@Component
public class QueryPlanCompiler {

    /** 编译候选项查询请求为单列去重投影的 QueryPlan */
    public QueryPlan compile(OptionReq req, PlanMeta meta) {
        if (req == null) {
            throw new IllegalArgumentException("候选项请求不能为空");
        }
        if (req.getColumnName() == null || req.getColumnName().isBlank()) {
            throw new IllegalArgumentException("候选项请求的列名不能为空");
        }

        ModuleTreeDTO tree = meta != null ? meta.moduleTree() : null;
        ModuleInfo targetMeta = resolveTargetModule(tree, req.getView());
        String table = req.getTableName();
        if (table == null || table.isBlank()) {
            if (targetMeta != null) {
                table = ModuleRelationResolver.resolvePrimaryTable(targetMeta);
            }
        }

        String view = req.getView();
        if ((view == null || view.isBlank()) && targetMeta != null) {
            view = targetMeta.getModuleCode();
        }

        String targetCol =
                (table != null && !table.isBlank())
                        ? table.trim() + "." + req.getColumnName().trim()
                        : req.getColumnName().trim();

        QueryReq queryReq =
                QueryReq.builder()
                        .view(view)
                        .columns(List.of(targetCol))
                        .filter(req.getFilter())
                        .build();

        return compileInternal(queryReq, meta, true);
    }

    /** 编译单据详情精准查询请求为 QueryPlan */
    public QueryPlan compileDetail(DetailQueryReq req, PlanMeta meta) {
        if (req == null) {
            throw new IllegalArgumentException("详情查询请求不能为空");
        }
        if (req.getId() == null) {
            throw new IllegalArgumentException("单据 ID 不能为空");
        }

        ModuleTreeDTO tree = meta != null ? meta.moduleTree() : null;
        if (tree == null || tree.getRootModule() == null) {
            throw new IllegalArgumentException("未找到对应的视图或元数据配置");
        }

        ModuleInfo targetMeta = resolveTargetModule(tree, req.getView());
        String primaryTable = ModuleRelationResolver.resolvePrimaryTable(targetMeta);
        if (primaryTable.isBlank()) {
            throw new IllegalArgumentException("视图 [" + req.getView() + "] 为无物理主表的虚拟模块，不支持单据详情查询");
        }
        Predicate idPred = ComparisonPredicate.eq(primaryTable + ".id", req.getId());
        Predicate mergedPred =
                (req.getFilter() != null) ? CompoundPredicate.and(idPred, req.getFilter()) : idPred;

        String view = req.getView();
        if (view == null || view.isBlank()) {
            view = targetMeta.getModuleCode();
        }

        DetailQueryReq queryReq =
                req.toBuilder().view(view).id(req.getId()).filter(mergedPred).build();

        return compileInternal(queryReq, meta, true);
    }

    /** 核心编译入口：基于自相似 View 树装配 QueryPlan */
    public QueryPlan compile(QueryReq req, PlanMeta meta) {
        return compileInternal(req, meta, req instanceof DetailQueryReq);
    }

    private QueryPlan compileInternal(QueryReq req, PlanMeta meta, boolean expandAllChildren) {
        if (req == null) {
            throw new IllegalArgumentException("查询请求不能为空");
        }
        ModuleTreeDTO tree = meta != null ? meta.moduleTree() : null;
        if (tree == null || tree.getRootModule() == null) {
            throw new IllegalArgumentException("未找到对应的视图或元数据配置");
        }

        ModuleInfo targetMeta = resolveTargetModule(tree, req.getView());
        Long targetModuleId =
                (targetMeta != null && targetMeta.getModuleId() != null)
                        ? targetMeta.getModuleId()
                        : tree.getRootModuleId();

        String reqView = req.getView();
        if (reqView == null || reqView.isBlank()) {
            req.setView(
                    targetMeta != null
                            ? targetMeta.getModuleCode()
                            : tree.getRootModule().getModuleCode());
        }

        TableQueryValidator.validate(req, tree);

        QueryNodePlan rootPlan =
                compileViewNode(
                        req,
                        targetMeta,
                        targetModuleId,
                        null,
                        meta.projectRelations(),
                        tree,
                        expandAllChildren,
                        Collections.emptySet());

        return QueryPlan.builder().rootNodePlan(rootPlan).build();
    }

    /** 根据请求 view 在模块树中定位目标模块（优先匹配根模块，再匹配树内子模块，确保与 SavePlanCompiler 同口径） */
    private static ModuleInfo resolveTargetModule(ModuleTreeDTO tree, String view) {
        if (tree == null) {
            return null;
        }
        ModuleInfo root = tree.getRootModule();
        if (view == null || view.isBlank() || root == null) {
            return root;
        }
        String trimmed = view.trim();
        if (matchesModuleView(root, trimmed)) {
            return root;
        }
        if (tree.getModuleMap() != null) {
            for (ModuleInfo info : tree.getModuleMap().values()) {
                if (matchesModuleView(info, trimmed)) {
                    return info;
                }
            }
        }
        return root;
    }

    private static boolean matchesModuleView(ModuleInfo info, String view) {
        if (info == null || view == null || view.isBlank()) {
            return false;
        }
        if (info.getModuleCode() != null && view.equalsIgnoreCase(info.getModuleCode().trim())) {
            return true;
        }
        if (info.getPrimaryTable() != null
                && !info.getPrimaryTable().isBlank()
                && view.equalsIgnoreCase(info.getPrimaryTable().trim())) {
            return true;
        }
        return info.getModuleId() != null && view.equals(String.valueOf(info.getModuleId()));
    }

    public record ParentRelationInfo(
            String parentKey,
            String childForeignKey,
            String parentPrimaryTable,
            String relationType) {}

    /** 递归编译单节点计划 (View 树正向同构装配) */
    private QueryNodePlan compileViewNode(
            QueryReq nodeReq,
            ModuleInfo meta,
            Long currentModuleId,
            ModuleInfo parentMeta,
            ProjectTableRelationsDTO projectRelations,
            ModuleTreeDTO tree,
            boolean expandAllChildren,
            Set<String> inheritedFilterTables) {

        if (meta == null) {
            throw new IllegalArgumentException("模块 ID [" + currentModuleId + "] 不存在或未配置元数据");
        }

        String primaryTable = ModuleRelationResolver.resolvePrimaryTable(meta);
        ParentRelationInfo parentRel =
                resolveParentRelation(primaryTable, meta, parentMeta, projectRelations, tree);
        String parentFk = parentRel.childForeignKey();
        String parentKey = parentRel.parentKey();
        boolean isVirtual = primaryTable == null || primaryTable.isBlank();

        // 1. 收集当前模块声明过的物理表集合 (主表、1:1 伴生表、N:1 上游表)
        Set<String> declaredTables = new HashSet<>();
        if (meta.getFields() != null) {
            for (ModuleFieldDTO f : meta.getFields()) {
                if (f.getTableName() != null && !f.getTableName().isBlank()) {
                    declaredTables.add(f.getTableName().trim().toLowerCase());
                }
            }
        }
        if (!isVirtual) {
            declaredTables.add(primaryTable.trim().toLowerCase());
            // 校验同模块 1:N 违规配置 (R1 违规拦截)
            if (projectRelations != null && projectRelations.getForwardRelations() != null) {
                List<TableRelationDTO> fwdRels =
                        projectRelations.getForwardRelations().get(primaryTable.toLowerCase());
                if (fwdRels != null) {
                    for (TableRelationDTO rel : fwdRels) {
                        if (rel.getJoinTable() != null
                                && declaredTables.contains(
                                        rel.getJoinTable().trim().toLowerCase())) {
                            String rType =
                                    rel.getRelationType() != null
                                            ? rel.getRelationType().toUpperCase()
                                            : "";
                            if ("1:N".equals(rType) || "ONE_TO_MANY".equals(rType)) {
                                throw new IllegalArgumentException(
                                        "同模块配置错误：主表 ["
                                                + primaryTable
                                                + "] 上不允许直接投影 1:N 从表 ["
                                                + rel.getJoinTable()
                                                + "] 的字段，请配置为子模块");
                            }
                        }
                    }
                }
            }
        }

        // 2. 编译投影字段 (PhysicalFieldSpec)
        List<PhysicalFieldSpec> primaryProjectedFields = new ArrayList<>();
        List<String> requestedColumns = nodeReq != null ? nodeReq.getColumns() : null;
        boolean isAllCols = (nodeReq != null) ? isAllColumns(requestedColumns) : expandAllChildren;

        if (!isVirtual) {
            if (isAllCols) {
                // 全字段投影模式：若存在附加表则显式投影主表与附加表，否则返回空列表触发 SELECT * 全字段扫描
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
            } else if (requestedColumns != null) {
                // 显式指定 columns (table.column 格式) 模式：按表名分别归并投影列
                Map<String, List<String>> colsByTable = new LinkedHashMap<>();
                for (String c : requestedColumns) {
                    if (c == null || c.isBlank() || "*".equals(c.trim())) {
                        continue;
                    }
                    String trimmed = c.trim();
                    if (trimmed.contains(".")) {
                        int dot = trimmed.indexOf('.');
                        String tbl = trimmed.substring(0, dot).trim().toLowerCase();
                        String clm = trimmed.substring(dot + 1).trim();
                        colsByTable.computeIfAbsent(tbl, k -> new ArrayList<>()).add(clm);
                    } else {
                        colsByTable
                                .computeIfAbsent(primaryTable.toLowerCase(), k -> new ArrayList<>())
                                .add(trimmed);
                    }
                }

                // 2.1 主表投影
                List<String> mainCols = colsByTable.get(primaryTable.toLowerCase());
                if (mainCols != null && !mainCols.isEmpty()) {
                    primaryProjectedFields.addAll(
                            compilePhysicalTableProjection(meta, primaryTable, mainCols));
                } else if (colsByTable.isEmpty()) {
                    primaryProjectedFields.addAll(
                            compilePhysicalTableProjection(
                                    meta, primaryTable, declaredColumnsOf(meta, primaryTable)));
                }

                // 2.2 伴生表与上游表投影
                for (Map.Entry<String, List<String>> entry : colsByTable.entrySet()) {
                    String tblName = entry.getKey();
                    if (!tblName.equalsIgnoreCase(primaryTable)
                            && declaredTables.contains(tblName)) {
                        // 校验同模块 1:N 违规配置 (R1 违规拦截)
                        if (projectRelations != null
                                && projectRelations.getForwardRelations() != null) {
                            List<TableRelationDTO> fwdRels =
                                    projectRelations
                                            .getForwardRelations()
                                            .get(primaryTable.toLowerCase());
                            if (fwdRels != null) {
                                for (TableRelationDTO rel : fwdRels) {
                                    if (rel.getJoinTable() != null
                                            && rel.getJoinTable().equalsIgnoreCase(tblName)) {
                                        String rType =
                                                rel.getRelationType() != null
                                                        ? rel.getRelationType().toUpperCase()
                                                        : "";
                                        if ("1:N".equals(rType) || "ONE_TO_MANY".equals(rType)) {
                                            throw new IllegalArgumentException(
                                                    "同模块配置错误：主表 ["
                                                            + primaryTable
                                                            + "] 上不允许直接投影 1:N 从表 ["
                                                            + tblName
                                                            + "] 的字段，请配置为子模块");
                                        }
                                    }
                                }
                            }
                        }
                        primaryProjectedFields.addAll(
                                compilePhysicalTableProjection(meta, tblName, entry.getValue()));
                    }
                }
            }
        }

        // 3. 附加 LEFT JOIN 规格 (伴生表 + 上游表)
        List<AttachedTableSpec> attachedJoins = new ArrayList<>();
        Set<String> applicableTables = new HashSet<>();
        if (!isVirtual) {
            applicableTables.add(primaryTable.toLowerCase());
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
            for (AttachedTableSpec aj : attachedJoins) {
                if (aj.getTargetTable() != null) {
                    applicableTables.add(aj.getTargetTable().toLowerCase());
                }
            }
        }

        // 4. 排序规则编译
        List<OrderField<?>> orderFields =
                isVirtual
                        ? Collections.emptyList()
                        : compileTableSorts(
                                primaryTable,
                                nodeReq != null ? nodeReq.getSort() : null,
                                applicableTables);

        // 5. 递归编译子节点 (View 树同构装配)
        List<QueryNodePlan> childPlans = new ArrayList<>();
        List<Long> childModuleIds = tree.getChildModuleIds(currentModuleId);
        Predicate currentFilter = (nodeReq != null) ? nodeReq.getFilter() : null;
        Set<String> effectiveFilterTables =
                new HashSet<>(
                        inheritedFilterTables != null
                                ? inheritedFilterTables
                                : Collections.emptySet());
        effectiveFilterTables.addAll(extractReferencedTables(currentFilter));

        Set<Long> compiledChildIds = new HashSet<>();

        if (nodeReq != null && nodeReq.getChildren() != null && !nodeReq.getChildren().isEmpty()) {
            // 场景 A：请求显式声明了子视图需求 (按 view 严格匹配子模块，彻底消歧同名物理表)
            for (QueryReq childReq : nodeReq.getChildren()) {
                if (childReq == null
                        || childReq.getView() == null
                        || childReq.getView().isBlank()) {
                    continue;
                }
                String targetView = childReq.getView().trim();
                Long matchedChildId = null;
                for (Long cmid : childModuleIds) {
                    ModuleInfo cm = tree.getModule(cmid);
                    if (matchesModuleView(cm, targetView)) {
                        matchedChildId = cmid;
                        break;
                    }
                }
                if (matchedChildId != null) {
                    compiledChildIds.add(matchedChildId);
                    childPlans.add(
                            compileViewNode(
                                    childReq,
                                    tree.getModule(matchedChildId),
                                    matchedChildId,
                                    meta,
                                    projectRelations,
                                    tree,
                                    expandAllChildren,
                                    effectiveFilterTables));
                } else {
                    log.warn(
                            "子视图 [{}] 未在当前视图 [{}] 的直接子模块中找到匹配配置", targetView, meta.getModuleCode());
                }
            }
        } else if (expandAllChildren || isAllCols) {
            // 场景 B：单据详情、候选项或全量展开模式，默认遍历编译所有配置的子模块
            for (Long childMid : childModuleIds) {
                ModuleInfo childMeta = tree.getModule(childMid);
                if (childMeta != null) {
                    compiledChildIds.add(childMid);
                    childPlans.add(
                            compileViewNode(
                                    null,
                                    childMeta,
                                    childMid,
                                    meta,
                                    projectRelations,
                                    tree,
                                    true,
                                    effectiveFilterTables));
                }
            }
        }

        // 场景 C：当 filter 显式引用了尚未在投影子树中声明的子模块/孙模块表时，按树匹配补充编译 fetchData=false 的辅助节点以触发 Semi-Join /
        // EXISTS 级联过滤
        if (!effectiveFilterTables.isEmpty()) {
            for (Long childMid : childModuleIds) {
                if (!compiledChildIds.contains(childMid)
                        && subtreeContainsAnyTable(childMid, tree, effectiveFilterTables)) {
                    ModuleInfo childMeta = tree.getModule(childMid);
                    if (childMeta != null) {
                        compiledChildIds.add(childMid);
                        childPlans.add(
                                compileViewNode(
                                        null,
                                        childMeta,
                                        childMid,
                                        meta,
                                        projectRelations,
                                        tree,
                                        false,
                                        effectiveFilterTables));
                    }
                }
            }
        }

        // 6. 过滤条件编译 (依据 MySQL 标准 AST 递归推导本地条件与 EXISTS 子查询，天然支持跨表 AND / OR / NOT)
        Condition effectiveFilterCondition = null;
        if (!isVirtual) {
            Predicate filterToCompile = (nodeReq != null) ? nodeReq.getFilter() : null;
            if (filterToCompile != null) {
                effectiveFilterCondition =
                        compilePredicateForPlan(
                                primaryTable, applicableTables, childPlans, filterToCompile, tree);
            }

            // 7. 子节点显式过滤条件自底向上上卷 (Semi-Join / EXISTS)
            List<Condition> childRollups = new ArrayList<>();
            for (QueryNodePlan cp : collectPhysicalPlans(childPlans)) {
                if (cp.getEffectiveFilterCondition() != null) {
                    Condition exists =
                            buildExistsConditionForChildPlan(
                                    cp, primaryTable, cp.getEffectiveFilterCondition());
                    if (exists != null) {
                        childRollups.add(exists);
                    }
                }
            }
            if (!childRollups.isEmpty()) {
                Condition rollupAnd = DSL.and(childRollups);
                effectiveFilterCondition =
                        (effectiveFilterCondition != null)
                                ? effectiveFilterCondition.and(rollupAnd)
                                : rollupAnd;
            }
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

        Integer pNo = (nodeReq instanceof PageQueryReq p) ? p.getPageNo() : 1;
        Integer pSize =
                (nodeReq instanceof PageQueryReq p)
                        ? p.getPageSize()
                        : (expandAllChildren ? 1 : 20);

        boolean shouldFetchData = !isVirtual && (nodeReq != null || expandAllChildren);

        String nodeView =
                (nodeReq != null && nodeReq.getView() != null && !nodeReq.getView().isBlank())
                        ? nodeReq.getView().trim()
                        : (meta != null
                                        && meta.getModuleCode() != null
                                        && !meta.getModuleCode().isBlank()
                                ? meta.getModuleCode()
                                : (primaryTable != null && !primaryTable.isBlank()
                                        ? primaryTable
                                        : String.valueOf(currentModuleId)));

        return QueryNodePlan.builder()
                .moduleId(currentModuleId)
                .view(nodeView)
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

    private static boolean isAllColumns(List<String> requestedColumns) {
        if (requestedColumns == null || requestedColumns.isEmpty()) {
            return true;
        }
        return requestedColumns.stream()
                .anyMatch(c -> c != null && (c.trim().equals("*") || c.trim().endsWith(".*")));
    }

    private List<OrderField<?>> compileTableSorts(
            String primaryTable, List<SortItem> sorts, Set<String> applicableTables) {
        if (sorts == null || sorts.isEmpty()) {
            return List.of(DSL.field(DSL.name(primaryTable, "id")).desc());
        }
        List<OrderField<?>> orderFields = new ArrayList<>();
        for (SortItem s : sorts) {
            if (s == null) {
                continue;
            }
            String tbl = s.getTableName();
            if (tbl == null || tbl.isBlank()) {
                tbl = primaryTable;
            }
            if (applicableTables != null && !applicableTables.contains(tbl.trim().toLowerCase())) {
                continue;
            }
            String col = s.getColumnName();
            if (col == null || col.isBlank()) {
                continue;
            }
            Field<Object> field = DSL.field(DSL.name(tbl.trim(), col.trim()));
            if ("ASC".equalsIgnoreCase(s.getDirection())) {
                orderFields.add(field.asc());
            } else {
                orderFields.add(field.desc());
            }
        }
        if (orderFields.isEmpty()) {
            orderFields.add(DSL.field(DSL.name(primaryTable, "id")).desc());
        }
        return orderFields;
    }

    private ParentRelationInfo resolveParentRelation(
            String childPrimaryTable,
            ModuleInfo childMeta,
            ModuleInfo parentMeta,
            ProjectTableRelationsDTO projectRelations,
            ModuleTreeDTO tree) {
        if (childPrimaryTable == null || childPrimaryTable.isBlank() || parentMeta == null) {
            return new ParentRelationInfo("id", null, null, "1:N");
        }

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

        if (physicalParentMeta == null || parentPrimary.isBlank()) {
            return new ParentRelationInfo("id", null, null, "1:N");
        }

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

        Long parentModId = physicalParentMeta.getModuleId();
        Long childModId = childMeta != null ? childMeta.getModuleId() : null;
        throw new IllegalStateException(
                String.format(
                        "元数据关联关系未自洽：未在 sys_table_relation 中找到物理父表 [%s](模块 %s) 到子表 [%s](模块 %s) 的单向关联定义(main_table -> join_table)",
                        parentPrimary, parentModId, childPrimaryTable, childModId));
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

    private List<PhysicalFieldSpec> compilePhysicalTableProjection(
            ModuleInfo meta, String table, List<String> requestedColumns) {
        List<PhysicalFieldSpec> specs = new ArrayList<>();
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
                        joins.add(
                                AttachedTableSpec.builder()
                                        .sourceTable(primaryTable)
                                        .sourceField("id")
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
            if (ModuleRelationResolver.isUpstreamRelation(rel, primaryTable, tree)) {
                String upTable = rel.getMainTable();
                if (upTable != null && !upTable.isBlank()) {
                    String upKey = upTable.trim().toLowerCase();
                    if (!declaredTables.contains(upKey)) {
                        continue;
                    }
                    if (includeAllUpstream || neededUpstreamTables.contains(upKey)) {
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
                                        .sourceField(fk)
                                        .targetTable(upTable.trim())
                                        .targetField(pk)
                                        .joinKind("N:1")
                                        .build());
                    }
                }
            }
        }
        return joins;
    }

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

    /** 从谓词 AST 中提取引用的所有物理表名称 (转为小写) */
    private static Set<String> extractReferencedTables(Predicate predicate) {
        Set<String> tables = new HashSet<>();
        collectReferencedTables(predicate, tables);
        return tables;
    }

    private static void collectReferencedTables(Predicate predicate, Set<String> tables) {
        if (predicate == null) {
            return;
        }
        if (predicate instanceof ComparisonPredicate comp) {
            String tbl = comp.getTableName();
            if (tbl == null && comp.getExpression() instanceof ColumnRef col) {
                tbl = col.getTableName();
            } else if (tbl == null
                    && comp.getExpression() instanceof FunctionExpr fn
                    && fn.getTargetColumn() != null) {
                tbl = fn.getTargetColumn().getTableName();
            }
            if (tbl != null && !tbl.isBlank()) {
                tables.add(tbl.trim().toLowerCase());
            }
        } else if (predicate instanceof CompoundPredicate comp) {
            if (comp.getPredicates() != null) {
                for (Predicate sub : comp.getPredicates()) {
                    collectReferencedTables(sub, tables);
                }
            }
        }
    }

    /** 检查以 mid 为根的子模块树中是否包含任意有效物理模块的目标表 (忽略无 primary_table 的虚拟模块上误配的字段) */
    private static boolean subtreeContainsAnyPhysicalTable(
            Long mid, ModuleTreeDTO tree, Set<String> targetTables) {
        if (mid == null || tree == null || targetTables == null || targetTables.isEmpty()) {
            return false;
        }
        ModuleInfo m = tree.getModule(mid);
        if (m != null) {
            String pt = ModuleRelationResolver.resolvePrimaryTable(m);
            if (!pt.isEmpty()) {
                if (targetTables.contains(pt.toLowerCase())) {
                    return true;
                }
                if (m.getFields() != null) {
                    for (ModuleFieldDTO f : m.getFields()) {
                        if (f != null
                                && f.getTableName() != null
                                && targetTables.contains(f.getTableName().trim().toLowerCase())) {
                            return true;
                        }
                    }
                }
            }
        }
        List<Long> kids = tree.getChildModuleIds(mid);
        if (kids != null) {
            for (Long kid : kids) {
                if (subtreeContainsAnyPhysicalTable(kid, tree, targetTables)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 检查以 mid 为根的子模块树中是否包含任意目标物理表 (支持直属子模块及深层孙模块) */
    private static boolean subtreeContainsAnyTable(
            Long mid, ModuleTreeDTO tree, Set<String> targetTables) {
        return subtreeContainsAnyPhysicalTable(mid, tree, targetTables);
    }

    private static boolean subtreeContainsTable(Long mid, ModuleTreeDTO tree, String targetTable) {
        if (mid == null || tree == null || targetTable == null || targetTable.isBlank()) {
            return false;
        }
        return subtreeContainsAnyPhysicalTable(mid, tree, Set.of(targetTable.trim().toLowerCase()));
    }

    private Condition compilePredicateForPlan(
            String primaryTable,
            Set<String> applicableTables,
            List<QueryNodePlan> childPlans,
            Predicate predicate,
            ModuleTreeDTO tree) {
        if (predicate == null) {
            return null;
        }

        List<QueryNodePlan> physicalChildren = collectPhysicalPlans(childPlans);

        if (predicate instanceof ComparisonPredicate comp) {
            String tbl = comp.getTableName();
            if (tbl == null && comp.getExpression() instanceof ColumnRef col) {
                tbl = col.getTableName();
            } else if (tbl == null
                    && comp.getExpression() instanceof FunctionExpr fn
                    && fn.getTargetColumn() != null) {
                tbl = fn.getTargetColumn().getTableName();
            }
            if (tbl == null || tbl.isBlank()) {
                tbl = primaryTable;
            }
            String targetTable = tbl != null ? tbl.trim().toLowerCase() : "";

            // 1. 本地表匹配 (主表、伴生表、上游表)
            if (applicableTables != null && applicableTables.contains(targetTable)) {
                return PredicateCompiler.compile(comp, primaryTable, applicableTables);
            }

            // 2. 子孙节点匹配 (递归下推并在本层封装为 EXISTS)
            for (QueryNodePlan cp : physicalChildren) {
                if (planContainsTable(cp, tree, targetTable)) {
                    Condition childCond = compilePredicateForChildPlan(cp, comp, tree);
                    if (childCond != null) {
                        return buildExistsConditionForChildPlan(cp, primaryTable, childCond);
                    }
                }
            }
            return null;
        } else if (predicate instanceof CompoundPredicate comp) {
            if (comp.getPredicates() == null || comp.getPredicates().isEmpty()) {
                return null;
            }

            // 优化：若复合条件引用的全部物理表均完整包含于某一个物理子节点树中，且不包含本地表，
            // 则整条复合条件作为一个整体下推到该子节点，以生成合并且语义紧凑的单个 EXISTS。
            Set<String> refTables = extractReferencedTables(comp);
            boolean hasLocal =
                    applicableTables != null
                            && refTables.stream().anyMatch(applicableTables::contains);
            if (!hasLocal && !physicalChildren.isEmpty()) {
                for (QueryNodePlan cp : physicalChildren) {
                    if (planContainsAllTables(cp, tree, refTables)) {
                        Condition childCond = compilePredicateForChildPlan(cp, comp, tree);
                        if (childCond != null) {
                            return buildExistsConditionForChildPlan(cp, primaryTable, childCond);
                        }
                    }
                }
            }

            // 否则（跨本地表与子表，或跨多个不同子树），分别递归编译每个分支并按操作符组合
            List<Condition> subConds = new ArrayList<>();
            for (Predicate sub : comp.getPredicates()) {
                Condition sc =
                        compilePredicateForPlan(
                                primaryTable, applicableTables, childPlans, sub, tree);
                if (sc != null && !DSL.noCondition().equals(sc)) {
                    subConds.add(sc);
                }
            }
            if (subConds.isEmpty()) {
                return null;
            }
            String op =
                    comp.getOperator() != null ? comp.getOperator().toUpperCase().trim() : "AND";
            if (subConds.size() == 1 && !"NOT".equals(op)) {
                return subConds.get(0);
            }
            return switch (op) {
                case "OR" -> DSL.or(subConds);
                case "NOT" -> DSL.not(subConds.get(0));
                case "AND" -> DSL.and(subConds);
                default -> DSL.and(subConds);
            };
        } else if (predicate instanceof RawSqlPredicate raw) {
            return PredicateCompiler.compile(raw, primaryTable, applicableTables);
        }
        return null;
    }

    private Condition compilePredicateForChildPlan(
            QueryNodePlan cp, Predicate predicate, ModuleTreeDTO tree) {
        String childPrimary = cp.getPrimaryTable();
        Set<String> childApplicable = new HashSet<>();
        if (childPrimary != null && !childPrimary.isBlank()) {
            childApplicable.add(childPrimary.toLowerCase());
        }
        if (cp.getAttachedJoins() != null) {
            for (AttachedTableSpec aj : cp.getAttachedJoins()) {
                if (aj.getTargetTable() != null) {
                    childApplicable.add(aj.getTargetTable().toLowerCase());
                }
            }
        }
        return compilePredicateForPlan(
                childPrimary, childApplicable, cp.getChildren(), predicate, tree);
    }

    @SuppressWarnings("unchecked")
    private Condition buildExistsConditionForChildPlan(
            QueryNodePlan childPlan, String parentPrimaryTable, Condition childCondition) {
        if (childPlan == null || childCondition == null) {
            return null;
        }
        String childTable = childPlan.getPrimaryTable();
        if (childTable == null || childTable.isBlank()) {
            return null;
        }
        if (childTable.equalsIgnoreCase(parentPrimaryTable)) {
            return childCondition;
        }
        String cFk = childPlan.getParentForeignKey();
        if (cFk != null && !cFk.isBlank()) {
            String pK = childPlan.getParentKey() != null ? childPlan.getParentKey() : "id";
            SelectJoinStep<?> selectStep = DSL.selectOne().from(DSL.table(DSL.name(childTable)));
            if (childPlan.getAttachedJoins() != null && !childPlan.getAttachedJoins().isEmpty()) {
                String condSql = childCondition.toString().toLowerCase();
                for (AttachedTableSpec joinSpec : childPlan.getAttachedJoins()) {
                    if (joinSpec != null
                            && joinSpec.getTargetTable() != null
                            && condSql.contains(joinSpec.getTargetTable().trim().toLowerCase())) {
                        String tgtTable = joinSpec.getTargetTable().trim();
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
            }
            return DSL.exists(
                    selectStep.where(
                            DSL.field(DSL.name(childTable, cFk))
                                    .eq(DSL.field(DSL.name(parentPrimaryTable, pK)))
                                    .and(DSL.field(DSL.name(childTable, "deleted")).eq((byte) 0))
                                    .and(childCondition)));
        }
        return null;
    }

    private List<QueryNodePlan> collectPhysicalPlans(List<QueryNodePlan> plans) {
        List<QueryNodePlan> result = new ArrayList<>();
        if (plans != null) {
            for (QueryNodePlan p : plans) {
                if (p.getPrimaryTable() != null && !p.getPrimaryTable().isBlank()) {
                    result.add(p);
                } else if (p.getChildren() != null) {
                    result.addAll(collectPhysicalPlans(p.getChildren()));
                }
            }
        }
        return result;
    }

    private boolean planContainsTable(QueryNodePlan plan, ModuleTreeDTO tree, String table) {
        if (plan == null || table == null || table.isBlank()) {
            return false;
        }
        String target = table.trim().toLowerCase();
        if (plan.getPrimaryTable() != null && plan.getPrimaryTable().equalsIgnoreCase(target)) {
            return true;
        }
        if (plan.getAttachedJoins() != null) {
            for (AttachedTableSpec aj : plan.getAttachedJoins()) {
                if (aj.getTargetTable() != null && aj.getTargetTable().equalsIgnoreCase(target)) {
                    return true;
                }
            }
        }
        if (plan.getModuleId() != null) {
            return subtreeContainsTable(plan.getModuleId(), tree, target);
        }
        return false;
    }

    private boolean planContainsAllTables(
            QueryNodePlan plan, ModuleTreeDTO tree, Set<String> tables) {
        if (plan == null || tables == null || tables.isEmpty()) {
            return false;
        }
        for (String tbl : tables) {
            if (!planContainsTable(plan, tree, tbl)) {
                return false;
            }
        }
        return true;
    }
}
