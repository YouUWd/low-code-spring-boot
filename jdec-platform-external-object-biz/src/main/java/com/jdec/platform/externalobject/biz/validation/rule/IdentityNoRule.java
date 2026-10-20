package com.jdec.platform.externalobject.biz.validation.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.jdec.platform.shared.validation.dynamic.FieldDefinition;
import com.jdec.platform.shared.validation.dynamic.FieldRule;
import com.jdec.platform.shared.validation.dynamic.ValidationContext;
import com.jdec.platform.shared.validation.dynamic.ValidationErrorCodes;
import com.jdec.platform.shared.validation.dynamic.ValidationState;

/**
 * 人员证件号校验。
 *
 * <p>证件类型为身份证时按 18 位身份证号校验；其他证件类型或未选择证件类型时不校验。
 */
public final class IdentityNoRule implements FieldRule {

    private static final String IDENTITY_TYPE = "member.identity_type";

    @Override
    public void validate(
            JsonNode value,
            FieldDefinition field,
            ValidationContext context,
            ValidationState state) {
        String identityNo = JsonValueSupport.text(value);
        if (identityNo.isEmpty()) {
            return;
        }
        JsonNode identityType = JsonValueSupport.read(context.current().row(), IDENTITY_TYPE);
        if (!JsonValueSupport.containsText(identityType, "身份证")) {
            return;
        }
        String error = IdCardSupport.formatError(identityNo);
        if (error != null) {
            state.addError(
                    context.recordPath(),
                    field.moduleId(),
                    field.fieldKey(),
                    ValidationErrorCodes.PATTERN_INVALID,
                    "证件号格式不正确：" + error);
        }
    }
}
