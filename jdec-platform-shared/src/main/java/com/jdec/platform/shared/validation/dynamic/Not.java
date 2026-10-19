package com.jdec.platform.shared.validation.dynamic;

import java.util.List;
import java.util.Objects;

/** 条件取反。 */
public record Not(Condition condition) implements Condition {

    public Not {
        Objects.requireNonNull(condition, "condition 不能为空");
    }

    @Override
    public ConditionResult evaluate(ConditionContext context) {
        ConditionResult result = condition.evaluate(context);
        return switch (result.value()) {
            case TRUE -> ConditionResult.no();
            case FALSE -> ConditionResult.yes();
            case UNKNOWN -> result;
        };
    }

    @Override
    public List<FieldRef> referencedFields() {
        return condition.referencedFields();
    }
}
