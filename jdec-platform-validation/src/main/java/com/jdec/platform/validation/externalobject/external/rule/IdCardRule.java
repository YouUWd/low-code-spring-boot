package com.jdec.platform.validation.externalobject.external.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.jdec.platform.shared.validation.dynamic.FieldDefinition;
import com.jdec.platform.shared.validation.dynamic.FieldRule;
import com.jdec.platform.shared.validation.dynamic.ValidationContext;
import com.jdec.platform.shared.validation.dynamic.ValidationErrorCodes;
import com.jdec.platform.shared.validation.dynamic.ValidationState;
import java.time.LocalDate;

/**
 * 身份证号校验：18 位、末位 X 大写、出生日期有效，并与【出生年月】【性别】一致。
 *
 * <p>出生年月、性别未提交时只校验身份证号自身格式。
 */
public final class IdCardRule implements FieldRule {

    static final String DATE_OF_BIRTH = "basic_info.date_of_birth";

    @Override
    public void validate(
            JsonNode value,
            FieldDefinition field,
            ValidationContext context,
            ValidationState state) {
        String idCard = JsonValueSupport.text(value);
        if (idCard.isEmpty()) {
            return;
        }

        String error = IdCardSupport.formatError(idCard);
        if (error != null) {
            addError(context, field, state, error);
            return;
        }

        LocalDate birthDate = IdCardSupport.birthDate(idCard);
        JsonNode dateOfBirth = JsonValueSupport.read(context.current().row(), DATE_OF_BIRTH);
        String dateText = JsonValueSupport.text(dateOfBirth);
        if (!dateText.isEmpty()
                && birthDate != null
                && !birthDate.toString().equals(normalizedDate(dateText))) {
            addError(context, field, state, "身份证号与出生年月不一致");
            return;
        }

        JsonNode gender = JsonValueSupport.resolveParentGender(context);
        String genderText = JsonValueSupport.text(gender);
        if (!genderText.isEmpty()) {
            Boolean female = resolveFemale(genderText);
            if (female != null && female != IdCardSupport.isFemale(idCard)) {
                addError(context, field, state, "身份证号与性别不一致");
            }
        }
    }

    private static String normalizedDate(String text) {
        if (text.length() >= 10 && text.charAt(4) == '-') {
            return text.substring(0, 10);
        }
        if (text.length() >= 8 && text.substring(0, 8).matches("\\d{8}")) {
            return text.substring(0, 4) + "-" + text.substring(4, 6) + "-" + text.substring(6, 8);
        }
        return text;
    }

    private static Boolean resolveFemale(String gender) {
        if (gender.contains("女") || "2".equals(gender) || "female".equalsIgnoreCase(gender)) {
            return Boolean.TRUE;
        }
        if (gender.contains("男") || "1".equals(gender) || "male".equalsIgnoreCase(gender)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private void addError(
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
