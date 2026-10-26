package com.jdec.platform.shared.validation.dynamic;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.regex.Pattern;

/** 正则规则。 */
public final class RegexRule implements FieldRule {

    private final Pattern pattern;

    public RegexRule(String regex) {
        this.pattern = Pattern.compile(regex);
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
        if (!pattern.matcher(value.asText()).matches()) {
            state.addError(
                    context.recordPath(),
                    field.moduleId(),
                    field.fieldKey(),
                    ValidationErrorCodes.PATTERN_INVALID,
                    "字段格式不正确");
        }
    }
}
