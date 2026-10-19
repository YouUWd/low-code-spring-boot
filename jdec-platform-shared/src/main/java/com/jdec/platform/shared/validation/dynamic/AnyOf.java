package com.jdec.platform.shared.validation.dynamic;

import java.util.ArrayList;
import java.util.List;

/** 任一条件满足。使用三态逻辑：{@code TRUE OR UNKNOWN = TRUE}。 */
public record AnyOf(List<Condition> conditions) implements Condition {

    public AnyOf {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    @Override
    public ConditionResult evaluate(ConditionContext context) {
        boolean hasUnknown = false;
        String unknownReason = null;

        for (Condition condition : conditions) {
            ConditionResult result = condition.evaluate(context);
            if (result.value() == TruthValue.TRUE) {
                return ConditionResult.yes();
            }
            if (result.value() == TruthValue.UNKNOWN) {
                hasUnknown = true;
                unknownReason = result.reason();
            }
        }

        return hasUnknown ? ConditionResult.unknown(unknownReason) : ConditionResult.no();
    }

    @Override
    public List<FieldRef> referencedFields() {
        List<FieldRef> refs = new ArrayList<>();
        for (Condition condition : conditions) {
            refs.addAll(condition.referencedFields());
        }
        return refs;
    }
}
