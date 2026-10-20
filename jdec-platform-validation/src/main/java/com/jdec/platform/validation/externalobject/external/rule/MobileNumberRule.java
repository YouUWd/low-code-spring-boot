package com.jdec.platform.validation.externalobject.external.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.jdec.platform.shared.validation.dynamic.FieldDefinition;
import com.jdec.platform.shared.validation.dynamic.FieldRule;
import com.jdec.platform.shared.validation.dynamic.ValidationContext;
import com.jdec.platform.shared.validation.dynamic.ValidationErrorCodes;
import com.jdec.platform.shared.validation.dynamic.ValidationState;
import java.util.regex.Pattern;

/** 手机号规则：去除空格后为 11 位大陆手机号。 */
public final class MobileNumberRule implements FieldRule {

    private static final Pattern MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    @Override
    public void validate(
            JsonNode value,
            FieldDefinition field,
            ValidationContext context,
            ValidationState state) {
        String mobile = JsonValueSupport.compactText(value);
        if (mobile.isEmpty()) {
            return;
        }
        if (!MOBILE_PATTERN.matcher(mobile).matches()) {
            state.addError(
                    context.recordPath(),
                    field.moduleId(),
                    field.fieldKey(),
                    ValidationErrorCodes.PATTERN_INVALID,
                    "手机号码应为11位数字");
        }
    }
}
