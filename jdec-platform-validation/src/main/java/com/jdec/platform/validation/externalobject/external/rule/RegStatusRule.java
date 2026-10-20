package com.jdec.platform.validation.externalobject.external.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.jdec.platform.shared.validation.dynamic.FieldDefinition;
import com.jdec.platform.shared.validation.dynamic.FieldRule;
import com.jdec.platform.shared.validation.dynamic.ValidationContext;
import com.jdec.platform.shared.validation.dynamic.ValidationState;
import java.util.List;
import java.util.Set;

/** 登记状态规则：外部对象为“个体工商户”时，只能选择“存续”或“注销”。 */
public final class RegStatusRule implements FieldRule {

    public static final String REG_STATUS_INVALID = "REG_STATUS_INVALID";

    private static final String INDIVIDUAL_BUSINESS = "个体工商户";
    private static final Set<String> ALLOWED_STATUSES = Set.of("存续", "注销");

    @Override
    public void validate(
            JsonNode value,
            FieldDefinition field,
            ValidationContext context,
            ValidationState state) {
        if (JsonValueSupport.isBlank(value)) {
            return;
        }
        JsonNode nature = JsonValueSupport.resolveCurrentNature(context);
        if (!JsonValueSupport.containsText(nature, INDIVIDUAL_BUSINESS)) {
            return;
        }
        List<String> statuses = JsonValueSupport.extractTexts(value);
        if (statuses.isEmpty()) {
            return;
        }
        boolean invalid = statuses.stream().anyMatch(status -> !ALLOWED_STATUSES.contains(status));
        if (invalid) {
            state.addError(
                    context.recordPath(),
                    field.moduleId(),
                    field.fieldKey(),
                    REG_STATUS_INVALID,
                    "外部对象为个体工商户时，登记状态只能选择存续或注销");
        }
    }
}
