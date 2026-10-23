package com.jdec.platform.data.biz.plan.compiler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.config.api.dto.common.ProjectTableRelationsDTO;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import com.jdec.platform.data.api.dto.request.FilterItem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

/**
 * 物理表过滤条件编译器 (FilterConditionCompiler)
 *
 * <p>职责：把面向 UI 的 {@link FilterItem} 树编译为强类型 jOOQ {@link Condition}，覆盖比较、集合、区间以及 JSON_CONTAINS /
 * JSON_CONTAINS_ALL 等操作符。从 {@link QueryPlanCompiler} 拆出，让编译内核聚焦于树结构装配。
 */
public final class FilterConditionCompiler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FilterConditionCompiler() {}

    /**
     * 编译归属于指定模块/主表（及其伴生表）的过滤条件列表。
     *
     * @param targetModuleId 当前目标节点模块 ID
     * @param primaryTable 当前目标节点物理主表名
     * @param filters 过滤条件列表
     * @param projectRelations 项目表间关系
     * @return 组合后的 Condition；无有效条件时返回 {@code null}
     */
    public static Condition compileTableFilters(
            Long targetModuleId,
            String primaryTable,
            List<FilterItem> filters,
            ProjectTableRelationsDTO projectRelations) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }

        Set<String> applicableTables = new HashSet<>();
        applicableTables.add(primaryTable.toLowerCase());
        if (projectRelations != null && projectRelations.getForwardRelations() != null) {
            List<TableRelationDTO> fwdRels =
                    projectRelations.getForwardRelations().get(primaryTable.toLowerCase());
            if (fwdRels != null) {
                for (TableRelationDTO rel : fwdRels) {
                    if (rel.getJoinTable() != null) {
                        applicableTables.add(rel.getJoinTable().trim().toLowerCase());
                    }
                }
            }
        }

        List<Condition> conditions = new ArrayList<>();
        for (FilterItem item : filters) {
            if (item != null) {
                // 统一转为 Predicate AST 交由 PredicateCompiler 编译，彻底消除逻辑二义性
                var pred = item.toPredicate();
                Condition cond =
                        PredicateCompiler.compile(
                                pred, targetModuleId, primaryTable, applicableTables);
                if (cond != null && !DSL.noCondition().equals(cond)) {
                    conditions.add(cond);
                }
            }
        }
        return conditions.isEmpty() ? null : DSL.and(conditions);
    }

    private static Condition compileSingleFilterItem(
            FilterItem item,
            Long targetModuleId,
            String defaultTable,
            Set<String> applicableTables) {
        if (item == null) {
            return null;
        }

        // 1. 尝试编译当前节点自身承载的物理字段条件 (selfCond)
        Condition selfCond = null;
        if (item.getColumnName() != null && !item.getColumnName().isBlank()) {
            // 模块归属判定：若显式指定了 moduleId，必须精确匹配当前节点 moduleId；若未指定 moduleId，则根据表名是否属于当前节点物理主表/伴生表判定
            boolean matchesModule =
                    (item.getModuleId() != null)
                            ? (targetModuleId != null && targetModuleId.equals(item.getModuleId()))
                            : true;

            boolean hasValidValue =
                    item.getValue() != null
                            || "IS_NULL".equalsIgnoreCase(item.getOperator())
                            || "IS_NOT_NULL".equalsIgnoreCase(item.getOperator());
            if (matchesModule && hasValidValue) {
                String tbl =
                        (item.getTableName() != null && !item.getTableName().isBlank())
                                ? item.getTableName().trim()
                                : defaultTable;
                if (applicableTables.contains(tbl.toLowerCase())) {
                    String cleanCol =
                            item.getColumnName()
                                    .replace("`", "")
                                    .replace("\"", "")
                                    .replace("'", "")
                                    .trim();
                    if (!cleanCol.isBlank()) {
                        selfCond =
                                buildSqlCondition(
                                        tbl, cleanCol, item.getOperator(), item.getValue());
                    }
                }
            }
        }

        // 2. 递归编译子条件列表 (children 内部固定为同级 AND 结合)
        List<Condition> subConditions = new ArrayList<>();
        if (item.getChildren() != null && !item.getChildren().isEmpty()) {
            for (FilterItem child : item.getChildren()) {
                Condition subCond =
                        compileSingleFilterItem(
                                child, targetModuleId, defaultTable, applicableTables);
                if (subCond != null && !DSL.noCondition().equals(subCond)) {
                    subConditions.add(subCond);
                }
            }
        }

        // 3. 组合逻辑：当前节点定义的 logic ("AND" 或 "OR")
        String logic =
                (item.getLogic() != null && !item.getLogic().isBlank())
                        ? item.getLogic().toUpperCase().trim()
                        : "AND";

        Condition childrenCond =
                subConditions.isEmpty()
                        ? null
                        : (subConditions.size() == 1
                                ? subConditions.get(0)
                                : ("OR".equals(logic)
                                        ? DSL.or(subConditions)
                                        : DSL.and(subConditions)));

        if (selfCond != null && childrenCond != null) {
            return "OR".equals(logic) ? selfCond.or(childrenCond) : selfCond.and(childrenCond);
        }
        if (selfCond != null) {
            return selfCond;
        }
        return childrenCond;
    }

    /** 根据操作符生成具体的 jOOQ Condition。 */
    private static Condition buildSqlCondition(
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

    /**
     * 编译 JSON_CONTAINS 条件。
     *
     * @param matchAll {@code true} 时要求包含全部元素 (AND)，{@code false} 时包含任一元素即可 (OR)
     */
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

    /** 将 Java 值格式化为适合 JSON_CONTAINS 匹配的目标 JSON 标量字符串 (如 "客户" -> "\"客户\"", 123 -> "123")。 */
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
}
