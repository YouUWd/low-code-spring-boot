package com.jdec.platform.externalobject.biz.validation.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.jdec.platform.shared.validation.dynamic.FieldDefinition;
import com.jdec.platform.shared.validation.dynamic.FieldRule;
import com.jdec.platform.shared.validation.dynamic.ValidationContext;
import com.jdec.platform.shared.validation.dynamic.ValidationState;

/** 合作类型规则：包含“监管”时，对象性质必须为“机关”。 */
public final class CooperationTypeRule implements FieldRule {

    public static final String COOPERATION_TYPE_INVALID = "COOPERATION_TYPE_INVALID";

    @Override
    public void validate(
            JsonNode value,
            FieldDefinition field,
            ValidationContext context,
            ValidationState state) {
        if (value == null || value.isNull()) {
            return;
        }
        if (!JsonValueSupport.containsText(value, "监管")) {
            return;
        }
        JsonNode nature = JsonValueSupport.resolveCurrentNature(context);
        if (nature == null) {
            return;
        }
        if (!JsonValueSupport.containsText(nature, "机关")) {
            state.addError(
                    context.recordPath(),
                    field.moduleId(),
                    field.fieldKey(),
                    COOPERATION_TYPE_INVALID,
                    "合作类型[监管]仅在对象性质为机关时可选");
        }
    }
}
