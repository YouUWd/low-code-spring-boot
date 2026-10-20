package com.jdec.platform.validation.externalobject.external.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.jdec.platform.shared.validation.dynamic.Condition;
import com.jdec.platform.shared.validation.dynamic.ConditionContext;
import com.jdec.platform.shared.validation.dynamic.ConditionResult;
import com.jdec.platform.shared.validation.dynamic.FieldRef;
import com.jdec.platform.validation.externalobject.external.ExternalObjectModules;
import java.util.List;

/** 身份证号适用性：仅对象性质为个人时适用。 */
public final class IdCardApplicabilityCondition implements Condition {

    @Override
    public ConditionResult evaluate(ConditionContext context) {
        JsonNode nature = JsonValueSupport.resolveParentNature(context);
        if (JsonValueSupport.isBlank(nature)) {
            return ConditionResult.unknown("对象性质缺失，无法判断字段是否适用");
        }
        return JsonValueSupport.isIndividual(nature) ? ConditionResult.yes() : ConditionResult.no();
    }

    @Override
    public List<FieldRef> referencedFields() {
        return List.of(FieldRef.parent(ExternalObjectModules.INFO, JsonValueSupport.INFO_NATURE));
    }
}
