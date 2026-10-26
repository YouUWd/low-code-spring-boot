package com.jdec.platform.shared.validation.dynamic;

/** 字段始终适用。 */
public final class AlwaysApplicable implements ApplicabilityPolicy {

    public static final AlwaysApplicable INSTANCE = new AlwaysApplicable();

    @Override
    public ApplicabilityResult evaluate(ConditionContext context) {
        return ApplicabilityResult.applicable();
    }
}
