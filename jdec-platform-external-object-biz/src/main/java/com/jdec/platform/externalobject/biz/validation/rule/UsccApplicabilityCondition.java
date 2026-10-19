package com.jdec.platform.externalobject.biz.validation.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.jdec.platform.externalobject.biz.validation.ExternalObjectModules;
import com.jdec.platform.shared.validation.dynamic.Condition;
import com.jdec.platform.shared.validation.dynamic.ConditionContext;
import com.jdec.platform.shared.validation.dynamic.ConditionResult;
import com.jdec.platform.shared.validation.dynamic.FieldRef;
import java.util.List;

/**
 * 统一社会信用代码适用性：个人不适用该字段（使用身份证号）；政府单位、事业单位非必填；其余对象性质必填。
 *
 * <p>非必填场景下如果提交了值，仍返回适用，以便继续校验值的格式。
 */
public final class UsccApplicabilityCondition implements Condition {

    @Override
    public ConditionResult evaluate(ConditionContext context) {
        JsonNode nature = JsonValueSupport.resolveParentNature(context);
        if (JsonValueSupport.isIndividual(nature)) {
            return ConditionResult.no();
        }
        if (!JsonValueSupport.isUsccExempt(nature)) {
            return ConditionResult.yes();
        }
        JsonNode uscc =
                context.current().row() == null
                        ? null
                        : context.current().row().get(JsonValueSupport.BASIC_INFO_USCC);
        return JsonValueSupport.isBlank(uscc) ? ConditionResult.no() : ConditionResult.yes();
    }

    @Override
    public List<FieldRef> referencedFields() {
        return List.of(FieldRef.parent(ExternalObjectModules.INFO, JsonValueSupport.INFO_NATURE));
    }
}
