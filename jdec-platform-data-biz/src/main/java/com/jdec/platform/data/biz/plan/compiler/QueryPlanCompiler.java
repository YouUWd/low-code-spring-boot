package com.jdec.platform.data.biz.plan.compiler;

import com.jdec.platform.data.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.data.api.dto.common.TableRelationDTO;
import com.jdec.platform.data.api.dto.model.SysModuleInfo;
import com.jdec.platform.data.api.dto.request.DynamicFilterItem;
import com.jdec.platform.data.api.dto.request.DynamicOptionReq;
import com.jdec.platform.data.api.dto.request.DynamicQueryReq;
import com.jdec.platform.data.api.dto.request.DynamicSortItem;
import com.jdec.platform.data.biz.plan.model.JoinTableSpec;
import com.jdec.platform.data.biz.plan.model.PhysicalFieldSpec;
import com.jdec.platform.data.biz.plan.model.QueryNodePlan;
import com.jdec.platform.data.biz.plan.model.QueryPlan;
import com.jdec.platform.data.biz.service.MetaCacheService;
import com.jdec.platform.data.biz.service.PermissionFilterService;
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

    private final MetaCacheService metaCacheService;
    private final PermissionFilterService permissionFilterService;

    /** 内部编译树节点载体 (隔离外部 DTO 与编译期自相似树结构) */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class InternalQueryNode {
        private Long moduleId;
        private List<Long> fields;
        private List<DynamicFilterItem> filters;
        private List<DynamicSortItem> sorts;
        private Integer pageNo;
        private Integer pageSize;
        private List<InternalQueryNode> children;
    }

    /** 编译候选项查询请求为单字段投影与全量过滤上下文的 QueryPlan */
    public QueryPlan compile(DynamicOptionReq req) {
        if (req == null) {
            throw new IllegalArgumentException("候选项请求不能为空");
        }
        if (req.getModuleId() == null || req.getFieldId() == null) {
            throw new IllegalArgumentException("候选项请求的模块 ID 与字段 ID 均不能为空");
        }

        DynamicQueryReq queryReq =
                DynamicQueryReq.builder()
                        .moduleId(req.getModuleId())
                        .fields(List.of(req.getFieldId()))
                        .filters(req.getFilters())
                        .build();
        return compile(queryReq);
    }

    /** 编译顶层查询请求为完整的 QueryPlan (以根 moduleId 与 fields 驱动，自动校验子树范围并推导执行树) */
    public QueryPlan compile(DynamicQueryReq req) {
        if (req == null) {
            throw new IllegalArgumentException("查询请求不能为空");
        }
        Long rootModuleId = req.getModuleId();
        if (rootModuleId == null) {
            throw new IllegalArgumentException("查询主模块 ID 不能为空");
        }

        SysModuleInfo rootMeta = metaCacheService.getModuleComplete(rootModuleId);
        if (rootMeta == null) {
            throw new IllegalArgumentException("模块 ID [" + rootModuleId + "] 不存在或未配置元数据");
        }

        boolean isGlobalWhitelistMode = req.getFields() != null && !req.getFields().isEmpty();

        // 1. 递归收集当前根模块及其所有子孙模块的合法模块 ID 闭包
        Set<Long> allowedModuleIds = new HashSet<>();
        allowedModuleIds.add(rootModuleId);
        if (rootMeta.getModuleNodes() != null) {
            for (var node : rootMeta.getModuleNodes()) {
                if (node != null && node.getId() != null) {
                    allowedModuleIds.add(node.getId());
                }
            }
        }

        // 2. 收集请求中出现的所有 fieldId (包括 fields, filters, sorts)
        Set<Long> allFieldIds = new LinkedHashSet<>();
        if (req.getFields() != null) {
            allFieldIds.addAll(req.getFields());
        }
        if (req.getFilters() != null) {
            for (var f : req.getFilters()) {
                if (f != null && f.getFieldId() != null) {
                    allFieldIds.add(f.getFieldId());
                }
            }
        }
        if (req.getSorts() != null) {
            for (var s : req.getSorts()) {
                if (s != null && s.getFieldId() != null) {
                    allFieldIds.add(s.getFieldId());
                }
            }
        }

        // 3. 严格校验字段元数据与子树归属作用域 (Scope Guard)
        List<ModuleFieldDTO> fieldDTOs = Collections.emptyList();
        if (!allFieldIds.isEmpty()) {
            fieldDTOs = metaCacheService.listFieldsByIds(new ArrayList<>(allFieldIds));
            if (fieldDTOs == null) {
                fieldDTOs = Collections.emptyList();
            }
            Map<Long, ModuleFieldDTO> fieldIdMap = new HashMap<>();
            for (ModuleFieldDTO f : fieldDTOs) {
                if (f != null && f.getId() != null) {
                    fieldIdMap.put(f.getId(), f);
                }
            }
            for (Long fid : allFieldIds) {
                ModuleFieldDTO f = fieldIdMap.get(fid);
                if (f == null) {
                    throw new IllegalArgumentException("未查询到指定字段的元数据定义: " + fid);
                }
                Long fModuleId = f.getModuleId();
                if (fModuleId != null
                        && !isDescendantOrSelf(fModuleId, rootModuleId, allowedModuleIds)) {
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
            }
        }

        // 4. 自动推导并构建内部结构化树
        InternalQueryNode structuredTree =
                buildInternalQueryTree(req, rootMeta, allFieldIds, fieldDTOs);
        QueryNodePlan rootPlan = compileNode(structuredTree, null, null, isGlobalWhitelistMode);
        return QueryPlan.builder().rootNodePlan(rootPlan).build();
    }

    private boolean isDescendantOrSelf(
            Long targetModuleId, Long rootModuleId, Set<Long> allowedModuleIds) {
        if (targetModuleId == null) {
            return false;
        }
        if (targetModuleId.equals(rootModuleId) || allowedModuleIds.contains(targetModuleId)) {
            return true;
        }
        Long cur = targetModuleId;
        Set<Long> visited = new HashSet<>();
        while (cur != null && cur > 0 && visited.add(cur)) {
            if (cur.equals(rootModuleId)) {
                allowedModuleIds.add(targetModuleId);
                return true;
            }
            SysModuleInfo m = metaCacheService.getModuleComplete(cur);
            cur = (m != null) ? m.getParentId() : null;
        }
        return false;
    }

    /** 根据平铺的 fields/filters/sorts 与 config-engine 拓扑，自动推导并组装内部结构化查询请求树 */
    private InternalQueryNode buildInternalQueryTree(
            DynamicQueryReq req,
            SysModuleInfo rootMeta,
            Set<Long> allFieldIds,
            List<ModuleFieldDTO> fieldDTOs) {

        Long rootModuleId = req.getModuleId();
        if (allFieldIds.isEmpty()) {
            return InternalQueryNode.builder()
                    .moduleId(rootModuleId)
                    .fields(req.getFields())
                    .filters(req.getFilters())
                    .sorts(req.getSorts())
                    .pageNo(req.getPageNo())
                    .pageSize(req.getPageSize())
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
                    .pageNo(req.getPageNo())
                    .pageSize(req.getPageSize())
                    .children(Collections.emptyList())
                    .build();
        }

        Map<Long, SysModuleInfo> metaMap = new HashMap<>();
        metaMap.put(rootModuleId, rootMeta);
        for (Long mid : involvedModuleIds) {
            SysModuleInfo m = metaCacheService.getModuleComplete(mid);
            if (m != null) {
                metaMap.put(mid, m);
            }
        }

        // 向上回溯补齐模块树祖先链路，确保孙模块与所有中间节点完整纳入闭包 (Transitive Closure)
        Set<Long> fullModuleIds = new LinkedHashSet<>(involvedModuleIds);
        for (Long mid : involvedModuleIds) {
            Long current = mid;
            Set<Long> visited = new HashSet<>();
            while (current != null && visited.add(current)) {
                SysModuleInfo m =
                        metaMap.computeIfAbsent(current, metaCacheService::getModuleComplete);
                if (m != null && m.getParentId() != null) {
                    Long pid = m.getParentId();
                    if (pid != 0L) {
                        fullModuleIds.add(pid);
                        if (pid.equals(rootModuleId)) {
                            break;
                        }
                        current = pid;
                    } else {
                        break;
                    }
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

        Map<Long, List<DynamicFilterItem>> filtersByModule = new HashMap<>();
        if (req.getFilters() != null) {
            for (var filter : req.getFilters()) {
                Long mid =
                        filter.getFieldId() != null
                                ? fieldToModule.getOrDefault(filter.getFieldId(), rootModuleId)
                                : rootModuleId;
                filtersByModule.computeIfAbsent(mid, k -> new ArrayList<>()).add(filter);
            }
        }

        Map<Long, List<DynamicSortItem>> sortsByModule = new HashMap<>();
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
        Map<Long, List<Long>> parentToChildren = new HashMap<>();
        for (Long mid : involvedModuleIds) {
            if (!mid.equals(rootModuleId)) {
                SysModuleInfo m = metaMap.computeIfAbsent(mid, metaCacheService::getModuleComplete);
                Long pid =
                        (m != null && m.getParentId() != null && m.getParentId() != 0L)
                                ? m.getParentId()
                                : rootModuleId;
                parentToChildren.computeIfAbsent(pid, k -> new ArrayList<>()).add(mid);
            }
        }

        return buildInternalNode(
                rootModuleId,
                rootModuleId,
                req,
                fieldsByModule,
                filtersByModule,
                sortsByModule,
                parentToChildren);
    }

    private InternalQueryNode buildInternalNode(
            Long currentModuleId,
            Long rootModuleId,
            DynamicQueryReq originalReq,
            Map<Long, List<Long>> fieldsByModule,
            Map<Long, List<DynamicFilterItem>> filtersByModule,
            Map<Long, List<DynamicSortItem>> sortsByModule,
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

        Integer pNo = currentModuleId.equals(rootModuleId) ? originalReq.getPageNo() : null;
        Integer pSize = currentModuleId.equals(rootModuleId) ? originalReq.getPageSize() : null;

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
            SysModuleInfo parentMeta,
            boolean isGlobalWhitelistMode) {

        Long moduleId = node.getModuleId();
        SysModuleInfo meta = metaCacheService.getModuleComplete(moduleId);
        if (meta == null) {
            throw new IllegalArgumentException("模块 ID [" + moduleId + "] 不存在或未配置元数据");
        }

        String primaryTable = resolvePrimaryTable(meta);
        ParentRelationInfo parentRel = resolveParentRelation(primaryTable, meta, parentMeta);
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
        List<PhysicalFieldSpec> primaryProjectedFields = new ArrayList<>();
        Map<String, List<PhysicalFieldSpec>> oneToManyFieldsByTable = new LinkedHashMap<>();
        Map<String, TableRelationDTO> oneToManyRelationByTable = new LinkedHashMap<>();

        if (!isVirtual && meta.getTableRelations() != null) {
            for (TableRelationDTO rel : meta.getTableRelations()) {
                if ("1:N".equalsIgnoreCase(rel.getRelationType())
                        && rel.getJoinTable() != null
                        && !rel.getJoinTable().equalsIgnoreCase(primaryTable)) {
                    oneToManyRelationByTable.put(rel.getJoinTable().toLowerCase(), rel);
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
                            compileNode(childNode, moduleId, meta, isGlobalWhitelistMode);
                    childPlans.add(cp);
                    if (cp.getPrimaryTable() != null && !cp.getPrimaryTable().isBlank()) {
                        handledTables.add(cp.getPrimaryTable().toLowerCase());
                    }
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

        // 6. 识别并编译当前主表所需伴生表 (1:1 或 N:1) 的物理 JOIN 规格
        List<JoinTableSpec> companionJoins =
                isVirtual
                        ? Collections.emptyList()
                        : resolveCompanionJoins(primaryTable, primaryProjectedFields, meta);

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

    /** 基于 sys_table_relation 单向定义推导伴生表物理 JOIN 规格 */
    private List<JoinTableSpec> resolveCompanionJoins(
            String primaryTable, List<PhysicalFieldSpec> projectedFields, SysModuleInfo meta) {
        if (primaryTable == null
                || primaryTable.isBlank()
                || meta == null
                || projectedFields == null) {
            return Collections.emptyList();
        }

        Set<String> neededCompanionTables = new LinkedHashSet<>();
        for (PhysicalFieldSpec spec : projectedFields) {
            if (spec != null && spec.getTableName() != null) {
                String tbl = spec.getTableName().trim();
                if (!tbl.equalsIgnoreCase(primaryTable)) {
                    neededCompanionTables.add(tbl.toLowerCase());
                }
            }
        }

        if (neededCompanionTables.isEmpty()) {
            return Collections.emptyList();
        }

        List<TableRelationDTO> relations = meta.getTableRelations();
        if (relations == null || relations.isEmpty()) {
            throw new IllegalStateException(
                    String.format(
                            "模块 [%s] 投影了伴生表字段 %s，但在 sys_table_relation 中未配置任何表关联元数据",
                            meta.getModuleId(), neededCompanionTables));
        }

        List<JoinTableSpec> joinSpecs = new ArrayList<>();
        for (String compTable : neededCompanionTables) {
            TableRelationDTO matchedRel = null;
            boolean isForward = false; // primary -> compTable

            for (TableRelationDTO rel : relations) {
                if (rel == null || rel.getMainTable() == null || rel.getJoinTable() == null) {
                    continue;
                }
                // 正向关系: main_table(主表) -> join_table(伴生表) 如 student -> student_profile (1:1)
                if (primaryTable.equalsIgnoreCase(rel.getMainTable())
                        && compTable.equalsIgnoreCase(rel.getJoinTable())) {
                    matchedRel = rel;
                    isForward = true;
                    break;
                }
                // 反向伴生关系: main_table(伴生表) -> join_table(主表) 如 clazz -> student (1:N 视角下的 N:1 归属)
                if (compTable.equalsIgnoreCase(rel.getMainTable())
                        && primaryTable.equalsIgnoreCase(rel.getJoinTable())) {
                    matchedRel = rel;
                    isForward = false;
                    break;
                }
            }

            if (matchedRel == null) {
                throw new IllegalStateException(
                        String.format(
                                "元数据关联关系未自洽：在模块 [%s] 的 sys_table_relation 中未找到主表 [%s] 与伴生表 [%s] 的关联定义",
                                meta.getModuleId(), primaryTable, compTable));
            }

            if (isForward) {
                // student.id = student_profile.student_id
                joinSpecs.add(
                        JoinTableSpec.builder()
                                .sourceTable(primaryTable)
                                .sourceField(
                                        matchedRel.getMainField() != null
                                                        && !matchedRel.getMainField().isBlank()
                                                ? matchedRel.getMainField()
                                                : "id")
                                .targetTable(matchedRel.getJoinTable())
                                .targetField(matchedRel.getJoinField())
                                .relationType(matchedRel.getRelationType())
                                .build());
            } else {
                // student.clazz_id = clazz.id
                joinSpecs.add(
                        JoinTableSpec.builder()
                                .sourceTable(primaryTable)
                                .sourceField(matchedRel.getJoinField())
                                .targetTable(matchedRel.getMainTable())
                                .targetField(
                                        matchedRel.getMainField() != null
                                                        && !matchedRel.getMainField().isBlank()
                                                ? matchedRel.getMainField()
                                                : "id")
                                .relationType(matchedRel.getRelationType())
                                .build());
            }
        }

        return joinSpecs;
    }

    /** 解析模块物理主表名 */
    public String resolvePrimaryTable(SysModuleInfo meta) {
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
            String parentKey, String childForeignKey, String parentPrimaryTable) {}

    /** 沿着模块树向上回溯查找最近的一个配置了有效物理主表的父/祖先模块，同时收集沿途的中间虚拟模块 */
    private SysModuleInfo findNearestPhysicalParentMeta(
            SysModuleInfo currentMeta, List<SysModuleInfo> intermediateCollector) {
        if (currentMeta == null) {
            return null;
        }
        Long curParentId = currentMeta.getParentId();
        Set<Long> visited = new HashSet<>();
        while (curParentId != null && curParentId > 0 && visited.add(curParentId)) {
            SysModuleInfo pMeta = metaCacheService.getModuleComplete(curParentId);
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
            String childPrimaryTable, SysModuleInfo childMeta, SysModuleInfo parentMeta) {
        if (childPrimaryTable == null || childPrimaryTable.isBlank() || parentMeta == null) {
            return new ParentRelationInfo("id", null, null);
        }

        // 查找物理父模块 (若直接父模块为虚拟模块，则递归向上回溯至最近具有物理主表的祖先模块)
        SysModuleInfo physicalParentMeta = parentMeta;
        String parentPrimary = resolvePrimaryTable(physicalParentMeta);
        List<SysModuleInfo> intermediateMetas = new ArrayList<>();

        if (parentPrimary == null || parentPrimary.isBlank()) {
            intermediateMetas.add(parentMeta);
            physicalParentMeta = findNearestPhysicalParentMeta(parentMeta, intermediateMetas);
            if (physicalParentMeta != null) {
                parentPrimary = resolvePrimaryTable(physicalParentMeta);
            }
        }

        if (parentPrimary == null || parentPrimary.isBlank()) {
            // 向上回溯到顶层仍未找到任何配置物理主表的父模块 (如根模块即为纯虚拟模块)
            return new ParentRelationInfo("id", null, null);
        }

        // 0. 第一性原理特殊情况：当子模块物理主表与物理父模块主表为同一物理表时 (例如 101 与 105 均为 student 表垂直拆分)
        if (parentPrimary.equalsIgnoreCase(childPrimaryTable)) {
            return new ParentRelationInfo("id", "id", parentPrimary);
        }

        // 1. 汇聚子模块、物理父模块以及沿途所有中间虚拟模块的 tableRelations
        List<TableRelationDTO> allRelations = new ArrayList<>();
        if (childMeta != null && childMeta.getTableRelations() != null) {
            allRelations.addAll(childMeta.getTableRelations());
        }
        if (physicalParentMeta.getTableRelations() != null) {
            allRelations.addAll(physicalParentMeta.getTableRelations());
        }
        for (SysModuleInfo interMeta : intermediateMetas) {
            if (interMeta != null && interMeta.getTableRelations() != null) {
                allRelations.addAll(interMeta.getTableRelations());
            }
        }

        // 2. 严格单向匹配: mainTable == parentPrimary && joinTable == childPrimaryTable
        for (TableRelationDTO rel : allRelations) {
            if (rel != null
                    && parentPrimary.equalsIgnoreCase(rel.getMainTable())
                    && childPrimaryTable.equalsIgnoreCase(rel.getJoinTable())) {
                if (rel.getJoinField() != null && !rel.getJoinField().isBlank()) {
                    String pKey =
                            (rel.getMainField() != null && !rel.getMainField().isBlank())
                                    ? rel.getMainField().trim()
                                    : "id";
                    return new ParentRelationInfo(pKey, rel.getJoinField().trim(), parentPrimary);
                }
            }
        }

        // 3. 严格 100% 自洽校验：杜绝任何推测与硬编码兜底 (如 parentTable + "_id")
        Long parentModId = physicalParentMeta.getModuleId();
        Long childModId = childMeta != null ? childMeta.getModuleId() : null;
        throw new IllegalStateException(
                String.format(
                        "元数据关联关系未自洽：未在 sys_table_relation 中找到物理父表 [%s](模块 %s) 到子表 [%s](模块 %s) 的单向关联定义(main_table -> join_table)",
                        parentPrimary, parentModId, childPrimaryTable, childModId));
    }

    private String resolveParentForeignKey(
            String childPrimaryTable, SysModuleInfo childMeta, SysModuleInfo parentMeta) {
        return resolveParentRelation(childPrimaryTable, childMeta, parentMeta).childForeignKey();
    }

    /** 编译字段投影规格 */
    private List<PhysicalFieldSpec> compileProjection(
            SysModuleInfo meta,
            String primaryTable,
            List<Long> fieldIds,
            boolean isGlobalWhitelistMode) {
        List<PhysicalFieldSpec> specs = new ArrayList<>();
        List<ModuleFieldDTO> allFields = meta.getFields();
        if (allFields == null || allFields.isEmpty()) {
            return specs;
        }

        // 经权限裁剪后的字段字典
        List<ModuleFieldDTO> readableFields = permissionFilterService.filterReadableFields(meta);
        Map<Long, ModuleFieldDTO> fieldMap = new HashMap<>();
        for (ModuleFieldDTO f : readableFields) {
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
            for (ModuleFieldDTO f : readableFields) {
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
    private Condition compileBaseConditions(SysModuleInfo meta, String primaryTable) {
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
            SysModuleInfo meta, String primaryTable, List<DynamicFilterItem> filters) {
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
        for (DynamicFilterItem item : filters) {
            if (item == null || item.getValue() == null || "".equals(item.getValue())) {
                continue;
            }
            if (item.getFieldId() == null) {
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

            Condition singleCond = buildSqlCondition(tbl, col, item.getOperator(), item.getValue());
            if (singleCond != null && !DSL.noCondition().equals(singleCond)) {
                conditions.add(singleCond);
            }
        }

        return conditions.isEmpty() ? null : DSL.and(conditions);
    }

    /** 编译过滤条件列表为安全 Condition (以 fieldId 为唯一依据) */
    private Condition compileConditions(
            SysModuleInfo meta, String primaryTable, List<DynamicFilterItem> filters) {
        Condition base = compileBaseConditions(meta, primaryTable);
        Condition user = compileUserFilters(meta, primaryTable, filters);
        return user != null ? base.and(user) : base;
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
            case "IN" -> {
                if (value instanceof Collection<?> coll) {
                    yield field.in(coll);
                } else if (value instanceof Object[] arr) {
                    yield field.in(arr);
                } else {
                    yield field.eq(value);
                }
            }
            default -> field.like("%" + value + "%");
        };
    }

    /** 编译排序规则列表 (以 fieldId 为唯一依据) */
    private List<OrderField<?>> compileSorts(
            SysModuleInfo meta, String primaryTable, List<DynamicSortItem> sorts) {
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

        for (DynamicSortItem item : sorts) {
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
}
