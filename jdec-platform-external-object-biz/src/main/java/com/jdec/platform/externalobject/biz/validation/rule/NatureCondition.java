package com.jdec.platform.externalobject.biz.validation.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.jdec.platform.externalobject.biz.validation.ExternalObjectModules;
import com.jdec.platform.shared.validation.dynamic.Condition;
import com.jdec.platform.shared.validation.dynamic.ConditionContext;
import com.jdec.platform.shared.validation.dynamic.ConditionResult;
import com.jdec.platform.shared.validation.dynamic.FieldRef;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 按对象性质判断字段是否适用。
 *
 * <p>对象性质是 JSON 词云数据，按关键字匹配（例如 {@code "政府机关"} 同时包含 {@code "机关"}）。
 */
public final class NatureCondition implements Condition {

    private final boolean expectedMatch;
    private final Set<String> natures;

    private NatureCondition(boolean expectedMatch, Set<String> natures) {
        this.expectedMatch = expectedMatch;
        this.natures = Set.copyOf(natures);
    }

    /** 对象性质为给定值之一时适用。 */
    public static NatureCondition in(String... natures) {
        return new NatureCondition(true, new LinkedHashSet<>(Arrays.asList(natures)));
    }

    /** 对象性质不是给定值时适用。 */
    public static NatureCondition notIn(String... natures) {
        return new NatureCondition(false, new LinkedHashSet<>(Arrays.asList(natures)));
    }

    @Override
    public ConditionResult evaluate(ConditionContext context) {
        JsonNode nature = JsonValueSupport.resolveNature(context);
        if (JsonValueSupport.isBlank(nature)) {
            return ConditionResult.unknown("对象性质缺失，无法判断字段是否适用");
        }
        boolean matched =
                natures.stream().anyMatch(value -> JsonValueSupport.containsText(nature, value));
        return matched == expectedMatch ? ConditionResult.yes() : ConditionResult.no();
    }

    @Override
    public List<FieldRef> referencedFields() {
        return List.of(
                FieldRef.nearestAncestor(ExternalObjectModules.INFO, JsonValueSupport.INFO_NATURE));
    }
}
