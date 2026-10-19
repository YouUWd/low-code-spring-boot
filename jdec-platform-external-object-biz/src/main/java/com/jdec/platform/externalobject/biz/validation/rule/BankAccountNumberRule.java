package com.jdec.platform.externalobject.biz.validation.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.jdec.platform.shared.validation.dynamic.FieldDefinition;
import com.jdec.platform.shared.validation.dynamic.FieldRule;
import com.jdec.platform.shared.validation.dynamic.ValidationContext;
import com.jdec.platform.shared.validation.dynamic.ValidationErrorCodes;
import com.jdec.platform.shared.validation.dynamic.ValidationState;

/** 银行卡账号规则：去除空格后为纯数字，最多 32 位。 */
public final class BankAccountNumberRule implements FieldRule {

    private static final int MAX_LENGTH = 32;

    @Override
    public void validate(
            JsonNode value,
            FieldDefinition field,
            ValidationContext context,
            ValidationState state) {
        String accountNo = JsonValueSupport.compactText(value);
        if (accountNo.isEmpty()) {
            return;
        }
        if (accountNo.length() > MAX_LENGTH) {
            state.addError(
                    context.recordPath(),
                    field.moduleId(),
                    field.fieldKey(),
                    ValidationErrorCodes.LENGTH_INVALID,
                    "账号最多32位");
        }
        if (!accountNo.matches("\\d+")) {
            state.addError(
                    context.recordPath(),
                    field.moduleId(),
                    field.fieldKey(),
                    ValidationErrorCodes.PATTERN_INVALID,
                    "账号只能包含数字");
        }
    }
}
