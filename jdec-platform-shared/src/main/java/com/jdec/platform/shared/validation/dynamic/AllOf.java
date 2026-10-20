package com.jdec.platform.shared.validation.dynamic;

import java.util.ArrayList;
import java.util.List;

/** 全部条件满足。使用三态逻辑：{@code FALSE AND UNKNOWN = FALSE}。 */
public record AllOf(List<Condition> conditions) implements Condition {

    public AllOf {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    @Override
    public ConditionResult evaluate(ConditionContext context) {
        boolean hasUnknown = false;
        String unknownReason = null;

        for (Condition condition : conditions) {
            ConditionResult result = condition.evaluate(context);
            if (result.value() == TruthValue.FALSE) {
                return ConditionResult.no();
            }
            if (result.value() == TruthValue.UNKNOWN) {
                hasUnknown = true;
                unknownReason = result.reason();
            }
        }

        return hasUnknown ? ConditionResult.unknown(unknownReason) : ConditionResult.yes();
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
