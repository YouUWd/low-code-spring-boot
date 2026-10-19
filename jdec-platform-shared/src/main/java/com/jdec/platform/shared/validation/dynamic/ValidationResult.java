package com.jdec.platform.shared.validation.dynamic;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 动态数据校验结果。
 *
 * @param valid 是否全部通过
 * @param errors 全部错误，按校验顺序排列
 */
public record ValidationResult(boolean valid, List<ValidationError> errors) {

    public ValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static ValidationResult of(List<ValidationError> errors) {
        return new ValidationResult(errors == null || errors.isEmpty(), errors);
    }

    public static ValidationResult passed() {
        return new ValidationResult(true, List.of());
    }

    /** 将错误拼接成一行摘要，便于抛异常或写日志。 */
    public String summary() {
        if (errors.isEmpty()) {
            return "校验通过";
        }
        return errors.stream()
                .map(
                        e ->
                                e.path()
                                        + (e.fieldKey() == null ? "" : "." + e.fieldKey())
                                        + ": "
                                        + e.message())
                .collect(Collectors.joining("; "));
    }
}
