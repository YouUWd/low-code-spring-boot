package com.jdec.platform.externalobject.biz.validation.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.jdec.platform.shared.validation.dynamic.FieldDefinition;
import com.jdec.platform.shared.validation.dynamic.FieldRule;
import com.jdec.platform.shared.validation.dynamic.ValidationContext;
import com.jdec.platform.shared.validation.dynamic.ValidationState;

/** 外部对象父级规则：不可选择自身作为父级。 */
public final class ParentNotSelfRule implements FieldRule {

    public static final String PARENT_SELF_INVALID = "PARENT_SELF_INVALID";

    private static final String INFO_ID = "info.id";

    @Override
    public void validate(
            JsonNode value,
            FieldDefinition field,
            ValidationContext context,
            ValidationState state) {
        String parentId = JsonValueSupport.text(value);
        if (parentId.isEmpty()) {
            return;
        }
        JsonNode id = JsonValueSupport.read(context.current().row(), INFO_ID);
        if (parentId.equals(JsonValueSupport.text(id))) {
            state.addError(
                    context.recordPath(),
                    field.moduleId(),
                    field.fieldKey(),
                    PARENT_SELF_INVALID,
                    "外部对象父级不可选择自身");
        }
    }
}
