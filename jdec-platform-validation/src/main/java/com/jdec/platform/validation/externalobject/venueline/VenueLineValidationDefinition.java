package com.jdec.platform.validation.externalobject.venueline;

import com.jdec.platform.shared.validation.dynamic.AlwaysApplicable;
import com.jdec.platform.shared.validation.dynamic.ApplicabilityPolicy;
import com.jdec.platform.shared.validation.dynamic.FieldDefinition;
import com.jdec.platform.shared.validation.dynamic.ModuleDefinition;
import com.jdec.platform.shared.validation.dynamic.RequiredRule;
import com.jdec.platform.shared.validation.dynamic.StringLengthRule;
import com.jdec.platform.shared.validation.dynamic.ValidationDefinitionProvider;
import com.jdec.platform.shared.validation.dynamic.ValueType;
import com.jdec.platform.validation.externalobject.support.JsonRequiredRule;
import com.jdec.platform.validation.externalobject.support.SiblingUniqueRule;
import java.util.Collection;
import java.util.List;

/**
 * 场地产线管理表单校验规则。
 *
 * <p>场地产线管理结构对应模块 1021 / 1032 / 1033。
 */
public class VenueLineValidationDefinition implements ValidationDefinitionProvider {

    private static final String VENUE_LINE = VenueLineModules.VENUE_LINE;
    private static final String VENUE = VenueLineModules.VENUE;
    private static final String LINE = VenueLineModules.LINE;

    @Override
    public Collection<ModuleDefinition> moduleDefinitions() {
        return List.of(
                new ModuleDefinition(VENUE_LINE, venueLineFields()),
                new ModuleDefinition(VENUE, venueFields()),
                new ModuleDefinition(LINE, lineFields()));
    }

    private List<FieldDefinition> venueLineFields() {
        return List.of(
                requiredJson(VENUE_LINE, "venue_line.company_name", AlwaysApplicable.INSTANCE));
    }

    private List<FieldDefinition> venueFields() {
        return List.of(
                new FieldDefinition(
                        VENUE,
                        "venue.name",
                        ValueType.STRING,
                        true,
                        AlwaysApplicable.INSTANCE,
                        List.of(
                                new RequiredRule(),
                                new StringLengthRule(0, 50),
                                new SiblingUniqueRule("同一外部对象下的场地名称不可重复"))),
                requiredJson(VENUE, "venue.address", AlwaysApplicable.INSTANCE),
                optionalText(VENUE, "venue.description", 500));
    }

    private List<FieldDefinition> lineFields() {
        return List.of(
                new FieldDefinition(
                        LINE,
                        "line.name",
                        ValueType.STRING,
                        true,
                        AlwaysApplicable.INSTANCE,
                        List.of(
                                new RequiredRule(),
                                new StringLengthRule(0, 50),
                                new SiblingUniqueRule("同一场地下的产线名称不可重复"))),
                requiredJson(LINE, "line.person", AlwaysApplicable.INSTANCE),
                optionalText(LINE, "line.description", 500));
    }

    private FieldDefinition requiredJson(
            String moduleId, String fieldKey, ApplicabilityPolicy applicability) {
        return new FieldDefinition(
                moduleId,
                fieldKey,
                ValueType.ANY,
                true,
                applicability,
                List.of(new RequiredRule(), new JsonRequiredRule()));
    }

    private FieldDefinition optionalText(String moduleId, String fieldKey, int maxLength) {
        return new FieldDefinition(
                moduleId,
                fieldKey,
                ValueType.STRING,
                true,
                AlwaysApplicable.INSTANCE,
                List.of(new StringLengthRule(0, maxLength)));
    }
}
