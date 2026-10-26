package com.jdec.platform.common.biz.service.impl;

import com.jdec.platform.common.api.dto.request.FieldSortItem;
import com.jdec.platform.common.api.dto.request.FlatFilterItem;
import com.jdec.platform.common.api.dto.request.FlatSortItem;
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
import com.jdec.platform.data.api.dto.request.FilterItem;
import com.jdec.platform.data.api.dto.request.SortItem;
import com.jdec.platform.data.api.dto.request.TableSpec;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
 *   <li>将菜单表头与字段 ID 转换为 {@code data-api} 物理表投影规格 {@link TableSpec}、{@link FilterItem} 与 {@link
 *       SortItem}。
 *   <li>执行 1:N 模块分支冲突校验、未命中 1:N 分支表头禁用修饰及已勾选字段收集。
 *   <li>将平铺物理表查询请求（{@link MenuTableSpec}、{@link FlatFilterItem}、{@link FlatSortItem}）消歧映射为底层物理查询参数。
 *   <li>将底层 {@link DataRow} 树递归转换为前端契约的 {@link DataRecordItem} 与 {@link ModuleDataNode} 同构树。
 * </ul>
 */
@Slf4j
final class PhysicalQueryMapper {

    private PhysicalQueryMapper() {}

    // ==================== 1. 基于 fieldId 的物理表/列映射 ====================

    static PhysicalFieldRef resolve(ModuleTreeDTO tree, Long fieldId) {
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

    record PhysicalFieldRef(Long fieldId, Long moduleId, String tableName, String columnName) {}

    static List<TableSpec> toTableSpecs(ModuleTreeDTO tree, Collection<Long> fieldIds) {
        Map<ModuleTableKey, LinkedHashSet<String>> grouped = new LinkedHashMap<>();
        if (fieldIds != null) {
            for (Long fieldId : fieldIds) {
                if (fieldId == null) {
                    continue;
                }
                PhysicalFieldRef ref = resolve(tree, fieldId);
                grouped.computeIfAbsent(
                                new ModuleTableKey(ref.moduleId(), ref.tableName()),
                                key -> new LinkedHashSet<>())
                        .add(ref.columnName());
            }
        }
        List<TableSpec> specs = new ArrayList<>();
        for (Map.Entry<ModuleTableKey, LinkedHashSet<String>> entry : grouped.entrySet()) {
            specs.add(
                    TableSpec.of(
                            entry.getKey().moduleId(),
                            entry.getKey().tableName(),
                            new ArrayList<>(entry.getValue())));
        }
        return specs;
    }

    static List<FilterItem> toTableFilters(
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
                                .moduleId(ref.moduleId())
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

    static List<SortItem> toTableSorts(ModuleTreeDTO tree, Collection<FieldSortItem> sorts) {
        List<SortItem> result = new ArrayList<>();
        if (sorts == null) {
            return result;
        }
        for (FieldSortItem sort : sorts) {
            if (sort != null) {
                PhysicalFieldRef ref = resolve(tree, sort.getFieldId());
                result.add(
                        SortItem.builder()
                                .moduleId(ref.moduleId())
                                .tableName(ref.tableName())
                                .columnName(ref.columnName())
                                .direction(
                                        sort.getDirection() != null ? sort.getDirection() : "ASC")
                                .build());
            }
        }
        return result;
    }

    // ==================== 2. 1:N 分支校验与表头字段收集 ====================

    static List<Long> collectAndMaskHeaderFields(
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

    static List<Long> collectCheckedFieldIds(MenuHeaderColumnResp node) {
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

    static List<TableSpec> toTableSpecs(
            List<MenuTableSpec> menuTableSpecs,
            ModuleTreeDTO moduleTree,
            Function<Long, Long> menuToModuleResolver) {
        if (CollectionUtils.isEmpty(menuTableSpecs)) {
            return null;
        }
        List<TableSpec> result = new ArrayList<>();
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
                    && moduleTree != null
                    && !moduleTree.containsPhysicalTable(rawTable)) {
                log.warn(
                        "MenuTableSpec 中指定的物理表名 [{}] 不在当前模块树的物理表中，根据模块 ID [{}] 自动校正为真实物理主表 [{}]",
                        rawTable,
                        modId,
                        modPrimaryTable);
                table = modPrimaryTable;
            }

            if (table != null && !table.isBlank()) {
                result.add(TableSpec.of(modId, table, mSpec.getColumns()));
            }
        }
        return result;
    }

    static Map<String, Set<String>> buildRequestedTableColumnsMap(
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
            addTableSpecToMap(requestedTableColumnsMap, rawTable, cols);

            Long modId =
                    resolveModuleIdForTable(
                            mSpec.getMenuId(),
                            mSpec.getTableName(),
                            moduleTree,
                            menuToModuleResolver);
            ModuleInfo mod = moduleTree != null ? moduleTree.getModule(modId) : null;
            if (mod != null && mod.getPrimaryTable() != null && !mod.getPrimaryTable().isBlank()) {
                addTableSpecToMap(
                        requestedTableColumnsMap, mod.getPrimaryTable().trim().toLowerCase(), cols);
            }
        }
        return requestedTableColumnsMap;
    }

    private static void addTableSpecToMap(
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

    static List<FilterItem> toFlatFilters(
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
                            .moduleId(modId)
                            .tableName(table)
                            .columnName(f.getColumnName().trim())
                            .operator(f.getOperator() != null ? f.getOperator() : "EQ")
                            .value(f.getValue())
                            .build());
        }
        return result;
    }

    static List<SortItem> toFlatSorts(
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
                            .moduleId(modId)
                            .tableName(table)
                            .columnName(s.getColumnName().trim())
                            .direction(s.getDirection() != null ? s.getDirection() : "DESC")
                            .build());
        }
        return result;
    }

    static Long resolveModuleIdForTable(
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

    static DataRecordItem toDataRecordItem(DataRow dataRow) {
        return toDataRecordItem(dataRow, null, null, null);
    }

    static DataRecordItem toDataRecordItem(
            DataRow dataRow,
            Map<Long, Long> moduleToMenuMap,
            Map<String, Set<String>> requestedTableColumnsMap,
            Function<Long, Long> moduleToMenuFallback) {
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
                Long subModId = childNode.getModuleId();
                List<DataRecordItem> childItems = new ArrayList<>();
                if (childNode.getRecords() != null) {
                    for (DataRow subRow : childNode.getRecords()) {
                        if (subRow != null) {
                            DataRecordItem childItem =
                                    toDataRecordItem(
                                            subRow,
                                            moduleToMenuMap,
                                            requestedTableColumnsMap,
                                            moduleToMenuFallback);
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
                                .menuId(subMenuId)
                                .record(childItems)
                                .build());
            }
        }

        List<ModuleDataNode> finalChildren = childrenNodes.isEmpty() ? null : childrenNodes;
        return DataRecordItem.builder().row(flatRow).children(finalChildren).build();
    }

    static List<DataRecordItem> extractTargetRecords(
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
            if (targetModuleId.equals(node.getModuleId())) {
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
}
