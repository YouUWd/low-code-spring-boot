package com.jdec.platform.data.api.dto.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.data.api.model.predicate.ColumnRef;
import com.jdec.platform.data.api.model.predicate.ComparisonPredicate;
import com.jdec.platform.data.api.model.predicate.CompoundPredicate;
import com.jdec.platform.data.api.model.predicate.FieldExpression;
import com.jdec.platform.data.api.model.predicate.Predicate;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 依据 MySQL 标准设计的谓词反序列化器 (FilterDeserializer)
 *
 * <p>支持以下多种输入形态并自动归一化为纯净的 {@link Predicate} AST：
 *
 * <ul>
 *   <li>1. 复合树形：{@code {"and": [...]}}, {@code {"or": [...]}}, {@code {"not": {...}}}
 *   <li>2. 隐式 AND 数组简写：{@code [{"field": "info.status", "op": "EQ", "value": 1}, ...]}
 *   <li>3. 单原子比较对象：{@code {"field": "info.status", "op": "EQ", "value": 1}}
 * </ul>
 */
public class FilterDeserializer extends JsonDeserializer<Predicate> {

    @Override
    public Predicate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        JsonNode root = mapper.readTree(p);
        return parseNode(root, mapper);
    }

    public static Predicate parseNode(JsonNode node, ObjectMapper mapper) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }

        // 1. 若为数组形态，隐式作为 AND 组合列表
        if (node.isArray()) {
            List<Predicate> list = new ArrayList<>();
            for (JsonNode child : node) {
                Predicate p = parseNode(child, mapper);
                if (p != null) {
                    list.add(p);
                }
            }
            if (list.isEmpty()) {
                return null;
            }
            return list.size() == 1 ? list.get(0) : CompoundPredicate.and(list);
        }

        // 2. 若为对象形态
        if (node.isObject()) {
            // A. 判断是否为复合逻辑容器 (AND / OR / NOT)
            if (node.has("and") || node.has("AND")) {
                JsonNode andNode = node.has("and") ? node.get("and") : node.get("AND");
                List<Predicate> list = new ArrayList<>();
                if (andNode.isArray()) {
                    for (JsonNode item : andNode) {
                        Predicate p = parseNode(item, mapper);
                        if (p != null) {
                            list.add(p);
                        }
                    }
                } else {
                    Predicate p = parseNode(andNode, mapper);
                    if (p != null) {
                        list.add(p);
                    }
                }
                return CompoundPredicate.and(list);
            }

            if (node.has("or") || node.has("OR")) {
                JsonNode orNode = node.has("or") ? node.get("or") : node.get("OR");
                List<Predicate> list = new ArrayList<>();
                if (orNode.isArray()) {
                    for (JsonNode item : orNode) {
                        Predicate p = parseNode(item, mapper);
                        if (p != null) {
                            list.add(p);
                        }
                    }
                } else {
                    Predicate p = parseNode(orNode, mapper);
                    if (p != null) {
                        list.add(p);
                    }
                }
                return CompoundPredicate.or(list);
            }

            if (node.has("not") || node.has("NOT")) {
                JsonNode notNode = node.has("not") ? node.get("not") : node.get("NOT");
                Predicate p = parseNode(notNode, mapper);
                return p != null ? CompoundPredicate.not(p) : null;
            }

            if (node.has("predicates")) {
                JsonNode predsNode = node.get("predicates");
                List<Predicate> list = new ArrayList<>();
                if (predsNode.isArray()) {
                    for (JsonNode item : predsNode) {
                        Predicate p = parseNode(item, mapper);
                        if (p != null) {
                            list.add(p);
                        }
                    }
                }
                String op = node.has("operator") ? node.get("operator").asText() : "AND";
                if ("OR".equalsIgnoreCase(op)) {
                    return CompoundPredicate.or(list);
                }
                if ("NOT".equalsIgnoreCase(op)) {
                    return list.isEmpty() ? null : CompoundPredicate.not(list.get(0));
                }
                return CompoundPredicate.and(list);
            }

            if (node.has("sqlTemplate")) {
                throw new IllegalArgumentException("安全限制：禁止通过外部 JSON 直接反序列化 sqlTemplate 原生 SQL 谓词");
            }

            FieldExpression expression = null;
            if (node.has("expression") && !node.get("expression").isNull()) {
                try {
                    expression = mapper.treeToValue(node.get("expression"), FieldExpression.class);
                } catch (Exception ignored) {
                }
            }

            // B. 原子比较条件 (field + op + value)
            String field = null;
            if (node.has("field") && !node.get("field").isNull()) {
                field = node.get("field").asText();
            } else if (node.has("columnName") && !node.get("columnName").isNull()) {
                String col = node.get("columnName").asText();
                String tbl =
                        (node.has("tableName") && !node.get("tableName").isNull())
                                ? node.get("tableName").asText()
                                : null;
                field = (tbl != null && !tbl.isBlank()) ? tbl + "." + col : col;
            } else if (expression instanceof ColumnRef col) {
                field = col.getField();
            }

            String op = "EQ";
            if (node.has("op") && !node.get("op").isNull()) {
                op = node.get("op").asText();
            } else if (node.has("operator") && !node.get("operator").isNull()) {
                op = node.get("operator").asText();
            }

            Object value = null;
            if (node.has("value") && !node.get("value").isNull()) {
                JsonNode valNode = node.get("value");
                if (valNode.isArray()) {
                    List<Object> valList = new ArrayList<>();
                    for (JsonNode elem : valNode) {
                        valList.add(extractValue(elem));
                    }
                    value = valList;
                } else {
                    value = extractValue(valNode);
                }
            }

            if (expression != null && !(expression instanceof ColumnRef)) {
                return ComparisonPredicate.builder()
                        .expression(expression)
                        .operator(op)
                        .value(value)
                        .build();
            }

            if (field != null && !field.isBlank()) {
                return ComparisonPredicate.of(field.trim(), op, value);
            }
        }

        return null;
    }

    private static Object extractValue(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isBoolean()) {
            return n.asBoolean();
        }
        if (n.isInt()) {
            return n.asInt();
        }
        if (n.isLong()) {
            return n.asLong();
        }
        if (n.isDouble() || n.isFloat()) {
            return n.asDouble();
        }
        return n.asText();
    }
}
