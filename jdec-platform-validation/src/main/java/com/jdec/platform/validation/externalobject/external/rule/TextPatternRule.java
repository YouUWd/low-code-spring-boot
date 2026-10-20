package com.jdec.platform.validation.externalobject.external.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.jdec.platform.shared.validation.dynamic.FieldDefinition;
import com.jdec.platform.shared.validation.dynamic.FieldRule;
import com.jdec.platform.shared.validation.dynamic.ValidationContext;
import com.jdec.platform.shared.validation.dynamic.ValidationErrorCodes;
import com.jdec.platform.shared.validation.dynamic.ValidationState;
import java.util.regex.Pattern;

/** 文本格式规则：空值不校验，值会去除首尾空白后再匹配。 */
public final class TextPatternRule implements FieldRule {

    private final Pattern pattern;
    private final String message;

    public TextPatternRule(String regex, String message) {
        this.pattern = Pattern.compile(regex);
        this.message = message;
    }

    @Override
    public void validate(
            JsonNode value,
            FieldDefinition field,
            ValidationContext context,
            ValidationState state) {
        String text = JsonValueSupport.text(value);
        if (text.isEmpty()) {
            return;
        }
        if (!pattern.matcher(text).matches()) {
            state.addError(
                    context.recordPath(),
                    field.moduleId(),
                    field.fieldKey(),
                    ValidationErrorCodes.PATTERN_INVALID,
                    message);
        }
    }
}
