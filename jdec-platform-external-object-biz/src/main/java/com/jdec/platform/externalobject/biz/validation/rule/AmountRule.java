package com.jdec.platform.externalobject.biz.validation.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.jdec.platform.shared.validation.dynamic.FieldDefinition;
import com.jdec.platform.shared.validation.dynamic.FieldRule;
import com.jdec.platform.shared.validation.dynamic.ValidationContext;
import com.jdec.platform.shared.validation.dynamic.ValidationErrorCodes;
import com.jdec.platform.shared.validation.dynamic.ValidationState;
import java.math.BigDecimal;

/** 金额规则：非负数字，最多保留 2 位小数。 */
public final class AmountRule implements FieldRule {

    private static final int MAX_SCALE = 2;

    @Override
    public void validate(
            JsonNode value,
            FieldDefinition field,
            ValidationContext context,
            ValidationState state) {
        BigDecimal amount = parse(value);
        if (amount == null) {
            if (value != null
                    && !value.isNull()
                    && !value.isMissingNode()
                    && !JsonValueSupport.text(value).isEmpty()) {
                addError(context, field, state, "金额必须是非负数字");
            }
            return;
        }
        if (amount.signum() < 0) {
            addError(context, field, state, "金额必须是非负数字");
            return;
        }
        if (amount.scale() > MAX_SCALE) {
            addError(context, field, state, "金额最多保留2位小数");
        }
    }

    private static BigDecimal parse(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        if (value.isNumber()) {
            return value.decimalValue();
        }
        if (!value.isTextual()) {
            return null;
        }
        String text = value.asText().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void addError(
            ValidationContext context,
            FieldDefinition field,
            ValidationState state,
            String message) {
        state.addError(
                context.recordPath(),
                field.moduleId(),
                field.fieldKey(),
                ValidationErrorCodes.RANGE_INVALID,
                message);
    }
}
