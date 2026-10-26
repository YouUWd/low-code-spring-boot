package com.jdec.platform.shared.validation.dynamic;

import java.util.List;
import java.util.Objects;

/** 由条件决定字段是否适用。 */
public final class ConditionApplicability implements ApplicabilityPolicy {

    private final Condition condition;

    public ConditionApplicability(Condition condition) {
        this.condition = Objects.requireNonNull(condition, "condition 不能为空");
    }

    @Override
    public ApplicabilityResult evaluate(ConditionContext context) {
        ConditionResult result = condition.evaluate(context);
        return switch (result.value()) {
            case TRUE -> ApplicabilityResult.applicable();
            case FALSE -> ApplicabilityResult.notApplicable("适用条件不满足");
            case UNKNOWN -> ApplicabilityResult.unknown(result.reason());
        };
    }

    @Override
    public List<FieldRef> referencedFields() {
        return condition.referencedFields();
    }
}
