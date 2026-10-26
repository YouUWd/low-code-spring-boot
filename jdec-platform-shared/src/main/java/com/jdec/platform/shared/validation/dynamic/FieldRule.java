package com.jdec.platform.shared.validation.dynamic;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 字段级校验规则。
 *
 * <p>规则只在字段适用后执行。缺失字段由 {@link DynamicRequestValidator} 统一处理，显式 {@code null} 会传入规则（由 {@link
 * RequiredRule} 决定是否合法）。
 */
public interface FieldRule {

    void validate(
            JsonNode value,
            FieldDefinition field,
            ValidationContext context,
            ValidationState state);
}
