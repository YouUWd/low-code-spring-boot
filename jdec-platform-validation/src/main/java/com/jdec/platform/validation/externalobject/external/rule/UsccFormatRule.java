package com.jdec.platform.validation.externalobject.external.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.jdec.platform.shared.validation.dynamic.FieldDefinition;
import com.jdec.platform.shared.validation.dynamic.FieldRule;
import com.jdec.platform.shared.validation.dynamic.ValidationContext;
import com.jdec.platform.shared.validation.dynamic.ValidationErrorCodes;
import com.jdec.platform.shared.validation.dynamic.ValidationState;
import java.util.regex.Pattern;

/**
 * 统一社会信用代码格式校验。
 *
 * <p>18 位大写字母或数字，前两位按对象性质匹配（企业 91、个体工商户 92、机关 11、事业单位 12、社会团体 51、 律师执业机构 31、村民委员会 54、居民委员会 55）。
 */
public final class UsccFormatRule implements FieldRule {

    private static final Pattern USCC_PATTERN = Pattern.compile("^[0-9A-Z]{18}$");
    private static final int USCC_LENGTH = 18;

    @Override
    public void validate(
            JsonNode value,
            FieldDefinition field,
            ValidationContext context,
            ValidationState state) {
        if (value == null || value.isNull() || !value.isValueNode()) {
            return;
        }
        String uscc = value.asText().trim();
        if (uscc.isEmpty()) {
            return;
        }

        if (uscc.length() != USCC_LENGTH) {
            addPatternError(context, field, state, "统一社会信用代码应为18位");
            return;
        }
        if (!USCC_PATTERN.matcher(uscc).matches()) {
            addPatternError(context, field, state, "统一社会信用代码只能包含18位字母或数字");
            return;
        }
        JsonNode nature = JsonValueSupport.resolveParentNature(context);
        String prefix = JsonValueSupport.usccPrefix(nature);
        if (prefix != null && !uscc.startsWith(prefix)) {
            addPatternError(context, field, state, "统一社会信用代码前两位应为" + prefix);
        }
    }

    private void addPatternError(
            ValidationContext context,
            FieldDefinition field,
            ValidationState state,
            String message) {
        state.addError(
                context.recordPath(),
                field.moduleId(),
                field.fieldKey(),
                ValidationErrorCodes.PATTERN_INVALID,
                message);
    }
}
