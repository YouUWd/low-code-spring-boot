package com.jdec.platform.validation.externalobject.external.rule;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/** 身份证号格式工具。 */
final class IdCardSupport {

    private static final Pattern ID_CARD_PATTERN = Pattern.compile("^\\d{17}[\\dX]$");
    private static final DateTimeFormatter BIRTH_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private IdCardSupport() {}

    /** 校验格式与出生日期，合法返回 {@code null}，否则返回错误信息。 */
    static String formatError(String idCard) {
        if (idCard == null || idCard.isEmpty()) {
            return null;
        }
        if (!ID_CARD_PATTERN.matcher(idCard).matches()) {
            return "身份证号应为18位，末位可为数字或大写X";
        }
        LocalDate birthDate = birthDate(idCard);
        if (birthDate == null) {
            return "身份证号中的出生日期无效";
        }
        return null;
    }

    /** 从身份证号解析出生日期，失败返回 {@code null}。 */
    static LocalDate birthDate(String idCard) {
        if (idCard == null || idCard.length() != 18) {
            return null;
        }
        try {
            return LocalDate.parse(idCard.substring(6, 14), BIRTH_DATE_FORMATTER);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    /** 第17位为偶数表示女性。 */
    static boolean isFemale(String idCard) {
        char genderDigit = idCard.charAt(16);
        return (genderDigit - '0') % 2 == 0;
    }
}
