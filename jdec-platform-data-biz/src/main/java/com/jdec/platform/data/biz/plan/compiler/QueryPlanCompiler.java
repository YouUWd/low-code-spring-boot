package com.jdec.platform.data.biz.plan.compiler;

import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import com.jdec.platform.config.api.dto.response.SysModuleMetaResp;
import com.jdec.platform.data.api.dto.request.DynamicFilterItem;
import com.jdec.platform.data.api.dto.request.DynamicQueryReq;
import com.jdec.platform.data.api.dto.request.DynamicSortItem;
import com.jdec.platform.data.biz.plan.model.JoinTableSpec;
import com.jdec.platform.data.biz.plan.model.PhysicalFieldSpec;
import com.jdec.platform.data.biz.plan.model.QueryNodePlan;
import com.jdec.platform.data.biz.plan.model.QueryPlan;
import com.jdec.platform.data.biz.service.MetadataCacheService;
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

    private final MetadataCacheService metadataCacheService;
    private final PermissionFilterService permissionFilterService;

    /** 编译顶层查询请求树为完整的 QueryPlan (支持纯 fields 驱动自动推导查询树，以及显式 children 树形入参) */
    public QueryPlan compile(DynamicQueryReq req) {
        if (req == null) {
            throw new IllegalArgumentException("查询请求不能为空");
        }

        boolean isGlobalWhitelistMode = req.getFields() != null && !req.getFields().isEmpty();

        // 1. 如果显式传递了 moduleId 并且显式构造了 children 模块树，直接按原有树结构递归编译
        if (req.getModuleId() != null
                && req.getChildren() != null
                && !req.getChildren().isEmpty()) {
            QueryNodePlan rootPlan = compileNode(req, null, null, isGlobalWhitelistMode);
            return QueryPlan.builder().rootNodePlan(rootPlan).build();
        }

        // 2. 纯 fields 驱动或单模块查询：根据 fields / filters / sorts 涉及的 fieldIds 自动推导模块树
        DynamicQueryReq structuredTreeReq = inferStructuredQueryTree(req);
        QueryNodePlan rootPlan = compileNode(structuredTreeReq, null, null, isGlobalWhitelistMode);
        return QueryPlan.builder().rootNodePlan(rootPlan).build();
    }

    /** 根据平铺的 fields/filters/sorts 与 config-engine 拓扑，自动推导并组装结构化查询请求树 */
    private DynamicQueryReq inferStructuredQueryTree(DynamicQueryReq req) {
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

        if (allFieldIds.isEmpty()) {
            if (req.getModuleId() != null) {
                return req;
            }
            throw new IllegalArgumentException("查询请求未指定任何 fields 或 moduleId");
        }

        List<ModuleFieldDTO> fieldDTOs =
                metadataCacheService.listFieldsByIds(new ArrayList<>(allFieldIds));
        if (fieldDTOs == null || fieldDTOs.isEmpty()) {
            if (req.getModuleId() != null) {
                return req;
            }
            throw new IllegalArgumentException("未查询到指定字段的元数据定义: " + allFieldIds);
        }

        Map<Long, Long> fieldToModule = new HashMap<>();
        Set<Long> involvedModuleIds = new LinkedHashSet<>();
        for (ModuleFieldDTO f : fieldDTOs) {
            if (f.getId() != null && f.getModuleId() != null) {
                fieldToModule.put(f.getId(), f.getModuleId());
                involvedModuleIds.add(f.getModuleId());
            }
        }
        if (req.getModuleId() != null) {
            involvedModuleIds.add(req.getModuleId());
        }

        if (involvedModuleIds.size() == 1) {
            Long soleModuleId = involvedModuleIds.iterator().next();
            DynamicQueryReq singleReq = new DynamicQueryReq();
            singleReq.setModuleId(soleModuleId);
            singleReq.setFields(req.getFields());
            singleReq.setFilters(req.getFilters());
            singleReq.setSorts(req.getSorts());
            singleReq.setPageNo(req.getPageNo());
            singleReq.setPageSize(req.getPageSize());
            return singleReq;
        }

        Map<Long, SysModuleMetaResp> metaMap = new HashMap<>();
        for (Long mid : involvedModuleIds) {
            SysModuleMetaResp m = metadataCacheService.getModuleComplete(mid);
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
                SysModuleMetaResp m =
                        metaMap.computeIfAbsent(current, metadataCacheService::getModuleComplete);
                if (m != null && m.getModule() != null) {
                    Long pid = m.getModule().getParentId();
                    if (pid != null && pid != 0L) {
                        fullModuleIds.add(pid);
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

        // 寻找顶层聚合根模块
        Long rootModuleId = req.getModuleId();
        if (rootModuleId == null) {
            for (Long mid : involvedModuleIds) {
                SysModuleMetaResp m =
                        metaMap.computeIfAbsent(mid, metadataCacheService::getModuleComplete);
                if (m != null && m.getModule() != null) {
                    Long pid = m.getModule().getParentId();
                    if (pid == null || pid == 0L) {
                        rootModuleId = mid;
                        break;
                    }
                }
            }
        }
        if (rootModuleId == null) {
            rootModuleId = involvedModuleIds.iterator().next();
        }

        // 按 moduleId 分组 fields, filters, sorts
        Map<Long, List<Long>> fieldsByModule = new HashMap<>();
        if (req.getFields() != null) {
            for (Long fid : req.getFields()) {
                Long mid = fieldToModule.get(fid);
                if (mid == null) {
                    mid = rootModuleId;
                }
                fieldsByModule.computeIfAbsent(mid, k -> new ArrayList<>()).add(fid);
            }
        }

        Map<Long, List<DynamicFilterItem>> filtersByModule = new HashMap<>();
        if (req.getFilters() != null) {
            for (var filter : req.getFilters()) {
                Long mid =
                        filter.getFieldId() != null
                                ? fieldToModule.get(filter.getFieldId())
                                : rootModuleId;
                if (mid == null) {
                    mid = rootModuleId;
                }
                filtersByModule.computeIfAbsent(mid, k -> new ArrayList<>()).add(filter);
            }
        }

        Map<Long, List<DynamicSortItem>> sortsByModule = new HashMap<>();
        if (req.getSorts() != null) {
            for (var sort : req.getSorts()) {
                Long mid =
                        sort.getFieldId() != null
                                ? fieldToModule.get(sort.getFieldId())
                                : rootModuleId;
                if (mid == null) {
                    mid = rootModuleId;
                }
                sortsByModule.computeIfAbsent(mid, k -> new ArrayList<>()).add(sort);
            }
        }

        // 构建模块层级 parentToChildren 映射 (严格基于真实模块树层级，杜绝跨级悬挂)
        Map<Long, List<Long>> parentToChildren = new HashMap<>();
        for (Long mid : involvedModuleIds) {
            if (!mid.equals(rootModuleId)) {
                SysModuleMetaResp m =
                        metaMap.computeIfAbsent(mid, metadataCacheService::getModuleComplete);
                Long pid =
                        (m != null
                                        && m.getModule() != null
                                        && m.getModule().getParentId() != null
                                        && m.getModule().getParentId() != 0L)
                                ? m.getModule().getParentId()
                                : rootModuleId;
                parentToChildren.computeIfAbsent(pid, k -> new ArrayList<>()).add(mid);
            }
        }

        return buildReqNode(
                rootModuleId,
                rootModuleId,
                req,
                fieldsByModule,
                filtersByModule,
                sortsByModule,
                parentToChildren);
    }

    private DynamicQueryReq buildReqNode(
            Long currentModuleId,
            Long rootModuleId,
            DynamicQueryReq originalReq,
            Map<Long, List<Long>> fieldsByModule,
            Map<Long, List<DynamicFilterItem>> filtersByModule,
            Map<Long, List<DynamicSortItem>> sortsByModule,
            Map<Long, List<Long>> parentToChildren) {

        DynamicQueryReq node = new DynamicQueryReq();
        node.setModuleId(currentModuleId);
        node.setFields(fieldsByModule.get(currentModuleId));
        node.setFilters(filtersByModule.get(currentModuleId));
        node.setSorts(sortsByModule.get(currentModuleId));

        if (currentModuleId.equals(rootModuleId)) {
            node.setPageNo(originalReq.getPageNo());
            node.setPageSize(originalReq.getPageSize());
        }

        List<Long> childIds = parentToChildren.get(currentModuleId);
        if (childIds != null && !childIds.isEmpty()) {
            List<DynamicQueryReq> childReqs = new ArrayList<>();
            for (Long cid : childIds) {
                childReqs.add(
                        buildReqNode(
                                cid,
                                rootModuleId,
                                originalReq,
                                fieldsByModule,
                                filtersByModule,
                                sortsByModule,
                                parentToChildren));
            }
            node.setChildren(childReqs);
        }
        return node;
    }

    /** 递归编译单个 DynamicQueryReq 节点 (兼容旧调用，默认非全局白名单) */
    public QueryNodePlan compileNode(
            DynamicQueryReq node, Long parentModuleId, SysModuleMetaResp parentMeta) {
        return compileNode(node, parentModuleId, parentMeta, false);
    }

    /** 递归编译单个 DynamicQueryReq 节点 */
    public QueryNodePlan compileNode(
            DynamicQueryReq node,
            Long parentModuleId,
            SysModuleMetaResp parentMeta,
            boolean isGlobalWhitelistMode) {

        Long moduleId = node.getModuleId();
        SysModuleMetaResp meta = metadataCacheService.getModuleComplete(moduleId);
        if (meta == null || meta.getModule() == null) {
            throw new IllegalArgumentException("模块 ID [" + moduleId + "] 不存在或未配置元数据");
        }

        String primaryTable = resolvePrimaryTable(meta);
        String parentFk = resolveParentForeignKey(primaryTable, meta, parentMeta);

        // 1. 编译字段投影规格 (基于权限 + fields 显式白名单)
        List<PhysicalFieldSpec> allProjectedFields =
                compileProjection(meta, primaryTable, node.getFields(), isGlobalWhitelistMode);

        // 识别 1:N 关系从表字段并自动拆分至子节点计划
        List<PhysicalFieldSpec> primaryProjectedFields = new ArrayList<>();
        Map<String, List<PhysicalFieldSpec>> oneToManyFieldsByTable = new LinkedHashMap<>();
        Map<String, TableRelationDTO> oneToManyRelationByTable = new LinkedHashMap<>();

        if (meta.getTableRelations() != null) {
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

        // 2. 编译过滤条件 (以 fieldId 驱动，构建安全 Condition)
        Condition condition = compileConditions(meta, primaryTable, node.getFilters());

        // 3. 编译排序规则 (以 fieldId 驱动)
        List<OrderField<?>> orderFields = compileSorts(meta, primaryTable, node.getSorts());

        // 4. 递归编译已有的显式子节点树
        List<QueryNodePlan> childPlans = new ArrayList<>();
        Set<String> handledTables = new HashSet<>();
        if (node.getChildren() != null && !node.getChildren().isEmpty()) {
            for (DynamicQueryReq childNode : node.getChildren()) {
                if (childNode != null && childNode.getModuleId() != null) {
                    QueryNodePlan cp =
                            compileNode(childNode, moduleId, meta, isGlobalWhitelistMode);
                    childPlans.add(cp);
                    if (cp.getPrimaryTable() != null) {
                        handledTables.add(cp.getPrimaryTable().toLowerCase());
                    }
                }
            }
        }

        // 5. 自动为同一模块内未被显式子模块覆盖的 1:N 从表生成子节点计划
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

                QueryNodePlan subPlan =
                        QueryNodePlan.builder()
                                .moduleId(moduleId)
                                .moduleMeta(meta)
                                .primaryTable(rel.getJoinTable())
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

        // 6. 识别并编译当前主表所需伴生表 (1:1 或 N:1) 的物理 JOIN 规格
        List<JoinTableSpec> companionJoins =
                resolveCompanionJoins(primaryTable, primaryProjectedFields, meta);

        return QueryNodePlan.builder()
                .moduleId(moduleId)
                .moduleMeta(meta)
                .primaryTable(primaryTable)
                .parentForeignKey(parentFk)
                .pageNo(node.getPageNo())
                .pageSize(node.getPageSize())
                .projectedFields(primaryProjectedFields)
                .condition(condition)
                .orderFields(orderFields)
                .companionJoins(companionJoins)
                .children(childPlans)
                .build();
    }

    /** 基于 sys_table_relation 单向定义推导伴生表物理 JOIN 规格 */
    private List<JoinTableSpec> resolveCompanionJoins(
            String primaryTable, List<PhysicalFieldSpec> projectedFields, SysModuleMetaResp meta) {
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
                            meta.getModule() != null ? meta.getModule().getId() : null,
                            neededCompanionTables));
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
                                meta.getModule() != null ? meta.getModule().getId() : null,
                                primaryTable,
                                compTable));
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
    public String resolvePrimaryTable(SysModuleMetaResp meta) {
        if (meta == null) {
            return "";
        }
        if (meta.getModule() != null
                && meta.getModule().getPrimaryTable() != null
                && !meta.getModule().getPrimaryTable().isBlank()) {
            return meta.getModule().getPrimaryTable().trim();
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

    /**
     * 从单向定义的 tableRelations 中严格推导外键关联字段名
     *
     * <p>第一性原理：TableRelations 仅单向定义 (main_table 为父表，join_table 为从表，join_field 为从表外键物理列)。
     * 结合模块树拓扑，父模块主表必须作为 main_table，子模块主表必须作为 join_table。 严禁任何猜列名、约定俗成、弱匹配回退，不匹配即抛出明确元数据异常。
     */
    private String resolveParentForeignKey(
            String childPrimaryTable, SysModuleMetaResp childMeta, SysModuleMetaResp parentMeta) {
        if (childPrimaryTable == null || childPrimaryTable.isBlank() || parentMeta == null) {
            return null;
        }
        String parentPrimary = resolvePrimaryTable(parentMeta);
        if (parentPrimary == null || parentPrimary.isBlank()) {
            throw new IllegalStateException(
                    String.format(
                            "父模块 [%s] 未配置有效的物理主表(primaryTable)", parentMeta.getModule().getId()));
        }

        // 0. 第一性原理特殊情况：当子模块物理主表与父模块物理主表为同一物理表时 (例如 101 学生档案 与 105 核心档案 均为 student 表)
        // 这是同一实体在不同业务模块视图下的垂直拆分，外键关联即为自身物理主键 id
        if (parentPrimary.equalsIgnoreCase(childPrimaryTable)) {
            return "id";
        }

        // 1. 汇聚父模块与子模块维护的 tableRelations
        List<TableRelationDTO> allRelations = new ArrayList<>();
        if (childMeta != null && childMeta.getTableRelations() != null) {
            allRelations.addAll(childMeta.getTableRelations());
        }
        if (parentMeta.getTableRelations() != null) {
            allRelations.addAll(parentMeta.getTableRelations());
        }

        // 2. 严格单向匹配: mainTable == parentPrimary && joinTable == childPrimaryTable
        for (TableRelationDTO rel : allRelations) {
            if (rel != null
                    && parentPrimary.equalsIgnoreCase(rel.getMainTable())
                    && childPrimaryTable.equalsIgnoreCase(rel.getJoinTable())) {
                if (rel.getJoinField() != null && !rel.getJoinField().isBlank()) {
                    return rel.getJoinField().trim();
                }
            }
        }

        // 3. 严格 100% 自洽校验：杜绝任何推测与硬编码兜底 (如 parentTable + "_id")
        Long parentModId = parentMeta.getModule() != null ? parentMeta.getModule().getId() : null;
        Long childModId =
                (childMeta != null && childMeta.getModule() != null)
                        ? childMeta.getModule().getId()
                        : null;
        throw new IllegalStateException(
                String.format(
                        "元数据关联关系未自洽：未在 sys_table_relation 中找到父表 [%s](模块 %s) 到子表 [%s](模块 %s) 的单向关联定义(main_table -> join_table)",
                        parentPrimary, parentModId, childPrimaryTable, childModId));
    }

    /** 编译字段投影规格 */
    private List<PhysicalFieldSpec> compileProjection(
            SysModuleMetaResp meta,
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

    /** 编译过滤条件列表为安全 Condition (以 fieldId 为唯一依据) */
    private Condition compileConditions(
            SysModuleMetaResp meta, String primaryTable, List<DynamicFilterItem> filters) {
        List<Condition> conditions = new ArrayList<>();

        // 1. 软删除过滤 (主表 deleted = 0)
        conditions.add(DSL.field(DSL.name(primaryTable, "deleted")).eq((byte) 0));

        // 2. 主体隔离过滤 (若主表包含 subject_id 字段)
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

        if (filters == null || filters.isEmpty()) {
            return DSL.and(conditions);
        }

        Map<Long, ModuleFieldDTO> fieldIdMap = new HashMap<>();
        if (meta.getFields() != null) {
            for (ModuleFieldDTO f : meta.getFields()) {
                if (f.getId() != null) {
                    fieldIdMap.put(f.getId(), f);
                }
            }
        }

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

        return DSL.and(conditions);
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
            SysModuleMetaResp meta, String primaryTable, List<DynamicSortItem> sorts) {
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
