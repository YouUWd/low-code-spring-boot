package com.jdec.platform.externalobject.biz.validation.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.jdec.platform.shared.validation.dynamic.FieldDefinition;
import com.jdec.platform.shared.validation.dynamic.FieldRule;
import com.jdec.platform.shared.validation.dynamic.RecordNode;
import com.jdec.platform.shared.validation.dynamic.ValidationContext;
import com.jdec.platform.shared.validation.dynamic.ValidationState;

/**
 * 同级记录唯一性规则：同一父记录下、同模块的兄弟记录中，该字段值不能重复。
 *
 * <p>适用于场地名称（同一外部对象下不可重复）、产线名称（同一场地下不可重复）。
 */
public final class SiblingUniqueRule implements FieldRule {

    public static final String DUPLICATE_VALUE = "DUPLICATE_VALUE";

    private final String message;

    public SiblingUniqueRule(String message) {
        this.message = message;
    }

    @Override
    public void validate(
            JsonNode value,
            FieldDefinition field,
            ValidationContext context,
            ValidationState state) {
        String current = JsonValueSupport.text(value);
        if (current.isEmpty()) {
            return;
        }
        RecordNode record = context.current();
        if (record.parent() == null) {
            return;
        }
        for (RecordNode sibling : record.parent().children()) {
            if (sibling == record || !sibling.moduleId().equals(record.moduleId())) {
                continue;
            }
            String siblingValue =
                    JsonValueSupport.text(JsonValueSupport.read(sibling.row(), field.fieldKey()));
            if (current.equals(siblingValue)) {
                state.addError(
                        context.recordPath(),
                        field.moduleId(),
                        field.fieldKey(),
                        DUPLICATE_VALUE,
                        message);
                return;
            }
        }
    }
}
