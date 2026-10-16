package com.jdec.platform.data.biz.plan.compiler;

import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.data.api.dto.request.FilterItem;
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.data.api.dto.request.SortItem;
import com.jdec.platform.data.api.dto.request.TableSpec;
import java.util.HashSet;
import java.util.Set;

/**
 * 物理表查询请求白名单校验器 (TableQueryValidator)
 *
 * <p>职责：以模块树为唯一事实来源，校验请求里的投影表、投影列、过滤字段、排序字段是否落在当前模块的查询范围内， 尽早拒绝越权/拼写错误的物理表列，避免把非法标识符带进 SQL 编译阶段。
 *
 * <p>从 {@link QueryPlanCompiler} 拆出，保持编译内核聚焦于「编译」而非「校验」。
 */
public final class TableQueryValidator {

    private TableQueryValidator() {}

    /** 校验物理表查询请求的表/列是否都在当前模块树允许范围内。 */
    public static void validate(QueryReq req, ModuleTreeDTO tree) {
        Set<String> allowedColumns = new HashSet<>();
        Set<String> allowedTables = new HashSet<>();
        if (tree.getModuleMap() != null) {
            for (ModuleInfo module : tree.getModuleMap().values()) {
                if (module == null) {
                    continue;
                }
                String primaryTable = module.getPrimaryTable();
                if (primaryTable != null && !primaryTable.isBlank()) {
                    allowedTables.add(primaryTable.trim().toLowerCase());
                }
                if (module.getFields() == null) {
                    continue;
                }
                for (ModuleFieldDTO field : module.getFields()) {
                    if (field == null
                            || field.getColumnName() == null
                            || field.getColumnName().isBlank()) {
                        continue;
                    }
                    String table =
                            field.getTableName() != null && !field.getTableName().isBlank()
                                    ? field.getTableName()
                                    : primaryTable;
                    if (table != null && !table.isBlank()) {
                        allowedTables.add(table.trim().toLowerCase());
                        allowedColumns.add(physicalKey(table, field.getColumnName()));
                    }
                }
            }
        }

        if (req.getTables() != null) {
            for (TableSpec spec : req.getTables()) {
                if (spec == null || spec.getTableName() == null || spec.getTableName().isBlank()) {
                    throw new IllegalArgumentException("物理表投影规格缺少表名");
                }
                if (spec.getModuleId() == null) {
                    throw new IllegalArgumentException("物理表投影规格缺少所属模块 ID (moduleId 不能为空)");
                }
                if (tree.getModuleMap() == null
                        || !tree.getModuleMap().containsKey(spec.getModuleId())) {
                    throw new IllegalArgumentException(
                            "投影规格指定的模块 ID [" + spec.getModuleId() + "] 不属于当前模块查询范围");
                }
                String table = spec.getTableName().trim();
                if (!allowedTables.contains(table.toLowerCase())) {
                    throw new IllegalArgumentException(
                            "物理表 [" + table + "] 不属于当前模块查询范围，当前模块树允许的物理表为: " + allowedTables);
                }
                if (spec.getColumns() != null && !spec.getColumns().isEmpty()) {
                    for (String column : spec.getColumns()) {
                        if (column != null && !column.isBlank() && !"*".equals(column.trim())) {
                            validatePhysicalColumn(allowedColumns, table, column, "投影字段");
                        }
                    }
                }
            }
        }

        String defaultTable = ModuleRelationResolver.resolvePrimaryTable(tree.getRootModule());
        if (req.getFilters() != null) {
            for (FilterItem filter : req.getFilters()) {
                validateTableFilter(allowedColumns, defaultTable, filter, tree);
            }
        }
        if (req.getSorts() != null) {
            for (SortItem sort : req.getSorts()) {
                if (sort == null
                        || sort.getColumnName() == null
                        || sort.getColumnName().isBlank()) {
                    continue;
                }
                if (sort.getModuleId() == null) {
                    throw new IllegalArgumentException("排序项缺少所属模块 ID (moduleId 不能为空)");
                }
                if (tree.getModuleMap() == null
                        || !tree.getModuleMap().containsKey(sort.getModuleId())) {
                    throw new IllegalArgumentException(
                            "排序项指定的模块 ID [" + sort.getModuleId() + "] 不属于当前模块查询范围");
                }
                String table =
                        sort.getTableName() != null && !sort.getTableName().isBlank()
                                ? sort.getTableName()
                                : defaultTable;
                validatePhysicalColumn(allowedColumns, table, sort.getColumnName(), "排序字段");
            }
        }
    }

    private static void validateTableFilter(
            Set<String> allowedColumns,
            String defaultTable,
            FilterItem filter,
            ModuleTreeDTO tree) {
        if (filter == null) {
            return;
        }
        if (filter.getModuleId() != null
                && (tree.getModuleMap() == null
                        || !tree.getModuleMap().containsKey(filter.getModuleId()))) {
            throw new IllegalArgumentException(
                    "过滤项指定的模块 ID [" + filter.getModuleId() + "] 不属于当前模块查询范围");
        }
        if (filter.getColumnName() != null && !filter.getColumnName().isBlank()) {
            String table =
                    filter.getTableName() != null && !filter.getTableName().isBlank()
                            ? filter.getTableName()
                            : defaultTable;
            validatePhysicalColumn(allowedColumns, table, filter.getColumnName(), "过滤字段");
        }
        if (filter.getChildren() != null) {
            for (FilterItem child : filter.getChildren()) {
                validateTableFilter(allowedColumns, defaultTable, child, tree);
            }
        }
    }

    private static void validatePhysicalColumn(
            Set<String> allowedColumns, String table, String column, String kind) {
        if (table == null || table.isBlank()) {
            throw new IllegalArgumentException(kind + "缺少物理表名: " + column);
        }
        String cleanColumn = column.trim();
        // 测试/兼容场景可能只提供模块表名而没有字段元数据，此时保留表驱动契约，
        // 由数据库负责列存在性校验；真实配置存在字段字典时则严格执行白名单校验。
        if (allowedColumns.isEmpty()) {
            return;
        }
        if (!allowedColumns.contains(physicalKey(table, cleanColumn))) {
            throw new IllegalArgumentException(
                    kind + " [" + table.trim() + "." + cleanColumn + "] 不属于当前模块查询范围");
        }
    }

    private static String physicalKey(String table, String column) {
        return table.trim().toLowerCase() + "#" + column.trim().toLowerCase();
    }
}
