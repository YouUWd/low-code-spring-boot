package com.jdec.platform.shared.validation.dynamic;

import com.fasterxml.jackson.databind.JsonNode;

/** 字符串长度规则，包含边界。 */
public final class StringLengthRule implements FieldRule {

    private final int minLength;
    private final int maxLength;

    public StringLengthRule(int minLength, int maxLength) {
        if (minLength < 0 || maxLength < minLength) {
            throw new IllegalArgumentException("字符串长度范围非法");
        }
        this.minLength = minLength;
        this.maxLength = maxLength;
    }

    @Override
    public void validate(
            JsonNode value,
            FieldDefinition field,
            ValidationContext context,
            ValidationState state) {
        if (value == null || value.isNull() || !value.isTextual()) {
            return;
        }
        int length = value.asText().length();
        if (length < minLength || length > maxLength) {
            state.addError(
                    context.recordPath(),
                    field.moduleId(),
                    field.fieldKey(),
                    ValidationErrorCodes.LENGTH_INVALID,
                    "字段长度必须在 " + minLength + " 到 " + maxLength + " 之间");
        }
    }
}
