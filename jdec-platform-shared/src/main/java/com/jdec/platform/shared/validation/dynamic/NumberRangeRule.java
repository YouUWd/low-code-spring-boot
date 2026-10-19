package com.jdec.platform.shared.validation.dynamic;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

/** 数值范围规则，包含边界。 */
public final class NumberRangeRule implements FieldRule {

    private final BigDecimal min;
    private final BigDecimal max;

    public NumberRangeRule(BigDecimal min, BigDecimal max) {
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new IllegalArgumentException("数值范围非法");
        }
        this.min = min;
        this.max = max;
    }

    public NumberRangeRule(long min, long max) {
        this(BigDecimal.valueOf(min), BigDecimal.valueOf(max));
    }

    @Override
    public void validate(
            JsonNode value,
            FieldDefinition field,
            ValidationContext context,
            ValidationState state) {
        if (value == null || value.isNull() || !value.isNumber()) {
            return;
        }
        BigDecimal number = value.decimalValue();
        if ((min != null && number.compareTo(min) < 0)
                || (max != null && number.compareTo(max) > 0)) {
            state.addError(
                    context.recordPath(),
                    field.moduleId(),
                    field.fieldKey(),
                    ValidationErrorCodes.RANGE_INVALID,
                    "字段值不在允许范围内");
        }
    }
}
