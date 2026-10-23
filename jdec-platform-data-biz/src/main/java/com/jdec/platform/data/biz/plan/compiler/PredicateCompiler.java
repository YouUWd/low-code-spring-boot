package com.jdec.platform.data.biz.plan.compiler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.data.api.model.predicate.ColumnRef;
import com.jdec.platform.data.api.model.predicate.ComparisonPredicate;
import com.jdec.platform.data.api.model.predicate.CompoundPredicate;
import com.jdec.platform.data.api.model.predicate.FieldExpression;
import com.jdec.platform.data.api.model.predicate.FunctionExpr;
import com.jdec.platform.data.api.model.predicate.Predicate;
import com.jdec.platform.data.api.model.predicate.RawSqlPredicate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

/**
 * 统一谓词抽象语法树编译器 (PredicateCompiler)。
 *
 * <p>职责：将高内聚、无二义性的强类型 {@link Predicate} AST 编译为 jOOQ {@link Condition}， 完美支持多层布尔逻辑分组、操作符、集合计算、JSON
 * 函数与表达式。
 */
public final class PredicateCompiler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PredicateCompiler() {}

    /**
     * 编译指定谓词树为 jOOQ Condition。
     *
     * @param predicate 谓词 AST 根节点
     * @param targetModuleId 当前编译作用域的模块 ID
     * @param defaultTable 缺省表名
     * @param applicableTables 当前节点可直接下推匹配的表名集合 (主表及伴生表)
     * @return 编译后的 jOOQ Condition；若未匹配或无效返回 null
     */
    public static Condition compile(
            Predicate predicate,
            Long targetModuleId,
            String defaultTable,
            Set<String> applicableTables) {
        if (predicate == null) {
            return null;
        }

        if (predicate instanceof ComparisonPredicate comp) {
            return compileComparison(comp, targetModuleId, defaultTable, applicableTables);
        } else if (predicate instanceof CompoundPredicate compound) {
            return compileCompound(compound, targetModuleId, defaultTable, applicableTables);
        } else if (predicate instanceof RawSqlPredicate raw) {
            return compileRaw(raw);
        }
        return null;
    }

    private static Condition compileCompound(
            CompoundPredicate comp,
            Long targetModuleId,
            String defaultTable,
            Set<String> applicableTables) {
        if (comp.getPredicates() == null || comp.getPredicates().isEmpty()) {
            return null;
        }

        String op = (comp.getOperator() != null) ? comp.getOperator().toUpperCase().trim() : "AND";
        List<Condition> subConds = new ArrayList<>();
        for (Predicate sub : comp.getPredicates()) {
            Condition sc = compile(sub, targetModuleId, defaultTable, applicableTables);
            if (sc != null && !DSL.noCondition().equals(sc)) {
                subConds.add(sc);
            }
        }

        if (subConds.isEmpty()) {
            return null;
        }
        if (subConds.size() == 1 && !"NOT".equals(op)) {
            return subConds.get(0);
        }

        return switch (op) {
            case "OR" -> DSL.or(subConds);
            case "NOT" -> DSL.not(subConds.get(0));
            case "AND" -> DSL.and(subConds);
            default -> DSL.and(subConds);
        };
    }

    private static Condition compileComparison(
            ComparisonPredicate comp,
            Long targetModuleId,
            String defaultTable,
            Set<String> applicableTables) {
        FieldExpression expr = comp.getExpression();
        if (expr == null) {
            return null;
        }

        Field<Object> targetField = null;
        if (expr instanceof ColumnRef col) {
            // 模块归属校验
            if (col.getModuleId() != null
                    && targetModuleId != null
                    && !col.getModuleId().equals(targetModuleId)) {
                return null;
            }
            String tbl =
                    (col.getTableName() != null && !col.getTableName().isBlank())
                            ? col.getTableName().trim()
                            : defaultTable;
            if (tbl != null
                    && applicableTables != null
                    && !applicableTables.contains(tbl.toLowerCase())) {
                return null;
            }
            if (col.getColumnName() == null || col.getColumnName().isBlank()) {
                return null;
            }
            targetField = DSL.field(DSL.name(tbl, cleanIdentifier(col.getColumnName())));
        } else if (expr instanceof FunctionExpr fn) {
            ColumnRef col = fn.getTargetColumn();
            String tbl =
                    (col != null && col.getTableName() != null)
                            ? col.getTableName().trim()
                            : defaultTable;
            if (col != null
                    && tbl != null
                    && applicableTables != null
                    && !applicableTables.contains(tbl.toLowerCase())) {
                return null;
            }
            String fnName =
                    fn.getFunctionName() != null ? fn.getFunctionName().toUpperCase().trim() : "";
            if ("JSON_EXTRACT".equals(fnName) && col != null && !fn.getArguments().isEmpty()) {
                Field<Object> baseCol =
                        DSL.field(DSL.name(tbl, cleanIdentifier(col.getColumnName())));
                targetField =
                        DSL.field(
                                "JSON_EXTRACT({0}, {1})",
                                Object.class, baseCol, DSL.val(fn.getArguments().get(0)));
            } else if ("DISTINCT".equals(fnName) && col != null) {
                targetField =
                        DSL.field(
                                "DISTINCT {0}",
                                Object.class, DSL.name(tbl, cleanIdentifier(col.getColumnName())));
            } else if (col != null) {
                targetField = DSL.field(DSL.name(tbl, cleanIdentifier(col.getColumnName())));
            }
        }

        if (targetField == null) {
            return null;
        }

        return buildSqlCondition(targetField, comp.getOperator(), comp.getValue());
    }

    private static Condition compileRaw(RawSqlPredicate raw) {
        if (raw.getSqlTemplate() == null || raw.getSqlTemplate().isBlank()) {
            return null;
        }
        if (raw.getParams() == null || raw.getParams().isEmpty()) {
            return DSL.condition(raw.getSqlTemplate());
        }
        return DSL.condition(raw.getSqlTemplate(), raw.getParams().toArray());
    }

    private static Condition buildSqlCondition(Field<Object> field, String operator, Object value) {
        String op =
                (operator != null && !operator.isBlank()) ? operator.toUpperCase().trim() : "EQ";

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
            case "CONTAINS", "JSON_CONTAINS", "CONTAINS_ANY" -> jsonContains(field, value, false);
            case "CONTAINS_ALL", "JSON_CONTAINS_ALL" -> jsonContains(field, value, true);
            case "IN" -> {
                if (value instanceof Collection<?> coll) {
                    yield field.in(coll);
                } else if (value instanceof Object[] arr) {
                    yield field.in(arr);
                } else {
                    yield field.eq(value);
                }
            }
            case "NOT_IN" -> {
                if (value instanceof Collection<?> coll) {
                    yield field.notIn(coll);
                } else if (value instanceof Object[] arr) {
                    yield field.notIn(arr);
                } else {
                    yield field.ne(value);
                }
            }
            case "BETWEEN", "RANGE" -> {
                Object start = null;
                Object end = null;
                if (value instanceof Collection<?> coll && coll.size() >= 2) {
                    Iterator<?> it = coll.iterator();
                    start = it.next();
                    end = it.next();
                } else if (value instanceof Object[] arr && arr.length >= 2) {
                    start = arr[0];
                    end = arr[1];
                } else {
                    yield field.eq(value);
                }

                if (start != null && end != null) {
                    yield field.between(start, end);
                } else if (start != null) {
                    yield field.ge(start);
                } else if (end != null) {
                    yield field.le(end);
                } else {
                    yield DSL.noCondition();
                }
            }
            default -> field.like("%" + value + "%");
        };
    }

    private static Condition jsonContains(Field<Object> field, Object value, boolean matchAll) {
        List<Object> items = null;
        if (value instanceof Collection<?> coll) {
            items = new ArrayList<>(coll);
        } else if (value instanceof Object[] arr) {
            items = Arrays.asList(arr);
        }
        if (items == null) {
            return DSL.condition("JSON_CONTAINS({0}, {1})", field, DSL.val(formatJsonValue(value)));
        }

        List<Condition> list = new ArrayList<>();
        for (Object item : items) {
            if (item != null) {
                list.add(
                        DSL.condition(
                                "JSON_CONTAINS({0}, {1})", field, DSL.val(formatJsonValue(item))));
            }
        }
        if (list.isEmpty()) {
            return DSL.noCondition();
        }
        return matchAll ? DSL.and(list) : DSL.or(list);
    }

    private static String formatJsonValue(Object val) {
        if (val == null) {
            return "null";
        }
        if (val instanceof Number || val instanceof Boolean) {
            return String.valueOf(val);
        }
        if (val instanceof String str) {
            str = str.trim();
            if ((str.startsWith("{") && str.endsWith("}"))
                    || (str.startsWith("[") && str.endsWith("]"))) {
                return str;
            }
            if (str.startsWith("\"") && str.endsWith("\"")) {
                return str;
            }
            return "\"" + str + "\"";
        }
        try {
            return MAPPER.writeValueAsString(val);
        } catch (Exception e) {
            return "\"" + String.valueOf(val) + "\"";
        }
    }

    private static String cleanIdentifier(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("`", "").replace("\"", "").replace("'", "").trim();
    }
}
