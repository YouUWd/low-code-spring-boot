package com.jdec.platform.shared.validation.dynamic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FieldRefScopeTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static DynamicRequestValidator validator;

    @BeforeAll
    static void setUp() {
        ModuleDefinition company =
                new ModuleDefinition(
                        "company",
                        List.of(
                                new FieldDefinition(
                                        "company",
                                        "company.company_type",
                                        ValueType.STRING,
                                        true,
                                        AlwaysApplicable.INSTANCE,
                                        List.of(
                                                new RequiredRule(),
                                                new EnumRule(Set.of("enterprise", "individual")))),
                                new FieldDefinition(
                                        "company",
                                        "company.child_reason",
                                        ValueType.STRING,
                                        true,
                                        new ConditionApplicability(
                                                FieldCompareCondition.eq(
                                                        FieldRef.childModule(
                                                                CollectionQuantifier.ANY,
                                                                "item",
                                                                "item.kind",
                                                                0),
                                                        "special")),
                                        List.of(new RequiredRule()))));

        ModuleDefinition item =
                new ModuleDefinition(
                        "item",
                        List.of(
                                new FieldDefinition(
                                        "item",
                                        "item.kind",
                                        ValueType.STRING,
                                        true,
                                        AlwaysApplicable.INSTANCE,
                                        List.of(
                                                new RequiredRule(),
                                                new EnumRule(Set.of("special", "normal")))),
                                new FieldDefinition(
                                        "item",
                                        "item.sibling_reason",
                                        ValueType.STRING,
                                        true,
                                        new ConditionApplicability(
                                                FieldCompareCondition.eq(
                                                        FieldRef.sibling(
                                                                CollectionQuantifier.ANY,
                                                                "item",
                                                                "item.kind",
                                                                0),
                                                        "special")),
                                        List.of(new RequiredRule()))));

        ModuleDefinition middle = new ModuleDefinition("middle", List.of());

        ModuleDefinition leaf =
                new ModuleDefinition(
                        "leaf",
                        List.of(
                                new FieldDefinition(
                                        "leaf",
                                        "leaf.ancestor_reason",
                                        ValueType.STRING,
                                        true,
                                        new ConditionApplicability(
                                                FieldCompareCondition.eq(
                                                        FieldRef.nearestAncestor(
                                                                "company", "company.company_type"),
                                                        "enterprise")),
                                        List.of(new RequiredRule()))));

        ModuleDefinition detail =
                new ModuleDefinition(
                        "detail",
                        List.of(
                                new FieldDefinition(
                                        "detail",
                                        "detail.root_reason",
                                        ValueType.STRING,
                                        true,
                                        new ConditionApplicability(
                                                FieldCompareCondition.eq(
                                                        FieldRef.root(
                                                                CollectionQuantifier.ANY,
                                                                "company",
                                                                "company.company_type",
                                                                0),
                                                        "enterprise")),
                                        List.of(new RequiredRule()))));

        validator =
                new DynamicRequestValidator(
                        ModuleDefinitionRegistry.of(List.of(company, item, middle, leaf, detail)),
                        OBJECT_MAPPER);
    }

    @Test
    void ancestorAndRootScopesControlNestedFields() throws Exception {
        ValidationResult result = validate("enterprise", "normal", "normal");

        assertTrue(
                hasError(result, "leaf.ancestor_reason", ValidationErrorCodes.REQUIRED),
                result.summary());
        assertTrue(
                hasError(result, "detail.root_reason", ValidationErrorCodes.REQUIRED),
                result.summary());
        assertFalse(hasError(result, "company.child_reason", ValidationErrorCodes.REQUIRED));
        assertFalse(hasError(result, "item.sibling_reason", ValidationErrorCodes.REQUIRED));
    }

    @Test
    void nonMatchingAncestorMakesFieldsNotApplicable() throws Exception {
        ValidationResult result = validate("individual", "normal", "normal");

        assertTrue(result.valid(), result.summary());
    }

    @Test
    void childModuleAndSiblingScopesSeeOtherRecords() throws Exception {
        ValidationResult result = validate("individual", "special", "normal");

        assertTrue(
                hasError(result, "company.child_reason", ValidationErrorCodes.REQUIRED),
                result.summary());
        assertTrue(
                hasError(result, "item.sibling_reason", ValidationErrorCodes.REQUIRED),
                result.summary());
    }

    private static String request(String companyType, String firstItemKind, String secondItemKind) {
        return """
                {
                  "nestedModuleData": {
                    "moduleId": "company",
                    "record": [{
                      "row": {"company.company_type": "%s"},
                      "children": [
                        {"moduleId": "item", "record": [
                          {"row": {"item.kind": "%s"}},
                          {"row": {"item.kind": "%s"}}
                        ]},
                        {"moduleId": "middle", "record": [{
                          "row": {},
                          "children": [
                            {"moduleId": "leaf", "record": [{"row": {}}]}
                          ]
                        }]},
                        {"moduleId": "detail", "record": [{"row": {}}]}
                      ]
                    }]
                  }
                }
                """
                .formatted(companyType, firstItemKind, secondItemKind);
    }

    private static ValidationResult validate(
            String companyType, String firstItemKind, String secondItemKind) throws Exception {
        return validator.validate(
                OBJECT_MAPPER.readTree(request(companyType, firstItemKind, secondItemKind)),
                ValidationOptions.defaults());
    }

    private static boolean hasError(ValidationResult result, String fieldKey, String code) {
        return result.errors().stream()
                .anyMatch(error -> fieldKey.equals(error.fieldKey()) && code.equals(error.code()));
    }
}
