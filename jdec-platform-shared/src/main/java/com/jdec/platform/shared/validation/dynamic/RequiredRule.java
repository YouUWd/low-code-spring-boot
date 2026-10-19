package com.jdec.platform.shared.validation.dynamic;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 必填规则。
 *
 * <p>约定 {@code null}、缺失值和空白字符串视为空。草稿模式下跳过。
 */
public final class RequiredRule implements FieldRule {

    @Override
    public void validate(
            JsonNode value,
            FieldDefinition field,
            ValidationContext context,
            ValidationState state) {
        if (context.state().options().mode() == ValidationMode.DRAFT) {
            return;
        }
        if (isEmpty(value)) {
            state.addError(
                    context.recordPath(),
                    field.moduleId(),
                    field.fieldKey(),
                    ValidationErrorCodes.REQUIRED,
                    "字段不能为空");
        }
    }

    private boolean isEmpty(JsonNode value) {
        return value == null
                || value.isMissingNode()
                || value.isNull()
                || (value.isTextual() && value.asText().isBlank());
    }
}
