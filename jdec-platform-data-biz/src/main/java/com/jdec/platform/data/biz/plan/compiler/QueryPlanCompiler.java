package com.jdec.platform.data.biz.plan.compiler;

import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.common.ProjectTableRelationsDTO;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.data.api.dto.request.DetailQueryReq;
import com.jdec.platform.data.api.dto.request.FilterItem;
import com.jdec.platform.data.api.dto.request.OptionReq;
import com.jdec.platform.data.api.dto.request.PageQueryReq;
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.data.api.dto.request.SortItem;
import com.jdec.platform.data.biz.plan.model.JoinTableSpec;
import com.jdec.platform.data.biz.plan.model.PhysicalFieldSpec;
import com.jdec.platform.data.biz.plan.model.QueryNodePlan;
import com.jdec.platform.data.biz.plan.model.QueryPlan;
import com.jdec.platform.shared.context.AppContext;
import java.util.*;
import lombok.RequiredArgsConstructor;
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
 *   <li>2. 严格以 fieldId 为基准，解析物理表名、物理列名与投影规格；
 *   <li>3. 构建强类型安全的 SQL 过滤 Condition 与 OrderField 序列；
 *   <li>4. 自动推导子模块外键连接关系（parentForeignKey），产出确定性的 QueryPlan。
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryPlanCompiler {

    private final ConfigApi configApi;

    /** 内部编译树节点载体 (隔离外部 DTO 与编译期自相似树结构) */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class InternalQueryNode {
        private Long moduleId;
        private List<Long> fields;
        private List<FilterItem> filters;
        private List<SortItem> sorts;
        private Integer pageNo;
        private Integer pageSize;
        private List<InternalQueryNode> children;
    }

    /** 编译候选项查询请求为单字段投影与全量过滤上下文的 QueryPlan */
    public QueryPlan compile(OptionReq req) {
        if (req == null) {
            throw new IllegalArgumentException("候选项请求不能为空");
        }
        if (req.getModuleId() == null || req.getFieldId() == null) {
            throw new IllegalArgumentException("候选项请求的模块 ID 与字段 ID 均不能为空");
        }

        QueryReq queryReq =
                QueryReq.builder()
                        .moduleId(req.getModuleId())
                        .fields(List.of(req.getFieldId()))
                        .filters(req.getFilters())
                        .build();
        return compile(queryReq);
    }

    /** 编译单据详情精准查询请求为 QueryPlan (自动由 ModuleTreeDTO 提取主键 id 字段，零重复 I/O) */
    public QueryPlan compileDetail(DetailQueryReq req) {
        if (req == null) {
            throw new IllegalArgumentException("详情查询请求不能为空");
        }
        return compileDetail(
                req.getModuleId(), req.getId(), req.getFields(), req.getFilters(), req.getSorts());
    }

    /** 编译单据详情精准查询快捷方法 */
    public QueryPlan compileDetail(Long moduleId, Long id, List<Long> fields) {
        return compileDetail(moduleId, id, fields, null, null);
    }

    /** 编译单据详情精准查询完整方法 (带投影与组合过滤) */
    public QueryPlan compileDetail(
            Long moduleId,
            Long id,
            List<Long> fields,
            List<FilterItem> userFilters,
            List<SortItem> userSorts) {
        if (moduleId == null || id == null) {
            throw new IllegalArgumentException("模块 ID 和单据 ID 不能为空");
        }

        ModuleTreeDTO tree = loadModuleTree(moduleId);
        if (tree == null || tree.getRootModule() == null) {
            throw new IllegalArgumentException("模块 ID [" + moduleId + "] 不存在或未配置元数据");
        }

        ModuleInfo rootMeta = tree.getRootModule();
        Long idFieldId = null;
        if (rootMeta.getFields() != null) {
            for (ModuleFieldDTO f : rootMeta.getFields()) {
                if ("id".equalsIgnoreCase(f.getColumnName())) {
                    idFieldId = f.getId();
                    break;
                }
            }
        }

        if (idFieldId == null) {
            throw new IllegalStateException(
                    String.format(
                            "模块 [%s](ID: %d) 的元数据中未配置主键 [id] 字段，无法执行单据详情查询",
                            rootMeta.getModuleName(), moduleId));
        }

        List<FilterItem> mergedFilters = new ArrayList<>();
        mergedFilters.add(FilterItem.builder().fieldId(idFieldId).operator("EQ").value(id).build());
        if (userFilters != null && !userFilters.isEmpty()) {
            mergedFilters.addAll(userFilters);
        }

        QueryReq queryReq =
                DetailQueryReq.builder()
                        .moduleId(moduleId)
                        .id(id)
                        .fields(fields)
                        .filters(mergedFilters)
                        .sorts(userSorts)
                        .build();

        return compile(queryReq);
    }

    /** 编译顶层查询请求为完整的 QueryPlan (以根 moduleId 与 fields 驱动，自动校验子树范围并推导执行树) */
    public QueryPlan compile(QueryReq req) {
        if (req == null) {
            throw new IllegalArgumentException("查询请求不能为空");
        }
        Long rootModuleId = req.getModuleId();
        if (rootModuleId == null) {
            throw new IllegalArgumentException("查询主模块 ID 不能为空");
        }

        ModuleTreeDTO tree = loadModuleTree(rootModuleId);
        if (tree == null || tree.getRootModule() == null) {
            throw new IllegalArgumentException("模块 ID [" + rootModuleId + "] 不存在或未配置元数据");
        }

        boolean isGlobalWhitelistMode = req.getFields() != null && !req.getFields().isEmpty();

        // 1. 收集请求中出现的所有 fieldId (包括 fields, filters 递归, sorts)
        Set<Long> allFieldIds = new LinkedHashSet<>();
        if (req.getFields() != null) {
            allFieldIds.addAll(req.getFields());
        }
        if (req.getFilters() != null) {
            collectFieldIdsFromFilters(req.getFilters(), allFieldIds);
        }
        if (req.getSorts() != null) {
            for (var s : req.getSorts()) {
                if (s != null && s.getFieldId() != null) {
                    allFieldIds.add(s.getFieldId());
                }
            }
        }

        // 2. 严格校验字段元数据与子树归属作用域 (Scope Guard，全内存 O(1) 检索，零外部 I/O)
        List<ModuleFieldDTO> fieldDTOs = new ArrayList<>();
        if (!allFieldIds.isEmpty()) {
            for (Long fid : allFieldIds) {
                ModuleFieldDTO f = tree.getField(fid);
                if (f == null) {
                    throw new IllegalArgumentException(
                            String.format("未在当前模块树 [%d] 的元数据中查询到指定字段定义: %d", rootModuleId, fid));
                }
                Long fModuleId = f.getModuleId();
                if (fModuleId != null && !tree.containsModule(fModuleId)) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "字段 [%s] (ID: %d, 所属模块: %d) 不属于主模块 [%d] 及其子模块作用域范围",
                                    f.getDisplayName() != null
                                            ? f.getDisplayName()
                                            : f.getColumnName(),
                                    f.getId(),
                                    fModuleId,
                                    rootModuleId));
                }
                fieldDTOs.add(f);
            }
        }

        // 3. 自动推导并构建内部结构化树 (直接消费 ModuleTreeDTO，零 DB 开销与零回溯)
        InternalQueryNode structuredTree =
                buildInternalQueryTree(req, tree, allFieldIds, fieldDTOs);
        ProjectTableRelationsDTO projectRelations = getProjectRelations();
        QueryNodePlan rootPlan =
                compileNode(
                        structuredTree, null, null, isGlobalWhitelistMode, projectRelations, tree);
        return QueryPlan.builder().rootNodePlan(rootPlan).build();
    }

    /** 根据平铺的 fields/filters/sorts 与 ModuleTreeDTO 拓扑，自动组装内部结构化查询请求树 */
    private InternalQueryNode buildInternalQueryTree(
            QueryReq req,
            ModuleTreeDTO tree,
            Set<Long> allFieldIds,
            List<ModuleFieldDTO> fieldDTOs) {

        Long rootModuleId = req.getModuleId();
        Integer pNo = (req instanceof PageQueryReq p) ? p.getPageNo() : 1;
        Integer pSize =
                (req instanceof PageQueryReq p)
                        ? p.getPageSize()
                        : (req instanceof DetailQueryReq ? 1 : 10);

        if (allFieldIds.isEmpty()) {
            return InternalQueryNode.builder()
                    .moduleId(rootModuleId)
                    .fields(req.getFields())
                    .filters(req.getFilters())
                    .sorts(req.getSorts())
                    .pageNo(pNo)
                    .pageSize(pSize)
                    .children(Collections.emptyList())
                    .build();
        }

        Map<Long, Long> fieldToModule = new HashMap<>();
        Set<Long> involvedModuleIds = new LinkedHashSet<>();
        for (ModuleFieldDTO f : fieldDTOs) {
            if (f.getId() != null && f.getModuleId() != null) {
                fieldToModule.put(f.getId(), f.getModuleId());
                involvedModuleIds.add(f.getModuleId());
            }
        }
        involvedModuleIds.add(rootModuleId);

        if (involvedModuleIds.size() == 1) {
            return InternalQueryNode.builder()
                    .moduleId(rootModuleId)
                    .fields(req.getFields())
                    .filters(req.getFilters())
                    .sorts(req.getSorts())
                    .pageNo(pNo)
                    .pageSize(pSize)
                    .children(Collections.emptyList())
                    .build();
        }

        // 向上补齐涉及模块到根节点的祖先节点闭包 (直接从 tree 的 moduleMap 获取，零 DB 查询)
        Set<Long> fullModuleIds = new LinkedHashSet<>(involvedModuleIds);
        for (Long mid : involvedModuleIds) {
            Long current = mid;
            while (current != null && !current.equals(rootModuleId)) {
                ModuleInfo m = tree.getModule(current);
                if (m != null && m.getParentId() != null && m.getParentId() != 0L) {
                    Long pid = m.getParentId();
                    fullModuleIds.add(pid);
                    if (pid.equals(rootModuleId)) {
                        break;
                    }
                    current = pid;
                } else {
                    break;
                }
            }
        }
        involvedModuleIds = fullModuleIds;

        // 按 moduleId 分组 fields, filters, sorts
        Map<Long, List<Long>> fieldsByModule = new HashMap<>();
        if (req.getFields() != null) {
            for (Long fid : req.getFields()) {
                Long mid = fieldToModule.getOrDefault(fid, rootModuleId);
                fieldsByModule.computeIfAbsent(mid, k -> new ArrayList<>()).add(fid);
            }
        }

        Map<Long, List<FilterItem>> filtersByModule = new HashMap<>();
        if (req.getFilters() != null) {
            for (var filter : req.getFilters()) {
                Long mid =
                        filter.getFieldId() != null
                                ? fieldToModule.getOrDefault(filter.getFieldId(), rootModuleId)
                                : rootModuleId;
                filtersByModule.computeIfAbsent(mid, k -> new ArrayList<>()).add(filter);
            }
        }

        Map<Long, List<SortItem>> sortsByModule = new HashMap<>();
        if (req.getSorts() != null) {
            for (var sort : req.getSorts()) {
                Long mid =
                        sort.getFieldId() != null
                                ? fieldToModule.getOrDefault(sort.getFieldId(), rootModuleId)
                                : rootModuleId;
                sortsByModule.computeIfAbsent(mid, k -> new ArrayList<>()).add(sort);
            }
        }

        // 构建模块层级 parentToChildren 映射 (严格基于真实模块树层级，杜绝跨级悬挂)
        Map<Long, List<Long>> activeParentToChildren = new HashMap<>();
        for (Long mid : involvedModuleIds) {
            if (!mid.equals(rootModuleId)) {
                ModuleInfo m = tree.getModule(mid);
                Long pid =
                        (m != null && m.getParentId() != null && m.getParentId() != 0L)
                                ? m.getParentId()
                                : rootModuleId;
                activeParentToChildren.computeIfAbsent(pid, k -> new ArrayList<>()).add(mid);
            }
        }

        // 🌟 结构化模块树推导完成
        log.debug(
                "[QueryPlanCompiler] 结构化模块树推导完成: rootModuleId={}, involvedModuleIds={}, parentToChildren={}",
                rootModuleId,
                involvedModuleIds,
                activeParentToChildren);

        return buildInternalNode(
                rootModuleId,
                rootModuleId,
                req,
                fieldsByModule,
                filtersByModule,
                sortsByModule,
                activeParentToChildren);
    }

    private InternalQueryNode buildInternalNode(
            Long currentModuleId,
            Long rootModuleId,
            QueryReq originalReq,
            Map<Long, List<Long>> fieldsByModule,
            Map<Long, List<FilterItem>> filtersByModule,
            Map<Long, List<SortItem>> sortsByModule,
            Map<Long, List<Long>> parentToChildren) {

        List<Long> childIds = parentToChildren.get(currentModuleId);
        List<InternalQueryNode> childNodes = new ArrayList<>();
        if (childIds != null && !childIds.isEmpty()) {
            for (Long cid : childIds) {
                childNodes.add(
                        buildInternalNode(
                                cid,
                                rootModuleId,
                                originalReq,
                                fieldsByModule,
                                filtersByModule,
                                sortsByModule,
                                parentToChildren));
            }
        }

        Integer pNo = null;
        Integer pSize = null;
        if (currentModuleId.equals(rootModuleId)) {
            pNo = (originalReq instanceof PageQueryReq p) ? p.getPageNo() : 1;
            pSize =
                    (originalReq instanceof PageQueryReq p)
                            ? p.getPageSize()
                            : (originalReq instanceof DetailQueryReq ? 1 : 10);
        }

        return InternalQueryNode.builder()
                .moduleId(currentModuleId)
                .fields(fieldsByModule.get(currentModuleId))
                .filters(filtersByModule.get(currentModuleId))
                .sorts(sortsByModule.get(currentModuleId))
                .pageNo(pNo)
                .pageSize(pSize)
                .children(childNodes)
                .build();
    }

    /** 递归编译单个内部节点 */
    private QueryNodePlan compileNode(
            InternalQueryNode node,
            Long parentModuleId,
            ModuleInfo parentMeta,
            boolean isGlobalWhitelistMode,
            ProjectTableRelationsDTO projectRelations,
            ModuleTreeDTO tree) {

        Long moduleId = node.getModuleId();
        ModuleInfo meta = tree != null ? tree.getModule(moduleId) : null;
        if (meta == null) {
            throw new IllegalArgumentException("模块 ID [" + moduleId + "] 不存在或未在当前模块树中配置元数据");
        }

        String primaryTable = resolvePrimaryTable(meta);
        ParentRelationInfo parentRel =
                resolveParentRelation(primaryTable, meta, parentMeta, projectRelations, tree);
        String parentFk = parentRel.childForeignKey();
        String parentKey = parentRel.parentKey();
        boolean isVirtual = primaryTable == null || primaryTable.isBlank();

        // 1. 编译字段投影规格 (基于权限 + fields 显式白名单)
        List<PhysicalFieldSpec> allProjectedFields =
                isVirtual
                        ? Collections.emptyList()
                        : compileProjection(
                                meta, primaryTable, node.getFields(), isGlobalWhitelistMode);

        // 识别 1:N 关系从表字段并自动拆分至子节点计划
        // TODO: 引入开关配置项（如 jdec.data.query.allow-module-internal-one-to-many 或 Config Center 系统参数），
        // 控制是否允许模块内配置 1:N 从表字段。若关闭则严格校验并抛出异常，引导开发者在元数据中将 1:N 抽象为独立子模块。
        List<PhysicalFieldSpec> primaryProjectedFields = new ArrayList<>();
        Map<String, List<PhysicalFieldSpec>> oneToManyFieldsByTable = new LinkedHashMap<>();
        Map<String, TableRelationDTO> oneToManyRelationByTable = new LinkedHashMap<>();

        if (!isVirtual && projectRelations != null) {
            String lowerPrimary = primaryTable.toLowerCase();
            List<TableRelationDTO> fwdRels =
                    projectRelations.getForwardRelations() != null
                            ? projectRelations.getForwardRelations().get(lowerPrimary)
                            : null;
            if (fwdRels != null) {
                for (TableRelationDTO rel : fwdRels) {
                    if ("1:N".equalsIgnoreCase(rel.getRelationType())
                            && rel.getJoinTable() != null
                            && !rel.getJoinTable().equalsIgnoreCase(primaryTable)) {
                        oneToManyRelationByTable.put(rel.getJoinTable().trim().toLowerCase(), rel);
                    }
                }
            }
        }

        for (PhysicalFieldSpec spec : allProjectedFields) {
            String tbl =
                    spec.getTableName() != null
                            ? spec.getTableName().toLowerCase()
                            : primaryTable.toLowerCase();
            if (!tbl.equalsIgnoreCase(primaryTable) && oneToManyRelationByTable.containsKey(tbl)) {
                oneToManyFieldsByTable.computeIfAbsent(tbl, k -> new ArrayList<>()).add(spec);
            } else {
                primaryProjectedFields.add(spec);
            }
        }

        // 2. 编译当前节点自身的用户显式业务过滤条件 (以 fieldId 驱动，无业务条件则返回 null)
        Condition userFilterCondition =
                isVirtual ? null : compileUserFilters(meta, primaryTable, node.getFilters());

        // 3. 编译排序规则 (以 fieldId 驱动)
        List<OrderField<?>> orderFields =
                isVirtual
                        ? Collections.emptyList()
                        : compileSorts(meta, primaryTable, node.getSorts());

        // 4. 递归编译已有的显式子节点树
        List<QueryNodePlan> childPlans = new ArrayList<>();
        Set<String> handledTables = new HashSet<>();
        if (node.getChildren() != null && !node.getChildren().isEmpty()) {
            for (InternalQueryNode childNode : node.getChildren()) {
                if (childNode != null && childNode.getModuleId() != null) {
                    QueryNodePlan cp =
                            compileNode(
                                    childNode,
                                    moduleId,
                                    meta,
                                    isGlobalWhitelistMode,
                                    projectRelations,
                                    tree);
                    childPlans.add(cp);
                    if (cp.getPrimaryTable() != null && !cp.getPrimaryTable().isBlank()) {
                        handledTables.add(cp.getPrimaryTable().toLowerCase());
                    }
                    // 记录编译子节点完成的元数据拓扑关系
                    log.debug(
                            "[QueryPlanCompiler] 节点编译子节点完成: parentModuleId={}, childModuleId={}, childTable={}, parentFk={}",
                            moduleId,
                            cp.getModuleId(),
                            cp.getPrimaryTable(),
                            cp.getParentForeignKey());
                }
            }
        }

        // 5. 自动为同一模块内未被显式子模块覆盖的 1:N 从表生成子节点计划
        if (!isVirtual) {
            for (var entry : oneToManyFieldsByTable.entrySet()) {
                String subTable = entry.getKey();
                if (!handledTables.contains(subTable)) {
                    TableRelationDTO rel = oneToManyRelationByTable.get(subTable);
                    String fk = rel.getJoinField();
                    if (fk == null || fk.isBlank()) {
                        throw new IllegalStateException(
                                String.format(
                                        "模块 [%s] 的 1:N 从表 [%s] 未配置外键列 join_field",
                                        moduleId, rel.getJoinTable()));
                    }
                    String pKey =
                            (rel.getMainField() != null && !rel.getMainField().isBlank())
                                    ? rel.getMainField().trim()
                                    : "id";

                    QueryNodePlan subPlan =
                            QueryNodePlan.builder()
                                    .moduleId(moduleId)
                                    .moduleMeta(meta)
                                    .primaryTable(rel.getJoinTable())
                                    .parentKey(pKey)
                                    .parentForeignKey(fk)
                                    .projectedFields(entry.getValue())
                                    .condition(
                                            DSL.field(DSL.name(rel.getJoinTable(), "deleted"))
                                                    .eq((byte) 0))
                                    .orderFields(
                                            List.of(
                                                    DSL.field(DSL.name(rel.getJoinTable(), "id"))
                                                            .asc()))
                                    .children(Collections.emptyList())
                                    .build();
                    childPlans.add(subPlan);
                }
            }
        }

        // 6. 识别并编译当前主表所需伴生表 (1:1 或 N:1) 的物理 JOIN 规格 (同时涵盖投影字段、过滤字段与排序字段)
        List<JoinTableSpec> companionJoins =
                isVirtual
                        ? Collections.emptyList()
                        : resolveCompanionJoins(
                                primaryTable,
                                primaryProjectedFields,
                                node.getFilters(),
                                node.getSorts(),
                                meta,
                                projectRelations);

        // 7. 第一性原理：自底向上汇聚子孙模块过滤约束 (Semi-Join / EXISTS 链式上卷，穿透虚拟中间层)
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
                        // 同物理表 (垂直拆分模块，如 101 与 105)：直接 AND 合并
                        rollupConditions.add(childPlan.getEffectiveFilterCondition());
                    } else if (childTable != null && childPlan.getParentForeignKey() != null) {
                        // 1:N 从表：以 Semi-Join (EXISTS) 链式上卷
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

        // 8. 复合当前节点的有效业务筛选条件
        Condition effectiveFilterCondition = null;
        if (userFilterCondition != null && !rollupConditions.isEmpty()) {
            effectiveFilterCondition = userFilterCondition.and(DSL.and(rollupConditions));
        } else if (userFilterCondition != null) {
            effectiveFilterCondition = userFilterCondition;
        } else if (!rollupConditions.isEmpty()) {
            effectiveFilterCondition = DSL.and(rollupConditions);
        }

        // 9. 结合软删除与租户隔离等基础底座条件，计算节点执行 SQL 时的完整 Condition
        Condition baseCondition = isVirtual ? null : compileBaseConditions(meta, primaryTable);
        Condition nodeCondition = null;
        if (baseCondition != null) {
            nodeCondition =
                    effectiveFilterCondition != null
                            ? baseCondition.and(effectiveFilterCondition)
                            : baseCondition;
        } else {
            nodeCondition = effectiveFilterCondition;
        }

        return QueryNodePlan.builder()
                .moduleId(moduleId)
                .moduleMeta(meta)
                .primaryTable(primaryTable)
                .parentKey(parentKey)
                .parentForeignKey(parentFk)
                .parentRelationType(
                        parentRel.relationType() != null ? parentRel.relationType() : "1:N")
                .pageNo(node.getPageNo())
                .pageSize(node.getPageSize())
                .projectedFields(primaryProjectedFields)
                .condition(nodeCondition)
                .effectiveFilterCondition(effectiveFilterCondition)
                .orderFields(orderFields)
                .companionJoins(companionJoins)
                .children(childPlans)
                .build();
    }

    /** 基于 sys_table_relation 单向定义推导伴生表物理 JOIN 规格 (兼顾投影字段、过滤字段与排序字段) */
    private List<JoinTableSpec> resolveCompanionJoins(
            String primaryTable,
            List<PhysicalFieldSpec> projectedFields,
            List<FilterItem> filters,
            List<SortItem> sorts,
            ModuleInfo meta,
            ProjectTableRelationsDTO projectRelations) {
        if (primaryTable == null || primaryTable.isBlank() || meta == null) {
            return Collections.emptyList();
        }

        Set<String> neededCompanionTables = new LinkedHashSet<>();
        if (projectedFields != null) {
            for (PhysicalFieldSpec spec : projectedFields) {
                if (spec != null && spec.getTableName() != null) {
                    String tbl = spec.getTableName().trim();
                    if (!tbl.equalsIgnoreCase(primaryTable)) {
                        neededCompanionTables.add(tbl.toLowerCase());
                    }
                }
            }
        }

        Map<Long, String> fieldTableMap = null;
        if (meta.getFields() != null) {
            fieldTableMap = new HashMap<>();
            for (ModuleFieldDTO f : meta.getFields()) {
                if (f != null && f.getId() != null && f.getTableName() != null) {
                    fieldTableMap.put(f.getId(), f.getTableName().trim().toLowerCase());
                }
            }
        }

        // 遍历当前节点过滤条件中引用的字段所涉及的物理表 (支持 options 或过滤字段未投影场景)
        if (filters != null && fieldTableMap != null) {
            for (FilterItem filter : filters) {
                if (filter != null && filter.getFieldId() != null) {
                    String tbl = fieldTableMap.get(filter.getFieldId());
                    if (tbl != null && !tbl.equalsIgnoreCase(primaryTable)) {
                        neededCompanionTables.add(tbl);
                    }
                }
            }
        }

        // 遍历当前节点排序规则中引用的字段所涉及的物理表 (支持排序字段未投影场景)
        if (sorts != null && fieldTableMap != null) {
            for (SortItem sort : sorts) {
                if (sort != null && sort.getFieldId() != null) {
                    String tbl = fieldTableMap.get(sort.getFieldId());
                    if (tbl != null && !tbl.equalsIgnoreCase(primaryTable)) {
                        neededCompanionTables.add(tbl);
                    }
                }
            }
        }

        if (neededCompanionTables.isEmpty()) {
            return Collections.emptyList();
        }

        List<JoinTableSpec> joinSpecs = new ArrayList<>();
        for (String compTable : neededCompanionTables) {
            TableRelationDTO matchedRel = null;
            boolean isForward = false; // primary -> compTable

            if (projectRelations != null) {
                // 正向关系: main_table(主表) -> join_table(伴生表) 如 student -> student_profile (1:1)
                matchedRel = projectRelations.findDirectRelation(primaryTable, compTable);
                if (matchedRel != null) {
                    isForward = true;
                } else {
                    // 反向伴生关系: main_table(伴生表) -> join_table(主表) 如 clazz -> student (1:N 视角下的 N:1
                    // 归属)
                    matchedRel = projectRelations.findDirectRelation(compTable, primaryTable);
                    if (matchedRel != null) {
                        isForward = false;
                    }
                }
            }

            if (matchedRel == null) {
                throw new IllegalStateException(
                        String.format(
                                "元数据关联关系未自洽：在模块 [%s] 的 sys_table_relation 中未找到主表 [%s] 与伴生表 [%s] 的关联定义",
                                meta.getModuleId(), primaryTable, compTable));
            }

            if (isForward) {
                joinSpecs.add(
                        JoinTableSpec.builder()
                                .targetTable(compTable)
                                .targetField(
                                        matchedRel.getJoinField() != null
                                                        && !matchedRel.getJoinField().isBlank()
                                                ? matchedRel.getJoinField().trim()
                                                : "id")
                                .sourceTable(primaryTable)
                                .sourceField(
                                        matchedRel.getMainField() != null
                                                        && !matchedRel.getMainField().isBlank()
                                                ? matchedRel.getMainField().trim()
                                                : "id")
                                .relationType(
                                        matchedRel.getRelationType() != null
                                                        && !matchedRel.getRelationType().isBlank()
                                                ? matchedRel.getRelationType().trim()
                                                : "1:1")
                                .build());
            } else {
                joinSpecs.add(
                        JoinTableSpec.builder()
                                .targetTable(compTable)
                                .targetField(
                                        matchedRel.getMainField() != null
                                                        && !matchedRel.getMainField().isBlank()
                                                ? matchedRel.getMainField().trim()
                                                : "id")
                                .sourceTable(primaryTable)
                                .sourceField(
                                        matchedRel.getJoinField() != null
                                                        && !matchedRel.getJoinField().isBlank()
                                                ? matchedRel.getJoinField().trim()
                                                : "id")
                                .relationType(
                                        matchedRel.getRelationType() != null
                                                        && !matchedRel.getRelationType().isBlank()
                                                ? matchedRel.getRelationType().trim()
                                                : "N:1")
                                .build());
            }
        }
        return joinSpecs;
    }

    public String resolvePrimaryTable(ModuleInfo meta) {
        if (meta == null) {
            return "";
        }
        if (meta.getPrimaryTable() != null && !meta.getPrimaryTable().isBlank()) {
            return meta.getPrimaryTable().trim();
        }
        if (meta.getFields() != null && !meta.getFields().isEmpty()) {
            for (ModuleFieldDTO f : meta.getFields()) {
                if (f.getTableName() != null && !f.getTableName().isBlank()) {
                    return f.getTableName().trim();
                }
            }
        }
        return "";
    }

    public record ParentRelationInfo(
            String parentKey,
            String childForeignKey,
            String parentPrimaryTable,
            String relationType) {}

    /** 沿着模块树向上回溯查找最近的一个配置了有效物理主表的父/祖先模块，同时收集沿途的中间虚拟模块 */
    private ModuleInfo findNearestPhysicalParentMeta(
            ModuleInfo currentMeta, List<ModuleInfo> intermediateCollector, ModuleTreeDTO tree) {
        if (currentMeta == null) {
            return null;
        }
        Long curParentId = currentMeta.getParentId();
        Set<Long> visited = new HashSet<>();
        while (curParentId != null && curParentId > 0 && visited.add(curParentId)) {
            ModuleInfo pMeta = tree != null ? tree.getModule(curParentId) : null;
            if (pMeta == null) {
                break;
            }
            String pTable = resolvePrimaryTable(pMeta);
            if (pTable != null && !pTable.isBlank()) {
                return pMeta;
            }
            if (intermediateCollector != null) {
                intermediateCollector.add(pMeta);
            }
            curParentId = pMeta.getParentId();
        }
        return null;
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
        String parentPrimary = resolvePrimaryTable(physicalParentMeta);
        List<ModuleInfo> intermediateMetas = new ArrayList<>();

        if (parentPrimary == null || parentPrimary.isBlank()) {
            intermediateMetas.add(parentMeta);
            physicalParentMeta = findNearestPhysicalParentMeta(parentMeta, intermediateMetas, tree);
            if (physicalParentMeta != null) {
                parentPrimary = resolvePrimaryTable(physicalParentMeta);
            }
        }

        // 向上回溯到顶层仍未找到任何配置物理主表的父模块 (如根模块即为纯虚拟模块)
        if (physicalParentMeta == null || parentPrimary == null || parentPrimary.isBlank()) {
            return new ParentRelationInfo("id", null, null, "1:N");
        }

        // 2. 特殊情况：当子模块物理主表与物理父模块主表为同一物理表时 (垂直拆分视图)，外键即为自身主键 id
        if (parentPrimary.equalsIgnoreCase(childPrimaryTable)) {
            return new ParentRelationInfo("id", "id", parentPrimary, "1:1");
        }

        // 3. 匹配单向关联: mainTable == parentPrimary && joinTable == childPrimaryTable
        TableRelationDTO matchedRel = null;
        if (projectRelations != null) {
            matchedRel = projectRelations.findDirectRelation(parentPrimary, childPrimaryTable);
        }

        if (matchedRel != null) {
            if (matchedRel.getJoinField() != null && !matchedRel.getJoinField().isBlank()) {
                String pKey = "id";
                if (matchedRel.getMainField() != null && !matchedRel.getMainField().isBlank()) {
                    pKey = matchedRel.getMainField().trim();
                }
                String relType =
                        (matchedRel.getRelationType() != null
                                        && !matchedRel.getRelationType().isBlank())
                                ? matchedRel.getRelationType().trim()
                                : "1:N";
                return new ParentRelationInfo(
                        pKey, matchedRel.getJoinField().trim(), parentPrimary, relType);
            }
        }

        // 4. 严格自洽校验：杜绝任何未配置关系的隐式推测
        Long parentModId = physicalParentMeta.getModuleId();
        Long childModId = childMeta != null ? childMeta.getModuleId() : null;
        throw new IllegalStateException(
                String.format(
                        "元数据关联关系未自洽：未在 sys_table_relation 中找到物理父表 [%s](模块 %s) 到子表 [%s](模块 %s) 的单向关联定义(main_table -> join_table)",
                        parentPrimary, parentModId, childPrimaryTable, childModId));
    }

    /** 编译字段投影规格 */
    private List<PhysicalFieldSpec> compileProjection(
            ModuleInfo meta,
            String primaryTable,
            List<Long> fieldIds,
            boolean isGlobalWhitelistMode) {
        List<PhysicalFieldSpec> specs = new ArrayList<>();
        List<ModuleFieldDTO> allFields = meta.getFields();
        if (allFields == null || allFields.isEmpty()) {
            return specs;
        }

        Map<Long, ModuleFieldDTO> fieldMap = new HashMap<>();
        for (ModuleFieldDTO f : allFields) {
            if (f.getId() != null) {
                fieldMap.put(f.getId(), f);
            }
        }

        if (fieldIds != null && !fieldIds.isEmpty()) {
            for (Long fid : fieldIds) {
                ModuleFieldDTO f = fieldMap.get(fid);
                if (f != null) {
                    specs.add(toPhysicalFieldSpec(f, primaryTable));
                }
            }
        } else if (isGlobalWhitelistMode) {
            // 🌟 白名单公理：全局处于显式白名单模式时，未被分配到字段的模块严格为空，绝不推测全选
            return Collections.emptyList();
        } else {
            // 默认投影当前模块全部可见字段
            for (ModuleFieldDTO f : allFields) {
                specs.add(toPhysicalFieldSpec(f, primaryTable));
            }
        }
        return specs;
    }

    private PhysicalFieldSpec toPhysicalFieldSpec(ModuleFieldDTO f, String defaultTable) {
        String tbl =
                (f.getTableName() != null && !f.getTableName().isBlank())
                        ? f.getTableName()
                        : defaultTable;
        return PhysicalFieldSpec.builder()
                .fieldId(f.getId())
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

    /** 编译用户显式业务过滤条件为安全 Condition (以 fieldId 为唯一依据，无业务条件返回 null) */
    private Condition compileUserFilters(
            ModuleInfo meta, String primaryTable, List<FilterItem> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }

        Map<Long, ModuleFieldDTO> fieldIdMap = new HashMap<>();
        if (meta.getFields() != null) {
            for (ModuleFieldDTO f : meta.getFields()) {
                if (f.getId() != null) {
                    fieldIdMap.put(f.getId(), f);
                }
            }
        }

        List<Condition> conditions = new ArrayList<>();
        for (FilterItem item : filters) {
            Condition cond = compileSingleFilterItem(item, fieldIdMap, primaryTable);
            if (cond != null && !DSL.noCondition().equals(cond)) {
                conditions.add(cond);
            }
        }

        return conditions.isEmpty() ? null : DSL.and(conditions);
    }

    /** 递归编译单个 FilterItem (支持叶子单字段条件和复合条件组) */
    private Condition compileSingleFilterItem(
            FilterItem item, Map<Long, ModuleFieldDTO> fieldIdMap, String primaryTable) {
        if (item == null) {
            return null;
        }

        // 1. 如果包含 children，作为条件组处理 (AND / OR)
        if (item.getChildren() != null && !item.getChildren().isEmpty()) {
            List<Condition> subConditions = new ArrayList<>();
            for (FilterItem child : item.getChildren()) {
                Condition subCond = compileSingleFilterItem(child, fieldIdMap, primaryTable);
                if (subCond != null && !DSL.noCondition().equals(subCond)) {
                    subConditions.add(subCond);
                }
            }
            if (subConditions.isEmpty()) {
                return null;
            }
            String logic =
                    (item.getLogic() != null && !item.getLogic().isBlank())
                            ? item.getLogic().toUpperCase().trim()
                            : "AND";
            return "OR".equals(logic) ? DSL.or(subConditions) : DSL.and(subConditions);
        }

        // 2. 单字段条件处理
        if (item.getValue() == null || "".equals(item.getValue())) {
            return null;
        }
        if (item.getFieldId() == null) {
            return null;
        }

        ModuleFieldDTO targetField = fieldIdMap.get(item.getFieldId());
        if (targetField == null) {
            return null;
        }

        String tbl =
                (targetField.getTableName() != null && !targetField.getTableName().isBlank())
                        ? targetField.getTableName()
                        : primaryTable;
        String col = targetField.getColumnName();
        if (col == null || col.isBlank()) {
            return null;
        }

        return buildSqlCondition(tbl, col, item.getOperator(), item.getValue());
    }

    /** 递归收集过滤条件树中引用的所有 fieldId */
    private void collectFieldIdsFromFilters(List<FilterItem> filters, Set<Long> fieldIdSet) {
        if (filters == null || filters.isEmpty() || fieldIdSet == null) {
            return;
        }
        for (FilterItem f : filters) {
            if (f == null) {
                continue;
            }
            if (f.getFieldId() != null) {
                fieldIdSet.add(f.getFieldId());
            }
            if (f.getChildren() != null && !f.getChildren().isEmpty()) {
                collectFieldIdsFromFilters(f.getChildren(), fieldIdSet);
            }
        }
    }

    /** 根据操作符生成具体的 jOOQ Condition */
    private Condition buildSqlCondition(
            String tableName, String columnName, String operator, Object value) {
        Field<Object> field = DSL.field(DSL.name(tableName, columnName));
        String op =
                (operator != null && !operator.isBlank()) ? operator.toUpperCase().trim() : "LIKE";

        return switch (op) {
            case "EQ" -> field.eq(value);
            case "NEQ" -> field.ne(value);
            case "LIKE" -> field.like("%" + value + "%");
            case "GT" -> field.gt(value);
            case "GTE" -> field.ge(value);
            case "LT" -> field.lt(value);
            case "LTE" -> field.le(value);
            case "IS_NULL" -> field.isNull();
            case "IS_NOT_NULL" -> field.isNotNull();
            case "CONTAINS", "JSON_CONTAINS", "CONTAINS_ANY" -> {
                // 包含任一元素: value 可以是单个字符串/数值，也可以是集合
                if (value instanceof Collection<?> coll) {
                    List<Condition> orList = new ArrayList<>();
                    for (Object item : coll) {
                        if (item != null) {
                            String itemJson = formatJsonValue(item);
                            orList.add(
                                    DSL.condition(
                                            "JSON_CONTAINS({0}, {1})", field, DSL.val(itemJson)));
                        }
                    }
                    yield orList.isEmpty() ? DSL.noCondition() : DSL.or(orList);
                } else if (value instanceof Object[] arr) {
                    List<Condition> orList = new ArrayList<>();
                    for (Object item : arr) {
                        if (item != null) {
                            String itemJson = formatJsonValue(item);
                            orList.add(
                                    DSL.condition(
                                            "JSON_CONTAINS({0}, {1})", field, DSL.val(itemJson)));
                        }
                    }
                    yield orList.isEmpty() ? DSL.noCondition() : DSL.or(orList);
                } else {
                    String itemJson = formatJsonValue(value);
                    yield DSL.condition("JSON_CONTAINS({0}, {1})", field, DSL.val(itemJson));
                }
            }
            case "CONTAINS_ALL", "JSON_CONTAINS_ALL" -> {
                // 包含全部元素: value 集合中的所有元素都必须存在
                if (value instanceof Collection<?> coll) {
                    List<Condition> andList = new ArrayList<>();
                    for (Object item : coll) {
                        if (item != null) {
                            String itemJson = formatJsonValue(item);
                            andList.add(
                                    DSL.condition(
                                            "JSON_CONTAINS({0}, {1})", field, DSL.val(itemJson)));
                        }
                    }
                    yield andList.isEmpty() ? DSL.noCondition() : DSL.and(andList);
                } else if (value instanceof Object[] arr) {
                    List<Condition> andList = new ArrayList<>();
                    for (Object item : arr) {
                        if (item != null) {
                            String itemJson = formatJsonValue(item);
                            andList.add(
                                    DSL.condition(
                                            "JSON_CONTAINS({0}, {1})", field, DSL.val(itemJson)));
                        }
                    }
                    yield andList.isEmpty() ? DSL.noCondition() : DSL.and(andList);
                } else {
                    String itemJson = formatJsonValue(value);
                    yield DSL.condition("JSON_CONTAINS({0}, {1})", field, DSL.val(itemJson));
                }
            }
            case "IN" -> {
                if (value instanceof Collection<?> coll) {
                    yield field.in(coll);
                } else if (value instanceof Object[] arr) {
                    yield field.in(arr);
                } else {
                    yield field.eq(value);
                }
            }
            case "BETWEEN", "RANGE" -> {
                if (value instanceof Collection<?> coll && coll.size() >= 2) {
                    Iterator<?> it = coll.iterator();
                    Object start = it.next();
                    Object end = it.next();
                    yield field.between(start, end);
                } else if (value instanceof Object[] arr && arr.length >= 2) {
                    yield field.between(arr[0], arr[1]);
                } else {
                    yield field.eq(value);
                }
            }
            default -> field.like("%" + value + "%");
        };
    }

    /** 辅助将 Java 值格式化为适合 JSON_CONTAINS 匹配的目标 JSON 标量字符串 (如 "客户" -> "\"客户\"", 123 -> "123") */
    private String formatJsonValue(Object val) {
        if (val == null) {
            return "null";
        }
        if (val instanceof Number || val instanceof Boolean) {
            return String.valueOf(val);
        }
        String str = String.valueOf(val).trim();
        if ((str.startsWith("{") && str.endsWith("}"))
                || (str.startsWith("[") && str.endsWith("]"))) {
            return str;
        }
        if (str.startsWith("\"") && str.endsWith("\"")) {
            return str;
        }
        return "\"" + str + "\"";
    }

    /** 编译排序规则列表 (以 fieldId 为唯一依据) */
    private List<OrderField<?>> compileSorts(
            ModuleInfo meta, String primaryTable, List<SortItem> sorts) {
        List<OrderField<?>> orderFields = new ArrayList<>();
        if (sorts == null || sorts.isEmpty()) {
            orderFields.add(DSL.field(DSL.name(primaryTable, "id")).desc());
            return orderFields;
        }

        Map<Long, ModuleFieldDTO> fieldIdMap = new HashMap<>();
        if (meta.getFields() != null) {
            for (ModuleFieldDTO f : meta.getFields()) {
                if (f.getId() != null) {
                    fieldIdMap.put(f.getId(), f);
                }
            }
        }

        for (SortItem item : sorts) {
            if (item == null || item.getFieldId() == null) {
                continue;
            }
            ModuleFieldDTO targetField = fieldIdMap.get(item.getFieldId());
            if (targetField == null) {
                continue;
            }

            String tbl =
                    (targetField.getTableName() != null && !targetField.getTableName().isBlank())
                            ? targetField.getTableName()
                            : primaryTable;
            String col = targetField.getColumnName();
            if (col == null || col.isBlank()) {
                continue;
            }

            Field<Object> field = DSL.field(DSL.name(tbl, col));
            orderFields.add(
                    "DESC".equalsIgnoreCase(item.getDirection()) ? field.desc() : field.asc());
        }

        if (orderFields.isEmpty()) {
            orderFields.add(DSL.field(DSL.name(primaryTable, "id")).desc());
        }
        return orderFields;
    }

    private ModuleTreeDTO loadModuleTree(Long rootModuleId) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        return configApi.getModuleTree(projectNo, subjectId, rootModuleId);
    }

    private ProjectTableRelationsDTO getProjectRelations() {
        String projectNo = AppContext.getProjectNo();
        return configApi.getProjectTableRelations(projectNo);
    }
}
