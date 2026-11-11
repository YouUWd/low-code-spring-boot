package com.jdec.platform.common.biz.query.mapper;

import com.jdec.platform.common.api.dto.request.FieldSortItem;
import com.jdec.platform.common.api.dto.request.FlatFilterItem;
import com.jdec.platform.common.api.dto.request.FlatSortItem;
import com.jdec.platform.common.api.dto.request.MenuChangeQueryReq;
import com.jdec.platform.common.api.dto.request.MenuFlatFieldFilterItem;
import com.jdec.platform.common.api.dto.request.MenuQueryReq;
import com.jdec.platform.common.api.dto.request.MenuTableSpec;
import com.jdec.platform.common.api.dto.response.DataRecordItem;
import com.jdec.platform.common.api.dto.response.ModuleDataNode;
import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.common.ProjectTableRelationsDTO;
import com.jdec.platform.config.api.dto.response.MenuHeaderColumnResp;
import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.common.DataRow;
import com.jdec.platform.data.api.dto.request.DetailQueryReq;
import com.jdec.platform.data.api.dto.request.FilterItem;
import com.jdec.platform.data.api.dto.request.PageQueryReq;
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.data.api.dto.request.SortItem;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.api.model.predicate.ColumnRef;
import com.jdec.platform.data.api.model.predicate.ComparisonPredicate;
import com.jdec.platform.data.api.model.predicate.CompoundPredicate;
import com.jdec.platform.data.api.model.predicate.FunctionExpr;
import com.jdec.platform.data.api.model.predicate.Predicate;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

/**
 * 物理查询与同构数据树映射器 (PhysicalQueryMapper)
 *
 * <p>核心职责：
 *
 * <ul>
 *   <li>将菜单表头与字段 ID 转换为 {@code data-api} 统一投影列 {@code table.column}、{@link FilterItem} 与 {@link
 *       SortItem}。
 *   <li>执行 1:N 模块分支冲突校验、未命中 1:N 分支表头禁用修饰及已勾选字段收集。
 *   <li>将平铺物理表查询请求（{@link MenuTableSpec}、{@link FlatFilterItem}、{@link FlatSortItem}）消歧映射为底层物理查询参数。
 *   <li>将底层 {@link DataRow} 树递归转换为前端契约的 {@link DataRecordItem} 与 {@link ModuleDataNode} 同构树。
 * </ul>
 */
@Slf4j
public final class PhysicalQueryMapper {

    private PhysicalQueryMapper() {}

    // ==================== 1. 基于 fieldId 的物理表/列映射 ====================

    public static PhysicalFieldRef resolve(ModuleTreeDTO tree, Long fieldId) {
        if (fieldId == null) {
            throw new IllegalArgumentException("字段 ID 不能为空");
        }
        if (tree == null) {
            throw new IllegalArgumentException("模块元数据不能为空");
        }

        ModuleFieldDTO field = tree.getField(fieldId);
        Long moduleId = tree.getModuleIdByFieldId(fieldId);
        if (field == null || moduleId == null) {
            throw new IllegalArgumentException("字段 [" + fieldId + "] 不属于当前模块树");
        }

        ModuleInfo module = tree.getModule(moduleId);
        String tableName = field.getTableName();
        if (tableName == null || tableName.isBlank()) {
            tableName = module != null ? module.getPrimaryTable() : null;
        }
        String columnName = field.getColumnName();
        if (tableName == null
                || tableName.isBlank()
                || columnName == null
                || columnName.isBlank()) {
            throw new IllegalArgumentException("字段 [" + fieldId + "] 未配置有效的物理表名或列名");
        }
        return new PhysicalFieldRef(fieldId, moduleId, tableName.trim(), columnName.trim());
    }

    record ModuleTableKey(Long moduleId, String tableName) {}

    public record PhysicalFieldRef(
            Long fieldId, Long moduleId, String tableName, String columnName) {}

    public static List<String> toColumns(ModuleTreeDTO tree, Collection<Long> fieldIds) {
        if (tree == null || fieldIds == null || fieldIds.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> columns = new LinkedHashSet<>();
        for (Long fieldId : fieldIds) {
            if (fieldId == null) {
                continue;
            }
            PhysicalFieldRef ref = resolve(tree, fieldId);
            columns.add(ref.tableName() + "." + ref.columnName());
        }
        return new ArrayList<>(columns);
    }

    public static List<FilterItem> toTableFilters(
            ModuleTreeDTO tree, Collection<MenuFlatFieldFilterItem> filters) {
        List<FilterItem> result = new ArrayList<>();
        if (filters == null) {
            return result;
        }
        for (MenuFlatFieldFilterItem filter : filters) {
            if (filter != null) {
                PhysicalFieldRef ref = resolve(tree, filter.getFieldId());
                result.add(
                        FilterItem.builder()
                                .field(ref.tableName() + "." + ref.columnName())
                                .tableName(ref.tableName())
                                .columnName(ref.columnName())
                                .operator(
                                        filter.getOperator() != null ? filter.getOperator() : "EQ")
                                .value(filter.getValue())
                                .build());
            }
        }
        return result;
    }

    /** 批量将 FilterItem 集合组装为统一的 MySQL 标准 Predicate AST (以 AND 联结) */
    public static Predicate toPredicate(Collection<FilterItem> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        List<Predicate> list = new ArrayList<>();
        for (FilterItem item : items) {
            if (item != null) {
                Predicate p = item.toPredicate();
                if (p != null) {
                    list.add(p);
                }
            }
        }
        if (list.isEmpty()) {
            return null;
        }
        if (list.size() == 1) {
            return list.get(0);
        }
        return CompoundPredicate.and(list);
    }

    public static List<SortItem> toTableSorts(ModuleTreeDTO tree, Collection<FieldSortItem> sorts) {
        List<SortItem> result = new ArrayList<>();
        if (sorts == null) {
            return result;
        }
        for (FieldSortItem sort : sorts) {
            if (sort != null) {
                PhysicalFieldRef ref = resolve(tree, sort.getFieldId());
                result.add(
                        SortItem.builder()
                                .field(ref.tableName() + "." + ref.columnName())
                                .direction(
                                        sort.getDirection() != null ? sort.getDirection() : "ASC")
                                .build());
            }
        }
        return result;
    }

    // ==================== 1.5 根据模块树 (ModuleTreeDTO) 构建完整 View 视图树 ====================

    /** 解析模块对应的视图标识 (view)：优先使用 moduleCode，未配置时回退至模块 ID 字符串或主表名。 */
    public static String resolveModuleView(ModuleInfo mod, Long moduleId) {
        if (mod != null && mod.getModuleCode() != null && !mod.getModuleCode().isBlank()) {
            return mod.getModuleCode().trim();
        }
        if (moduleId != null) {
            return String.valueOf(moduleId);
        }
        if (mod != null && mod.getPrimaryTable() != null && !mod.getPrimaryTable().isBlank()) {
            return mod.getPrimaryTable().trim();
        }
        return null;
    }

    /** 获取单个模块（即一个多表组合的 View 视图）所声明的全部物理表集合（含主表、1:1 伴生表、N:1 上游表）。 */
    public static Set<String> getDeclaredTablesOfModule(ModuleInfo mod) {
        if (mod == null) {
            return Collections.emptySet();
        }
        Set<String> tables = new LinkedHashSet<>();
        if (mod.getPrimaryTable() != null && !mod.getPrimaryTable().isBlank()) {
            tables.add(mod.getPrimaryTable().trim().toLowerCase());
        }
        if (mod.getFields() != null) {
            for (ModuleFieldDTO f : mod.getFields()) {
                if (f != null && f.getTableName() != null && !f.getTableName().isBlank()) {
                    tables.add(f.getTableName().trim().toLowerCase());
                }
            }
        }
        return tables;
    }

    /**
     * 根据模块树、勾选字段及补齐后的列集合，构建完整的自相似分页查询视图树 (PageQueryReq View Tree)。 自动将过滤条件按所属模块拆分分发至根节点与各子视图节点的
     * filter 中。
     */
    public static PageQueryReq buildPageQueryViewTree(
            ModuleTreeDTO moduleTree,
            Collection<Long> checkedFieldIds,
            List<String> flatColumns,
            Collection<FilterItem> filters,
            List<SortItem> sorts,
            Integer pageNo,
            Integer pageSize) {
        return buildPageQueryViewTree(
                moduleTree,
                checkedFieldIds,
                flatColumns,
                toPredicate(filters),
                sorts,
                pageNo,
                pageSize);
    }

    /**
     * 根据模块树、勾选字段及补齐后的列集合，构建完整的自相似分页查询视图树 (PageQueryReq View Tree)。 每个模块作为一个多表组合的 View
     * 节点，仅携带归属本视图表的列，子模块递归挂载于 children。
     */
    public static PageQueryReq buildPageQueryViewTree(
            ModuleTreeDTO moduleTree,
            Collection<Long> checkedFieldIds,
            List<String> flatColumns,
            Predicate filter,
            List<SortItem> sorts,
            Integer pageNo,
            Integer pageSize) {
        QueryReq baseTree =
                buildViewTreeInternal(
                        moduleTree, checkedFieldIds, null, null, flatColumns, filter, sorts);
        return PageQueryReq.builder()
                .view(baseTree.getView())
                .columns(baseTree.getColumns())
                .filter(baseTree.getFilter())
                .sort(baseTree.getSort())
                .children(baseTree.getChildren())
                .pageNo(pageNo)
                .pageSize(pageSize)
                .build();
    }

    /** 根据模块树、MenuTableSpec 列表及补齐后的列集合，构建完整的自相似列表查询视图树 (QueryReq View Tree)。 */
    public static QueryReq buildQueryViewTree(
            ModuleTreeDTO moduleTree,
            List<MenuTableSpec> menuTableSpecs,
            Function<Long, Long> menuToModuleResolver,
            List<String> flatColumns,
            Collection<FilterItem> filters,
            List<SortItem> sorts) {
        return buildViewTreeInternal(
                moduleTree,
                null,
                menuTableSpecs,
                menuToModuleResolver,
                flatColumns,
                toPredicate(filters),
                sorts);
    }

    /** 根据模块树、MenuTableSpec 列表及补齐后的列集合，构建完整的自相似列表查询视图树 (QueryReq View Tree)。 */
    public static QueryReq buildQueryViewTree(
            ModuleTreeDTO moduleTree,
            List<MenuTableSpec> menuTableSpecs,
            Function<Long, Long> menuToModuleResolver,
            List<String> flatColumns,
            Predicate filter,
            List<SortItem> sorts) {
        return buildViewTreeInternal(
                moduleTree, null, menuTableSpecs, menuToModuleResolver, flatColumns, filter, sorts);
    }

    /** 根据模块树、勾选字段及补齐后的列集合，构建完整的自相似单据详情查询视图树 (DetailQueryReq View Tree)。 */
    public static DetailQueryReq buildDetailQueryViewTree(
            ModuleTreeDTO moduleTree,
            Collection<Long> checkedFieldIds,
            List<String> flatColumns,
            Long id) {
        QueryReq baseTree =
                buildViewTreeInternal(
                        moduleTree, checkedFieldIds, null, null, flatColumns, null, null);
        return DetailQueryReq.builder()
                .view(baseTree.getView())
                .id(id)
                .columns(baseTree.getColumns())
                .children(baseTree.getChildren())
                .build();
    }

    private static QueryReq buildViewTreeInternal(
            ModuleTreeDTO moduleTree,
            Collection<Long> checkedFieldIds,
            List<MenuTableSpec> menuTableSpecs,
            Function<Long, Long> menuToModuleResolver,
            List<String> flatColumns,
            Predicate filter,
            List<SortItem> sorts) {
        if (moduleTree == null) {
            return QueryReq.builder().columns(flatColumns).filter(filter).sort(sorts).build();
        }
        Long rootModuleId = moduleTree.getRootModuleId();
        ModuleInfo rootMod =
                rootModuleId != null
                        ? moduleTree.getModule(rootModuleId)
                        : moduleTree.getRootModule();
        if (rootMod == null) {
            rootMod = moduleTree.getRootModule();
        }
        String rootView = resolveModuleView(rootMod, rootModuleId);

        Map<Long, Set<String>> colsByModule =
                groupColumnsByModule(
                        moduleTree,
                        checkedFieldIds,
                        menuTableSpecs,
                        menuToModuleResolver,
                        flatColumns);

        Map<Long, Predicate> filtersByModule = groupPredicateByModule(moduleTree, filter);

        List<QueryReq> children =
                buildViewTreeChildren(
                        rootModuleId, moduleTree, colsByModule, filtersByModule, sorts);

        Set<String> rootColSet = rootModuleId != null ? colsByModule.get(rootModuleId) : null;
        List<String> rootCols =
                (rootColSet != null && !rootColSet.isEmpty()) ? new ArrayList<>(rootColSet) : null;
        if ((rootCols == null || rootCols.isEmpty())
                && rootMod != null
                && rootMod.getPrimaryTable() != null
                && !rootMod.getPrimaryTable().isBlank()
                && !children.isEmpty()) {
            rootCols = List.of(rootMod.getPrimaryTable().trim() + ".id");
        }

        Predicate rootFilter =
                (rootModuleId != null && filtersByModule != null)
                        ? filtersByModule.get(rootModuleId)
                        : filter;

        return QueryReq.builder()
                .view(rootView)
                .columns(rootCols)
                .filter(rootFilter)
                .sort(sorts)
                .children(children.isEmpty() ? null : children)
                .build();
    }

    private static Map<Long, Set<String>> groupColumnsByModule(
            ModuleTreeDTO moduleTree,
            Collection<Long> checkedFieldIds,
            List<MenuTableSpec> menuTableSpecs,
            Function<Long, Long> menuToModuleResolver,
            List<String> flatColumns) {
        Map<Long, Set<String>> colsByModule = new LinkedHashMap<>();
        Long rootModuleId = moduleTree.getRootModuleId();
        if (rootModuleId != null) {
            colsByModule.put(rootModuleId, new LinkedHashSet<>());
        }

        if (checkedFieldIds != null) {
            for (Long fieldId : checkedFieldIds) {
                if (fieldId == null) {
                    continue;
                }
                PhysicalFieldRef ref = resolve(moduleTree, fieldId);
                colsByModule
                        .computeIfAbsent(ref.moduleId(), k -> new LinkedHashSet<>())
                        .add(ref.tableName() + "." + ref.columnName());
            }
        }

        if (menuTableSpecs != null) {
            for (MenuTableSpec mSpec : menuTableSpecs) {
                if (mSpec == null) {
                    continue;
                }
                Long modId =
                        resolveModuleIdForTable(
                                mSpec.getMenuId(),
                                mSpec.getTableName(),
                                moduleTree,
                                menuToModuleResolver);
                if (modId != null) {
                    colsByModule.computeIfAbsent(modId, k -> new LinkedHashSet<>());
                }
            }
        }

        if (flatColumns != null) {
            String defaultTable = moduleTree.getPrimaryTable();
            for (String rawCol : flatColumns) {
                if (rawCol == null || rawCol.isBlank()) {
                    continue;
                }
                String trimmed = rawCol.trim();
                String table =
                        trimmed.contains(".")
                                ? trimmed.substring(0, trimmed.indexOf('.')).trim().toLowerCase()
                                : (defaultTable != null ? defaultTable.trim().toLowerCase() : "");
                if (table.isEmpty()) {
                    continue;
                }

                boolean matchedActive = false;
                for (Long activeModId : new ArrayList<>(colsByModule.keySet())) {
                    ModuleInfo activeMod = moduleTree.getModule(activeModId);
                    if (getDeclaredTablesOfModule(activeMod).contains(table)) {
                        colsByModule.get(activeModId).add(trimmed);
                        matchedActive = true;
                    }
                }
                if (!matchedActive && moduleTree.getModuleMap() != null) {
                    for (Map.Entry<Long, ModuleInfo> entry : moduleTree.getModuleMap().entrySet()) {
                        if (getDeclaredTablesOfModule(entry.getValue()).contains(table)) {
                            colsByModule
                                    .computeIfAbsent(entry.getKey(), k -> new LinkedHashSet<>())
                                    .add(trimmed);
                            break;
                        }
                    }
                }
            }
        }

        return colsByModule;
    }

    private static List<QueryReq> buildViewTreeChildren(
            Long parentModuleId,
            ModuleTreeDTO moduleTree,
            Map<Long, Set<String>> colsByModule,
            Map<Long, Predicate> filtersByModule,
            List<SortItem> allSorts) {
        if (parentModuleId == null || moduleTree == null) {
            return Collections.emptyList();
        }
        List<Long> childModuleIds = resolveChildModuleIds(moduleTree, parentModuleId);
        if (childModuleIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<QueryReq> children = new ArrayList<>();
        for (Long childMid : childModuleIds) {
            boolean hasCols = hasAnyColumnsInSubtree(childMid, moduleTree, colsByModule);
            boolean hasFilters = hasAnyFilterInSubtree(childMid, moduleTree, filtersByModule);
            if (!hasCols && !hasFilters) {
                continue;
            }
            ModuleInfo childMod = moduleTree.getModule(childMid);
            String childView = resolveModuleView(childMod, childMid);
            List<QueryReq> subChildren =
                    buildViewTreeChildren(
                            childMid, moduleTree, colsByModule, filtersByModule, allSorts);

            Set<String> colSet = colsByModule.get(childMid);
            List<String> childCols =
                    (colSet != null && !colSet.isEmpty()) ? new ArrayList<>(colSet) : null;
            if ((childCols == null || childCols.isEmpty())
                    && childMod != null
                    && childMod.getPrimaryTable() != null
                    && !childMod.getPrimaryTable().isBlank()) {
                childCols = List.of(childMod.getPrimaryTable().trim() + ".id");
            }

            List<SortItem> childSorts =
                    filterSortsForModule(allSorts, getDeclaredTablesOfModule(childMod));

            Predicate childFilter = filtersByModule != null ? filtersByModule.get(childMid) : null;

            children.add(
                    QueryReq.builder()
                            .view(childView)
                            .columns(childCols)
                            .filter(childFilter)
                            .sort(childSorts.isEmpty() ? null : childSorts)
                            .children(subChildren.isEmpty() ? null : subChildren)
                            .build());
        }
        return children;
    }

    private static boolean hasAnyFilterInSubtree(
            Long moduleId, ModuleTreeDTO moduleTree, Map<Long, Predicate> filtersByModule) {
        if (moduleId == null || filtersByModule == null || filtersByModule.isEmpty()) {
            return false;
        }
        Predicate direct = filtersByModule.get(moduleId);
        if (direct != null) {
            return true;
        }
        for (Long childMid : resolveChildModuleIds(moduleTree, moduleId)) {
            if (hasAnyFilterInSubtree(childMid, moduleTree, filtersByModule)) {
                return true;
            }
        }
        return false;
    }

    /** 将 FilterItem 过滤条件集合按其物理表归属分组映射至各模块 ID (moduleId -> List<FilterItem>)。 */
    public static Map<Long, List<FilterItem>> groupFiltersByModule(
            ModuleTreeDTO moduleTree, Collection<FilterItem> filters) {
        if (filters == null || filters.isEmpty() || moduleTree == null) {
            return Collections.emptyMap();
        }
        Long rootModuleId = moduleTree.getRootModuleId();
        if (rootModuleId == null && moduleTree.getRootModule() != null) {
            rootModuleId = moduleTree.getRootModule().getModuleId();
        }

        Map<Long, List<FilterItem>> grouped = new LinkedHashMap<>();
        for (FilterItem item : filters) {
            if (item == null) {
                continue;
            }
            Long modId = resolveModuleIdForTable(moduleTree, item.getTableName());
            if (modId == null) {
                modId = rootModuleId;
            }
            grouped.computeIfAbsent(modId, k -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    /**
     * 将统一的 Predicate 过滤条件树按物理表归属，安全拆解分发至模块树中的各模块 ID (moduleId -> Predicate)。 顶层 AND
     * 组合将被解包，针对各模块引用的表分别聚合为该模块局部的 Predicate。
     */
    public static Map<Long, Predicate> groupPredicateByModule(
            ModuleTreeDTO moduleTree, Predicate filter) {
        if (filter == null || moduleTree == null) {
            return Collections.emptyMap();
        }
        Long rootModuleId = moduleTree.getRootModuleId();
        if (rootModuleId == null && moduleTree.getRootModule() != null) {
            rootModuleId = moduleTree.getRootModule().getModuleId();
        }

        List<Predicate> flatPredicates = flattenTopLevelAndPredicates(filter);
        if (flatPredicates.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, List<Predicate>> grouped = new LinkedHashMap<>();
        for (Predicate p : flatPredicates) {
            if (p == null) {
                continue;
            }
            Long modId = resolveModuleIdForPredicate(moduleTree, p);
            if (modId == null) {
                modId = rootModuleId;
            }
            grouped.computeIfAbsent(modId, k -> new ArrayList<>()).add(p);
        }

        Map<Long, Predicate> result = new LinkedHashMap<>();
        for (Map.Entry<Long, List<Predicate>> entry : grouped.entrySet()) {
            List<Predicate> list = entry.getValue();
            if (list == null || list.isEmpty()) {
                continue;
            }
            Predicate combined = (list.size() == 1) ? list.get(0) : CompoundPredicate.and(list);
            result.put(entry.getKey(), combined);
        }
        return result;
    }

    private static List<Predicate> flattenTopLevelAndPredicates(Predicate filter) {
        List<Predicate> result = new ArrayList<>();
        if (filter == null) {
            return result;
        }
        if (filter instanceof CompoundPredicate compound
                && "AND".equalsIgnoreCase(compound.getOperator())) {
            if (compound.getPredicates() != null) {
                for (Predicate sub : compound.getPredicates()) {
                    result.addAll(flattenTopLevelAndPredicates(sub));
                }
            }
        } else {
            result.add(filter);
        }
        return result;
    }

    /** 根据单个 Predicate AST 提取其引用的物理表，判定其唯一归属的模块 ID。 若引用无表名或跨多个不同模块，则回退归属根模块。 */
    public static Long resolveModuleIdForPredicate(ModuleTreeDTO moduleTree, Predicate predicate) {
        if (moduleTree == null) {
            return null;
        }
        Long rootModuleId = moduleTree.getRootModuleId();
        if (rootModuleId == null && moduleTree.getRootModule() != null) {
            rootModuleId = moduleTree.getRootModule().getModuleId();
        }
        Set<String> tables = extractReferencedTables(predicate);
        if (tables.isEmpty()) {
            return rootModuleId;
        }
        if (tables.size() == 1) {
            return resolveModuleIdForTable(moduleTree, tables.iterator().next());
        }

        // 包含多个物理表：检查是否存在某一个模块包含全部引用的表
        if (moduleTree.getModuleMap() != null) {
            for (Map.Entry<Long, ModuleInfo> entry : moduleTree.getModuleMap().entrySet()) {
                if (rootModuleId != null && rootModuleId.equals(entry.getKey())) {
                    continue;
                }
                Set<String> declared = getDeclaredTablesOfModule(entry.getValue());
                if (declared.containsAll(tables)) {
                    return entry.getKey();
                }
            }
        }
        ModuleInfo rootMod =
                rootModuleId != null
                        ? moduleTree.getModule(rootModuleId)
                        : moduleTree.getRootModule();
        if (rootMod != null && getDeclaredTablesOfModule(rootMod).containsAll(tables)) {
            return rootModuleId;
        }

        // 跨模块复合条件，回退至根节点
        return rootModuleId;
    }

    /** 根据物理表名（不区分大小写）查找归属的模块 ID。 匹配优先级：各模块 primaryTable 精确匹配 -> 子模块声明表集合 -> 根模块声明表集合 -> 根模块回退。 */
    public static Long resolveModuleIdForTable(ModuleTreeDTO moduleTree, String rawTable) {
        if (moduleTree == null || rawTable == null || rawTable.isBlank()) {
            return moduleTree != null ? moduleTree.getRootModuleId() : null;
        }
        String table = rawTable.trim().toLowerCase();
        Long rootModuleId = moduleTree.getRootModuleId();
        ModuleInfo rootMod =
                rootModuleId != null
                        ? moduleTree.getModule(rootModuleId)
                        : moduleTree.getRootModule();

        // 1. 优先比对主表名 (精确匹配各模块 primaryTable)
        if (rootMod != null
                && rootMod.getPrimaryTable() != null
                && table.equalsIgnoreCase(rootMod.getPrimaryTable().trim())) {
            return rootModuleId;
        }
        if (moduleTree.getModuleMap() != null) {
            for (Map.Entry<Long, ModuleInfo> entry : moduleTree.getModuleMap().entrySet()) {
                ModuleInfo mod = entry.getValue();
                if (mod != null
                        && mod.getPrimaryTable() != null
                        && table.equalsIgnoreCase(mod.getPrimaryTable().trim())) {
                    return entry.getKey();
                }
            }
        }

        // 2. 比对模块声明的物理表集合 (伴生表、上游表等)
        // 优先检查子模块
        if (moduleTree.getModuleMap() != null) {
            for (Map.Entry<Long, ModuleInfo> entry : moduleTree.getModuleMap().entrySet()) {
                if (rootModuleId != null && rootModuleId.equals(entry.getKey())) {
                    continue;
                }
                if (getDeclaredTablesOfModule(entry.getValue()).contains(table)) {
                    return entry.getKey();
                }
            }
        }
        // 其次检查根模块
        if (rootMod != null && getDeclaredTablesOfModule(rootMod).contains(table)) {
            return rootModuleId;
        }

        return rootModuleId;
    }

    /** 递归从 Predicate AST 中抽取所有引用的物理表名（转为小写）。 */
    public static Set<String> extractReferencedTables(Predicate predicate) {
        if (predicate == null) {
            return Collections.emptySet();
        }
        Set<String> tables = new LinkedHashSet<>();
        if (predicate instanceof ComparisonPredicate comp) {
            String tbl = comp.getTableName();
            if (tbl == null && comp.getExpression() instanceof ColumnRef col) {
                tbl = col.getTableName();
            } else if (tbl == null
                    && comp.getExpression() instanceof FunctionExpr fn
                    && fn.getTargetColumn() != null) {
                tbl = fn.getTargetColumn().getTableName();
            }
            if (tbl == null && comp.getField() != null && comp.getField().contains(".")) {
                tbl = comp.getField().substring(0, comp.getField().indexOf('.')).trim();
            }
            if (tbl != null && !tbl.isBlank()) {
                tables.add(tbl.trim().toLowerCase());
            }
        } else if (predicate instanceof CompoundPredicate comp) {
            if (comp.getPredicates() != null) {
                for (Predicate sub : comp.getPredicates()) {
                    tables.addAll(extractReferencedTables(sub));
                }
            }
        }
        return tables;
    }

    private static boolean hasAnyColumnsInSubtree(
            Long moduleId, ModuleTreeDTO moduleTree, Map<Long, Set<String>> colsByModule) {
        if (moduleId == null || colsByModule == null) {
            return false;
        }
        Set<String> direct = colsByModule.get(moduleId);
        if (direct != null && !direct.isEmpty()) {
            return true;
        }
        for (Long childMid : resolveChildModuleIds(moduleTree, moduleId)) {
            if (hasAnyColumnsInSubtree(childMid, moduleTree, colsByModule)) {
                return true;
            }
        }
        return false;
    }

    private static List<Long> resolveChildModuleIds(ModuleTreeDTO tree, Long parentId) {
        if (tree == null || parentId == null) {
            return Collections.emptyList();
        }
        List<Long> direct = tree.getChildModuleIds(parentId);
        if (direct != null && !direct.isEmpty()) {
            return direct;
        }
        if ((tree.getParentToChildren() == null || tree.getParentToChildren().isEmpty())
                && tree.getModuleMap() != null) {
            List<Long> fallback = new ArrayList<>();
            for (ModuleInfo m : tree.getModuleMap().values()) {
                if (m == null || m.getModuleId() == null || m.getModuleId().equals(parentId)) {
                    continue;
                }
                if (parentId.equals(m.getParentId())
                        || (m.getParentId() == null && parentId.equals(tree.getRootModuleId()))) {
                    fallback.add(m.getModuleId());
                }
            }
            return fallback;
        }
        return Collections.emptyList();
    }

    private static List<SortItem> filterSortsForModule(
            List<SortItem> allSorts, Set<String> declaredTables) {
        if (allSorts == null
                || allSorts.isEmpty()
                || declaredTables == null
                || declaredTables.isEmpty()) {
            return Collections.emptyList();
        }
        List<SortItem> matched = new ArrayList<>();
        for (SortItem s : allSorts) {
            if (s != null
                    && s.getTableName() != null
                    && declaredTables.contains(s.getTableName().trim().toLowerCase())) {
                matched.add(s);
            }
        }
        return matched;
    }

    // ==================== 2. 1:N 分支校验与表头字段收集 ====================

    public static List<Long> collectAndMaskHeaderFields(
            MenuHeaderColumnResp headerRoot,
            MenuQueryReq req,
            ModuleTreeDTO moduleTree,
            ProjectTableRelationsDTO projectRelations) {
        Map<Long, Long> branchCache = new HashMap<>();
        Long active1NBranch =
                resolveActiveOneToManyBranch(
                        headerRoot, req, moduleTree, projectRelations, branchCache);
        List<Long> checkedFieldIds = new ArrayList<>();
        processHeaderAndCollectFieldIds(
                headerRoot,
                checkedFieldIds,
                active1NBranch,
                moduleTree,
                projectRelations,
                branchCache);
        return checkedFieldIds;
    }

    public static List<Long> collectCheckedFieldIds(MenuHeaderColumnResp node) {
        List<Long> result = new ArrayList<>();
        collectCheckedFieldIdsRecursive(node, result);
        return result;
    }

    private static void collectCheckedFieldIdsRecursive(
            MenuHeaderColumnResp node, List<Long> result) {
        if (node == null) {
            return;
        }
        if (node.getFieldId() != null) {
            result.add(node.getFieldId());
        }
        if (node.getChildren() != null) {
            for (MenuHeaderColumnResp child : node.getChildren()) {
                collectCheckedFieldIdsRecursive(child, result);
            }
        }
    }

    private static Long resolveActiveOneToManyBranch(
            MenuHeaderColumnResp headerRoot,
            MenuQueryReq req,
            ModuleTreeDTO moduleTree,
            ProjectTableRelationsDTO projectRelations,
            Map<Long, Long> branchCache) {
        if (headerRoot == null || req == null) {
            return null;
        }

        Set<Long> hitOneToManyBranches = new LinkedHashSet<>();
        if (!CollectionUtils.isEmpty(req.getFilters())) {
            for (MenuFlatFieldFilterItem flatFilter : req.getFilters()) {
                if (flatFilter != null && flatFilter.getFieldId() != null) {
                    Long branch =
                            resolveBranchByFieldId(
                                    flatFilter.getFieldId(),
                                    headerRoot,
                                    moduleTree,
                                    projectRelations,
                                    branchCache);
                    if (branch != null) {
                        hitOneToManyBranches.add(branch);
                    }
                }
            }
        }
        if (!CollectionUtils.isEmpty(req.getSorts())) {
            for (FieldSortItem sortItem : req.getSorts()) {
                if (sortItem != null && sortItem.getFieldId() != null) {
                    Long branch =
                            resolveBranchByFieldId(
                                    sortItem.getFieldId(),
                                    headerRoot,
                                    moduleTree,
                                    projectRelations,
                                    branchCache);
                    if (branch != null) {
                        hitOneToManyBranches.add(branch);
                    }
                }
            }
        }

        if (hitOneToManyBranches.size() > 1) {
            throw new BusinessException(
                    400,
                    "带搜索或排序条件查询时，最多只允许 1 个 1:N 模块参与筛选，当前搜索条件涉及 "
                            + hitOneToManyBranches.size()
                            + " 个 1:N 模块分支");
        }

        return hitOneToManyBranches.isEmpty() ? null : hitOneToManyBranches.iterator().next();
    }

    private static Long resolveBranchByFieldId(
            Long fieldId,
            MenuHeaderColumnResp headerRoot,
            ModuleTreeDTO moduleTree,
            ProjectTableRelationsDTO projectRelations,
            Map<Long, Long> branchCache) {
        if (fieldId == null) {
            return null;
        }
        Long fieldModuleId = moduleTree != null ? moduleTree.getModuleIdByFieldId(fieldId) : null;
        if (fieldModuleId == null) {
            fieldModuleId = findModuleIdByFieldIdInHeader(headerRoot, fieldId);
        }
        return resolveTopOneToManyBranchWithCache(
                fieldModuleId, moduleTree, projectRelations, branchCache);
    }

    private static void processHeaderAndCollectFieldIds(
            MenuHeaderColumnResp node,
            List<Long> checkedFieldIds,
            Long active1NBranch,
            ModuleTreeDTO moduleTree,
            ProjectTableRelationsDTO projectRelations,
            Map<Long, Long> branchCache) {
        if (node == null) {
            return;
        }

        boolean isOther1NBranch = false;
        if (active1NBranch != null) {
            Long nodeModuleId = node.getModuleId();
            if (nodeModuleId == null && node.getFieldId() != null && moduleTree != null) {
                nodeModuleId = moduleTree.getModuleIdByFieldId(node.getFieldId());
            }
            Long top1NBranch =
                    resolveTopOneToManyBranchWithCache(
                            nodeModuleId, moduleTree, projectRelations, branchCache);
            isOther1NBranch = top1NBranch != null && !top1NBranch.equals(active1NBranch);
        }

        if (isOther1NBranch) {
            node.setSearchType(null);
            node.setSorted(0);
        } else if (node.getFieldId() != null) {
            checkedFieldIds.add(node.getFieldId());
        }

        if (node.getChildren() != null) {
            for (MenuHeaderColumnResp child : node.getChildren()) {
                processHeaderAndCollectFieldIds(
                        child,
                        checkedFieldIds,
                        active1NBranch,
                        moduleTree,
                        projectRelations,
                        branchCache);
            }
        }
    }

    private static Long resolveTopOneToManyBranchWithCache(
            Long moduleId,
            ModuleTreeDTO moduleTree,
            ProjectTableRelationsDTO projectRelations,
            Map<Long, Long> branchCache) {
        if (moduleId == null
                || moduleTree == null
                || moduleTree.getRootModuleId() == null
                || moduleId.equals(moduleTree.getRootModuleId())) {
            return null;
        }
        if (branchCache != null && branchCache.containsKey(moduleId)) {
            return branchCache.get(moduleId);
        }
        Long branch = moduleTree.resolveTopOneToManyBranch(moduleId, projectRelations);
        if (branchCache != null) {
            branchCache.put(moduleId, branch);
        }
        return branch;
    }

    private static Long findModuleIdByFieldIdInHeader(MenuHeaderColumnResp node, Long fieldId) {
        if (node == null || fieldId == null) {
            return null;
        }
        if (fieldId.equals(node.getFieldId())) {
            return node.getModuleId();
        }
        if (node.getChildren() != null) {
            for (MenuHeaderColumnResp child : node.getChildren()) {
                Long found = findModuleIdByFieldIdInHeader(child, fieldId);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // ==================== 3. MenuListReq 平铺表名/过滤/排序映射 ====================

    public static List<String> toColumns(
            List<MenuTableSpec> menuTableSpecs,
            ModuleTreeDTO moduleTree,
            Function<Long, Long> menuToModuleResolver) {
        Set<String> columns = new LinkedHashSet<>();
        if (CollectionUtils.isEmpty(menuTableSpecs)) {
            // 为 null 或空时默认查当前模块及其关联子表的全部字段
            if (moduleTree != null && moduleTree.getModuleMap() != null) {
                for (ModuleInfo mod : moduleTree.getModuleMap().values()) {
                    if (mod != null && mod.getFields() != null) {
                        String modTable = mod.getPrimaryTable();
                        for (ModuleFieldDTO f : mod.getFields()) {
                            if (f != null
                                    && f.getColumnName() != null
                                    && !f.getColumnName().isBlank()) {
                                String table =
                                        (f.getTableName() != null && !f.getTableName().isBlank())
                                                ? f.getTableName().trim()
                                                : modTable;
                                if (table != null && !table.isBlank()) {
                                    columns.add(table + "." + f.getColumnName().trim());
                                }
                            }
                        }
                    }
                }
            }
            return new ArrayList<>(columns);
        }

        for (MenuTableSpec mSpec : menuTableSpecs) {
            if (mSpec == null) {
                continue;
            }
            Long modId =
                    resolveModuleIdForTable(
                            mSpec.getMenuId(),
                            mSpec.getTableName(),
                            moduleTree,
                            menuToModuleResolver);
            ModuleInfo mod = moduleTree != null ? moduleTree.getModule(modId) : null;
            String modPrimaryTable = mod != null ? mod.getPrimaryTable() : null;

            String rawTable = mSpec.getTableName() != null ? mSpec.getTableName().trim() : null;
            String table = rawTable;
            if (table == null || table.isBlank()) {
                table = modPrimaryTable;
            } else if (modPrimaryTable != null
                    && !table.equalsIgnoreCase(modPrimaryTable)
                    && !moduleTree.containsPhysicalTable(rawTable)) {
                log.warn(
                        "MenuTableSpec 中指定的物理表名 [{}] 不在当前模块树的物理表中，根据模块 ID [{}] 自动校正为真实物理主表 [{}]",
                        rawTable,
                        modId,
                        modPrimaryTable);
                table = modPrimaryTable;
            }

            if (table != null && !table.isBlank()) {
                if (CollectionUtils.isEmpty(mSpec.getColumns())
                        || mSpec.getColumns().contains("*")
                        || mSpec.getColumns().contains(table + ".*")) {
                    // 为 null 或空（或包含 *）时代表投影该表在模块元数据定义中的所有列
                    boolean addedFromMeta = false;
                    if (mod != null && mod.getFields() != null) {
                        for (ModuleFieldDTO f : mod.getFields()) {
                            if (f != null
                                    && f.getColumnName() != null
                                    && !f.getColumnName().isBlank()) {
                                String fTable =
                                        (f.getTableName() != null && !f.getTableName().isBlank())
                                                ? f.getTableName().trim()
                                                : table;
                                if (fTable.equalsIgnoreCase(table)) {
                                    columns.add(fTable + "." + f.getColumnName().trim());
                                    addedFromMeta = true;
                                }
                            }
                        }
                    }
                    if (!addedFromMeta && moduleTree != null && moduleTree.getFieldMap() != null) {
                        for (ModuleFieldDTO f : moduleTree.getFieldMap().values()) {
                            if (f != null
                                    && f.getColumnName() != null
                                    && !f.getColumnName().isBlank()) {
                                String fTable =
                                        (f.getTableName() != null && !f.getTableName().isBlank())
                                                ? f.getTableName().trim()
                                                : null;
                                if (table.equalsIgnoreCase(fTable)) {
                                    columns.add(table + "." + f.getColumnName().trim());
                                    addedFromMeta = true;
                                }
                            }
                        }
                    }
                    if (!addedFromMeta) {
                        columns.add(table + ".*");
                    }
                } else {
                    for (String c : mSpec.getColumns()) {
                        if (c != null && !c.isBlank()) {
                            String col = c.trim();
                            if (col.contains(".")) {
                                columns.add(col);
                            } else {
                                columns.add(table + "." + col);
                            }
                        }
                    }
                }
            }
        }
        return new ArrayList<>(columns);
    }

    public static Map<String, Set<String>> buildRequestedTableColumnsMap(
            List<MenuTableSpec> menuTableSpecs,
            ModuleTreeDTO moduleTree,
            Function<Long, Long> menuToModuleResolver) {
        if (CollectionUtils.isEmpty(menuTableSpecs)) {
            return null;
        }
        Map<String, Set<String>> requestedTableColumnsMap = new HashMap<>();
        for (MenuTableSpec mSpec : menuTableSpecs) {
            if (mSpec == null || mSpec.getTableName() == null || mSpec.getTableName().isBlank()) {
                continue;
            }
            String rawTable = mSpec.getTableName().trim().toLowerCase();
            Set<String> cols = null;
            if (mSpec.getColumns() != null
                    && !mSpec.getColumns().isEmpty()
                    && !mSpec.getColumns().contains("*")) {
                cols = new HashSet<>();
                for (String c : mSpec.getColumns()) {
                    if (c != null && !c.isBlank()) {
                        String cleanC =
                                c.contains(".")
                                        ? c.substring(c.lastIndexOf('.') + 1).trim()
                                        : c.trim();
                        cols.add(cleanC.toLowerCase());
                    }
                }
            }
            addTableColumnsToMap(requestedTableColumnsMap, rawTable, cols);

            Long modId =
                    resolveModuleIdForTable(
                            mSpec.getMenuId(),
                            mSpec.getTableName(),
                            moduleTree,
                            menuToModuleResolver);
            ModuleInfo mod = moduleTree != null ? moduleTree.getModule(modId) : null;
            if (mod != null && mod.getPrimaryTable() != null && !mod.getPrimaryTable().isBlank()) {
                addTableColumnsToMap(
                        requestedTableColumnsMap, mod.getPrimaryTable().trim().toLowerCase(), cols);
            }
        }
        return requestedTableColumnsMap;
    }

    private static void addTableColumnsToMap(
            Map<String, Set<String>> map, String table, Set<String> cols) {
        if (table == null || table.isBlank()) {
            return;
        }
        String key = table.trim().toLowerCase();
        if (!map.containsKey(key)) {
            map.put(key, cols != null ? new HashSet<>(cols) : null);
        } else {
            Set<String> existing = map.get(key);
            if (existing != null) {
                if (cols == null) {
                    map.put(key, null);
                } else {
                    existing.addAll(cols);
                }
            }
        }
    }

    public static List<FilterItem> toFlatFilters(
            List<FlatFilterItem> flatFilters,
            ModuleTreeDTO moduleTree,
            Function<Long, Long> menuToModuleResolver) {
        if (CollectionUtils.isEmpty(flatFilters)) {
            return new ArrayList<>();
        }
        String primaryTable = moduleTree != null ? moduleTree.getPrimaryTable() : null;
        List<FilterItem> result = new ArrayList<>();
        for (FlatFilterItem f : flatFilters) {
            if (f == null || f.getColumnName() == null || f.getColumnName().isBlank()) {
                continue;
            }
            String table =
                    (f.getTableName() != null && !f.getTableName().isBlank())
                            ? f.getTableName().trim()
                            : primaryTable;
            Long modId =
                    resolveModuleIdForTable(f.getMenuId(), table, moduleTree, menuToModuleResolver);
            result.add(
                    FilterItem.builder()
                            .field(table + "." + f.getColumnName().trim())
                            .tableName(table)
                            .columnName(f.getColumnName().trim())
                            .operator(f.getOperator() != null ? f.getOperator() : "EQ")
                            .value(f.getValue())
                            .build());
        }
        return result;
    }

    public static List<SortItem> toFlatSorts(
            List<FlatSortItem> flatSorts,
            ModuleTreeDTO moduleTree,
            Function<Long, Long> menuToModuleResolver) {
        if (CollectionUtils.isEmpty(flatSorts)) {
            return new ArrayList<>();
        }
        String primaryTable = moduleTree != null ? moduleTree.getPrimaryTable() : null;
        List<SortItem> result = new ArrayList<>();
        for (FlatSortItem s : flatSorts) {
            if (s == null || s.getColumnName() == null || s.getColumnName().isBlank()) {
                continue;
            }
            String table =
                    (s.getTableName() != null && !s.getTableName().isBlank())
                            ? s.getTableName().trim()
                            : primaryTable;
            Long modId =
                    resolveModuleIdForTable(s.getMenuId(), table, moduleTree, menuToModuleResolver);
            result.add(
                    SortItem.builder()
                            .field(table + "." + s.getColumnName().trim())
                            .direction(s.getDirection() != null ? s.getDirection() : "DESC")
                            .build());
        }
        return result;
    }

    public static Long resolveModuleIdForTable(
            Long specificMenuId,
            String tableName,
            ModuleTreeDTO moduleTree,
            Function<Long, Long> menuToModuleResolver) {
        if (specificMenuId != null && menuToModuleResolver != null) {
            Long mappedModId = menuToModuleResolver.apply(specificMenuId);
            if (mappedModId != null
                    && moduleTree != null
                    && moduleTree.getModule(mappedModId) != null) {
                return mappedModId;
            }
        }
        return moduleTree != null ? moduleTree.findModuleIdByTableName(tableName) : null;
    }

    // ==================== 4. DataRow -> DataRecordItem / ModuleDataNode 树转换 ====================

    public static DataRecordItem toDataRecordItem(DataRow dataRow) {
        return toDataRecordItem(dataRow, null, null, null, (ModuleTreeDTO) null);
    }

    public static DataRecordItem toDataRecordItem(DataRow dataRow, ModuleTreeDTO moduleTree) {
        return toDataRecordItem(dataRow, null, null, null, moduleTree);
    }

    public static DataRecordItem toDataRecordItem(
            DataRow dataRow,
            Map<Long, Long> moduleToMenuMap,
            Map<String, Set<String>> requestedTableColumnsMap,
            Function<Long, Long> moduleToMenuFallback) {
        return toDataRecordItem(
                dataRow,
                moduleToMenuMap,
                requestedTableColumnsMap,
                moduleToMenuFallback,
                (ModuleTreeDTO) null);
    }

    public static DataRecordItem toDataRecordItem(
            DataRow dataRow,
            Map<Long, Long> moduleToMenuMap,
            Map<String, Set<String>> requestedTableColumnsMap,
            Function<Long, Long> moduleToMenuFallback,
            ModuleTreeDTO moduleTree) {
        if (dataRow == null) {
            return DataRecordItem.builder().row(Collections.emptyMap()).build();
        }

        Map<String, Object> flatRow = new LinkedHashMap<>();
        List<ModuleDataNode> childrenNodes = new ArrayList<>();

        if (dataRow.getTables() != null) {
            for (String tableName : dataRow.getTables().keySet()) {
                if (tableName == null || tableName.isBlank()) {
                    continue;
                }
                String normalizedTable = tableName.trim().toLowerCase();
                if (requestedTableColumnsMap != null
                        && !requestedTableColumnsMap.containsKey(normalizedTable)) {
                    continue;
                }
                Set<String> allowedCols =
                        requestedTableColumnsMap != null
                                ? requestedTableColumnsMap.get(normalizedTable)
                                : null;

                Map<String, Object> tableData = dataRow.getTable(tableName);
                if (tableData != null) {
                    for (Map.Entry<String, Object> colEntry : tableData.entrySet()) {
                        if (colEntry.getKey() != null && !colEntry.getKey().isBlank()) {
                            String rawCol = colEntry.getKey().trim();
                            String cleanCol =
                                    rawCol.contains(".")
                                            ? rawCol.substring(rawCol.lastIndexOf('.') + 1).trim()
                                            : rawCol;
                            if (allowedCols != null
                                    && !allowedCols.contains(cleanCol.toLowerCase())) {
                                continue;
                            }
                            flatRow.put(tableName + "." + cleanCol, colEntry.getValue());
                        }
                    }
                }
            }
        }

        if (dataRow.getChildren() != null) {
            for (DataBlock childNode : dataRow.getChildren()) {
                if (childNode == null) {
                    continue;
                }
                String childView = childNode.getView();
                Long subModId = resolveSubModuleId(childNode, moduleTree);
                String resolvedModuleCode = childView;
                if (subModId != null && moduleTree != null) {
                    ModuleInfo modInfo = moduleTree.getModule(subModId);
                    if (modInfo != null
                            && modInfo.getModuleCode() != null
                            && !modInfo.getModuleCode().isBlank()) {
                        resolvedModuleCode = modInfo.getModuleCode();
                    }
                }

                List<DataRecordItem> childItems = new ArrayList<>();
                if (childNode.getRecords() != null) {
                    for (DataRow subRow : childNode.getRecords()) {
                        if (subRow != null) {
                            DataRecordItem childItem =
                                    toDataRecordItem(
                                            subRow,
                                            moduleToMenuMap,
                                            requestedTableColumnsMap,
                                            moduleToMenuFallback,
                                            moduleTree);
                            if ((childItem.getRow() != null && !childItem.getRow().isEmpty())
                                    || (childItem.getChildren() != null
                                            && !childItem.getChildren().isEmpty())) {
                                childItems.add(childItem);
                            }
                        }
                    }
                }

                if (childItems.isEmpty()) {
                    continue;
                }

                Long subMenuId =
                        (moduleToMenuMap != null && subModId != null)
                                ? moduleToMenuMap.get(subModId)
                                : null;
                if (subMenuId == null && subModId != null && moduleToMenuFallback != null) {
                    subMenuId = moduleToMenuFallback.apply(subModId);
                }

                childrenNodes.add(
                        ModuleDataNode.builder()
                                .moduleId(subModId)
                                .moduleCode(resolvedModuleCode)
                                .menuId(subMenuId)
                                .record(childItems)
                                .build());
            }
        }

        List<ModuleDataNode> finalChildren = childrenNodes.isEmpty() ? null : childrenNodes;
        return DataRecordItem.builder().row(flatRow).children(finalChildren).build();
    }

    /** 根据 DataBlock.view（moduleCode / primaryTable / 数字视图名）结合 ModuleTreeDTO 解析子模块 ID */
    private static Long resolveSubModuleId(DataBlock childNode, ModuleTreeDTO moduleTree) {
        if (childNode == null) {
            return null;
        }
        String childView = childNode.getView();
        if (childView == null || childView.isBlank()) {
            return null;
        }
        String trimmed = childView.trim();
        if (trimmed.matches("\\d+")) {
            try {
                return Long.parseLong(trimmed);
            } catch (NumberFormatException ignored) {
            }
        }
        if (moduleTree != null && moduleTree.getModuleMap() != null) {
            for (ModuleInfo info : moduleTree.getModuleMap().values()) {
                if (info != null) {
                    if (info.getModuleCode() != null
                            && trimmed.equalsIgnoreCase(info.getModuleCode().trim())) {
                        return info.getModuleId();
                    }
                    if (info.getPrimaryTable() != null
                            && !info.getPrimaryTable().isBlank()
                            && trimmed.equalsIgnoreCase(info.getPrimaryTable().trim())) {
                        return info.getModuleId();
                    }
                }
            }
        }
        return null;
    }

    /** 兼容历史重载：moduleCode 现已由底层 DataBlock 原生携带，不再依赖外部 moduleCodeResolver */
    public static DataRecordItem toDataRecordItem(
            DataRow dataRow,
            Map<Long, Long> moduleToMenuMap,
            Map<String, Set<String>> requestedTableColumnsMap,
            Function<Long, Long> moduleToMenuFallback,
            Function<Long, String> moduleCodeResolver) {
        return toDataRecordItem(
                dataRow,
                moduleToMenuMap,
                requestedTableColumnsMap,
                moduleToMenuFallback,
                (ModuleTreeDTO) null);
    }

    public static List<DataRecordItem> extractTargetRecords(
            List<DataRecordItem> rootRecords, Long targetModuleId) {
        List<DataRecordItem> targetItems = new ArrayList<>();
        if (CollectionUtils.isEmpty(rootRecords) || targetModuleId == null) {
            return targetItems;
        }
        for (DataRecordItem item : rootRecords) {
            if (item != null && item.getChildren() != null) {
                collectRecordsFromChildren(item.getChildren(), targetModuleId, targetItems);
            }
        }
        return targetItems;
    }

    private static void collectRecordsFromChildren(
            List<ModuleDataNode> children, Long targetModuleId, List<DataRecordItem> targetItems) {
        if (CollectionUtils.isEmpty(children) || targetModuleId == null) {
            return;
        }
        for (ModuleDataNode node : children) {
            if (node == null) {
                continue;
            }
            if (targetModuleId.equals(node.getModuleId())
                    || String.valueOf(targetModuleId).equalsIgnoreCase(node.getModuleCode())) {
                if (node.getRecord() != null) {
                    targetItems.addAll(node.getRecord());
                }
            } else if (node.getRecord() != null) {
                for (DataRecordItem subItem : node.getRecord()) {
                    if (subItem != null && subItem.getChildren() != null) {
                        collectRecordsFromChildren(
                                subItem.getChildren(), targetModuleId, targetItems);
                    }
                }
            }
        }
    }

    // ==================== 6. 变更单表头修饰、投影与单表映射 ====================

    public static List<String> collectChangeTargetColumns(
            MenuHeaderColumnResp headerRoot, ModuleTreeDTO moduleTree, String primaryTable) {
        return collectChangeTargetColumns(headerRoot, moduleTree, primaryTable, null);
    }

    public static List<String> collectChangeTargetColumns(
            MenuHeaderColumnResp headerRoot,
            ModuleTreeDTO moduleTree,
            String primaryTable,
            Set<String> actualChangeTableColumns) {
        Set<String> targetColumns =
                new LinkedHashSet<>(
                        List.of("id", "change_no", "source_id", "modify_reason", "read_status"));

        List<Long> checkedFieldIds = collectCheckedFieldIds(headerRoot);
        if (!CollectionUtils.isEmpty(checkedFieldIds)) {
            for (Long fId : checkedFieldIds) {
                if (fId == null) {
                    continue;
                }
                try {
                    PhysicalFieldRef ref = resolve(moduleTree, fId);
                    if (primaryTable.equalsIgnoreCase(ref.tableName())
                            && ref.columnName() != null
                            && !ref.columnName().isBlank()) {
                        targetColumns.add(ref.columnName().trim());
                    }
                } catch (Exception ignored) {
                    // 忽略非物理字段或跨模块字段
                }
            }
        }

        if (moduleTree != null && moduleTree.getFieldMap() != null) {
            for (ModuleFieldDTO f : moduleTree.getFieldMap().values()) {
                if (f != null && f.getColumnName() != null && !f.getColumnName().isBlank()) {
                    String tbl =
                            (f.getTableName() == null || f.getTableName().isBlank())
                                    ? primaryTable
                                    : f.getTableName();
                    if (primaryTable.equalsIgnoreCase(tbl)) {
                        targetColumns.add(f.getColumnName().trim());
                    }
                }
            }
        }

        if (!CollectionUtils.isEmpty(actualChangeTableColumns)) {
            Set<String> lowerActual = new HashSet<>();
            for (String col : actualChangeTableColumns) {
                if (col != null && !col.isBlank()) {
                    lowerActual.add(col.trim().toLowerCase());
                }
            }
            for (String sysCol : BUILTIN_SYSTEM_COLUMNS) {
                if (lowerActual.contains(sysCol.toLowerCase())) {
                    targetColumns.add(sysCol);
                }
            }
        }
        return new ArrayList<>(targetColumns);
    }

    public static List<FilterItem> buildChangeFilters(
            MenuChangeQueryReq req,
            ModuleTreeDTO moduleTree,
            String primaryTable,
            String changeTable) {
        List<FilterItem> singleFilters = new ArrayList<>();
        singleFilters.add(FilterItem.eq(changeTable, "deleted", (byte) 0));

        if (req != null
                && req.getReadStatus() != null
                && (req.getReadStatus() == 0 || req.getReadStatus() == 1)) {
            singleFilters.add(FilterItem.eq(changeTable, "read_status", req.getReadStatus()));
        }

        if (req != null && req.getFilters() != null) {
            for (FilterItem f : toTableFilters(moduleTree, req.getFilters())) {
                if (f != null
                        && primaryTable.equalsIgnoreCase(f.getTableName())
                        && f.getColumnName() != null
                        && !f.getColumnName().isBlank()) {
                    f.setTableName(changeTable);
                    singleFilters.add(f);
                }
            }
        }
        return singleFilters;
    }

    public static List<SortItem> buildChangeSorts(
            MenuChangeQueryReq req, ModuleTreeDTO moduleTree, String primaryTable) {
        List<SortItem> singleSorts = new ArrayList<>();
        if (req != null && req.getSorts() != null) {
            for (SortItem s : toTableSorts(moduleTree, req.getSorts())) {
                if (s != null
                        && primaryTable.equalsIgnoreCase(s.getTableName())
                        && s.getColumnName() != null
                        && !s.getColumnName().isBlank()) {
                    singleSorts.add(s);
                }
            }
        }
        if (singleSorts.isEmpty()) {
            singleSorts.add(SortItem.desc("id"));
        }
        return singleSorts;
    }

    public static List<DataRow> toChangeDataRows(
            DataPage<List<Map<String, Object>>> pageResult, String primaryTable) {
        if (pageResult == null || pageResult.getData() == null) {
            return Collections.emptyList();
        }
        List<DataRow> rawRecords = new ArrayList<>(pageResult.getData().size());
        for (Map<String, Object> recordMap : pageResult.getData()) {
            if (recordMap == null) {
                continue;
            }
            Map<String, Object> merged = new LinkedHashMap<>(recordMap);
            if (merged.get("id") != null) {
                merged.put("change_id", merged.get("id"));
            }
            DataRow dr = new DataRow();
            dr.putTable(primaryTable, merged);
            rawRecords.add(dr);
        }
        return rawRecords;
    }

    /** 裁剪表头树，仅保留根模块 moduleId 对应的一级列节点（过滤掉所有其他子模块/菜单及更深层节点），并移除 sorted 和 searchType */
    public static MenuHeaderColumnResp pruneHeaderToFirstLevel(MenuHeaderColumnResp headerRoot) {
        if (headerRoot == null) {
            return null;
        }
        Long rootModuleId = headerRoot.getModuleId();
        MenuHeaderColumnResp pruned = copyHeaderColumn(headerRoot);
        pruned.setSorted(null);
        pruned.setSearchType(null);
        pruned.setFixed(null);
        if (headerRoot.getChildren() != null) {
            List<MenuHeaderColumnResp> firstLevel = new ArrayList<>();
            for (MenuHeaderColumnResp child : headerRoot.getChildren()) {
                if (child == null) {
                    continue;
                }
                if (rootModuleId != null && !rootModuleId.equals(child.getModuleId())) {
                    continue;
                }
                if (Integer.valueOf(0).equals(child.getNodeType())
                        && child.getFieldId() == null
                        && child.getDataIndex() == null) {
                    continue;
                }
                if (isExcludedChangeHeaderColumn(child)) {
                    continue;
                }
                MenuHeaderColumnResp prunedChild = copyHeaderColumn(child);
                prunedChild.setChildren(null);
                prunedChild.setSorted(null);
                prunedChild.setSearchType(null);
                prunedChild.setFixed(null);
                renameChangeHeaderColumn(prunedChild);
                firstLevel.add(prunedChild);
            }
            // 变更申请单表头排布规则：变更单号放最前面，变更原因放在变更表中的创建人之前
            reorderChangeHeaderColumns(firstLevel);
            pruned.setChildren(firstLevel);
        }
        return pruned;
    }

    private static void renameChangeHeaderColumn(MenuHeaderColumnResp column) {
        if (column == null) {
            return;
        }
        if (isCreatorColumn(column)) {
            column.setLabel("变更申请人");
        } else if (isCreateTimeColumn(column)) {
            column.setLabel("消息发送时间");
        }
    }

    private static void reorderChangeHeaderColumns(List<MenuHeaderColumnResp> columns) {
        if (CollectionUtils.isEmpty(columns)) {
            return;
        }
        MenuHeaderColumnResp changeNoNode = null;
        MenuHeaderColumnResp modifyReasonNode = null;

        Iterator<MenuHeaderColumnResp> iterator = columns.iterator();
        while (iterator.hasNext()) {
            MenuHeaderColumnResp node = iterator.next();
            if (node == null) {
                continue;
            }
            if (isChangeNoColumn(node)) {
                if (changeNoNode == null) {
                    changeNoNode = node;
                }
                iterator.remove();
            } else if (isModifyReasonColumn(node)) {
                if (modifyReasonNode == null) {
                    modifyReasonNode = node;
                }
                iterator.remove();
            }
        }

        if (changeNoNode != null) {
            columns.add(0, changeNoNode);
        }

        if (modifyReasonNode != null) {
            int creatorIndex = -1;
            for (int i = 0; i < columns.size(); i++) {
                if (isCreatorColumn(columns.get(i))) {
                    creatorIndex = i;
                    break;
                }
            }
            if (creatorIndex >= 0) {
                columns.add(creatorIndex, modifyReasonNode);
            } else {
                columns.add(modifyReasonNode);
            }
        }
    }

    private static boolean isChangeNoColumn(MenuHeaderColumnResp node) {
        if (node == null) {
            return false;
        }
        String di = node.getDataIndex();
        if (di != null) {
            String lower = di.trim().toLowerCase();
            if (lower.equals("change_no") || lower.endsWith(".change_no")) {
                return true;
            }
        }
        String label = node.getLabel();
        return label != null && label.contains("变更单号");
    }

    private static boolean isModifyReasonColumn(MenuHeaderColumnResp node) {
        if (node == null) {
            return false;
        }
        String di = node.getDataIndex();
        if (di != null) {
            String lower = di.trim().toLowerCase();
            if (lower.equals("modify_reason") || lower.endsWith(".modify_reason")) {
                return true;
            }
        }
        String label = node.getLabel();
        return label != null && label.contains("变更原因");
    }

    private static boolean isCreatorColumn(MenuHeaderColumnResp node) {
        if (node == null) {
            return false;
        }
        String di = node.getDataIndex();
        if (di != null) {
            String lower = di.trim().toLowerCase();
            if (lower.equals("created_by")
                    || lower.endsWith(".created_by")
                    || lower.equals("create_by")
                    || lower.endsWith(".create_by")) {
                return true;
            }
        }
        String label = node.getLabel();
        return label != null && (label.contains("创建人") || label.contains("变更申请人"));
    }

    private static boolean isCreateTimeColumn(MenuHeaderColumnResp node) {
        if (node == null) {
            return false;
        }
        String di = node.getDataIndex();
        if (di != null) {
            String lower = di.trim().toLowerCase();
            if (lower.equals("created_date")
                    || lower.endsWith(".created_date")
                    || lower.equals("create_date")
                    || lower.endsWith(".create_date")
                    || lower.equals("create_time")
                    || lower.endsWith(".create_time")
                    || lower.equals("created_time")
                    || lower.endsWith(".created_time")) {
                return true;
            }
        }
        String label = node.getLabel();
        return label != null && (label.contains("创建时间") || label.contains("消息发送时间"));
    }

    private static MenuHeaderColumnResp copyHeaderColumn(MenuHeaderColumnResp node) {
        if (node == null) {
            return null;
        }
        return MenuHeaderColumnResp.builder()
                .sourceId(node.getSourceId())
                .label(node.getLabel())
                .colors(node.getColors() != null ? new ArrayList<>(node.getColors()) : null)
                .fieldId(node.getFieldId())
                .moduleId(node.getModuleId())
                .dataIndex(node.getDataIndex())
                .relationWord(node.getRelationWord())
                .nodeType(node.getNodeType())
                .width(node.getWidth())
                .sorted(node.getSorted())
                .searchType(node.getSearchType())
                .businessTypeCode(node.getBusinessTypeCode())
                .businessTypeName(node.getBusinessTypeName())
                .unitDataIndex(node.getUnitDataIndex())
                .ellipsis(node.getEllipsis())
                .fixed(node.getFixed())
                .sortValue(node.getSortValue())
                .children(node.getChildren())
                .build();
    }

    /** 确保变更单固有属性字段 (change_no, modify_reason) 在表头树中存在 */
    public static void ensureChangeColumnsInHeader(
            MenuHeaderColumnResp headerRoot, String primaryTable, Long rootModuleId) {
        if (headerRoot == null) {
            return;
        }
        if (headerRoot.getChildren() == null) {
            headerRoot.setChildren(new ArrayList<>());
        } else if (!(headerRoot.getChildren() instanceof ArrayList)) {
            headerRoot.setChildren(new ArrayList<>(headerRoot.getChildren()));
        }

        List<MenuHeaderColumnResp> extraColumns = new ArrayList<>();
        addChangeColumnIfAbsent(
                extraColumns, headerRoot, primaryTable, rootModuleId, "change_no", "变更单号", 180);
        addChangeColumnIfAbsent(
                extraColumns, headerRoot, primaryTable, rootModuleId, "modify_reason", "变更原因", 200);

        if (!extraColumns.isEmpty()) {
            headerRoot.getChildren().addAll(0, extraColumns);
        }
    }

    private static boolean isExcludedChangeHeaderColumn(MenuHeaderColumnResp child) {
        if (child == null) {
            return false;
        }
        String label = child.getLabel();
        if (label != null
                && (label.contains("变更ID")
                        || label.contains("原单ID")
                        || label.contains("读取状态")
                        || label.contains("更新人")
                        || label.contains("修改人")
                        || label.contains("更新时间")
                        || label.contains("修改时间")
                        || label.contains("主体ID"))) {
            return true;
        }
        String di = child.getDataIndex();
        if (di != null) {
            String lower = di.trim().toLowerCase();
            return lower.equals("id")
                    || lower.endsWith(".id")
                    || lower.equals("source_id")
                    || lower.endsWith(".source_id")
                    || lower.equals("read_status")
                    || lower.endsWith(".read_status")
                    || lower.equals("updated_by")
                    || lower.endsWith(".updated_by")
                    || lower.equals("update_by")
                    || lower.endsWith(".update_by")
                    || lower.equals("updated_date")
                    || lower.endsWith(".updated_date")
                    || lower.equals("update_date")
                    || lower.endsWith(".update_date")
                    || lower.equals("updated_time")
                    || lower.endsWith(".updated_time")
                    || lower.equals("update_time")
                    || lower.endsWith(".update_time")
                    || lower.equals("subject_id")
                    || lower.endsWith(".subject_id");
        }
        return false;
    }

    private static void addChangeColumnIfAbsent(
            List<MenuHeaderColumnResp> extraColumns,
            MenuHeaderColumnResp headerRoot,
            String primaryTable,
            Long rootModuleId,
            String colName,
            String label,
            int width) {
        String targetDataIndex = primaryTable != null ? primaryTable + "." + colName : colName;
        if (!checkColumnRecursive(headerRoot, colName, targetDataIndex)) {
            extraColumns.add(
                    MenuHeaderColumnResp.builder()
                            .label(label)
                            .dataIndex(targetDataIndex)
                            .nodeType(1)
                            .moduleId(rootModuleId)
                            .width(width)
                            .build());
        }
    }

    private static boolean checkColumnRecursive(
            MenuHeaderColumnResp node, String colName, String targetDataIndex) {
        if (node == null) {
            return false;
        }
        String di = node.getDataIndex();
        if (di != null
                && (di.equalsIgnoreCase(targetDataIndex)
                        || di.equalsIgnoreCase(colName)
                        || di.endsWith("." + colName))) {
            return true;
        }
        if (node.getChildren() != null) {
            for (MenuHeaderColumnResp child : node.getChildren()) {
                if (checkColumnRecursive(child, colName, targetDataIndex)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ==================== 5. 系统影子/辅助投影列补齐与剥离 ====================

    /**
     * 平台内置系统级辅助列（主键、树形拓扑节点、软删除、状态、版本与审计字段等）。
     *
     * <p>与底层 {@code TableQueryValidator.BUILTIN_SYSTEM_COLUMNS}
     * 保持一致；当物理表中实际存在这些辅助列时，默认纳入查询投影并作为正式列返回。
     */
    public static final List<String> BUILTIN_SYSTEM_COLUMNS =
            List.of(
                    // 1. 主键与单据版本溯源
                    "id", // 物理主键
                    "business_no", // 业务单号
                    "version_no", // 乐观锁/单据版本号
                    "source_id", // 变更单对应原单据 ID
                    // 2. 单据状态与生命周期
                    "business_status", // 业务流程状态（如草稿、审批中、已生效）
                    "active", // 激活/启用标识（1=启用，0=停用）
                    "deleted", // 软删除标识（0=未删除，1=已删除）
                    // 3. 树形拓扑与行序
                    "node_id", // 树形节点唯一标识
                    "parent_node_id", // 树形父节点标识
                    "sort_order", // 同级/明细行排序号
                    // 4. 主体归属
                    "subject_id", // 业务主体/租户归属 ID
                    // 5. 审计字段
                    "created_by", // 创建人
                    "created_date", // 创建时间
                    "updated_by", // 最后修改人
                    "updated_date" // 最后修改时间
                    );

    /**
     * 为当前统一投影列名列表自动补齐各物理表实际存在的平台内置系统级辅助列。
     *
     * @param columns 当前统一投影列名列表（格式为 table.column）
     * @param moduleTree 模块元数据树
     * @param rootModuleId 根模块 ID
     * @param tableColumnsResolver 物理表实际列名解析函数（tableName -&gt; 物理列名集合）
     */
    public static void supplementSystemColumns(
            List<String> columns,
            ModuleTreeDTO moduleTree,
            Long rootModuleId,
            Function<String, Set<String>> tableColumnsResolver) {
        supplementSystemColumns(columns, moduleTree, rootModuleId, tableColumnsResolver, null);
    }

    /**
     * 为当前统一投影列名列表自动补齐各物理表实际存在的平台内置系统级辅助列，并同步放行 {@code requestedTableColumnsMap}。
     *
     * @param columns 当前统一投影列名列表（格式为 table.column）
     * @param moduleTree 模块元数据树
     * @param rootModuleId 根模块 ID
     * @param tableColumnsResolver 物理表实际列名解析函数（tableName -&gt; 物理列名集合）
     * @param requestedTableColumnsMap 调用方显式请求的表列白名单映射（可选，非空时同步放行补入的辅助列）
     */
    public static void supplementSystemColumns(
            List<String> columns,
            ModuleTreeDTO moduleTree,
            Long rootModuleId,
            Function<String, Set<String>> tableColumnsResolver,
            Map<String, Set<String>> requestedTableColumnsMap) {
        if (columns == null || tableColumnsResolver == null) {
            return;
        }

        Map<String, List<String>> resolvedSysColsCache = new HashMap<>();

        Map<String, Set<String>> existingColsByTable = new LinkedHashMap<>();
        for (String c : columns) {
            if (c == null || c.isBlank()) {
                continue;
            }
            int dot = c.indexOf('.');
            if (dot > 0) {
                String tbl = c.substring(0, dot).trim().toLowerCase();
                String col = c.substring(dot + 1).trim().toLowerCase();
                existingColsByTable.computeIfAbsent(tbl, k -> new LinkedHashSet<>()).add(col);
            }
        }

        if (moduleTree != null && rootModuleId != null) {
            ModuleInfo rootModule = moduleTree.getModule(rootModuleId);
            String primaryTable =
                    rootModule != null && rootModule.getPrimaryTable() != null
                            ? rootModule.getPrimaryTable().trim()
                            : null;
            if (primaryTable != null && !primaryTable.isBlank()) {
                String primaryLower = primaryTable.toLowerCase();
                if (!existingColsByTable.containsKey(primaryLower)) {
                    List<String> primarySysCols =
                            resolveActualSystemColumns(
                                    primaryTable, tableColumnsResolver, resolvedSysColsCache);
                    for (String sysCol : primarySysCols) {
                        columns.add(primaryTable + "." + sysCol);
                        existingColsByTable
                                .computeIfAbsent(primaryLower, k -> new LinkedHashSet<>())
                                .add(sysCol.toLowerCase());
                    }
                }
            }
        }

        for (Map.Entry<String, Set<String>> entry :
                new ArrayList<>(existingColsByTable.entrySet())) {
            String tableName = entry.getKey();
            Set<String> currentColsLower = entry.getValue();
            if (currentColsLower.contains("*")) {
                continue;
            }

            List<String> actualSysCols =
                    resolveActualSystemColumns(
                            tableName, tableColumnsResolver, resolvedSysColsCache);
            for (String sysCol : actualSysCols) {
                if (currentColsLower.add(sysCol.toLowerCase())) {
                    columns.add(tableName + "." + sysCol);
                }
            }

            if (requestedTableColumnsMap != null) {
                Set<String> allowedCols = requestedTableColumnsMap.get(tableName.toLowerCase());
                if (allowedCols != null) {
                    for (String sysCol : actualSysCols) {
                        allowedCols.add(sysCol.toLowerCase());
                    }
                }
            }
        }
    }

    private static List<String> resolveActualSystemColumns(
            String tableName,
            Function<String, Set<String>> tableColumnsResolver,
            Map<String, List<String>> cache) {
        String key = tableName.toLowerCase();
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        Set<String> physicalCols = null;
        try {
            physicalCols = tableColumnsResolver.apply(tableName);
        } catch (Exception ex) {
            log.warn("获取物理表 [{}] 实际列名集合失败: err={}", tableName, ex.getMessage());
        }
        if (CollectionUtils.isEmpty(physicalCols)) {
            cache.put(key, Collections.emptyList());
            return Collections.emptyList();
        }
        Set<String> lowerPhysical = new HashSet<>();
        for (String col : physicalCols) {
            if (col != null && !col.isBlank()) {
                lowerPhysical.add(col.trim().toLowerCase());
            }
        }
        List<String> matched = new ArrayList<>();
        for (String sysCol : BUILTIN_SYSTEM_COLUMNS) {
            if (lowerPhysical.contains(sysCol.toLowerCase())) {
                matched.add(sysCol);
            }
        }
        cache.put(key, matched);
        return matched;
    }

    /**
     * 主表系统级辅助/影子投影列名集合。
     *
     * <p>这些列用于驱动行级业务规则（如按钮控制、审批行标识、状态判定等）。 若模块定义了该列且前端表头未请求投影，物理查询时将自动补入底层 SELECT，
     * 并在结果组装返回前端时剥离移除，确保业务规则在内存中无感知计算，且不越权泄露给前端。
     */
    public static final Set<String> SYSTEM_SHADOW_COLUMNS = Set.of("nature");

    /**
     * 根据候选字段集合与模块元数据，自动在统一投影列名列表中补齐缺失的影子投影列。
     *
     * @param columns 当前统一投影列名列表（格式为 table.column）
     * @param moduleTree 模块元数据树
     * @param rootModuleId 根模块 ID
     * @return 本次实际额外补入的影子列标识集合（格式为 {@code tableName.columnName}），供后续结果剥离
     */
    public static Set<String> supplementShadowColumns(
            List<String> columns, ModuleTreeDTO moduleTree, Long rootModuleId) {
        return supplementShadowColumns(columns, moduleTree, rootModuleId, SYSTEM_SHADOW_COLUMNS);
    }

    /**
     * 根据候选字段集合与模块元数据，自动在统一投影列名列表中补齐缺失的影子投影列。
     *
     * @param columns 当前统一投影列名列表（格式为 table.column）
     * @param moduleTree 模块元数据树
     * @param rootModuleId 根模块 ID
     * @param candidateColumns 待检查的候选列名集合
     * @return 本次实际额外补入的影子列标识集合（格式为小写 {@code table.column}），供后续结果剥离
     */
    public static Set<String> supplementShadowColumns(
            List<String> columns,
            ModuleTreeDTO moduleTree,
            Long rootModuleId,
            Collection<String> candidateColumns) {
        if (columns == null
                || moduleTree == null
                || rootModuleId == null
                || candidateColumns == null
                || candidateColumns.isEmpty()) {
            return Collections.emptySet();
        }

        ModuleInfo rootModule = moduleTree.getModule(rootModuleId);
        String primaryTable =
                rootModule != null && rootModule.getPrimaryTable() != null
                        ? rootModule.getPrimaryTable().trim()
                        : null;

        Map<String, Set<String>> existingColsByTable = new LinkedHashMap<>();
        for (String c : columns) {
            if (c == null || c.isBlank()) {
                continue;
            }
            int dot = c.indexOf('.');
            if (dot > 0) {
                String tbl = c.substring(0, dot).trim().toLowerCase();
                String col = c.substring(dot + 1).trim().toLowerCase();
                existingColsByTable.computeIfAbsent(tbl, k -> new LinkedHashSet<>()).add(col);
            }
        }

        Set<String> supplementedKeys = new LinkedHashSet<>();

        for (String cand : candidateColumns) {
            if (cand == null || cand.isBlank()) {
                continue;
            }
            ShadowFieldTarget target =
                    resolveShadowFieldTarget(moduleTree, rootModuleId, primaryTable, cand.trim());
            if (target == null) {
                continue;
            }

            String targetTable = target.tableName();
            String targetTableLower = targetTable.toLowerCase();
            String realColName = target.columnName();
            String realColLower = realColName.toLowerCase();

            boolean isPrimary = primaryTable != null && primaryTable.equalsIgnoreCase(targetTable);
            Set<String> tableCols = existingColsByTable.get(targetTableLower);
            if (tableCols == null) {
                if (!isPrimary) {
                    // 非主表且当前查询未涉及该表，跳过
                    continue;
                }
                tableCols = new LinkedHashSet<>();
                existingColsByTable.put(targetTableLower, tableCols);
            }

            if (tableCols.contains("*")) {
                continue;
            }

            if (tableCols.add(realColLower)) {
                columns.add(targetTable + "." + realColName);
                supplementedKeys.add(targetTableLower + "." + realColLower);
            }
        }

        return supplementedKeys;
    }

    record ShadowFieldTarget(Long moduleId, String tableName, String columnName) {}

    private static ShadowFieldTarget resolveShadowFieldTarget(
            ModuleTreeDTO moduleTree, Long rootModuleId, String primaryTable, String candidate) {
        if (candidate.contains(".")) {
            String rawTable = candidate.substring(0, candidate.indexOf('.')).trim();
            String rawCol = candidate.substring(candidate.indexOf('.') + 1).trim();
            if (rawTable.isBlank() || rawCol.isBlank()) {
                return null;
            }
            Long modId = moduleTree.findModuleIdByTableName(rawTable);
            if (modId == null) {
                modId = rootModuleId;
            }
            ModuleInfo mod = moduleTree.getModule(modId);
            String realTable =
                    mod != null && mod.getPrimaryTable() != null
                            ? mod.getPrimaryTable().trim()
                            : rawTable;
            String realCol = findRealColumnName(mod, rawCol);
            if (realCol != null) {
                return new ShadowFieldTarget(modId, realTable, realCol);
            }
            if (moduleTree.getFieldMap() != null) {
                for (ModuleFieldDTO f : moduleTree.getFieldMap().values()) {
                    if (f != null
                            && rawTable.equalsIgnoreCase(f.getTableName())
                            && rawCol.equalsIgnoreCase(f.getColumnName())) {
                        Long fModId = moduleTree.getModuleIdByFieldId(f.getId());
                        return new ShadowFieldTarget(
                                fModId != null ? fModId : modId,
                                f.getTableName().trim(),
                                f.getColumnName().trim());
                    }
                }
            }
            return null;
        }

        // 不含表名前缀时，优先在主表中查找
        ModuleInfo rootMod = moduleTree.getModule(rootModuleId);
        String realColInRoot = findRealColumnName(rootMod, candidate);
        if (realColInRoot != null && primaryTable != null) {
            return new ShadowFieldTarget(rootModuleId, primaryTable, realColInRoot);
        }

        // 若主表无此列，检索模块树中其他所有子模块（跨表查找）
        if (moduleTree.getModuleMap() != null) {
            for (ModuleInfo subMod : moduleTree.getModuleMap().values()) {
                if (subMod == null
                        || (rootModuleId != null && rootModuleId.equals(subMod.getModuleId()))) {
                    continue;
                }
                String realCol = findRealColumnName(subMod, candidate);
                if (realCol != null
                        && subMod.getPrimaryTable() != null
                        && !subMod.getPrimaryTable().isBlank()) {
                    return new ShadowFieldTarget(
                            subMod.getModuleId(), subMod.getPrimaryTable().trim(), realCol);
                }
            }
        }

        return null;
    }

    private static String findRealColumnName(ModuleInfo mod, String targetCol) {
        if (mod == null || mod.getFields() == null) {
            return null;
        }
        for (ModuleFieldDTO f : mod.getFields()) {
            if (f != null
                    && f.getColumnName() != null
                    && f.getColumnName().trim().equalsIgnoreCase(targetCol)) {
                return f.getColumnName().trim();
            }
        }
        return null;
    }

    /**
     * 从最终组装的数据记录项树中剥离移除本次查询额外补充的影子投影列。
     *
     * <p>以 {@code table.column} 形式严格进行大小写无关的精确匹配剥离，避免误伤同名列。
     *
     * @param items 数据记录项列表
     * @param shadowKeys 待剥离的影子列集合（格式为 {@code table.column}）
     */
    public static void removeShadowColumns(
            List<DataRecordItem> items, Collection<String> shadowKeys) {
        if (CollectionUtils.isEmpty(items) || CollectionUtils.isEmpty(shadowKeys)) {
            return;
        }
        Set<String> normalizedShadowKeys = new HashSet<>();
        for (String k : shadowKeys) {
            if (k != null && !k.isBlank()) {
                normalizedShadowKeys.add(k.trim().toLowerCase());
            }
        }
        removeShadowColumnsInternal(items, normalizedShadowKeys);
    }

    private static void removeShadowColumnsInternal(
            List<DataRecordItem> items, Set<String> normalizedShadowKeys) {
        if (CollectionUtils.isEmpty(items) || CollectionUtils.isEmpty(normalizedShadowKeys)) {
            return;
        }
        for (DataRecordItem item : items) {
            if (item == null) {
                continue;
            }
            Map<String, Object> row = item.getRow();
            if (row != null && !row.isEmpty()) {
                List<String> keysToRemove = new ArrayList<>();
                for (String rawKey : row.keySet()) {
                    if (rawKey != null
                            && normalizedShadowKeys.contains(rawKey.trim().toLowerCase())) {
                        keysToRemove.add(rawKey);
                    }
                }
                for (String k : keysToRemove) {
                    row.remove(k);
                }
            }
            if (!CollectionUtils.isEmpty(item.getChildren())) {
                for (ModuleDataNode childNode : item.getChildren()) {
                    if (childNode != null && !CollectionUtils.isEmpty(childNode.getRecord())) {
                        removeShadowColumnsInternal(childNode.getRecord(), normalizedShadowKeys);
                    }
                }
            }
        }
    }
}
