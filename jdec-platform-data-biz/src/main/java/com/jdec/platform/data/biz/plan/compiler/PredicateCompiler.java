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
 * 局部谓词抽象语法树编译器 (PredicateCompiler)。
 *
 * <p>本编译器专注于单表/单节点物理可见作用域（applicableTables，即主表及伴生表）的局部 Condition 编译。
 *
 * <p>核心契约（仅返回本地可见子集）：
 *
 * <ul>
 *   <li>1. 本编译器不具备整树跨模块拓扑感知能力，不负责推导子查询或 EXISTS 半连接；
 *   <li>2. 若谓词引用的物理表不在 applicableTables 内，视作当前作用域不可见并略过（返回 null）；
 *   <li>3. 若复合谓词（AND / OR）中混杂了不可达表的条件，仅返回当前本地可见分支编译成的子集 Condition；
 *   <li>4. 涉及整树跨子模块、跨层级的复杂布尔关系与级联 EXISTS 上卷，统一由整树计划编译器 {@code QueryPlanCompiler} 负责。
 * </ul>
 */
public final class PredicateCompiler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PredicateCompiler() {}

    /**
     * 编译指定谓词树为当前节点物理可见范围内的局部 jOOQ Condition。
     *
     * @param predicate 谓词 AST 根节点
     * @param defaultTable 缺省物理主表名
     * @param applicableTables 当前节点可直接下推匹配的表名集合 (主表及伴生表)
     * @return 编译后的 jOOQ Condition；若未匹配或无效返回 null
     */
    public static Condition compile(
            Predicate predicate, String defaultTable, Set<String> applicableTables) {
        if (predicate == null) {
            return null;
        }

        if (predicate instanceof ComparisonPredicate comp) {
            return compileComparison(comp, defaultTable, applicableTables);
        } else if (predicate instanceof CompoundPredicate compound) {
            return compileCompound(compound, defaultTable, applicableTables);
        } else if (predicate instanceof RawSqlPredicate raw) {
            return compileRaw(raw);
        }
        return null;
    }

    /**
     * 兼容重载 (targetModuleId 已废弃，统一委托三参 compile)。
     *
     * @deprecated 请使用 {@link #compile(Predicate, String, Set)}
     */
    @Deprecated(forRemoval = true)
    public static Condition compile(
            Predicate predicate,
            Long targetModuleId,
            String defaultTable,
            Set<String> applicableTables) {
        return compile(predicate, defaultTable, applicableTables);
    }

    private static Condition compileCompound(
            CompoundPredicate comp, String defaultTable, Set<String> applicableTables) {
        if (comp.getPredicates() == null || comp.getPredicates().isEmpty()) {
            return null;
        }

        String op = (comp.getOperator() != null) ? comp.getOperator().toUpperCase().trim() : "AND";
        List<Condition> subConds = new ArrayList<>();
        for (Predicate sub : comp.getPredicates()) {
            Condition sc = compile(sub, defaultTable, applicableTables);
            if (sc == null || DSL.noCondition().equals(sc)) {
                continue;
            }
            subConds.add(sc);
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
            ComparisonPredicate comp, String defaultTable, Set<String> applicableTables) {
        FieldExpression expr = comp.getExpression();
        if (expr == null) {
            return null;
        }

        Field<Object> targetField = null;
        if (expr instanceof ColumnRef col) {
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

    /** 依据 MySQL 标准映射操作符 */
    private static Condition buildSqlCondition(Field<Object> field, String operator, Object value) {
        String op =
                (operator != null && !operator.isBlank()) ? operator.toUpperCase().trim() : "EQ";

        return switch (op) {
            case "EQ", "=" -> field.eq(value);
            case "NEQ", "!=", "<>" -> field.ne(value);
            case "LIKE" -> {
                if (value == null) {
                    yield DSL.noCondition();
                }
                String s = value.toString();
                yield s.contains("%") || s.contains("_")
                        ? field.like(s)
                        : field.like("%" + s + "%");
            }
            case "NOT_LIKE" -> {
                if (value == null) {
                    yield DSL.noCondition();
                }
                String s = value.toString();
                yield s.contains("%") || s.contains("_")
                        ? field.notLike(s)
                        : field.notLike("%" + s + "%");
            }
            case "GT", ">" -> field.gt(value);
            case "GTE", ">=" -> field.ge(value);
            case "LT", "<" -> field.lt(value);
            case "LTE", "<=" -> field.le(value);
            case "IS_NULL" -> field.isNull();
            case "IS_NOT_NULL" -> field.isNotNull();
            case "CONTAINS", "JSON_CONTAINS", "CONTAINS_ANY" -> jsonContains(field, value, false);
            case "CONTAINS_ALL", "JSON_CONTAINS_ALL" -> jsonContains(field, value, true);
            case "REGEXP", "RLIKE" ->
                    DSL.condition("{0} REGEXP {1}", field, DSL.val(String.valueOf(value)));
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
            case "NOT_BETWEEN" -> {
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
                    yield field.ne(value);
                }

                if (start != null && end != null) {
                    yield field.notBetween(start, end);
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
        return raw.trim().replace("`", "").replace("'", "").replace("\"", "");
    }
}
