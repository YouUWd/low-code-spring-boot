package com.jdec.platform.externalobject.biz.validation.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.jdec.platform.shared.validation.dynamic.FieldDefinition;
import com.jdec.platform.shared.validation.dynamic.FieldRule;
import com.jdec.platform.shared.validation.dynamic.ValidationContext;
import com.jdec.platform.shared.validation.dynamic.ValidationErrorCodes;
import com.jdec.platform.shared.validation.dynamic.ValidationMode;
import com.jdec.platform.shared.validation.dynamic.ValidationState;

/**
 * JSON 值必填规则。
 *
 * <p>共享模块的 {@code RequiredRule} 只把 null、缺失和空白字符串视为空；对象性质、合作类型等 JSON 字段 以数组/对象提交时，空数组 {@code []}、空对象
 * {@code {}} 同样应当视为未填写。
 */
public final class JsonRequiredRule implements FieldRule {

    @Override
    public void validate(
            JsonNode value,
            FieldDefinition field,
            ValidationContext context,
            ValidationState state) {
        if (state.options().mode() == ValidationMode.DRAFT) {
            return;
        }
        // null、缺失和空白字符串由 RequiredRule 负责，这里只补充空数组/空对象等 JSON 空值。
        if (value == null
                || value.isMissingNode()
                || value.isNull()
                || (value.isTextual() && value.asText().isBlank())) {
            return;
        }
        if (JsonValueSupport.isBlank(value)) {
            state.addError(
                    context.recordPath(),
                    field.moduleId(),
                    field.fieldKey(),
                    ValidationErrorCodes.REQUIRED,
                    "字段不能为空");
        }
    }
}
