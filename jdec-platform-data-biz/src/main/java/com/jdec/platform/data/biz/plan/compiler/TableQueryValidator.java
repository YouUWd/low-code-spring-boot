package com.jdec.platform.data.biz.plan.compiler;

import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.data.api.dto.request.SortItem;
import com.jdec.platform.data.api.model.predicate.ColumnRef;
import com.jdec.platform.data.api.model.predicate.ComparisonPredicate;
import com.jdec.platform.data.api.model.predicate.CompoundPredicate;
import com.jdec.platform.data.api.model.predicate.FunctionExpr;
import com.jdec.platform.data.api.model.predicate.Predicate;
import java.util.HashSet;
import java.util.Set;

/**
 * 物理表查询请求白名单校验器 (TableQueryValidator)
 *
 * <p>职责：以模块树为唯一事实来源，校验请求里的投影表、投影列、过滤字段、排序字段是否落在当前视图树的允许范围内， 尽早拒绝越权/拼写错误的物理表列，避免把非法标识符带进 SQL 编译阶段。
 */
public final class TableQueryValidator {

    /** 平台内置系统级辅助列（主键、树形拓扑节点、软删除、状态、版本与审计字段等） */
    private static final Set<String> BUILTIN_SYSTEM_COLUMNS =
            Set.of(
                    "id",
                    "business_no",
                    "version_no",
                    "source_id",
                    "business_status",
                    "active",
                    "deleted",
                    "node_id",
                    "parent_node_id",
                    "sort_order",
                    "subject_id",
                    "created_by",
                    "created_date",
                    "created_name",
                    "updated_by",
                    "updated_date",
                    "updated_name");

    private TableQueryValidator() {}

    public static boolean isBuiltinSystemColumn(String column) {
        if (column == null) {
            return false;
        }
        String clean =
                column.contains(".")
                        ? column.substring(column.lastIndexOf('.') + 1).trim()
                        : column.trim();
        return BUILTIN_SYSTEM_COLUMNS.contains(clean.toLowerCase());
    }

    /** 校验查询请求的表/列是否都在当前视图树允许范围内。 */
    public static void validate(QueryReq req, ModuleTreeDTO tree) {
        if (req == null || tree == null) {
            return;
        }

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

        // 1. 校验 columns 投影列
        if (req.getColumns() != null) {
            for (String col : req.getColumns()) {
                if (col == null || col.isBlank() || "*".equals(col.trim())) {
                    continue;
                }
                String trimmed = col.trim();
                if (trimmed.contains(".")) {
                    int dot = trimmed.indexOf('.');
                    String tbl = trimmed.substring(0, dot).trim().toLowerCase();
                    String clm = trimmed.substring(dot + 1).trim().toLowerCase();
                    if (!allowedTables.contains(tbl)) {
                        throw new IllegalArgumentException("投影列指定的物理表 [" + tbl + "] 不属于当前模块查询范围");
                    }
                    if (!"*".equals(clm)
                            && !BUILTIN_SYSTEM_COLUMNS.contains(clm)
                            && !allowedColumns.contains(physicalKey(tbl, clm))) {
                        throw new IllegalArgumentException("投影字段 [" + trimmed + "] 不属于当前模块查询范围");
                    }
                } else {
                    String clm = trimmed.toLowerCase();
                    if (!BUILTIN_SYSTEM_COLUMNS.contains(clm) && !allowedColumns.isEmpty()) {
                        boolean matchAny =
                                allowedColumns.stream().anyMatch(k -> k.endsWith("." + clm));
                        if (!matchAny) {
                            throw new IllegalArgumentException(
                                    "投影字段 [" + trimmed + "] 不属于当前模块查询范围");
                        }
                    }
                }
            }
        }

        // 2. 校验 filter 过滤条件
        if (req.getFilter() != null) {
            validatePredicate(req.getFilter(), allowedTables, allowedColumns);
        }

        // 3. 校验 sort 排序字段
        if (req.getSort() != null) {
            for (SortItem sort : req.getSort()) {
                if (sort == null || sort.getField() == null || sort.getField().isBlank()) {
                    continue;
                }
                String f = sort.getField().trim();
                if (f.contains(".")) {
                    int dot = f.indexOf('.');
                    String tbl = f.substring(0, dot).trim().toLowerCase();
                    String clm = f.substring(dot + 1).trim().toLowerCase();
                    if (!allowedTables.contains(tbl)) {
                        throw new IllegalArgumentException("排序字段所属表 [" + tbl + "] 不属于当前模块查询范围");
                    }
                    if (!"*".equals(clm)
                            && !BUILTIN_SYSTEM_COLUMNS.contains(clm)
                            && !allowedColumns.contains(physicalKey(tbl, clm))) {
                        throw new IllegalArgumentException("排序字段 [" + f + "] 不属于当前模块查询范围");
                    }
                } else {
                    String clm = f.toLowerCase();
                    if (!BUILTIN_SYSTEM_COLUMNS.contains(clm) && !allowedColumns.isEmpty()) {
                        boolean matchAny =
                                allowedColumns.stream().anyMatch(k -> k.endsWith("." + clm));
                        if (!matchAny) {
                            throw new IllegalArgumentException("排序字段 [" + f + "] 不属于当前模块查询范围");
                        }
                    }
                }
            }
        }

        // 4. 递归校验 children 子视图
        if (req.getChildren() != null) {
            for (QueryReq child : req.getChildren()) {
                validate(child, tree);
            }
        }
    }

    private static void validatePredicate(
            Predicate p, Set<String> allowedTables, Set<String> allowedColumns) {
        if (p == null) {
            return;
        }
        if (p instanceof ComparisonPredicate comp) {
            String f = comp.getField();
            if (f == null && comp.getExpression() instanceof FunctionExpr fn) {
                ColumnRef targetCol = fn.getTargetColumn();
                if (targetCol != null) {
                    f = targetCol.getField();
                }
            }
            if (f != null && !f.isBlank()) {
                String trimmed = f.trim();
                if (trimmed.contains(".")) {
                    int dot = trimmed.indexOf('.');
                    String tbl = trimmed.substring(0, dot).trim().toLowerCase();
                    String clm = trimmed.substring(dot + 1).trim().toLowerCase();
                    if (!allowedTables.contains(tbl)) {
                        throw new IllegalArgumentException("过滤字段所属表 [" + tbl + "] 不属于当前模块查询范围");
                    }
                    if (!BUILTIN_SYSTEM_COLUMNS.contains(clm)
                            && !allowedColumns.contains(physicalKey(tbl, clm))) {
                        throw new IllegalArgumentException("过滤字段 [" + trimmed + "] 不属于当前模块查询范围");
                    }
                } else {
                    String clm = trimmed.toLowerCase();
                    if (!BUILTIN_SYSTEM_COLUMNS.contains(clm) && !allowedColumns.isEmpty()) {
                        boolean matchAny =
                                allowedColumns.stream().anyMatch(k -> k.endsWith("." + clm));
                        if (!matchAny) {
                            throw new IllegalArgumentException(
                                    "过滤字段 [" + trimmed + "] 不属于当前模块查询范围");
                        }
                    }
                }
            }
        } else if (p instanceof CompoundPredicate comp) {
            if (comp.getPredicates() != null) {
                for (Predicate sub : comp.getPredicates()) {
                    validatePredicate(sub, allowedTables, allowedColumns);
                }
            }
        }
    }

    private static String physicalKey(String table, String column) {
        return table.trim().toLowerCase() + "." + column.trim().toLowerCase();
    }
}
