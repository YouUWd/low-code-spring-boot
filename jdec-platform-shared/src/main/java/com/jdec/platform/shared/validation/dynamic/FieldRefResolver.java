package com.jdec.platform.shared.validation.dynamic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 按作用域解析控制字段。
 *
 * <p>解析与比较分离：本类只负责找到目标记录并取出字段值，集合量词和比较运算由 {@link FieldCompareCondition} 完成。
 */
public final class FieldRefResolver {

    private FieldRefResolver() {}

    public static ResolvedFieldValues resolve(
            FieldRef ref,
            RequestTree tree,
            RecordNode current,
            ValidationState state,
            ModuleDefinitionRegistry registry) {
        List<RecordNode> targets =
                switch (ref.scope()) {
                    case CURRENT -> resolveCurrent(ref, current);
                    case PARENT -> resolveParent(ref, current);
                    case ANCESTOR -> resolveAncestor(ref, current);
                    case ROOT -> tree.roots();
                    case SIBLING_RECORDS -> resolveSiblings(current);
                    case CHILD_MODULE_RECORDS -> List.copyOf(current.children());
                    case MODULE_RECORDS -> tree.recordsByModule(ref.moduleId());
                    case ALL_RECORDS -> tree.allRecords();
                };

        if (targets.isEmpty()) {
            return ResolvedFieldValues.missing("未找到条件引用对应的记录，scope=" + ref.scope());
        }

        if (ref.moduleId() != null) {
            targets =
                    targets.stream()
                            .filter(n -> Objects.equals(n.moduleId(), ref.moduleId()))
                            .toList();
        }

        if (targets.isEmpty()) {
            return ResolvedFieldValues.missing("未找到模块 " + ref.moduleId() + " 的目标记录");
        }

        List<RecordNode> selected = select(targets, ref);
        if (selected.isEmpty()) {
            return ResolvedFieldValues.missing("条件引用未选中任何记录");
        }

        List<ResolvedFieldValue> values = new ArrayList<>(selected.size());
        for (RecordNode target : selected) {
            JsonNode value = readFieldValue(target, ref.fieldKey());
            boolean invalid =
                    state.isInvalid(target, ref.fieldKey())
                            || isLogicallyInvalid(target, ref.fieldKey(), value, registry);
            values.add(new ResolvedFieldValue(target, ref.fieldKey(), value, invalid));
        }
        return ResolvedFieldValues.found(values);
    }

    private static List<RecordNode> resolveCurrent(FieldRef ref, RecordNode current) {
        if (ref.moduleId() != null && !ref.moduleId().equals(current.moduleId())) {
            return List.of();
        }
        return List.of(current);
    }

    private static List<RecordNode> resolveParent(FieldRef ref, RecordNode current) {
        RecordNode parent = current.parent();
        return parent == null ? List.of() : List.of(parent);
    }

    private static List<RecordNode> resolveAncestor(FieldRef ref, RecordNode current) {
        if (ref.ancestorDepth() == -1) {
            RecordNode node = current.parent();
            while (node != null) {
                if (Objects.equals(node.moduleId(), ref.moduleId())) {
                    return List.of(node);
                }
                node = node.parent();
            }
            return List.of();
        }

        RecordNode node = current;
        for (int i = 0; i < ref.ancestorDepth(); i++) {
            node = node == null ? null : node.parent();
        }
        return node == null ? List.of() : List.of(node);
    }

    private static List<RecordNode> resolveSiblings(RecordNode current) {
        if (current.parent() == null) {
            return List.of();
        }
        return current.parent().children().stream()
                .filter(n -> Objects.equals(n.moduleId(), current.moduleId()))
                .toList();
    }

    private static List<RecordNode> select(List<RecordNode> targets, FieldRef ref) {
        return switch (ref.quantifier()) {
            case FIRST -> List.of(targets.get(0));
            case INDEX ->
                    ref.recordIndex() < targets.size()
                            ? List.of(targets.get(ref.recordIndex()))
                            : List.of();
            case ANY, ALL -> targets;
        };
    }

    /** 读取字段值。优先直接匹配 {@code fieldKey}；对 {@code 表名.列名} 形式额外兼容按表分组的 row。 */
    public static JsonNode readFieldValue(RecordNode record, String fieldKey) {
        ObjectNode row = record.row();
        if (row == null || fieldKey == null) {
            return MissingNode.getInstance();
        }

        JsonNode direct = row.get(fieldKey);
        if (direct != null) {
            return direct;
        }

        int dot = fieldKey.indexOf('.');
        if (dot <= 0 || dot == fieldKey.length() - 1) {
            return MissingNode.getInstance();
        }

        String tableName = fieldKey.substring(0, dot);
        String columnName = fieldKey.substring(dot + 1);
        return readFromTableNode(row.get(tableName), columnName);
    }

    private static JsonNode readFromTableNode(JsonNode tableNode, String columnName) {
        if (tableNode == null || tableNode.isNull()) {
            return MissingNode.getInstance();
        }

        JsonNode candidate = tableNode;
        if (tableNode.isArray()) {
            if (tableNode.isEmpty()) {
                return MissingNode.getInstance();
            }
            candidate = tableNode.get(0);
        }
        if (candidate == null || !candidate.isObject()) {
            return MissingNode.getInstance();
        }

        JsonNode data = candidate.get("data");
        if (data != null && data.isObject()) {
            JsonNode value = data.get(columnName);
            if (value != null) {
                return value;
            }
        }

        JsonNode value = candidate.get(columnName);
        return value == null ? MissingNode.getInstance() : value;
    }

    /** 判断控制字段值本身是否已不合法（类型错误或枚举值不合法）。这样即使控制字段尚未被校验，也能把依赖字段 判为 {@code UNKNOWN}，不依赖记录遍历顺序。 */
    private static boolean isLogicallyInvalid(
            RecordNode record, String fieldKey, JsonNode value, ModuleDefinitionRegistry registry) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return false;
        }
        ModuleDefinition module = registry.get(record.moduleId());
        if (module == null) {
            return false;
        }
        FieldDefinition field = module.findField(fieldKey);
        if (field == null) {
            return false;
        }
        if (!field.valueType().matches(value)) {
            return true;
        }
        if (value.isValueNode()) {
            for (FieldRule rule : field.rules()) {
                if (rule instanceof EnumRule enumRule
                        && !enumRule.allowedValues().contains(value.asText())) {
                    return true;
                }
            }
        }
        return false;
    }
}
