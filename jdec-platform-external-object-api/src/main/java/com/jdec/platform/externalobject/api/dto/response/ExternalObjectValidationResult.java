package com.jdec.platform.externalobject.api.dto.response;

import com.jdec.platform.shared.validation.dynamic.ValidationError;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 外部对象表单校验结果。
 *
 * @param valid 是否全部校验通过
 * @param missingRequiredFields 校验未通过的必填项中文名集合；子级字段格式为 {@code 菜单名-字段中文名}
 * @param errors 框架返回的原始错误明细
 */
public record ExternalObjectValidationResult(
        boolean valid, Set<String> missingRequiredFields, List<ValidationError> errors) {

    public ExternalObjectValidationResult {
        missingRequiredFields =
                missingRequiredFields == null
                        ? Set.of()
                        : Collections.unmodifiableSet(new LinkedHashSet<>(missingRequiredFields));
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
