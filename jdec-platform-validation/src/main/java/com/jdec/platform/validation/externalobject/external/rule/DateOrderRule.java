package com.jdec.platform.validation.externalobject.external.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.jdec.platform.shared.validation.dynamic.FieldDefinition;
import com.jdec.platform.shared.validation.dynamic.FieldRule;
import com.jdec.platform.shared.validation.dynamic.ValidationContext;
import com.jdec.platform.shared.validation.dynamic.ValidationErrorCodes;
import com.jdec.platform.shared.validation.dynamic.ValidationState;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 日期先后规则：当前日期必须晚于同一行中指定字段的日期。
 *
 * <p>任一端为空或无法解析时跳过。
 */
public final class DateOrderRule implements FieldRule {

    private final String startFieldKey;
    private final String message;

    public DateOrderRule(String startFieldKey, String message) {
        this.startFieldKey = startFieldKey;
        this.message = message;
    }

    @Override
    public void validate(
            JsonNode value,
            FieldDefinition field,
            ValidationContext context,
            ValidationState state) {
        LocalDate end = parse(JsonValueSupport.text(value));
        JsonNode startNode = JsonValueSupport.read(context.current().row(), startFieldKey);
        LocalDate start = parse(JsonValueSupport.text(startNode));
        if (start == null || end == null) {
            return;
        }
        if (!end.isAfter(start)) {
            state.addError(
                    context.recordPath(),
                    field.moduleId(),
                    field.fieldKey(),
                    ValidationErrorCodes.RANGE_INVALID,
                    message);
        }
    }

    private static LocalDate parse(String text) {
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(text.length() >= 10 ? text.substring(0, 10) : text);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }
}
