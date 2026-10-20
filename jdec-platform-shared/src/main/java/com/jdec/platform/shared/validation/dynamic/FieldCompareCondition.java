package com.jdec.platform.shared.validation.dynamic;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 字段比较条件：读取 {@link FieldRef} 指向的控制字段并按集合量词与期望值比较。
 *
 * <p>控制字段缺失、为 {@code null}、不是标量、校验未通过，或引用目标不存在时返回 {@link TruthValue#UNKNOWN}，不能静默当作“不适用”。
 */
public final class FieldCompareCondition implements Condition {

    private final FieldRef fieldRef;
    private final CompareOperator operator;
    private final Set<String> expectedValues;

    public FieldCompareCondition(
            FieldRef fieldRef, CompareOperator operator, Set<String> expectedValues) {
        this.fieldRef = Objects.requireNonNull(fieldRef, "fieldRef 不能为空");
        this.operator = Objects.requireNonNull(operator, "operator 不能为空");
        this.expectedValues = expectedValues == null ? Set.of() : Set.copyOf(expectedValues);
    }

    public FieldCompareCondition(
            FieldRef fieldRef, CompareOperator operator, String... expectedValues) {
        this(fieldRef, operator, expectedValues == null ? Set.of() : Set.of(expectedValues));
    }

    public static FieldCompareCondition eq(FieldRef fieldRef, String expectedValue) {
        return new FieldCompareCondition(fieldRef, CompareOperator.EQ, Set.of(expectedValue));
    }

    public static FieldCompareCondition ne(FieldRef fieldRef, String expectedValue) {
        return new FieldCompareCondition(fieldRef, CompareOperator.NE, Set.of(expectedValue));
    }

    public static FieldCompareCondition in(FieldRef fieldRef, Set<String> expectedValues) {
        return new FieldCompareCondition(fieldRef, CompareOperator.IN, expectedValues);
    }

    public static FieldCompareCondition notIn(FieldRef fieldRef, Set<String> expectedValues) {
        return new FieldCompareCondition(fieldRef, CompareOperator.NOT_IN, expectedValues);
    }

    public static FieldCompareCondition exists(FieldRef fieldRef) {
        return new FieldCompareCondition(fieldRef, CompareOperator.EXISTS, Set.of());
    }

    public static FieldCompareCondition notExists(FieldRef fieldRef) {
        return new FieldCompareCondition(fieldRef, CompareOperator.NOT_EXISTS, Set.of());
    }

    @Override
    public ConditionResult evaluate(ConditionContext context) {
        ResolvedFieldValues resolved =
                FieldRefResolver.resolve(
                        fieldRef,
                        context.tree(),
                        context.current(),
                        context.state(),
                        context.registry());

        if (!resolved.targetFound()) {
            return ConditionResult.unknown(resolved.reason());
        }

        List<ResolvedFieldValue> values = resolved.values();
        if (values.isEmpty()) {
            return ConditionResult.unknown("条件引用没有解析到字段值");
        }

        return switch (fieldRef.quantifier()) {
            case ANY -> aggregateAny(values);
            case ALL -> aggregateAll(values);
            case FIRST, INDEX ->
                    values.size() == 1
                            ? compareOne(values.get(0))
                            : ConditionResult.unknown("目标值数量不符合预期");
        };
    }

    private ConditionResult aggregateAny(List<ResolvedFieldValue> values) {
        boolean hasUnknown = false;
        for (ResolvedFieldValue value : values) {
            ConditionResult result = compareOne(value);
            if (result.value() == TruthValue.TRUE) {
                return ConditionResult.yes();
            }
            if (result.value() == TruthValue.UNKNOWN) {
                hasUnknown = true;
            }
        }
        return hasUnknown ? ConditionResult.unknown("部分候选控制字段缺失或无效") : ConditionResult.no();
    }

    private ConditionResult aggregateAll(List<ResolvedFieldValue> values) {
        boolean hasUnknown = false;
        for (ResolvedFieldValue value : values) {
            ConditionResult result = compareOne(value);
            if (result.value() == TruthValue.FALSE) {
                return ConditionResult.no();
            }
            if (result.value() == TruthValue.UNKNOWN) {
                hasUnknown = true;
            }
        }
        return hasUnknown ? ConditionResult.unknown("部分候选控制字段缺失或无效") : ConditionResult.yes();
    }

    private ConditionResult compareOne(ResolvedFieldValue resolved) {
        if (resolved.invalid()) {
            return ConditionResult.unknown(
                    "控制字段校验未通过：" + resolved.record().path() + "." + resolved.fieldKey());
        }

        JsonNode value = resolved.value();
        boolean missing = value == null || value.isMissingNode() || value.isNull();

        if (operator == CompareOperator.EXISTS) {
            return missing ? ConditionResult.no() : ConditionResult.yes();
        }
        if (operator == CompareOperator.NOT_EXISTS) {
            return missing ? ConditionResult.yes() : ConditionResult.no();
        }
        if (missing) {
            return ConditionResult.unknown("控制字段缺失或为 null");
        }
        if (!value.isValueNode()) {
            return ConditionResult.unknown("控制字段不是标量值");
        }

        boolean matched = expectedValues.contains(value.asText());
        return switch (operator) {
            case EQ, IN -> matched ? ConditionResult.yes() : ConditionResult.no();
            case NE, NOT_IN -> matched ? ConditionResult.no() : ConditionResult.yes();
            case EXISTS, NOT_EXISTS -> throw new IllegalStateException("已提前处理");
        };
    }

    @Override
    public List<FieldRef> referencedFields() {
        return List.of(fieldRef);
    }
}
