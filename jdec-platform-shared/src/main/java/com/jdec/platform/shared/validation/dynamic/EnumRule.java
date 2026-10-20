package com.jdec.platform.shared.validation.dynamic;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

/** 枚举值白名单规则。 */
public final class EnumRule implements FieldRule {

    private final Set<String> allowedValues;

    public EnumRule(Set<String> allowedValues) {
        this.allowedValues = allowedValues == null ? Set.of() : Set.copyOf(allowedValues);
    }

    public Set<String> allowedValues() {
        return allowedValues;
    }

    @Override
    public void validate(
            JsonNode value,
            FieldDefinition field,
            ValidationContext context,
            ValidationState state) {
        if (value == null || value.isMissingNode() || value.isNull() || !value.isValueNode()) {
            return;
        }
        if (!allowedValues.contains(value.asText())) {
            state.addError(
                    context.recordPath(),
                    field.moduleId(),
                    field.fieldKey(),
                    ValidationErrorCodes.ENUM_INVALID,
                    "字段值不在允许范围内");
        }
    }
}
