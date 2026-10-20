package com.jdec.platform.shared.validation.dynamic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DynamicRequestValidatorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String COMPANY = "company";
    private static final String CONTACT = "contact";
    private static final String APPROVAL = "approval";

    private static DynamicRequestValidator validator;

    @BeforeAll
    static void setUp() {
        ModuleDefinition company =
                new ModuleDefinition(
                        COMPANY,
                        List.of(
                                new FieldDefinition(
                                        COMPANY,
                                        "company.company_type",
                                        ValueType.STRING,
                                        true,
                                        AlwaysApplicable.INSTANCE,
                                        List.of(
                                                new RequiredRule(),
                                                new EnumRule(Set.of("enterprise", "individual")))),
                                new FieldDefinition(
                                        COMPANY,
                                        "company.social_credit_code",
                                        ValueType.STRING,
                                        true,
                                        enterpriseCondition(),
                                        List.of(new RequiredRule(), new StringLengthRule(18, 18))),
                                new FieldDefinition(
                                        COMPANY,
                                        "company.approved_reason",
                                        ValueType.STRING,
                                        true,
                                        new ConditionApplicability(
                                                FieldCompareCondition.eq(
                                                        FieldRef.moduleAny(
                                                                APPROVAL, "approval.status"),
                                                        "approved")),
                                        List.of(new RequiredRule())),
                                new FieldDefinition(
                                        COMPANY,
                                        "company.all_approved_reason",
                                        ValueType.STRING,
                                        true,
                                        new ConditionApplicability(
                                                FieldCompareCondition.eq(
                                                        FieldRef.moduleAll(
                                                                APPROVAL, "approval.status"),
                                                        "approved")),
                                        List.of(new RequiredRule()))));

        ModuleDefinition contact =
                new ModuleDefinition(
                        CONTACT,
                        List.of(
                                new FieldDefinition(
                                        CONTACT,
                                        "contact.legal_person_id_card",
                                        ValueType.STRING,
                                        true,
                                        new ConditionApplicability(
                                                FieldCompareCondition.eq(
                                                        FieldRef.parent(
                                                                COMPANY, "company.company_type"),
                                                        "enterprise")),
                                        List.of(new RequiredRule()))));

        ModuleDefinition approval =
                new ModuleDefinition(
                        APPROVAL,
                        List.of(
                                new FieldDefinition(
                                        APPROVAL,
                                        "approval.status",
                                        ValueType.STRING,
                                        true,
                                        AlwaysApplicable.INSTANCE,
                                        List.of(new EnumRule(Set.of("approved", "rejected"))))));

        validator =
                new DynamicRequestValidator(
                        ModuleDefinitionRegistry.of(List.of(company, contact, approval)),
                        OBJECT_MAPPER);
    }

    private static ApplicabilityPolicy enterpriseCondition() {
        return new ConditionApplicability(
                FieldCompareCondition.eq(FieldRef.current("company.company_type"), "enterprise"));
    }

    @Test
    void enterpriseRequiresDependentFields() throws Exception {
        ValidationResult result = validate(enterpriseRequest(), ValidationOptions.defaults());

        assertFalse(result.valid());
        assertTrue(hasError(result, "company.social_credit_code", ValidationErrorCodes.REQUIRED));
        assertTrue(hasError(result, "contact.legal_person_id_card", ValidationErrorCodes.REQUIRED));

        ValidationError contactError =
                findError(result, "contact.legal_person_id_card", ValidationErrorCodes.REQUIRED);
        assertEquals("nestedModuleData.record[0].children[0].record[0]", contactError.path());
    }

    @Test
    void enterpriseWithDependentFieldsPasses() throws Exception {
        String json =
                """
                {
                  "nestedModuleData": {
                    "moduleId": "company",
                    "record": [{
                      "row": {
                        "company.company_type": "enterprise",
                        "company.social_credit_code": "91310000000000000X"
                      },
                      "children": [
                        {"moduleId": "contact", "record": [
                          {"row": {"contact.legal_person_id_card": "310101199001010011"}}
                        ]},
                        {"moduleId": "approval", "record": [
                          {"row": {"approval.status": "rejected"}}
                        ]}
                      ]
                    }]
                  }
                }
                """;
        ValidationResult result = validate(json, ValidationOptions.defaults());
        assertTrue(result.valid(), result.summary());
    }

    @Test
    void individualDoesNotRequireEnterpriseFields() throws Exception {
        String json =
                """
                {
                  "nestedModuleData": {
                    "moduleId": "company",
                    "record": [{
                      "row": {"company.company_type": "individual"},
                      "children": [
                        {"moduleId": "contact", "record": [{"row": {}}]},
                        {"moduleId": "approval", "record": [
                          {"row": {"approval.status": "rejected"}}
                        ]}
                      ]
                    }]
                  }
                }
                """;
        ValidationResult result = validate(json, ValidationOptions.defaults());
        assertTrue(result.valid(), result.summary());
    }

    @Test
    void submittingNotApplicableFieldIsRejected() throws Exception {
        String json =
                """
                {
                  "nestedModuleData": {
                    "moduleId": "company",
                    "record": [{
                      "row": {
                        "company.company_type": "individual",
                        "company.social_credit_code": "91310000000000000X"
                      },
                      "children": [
                        {"moduleId": "approval", "record": [
                          {"row": {"approval.status": "rejected"}}
                        ]}
                      ]
                    }]
                  }
                }
                """;
        ValidationResult result = validate(json, ValidationOptions.defaults());

        assertTrue(
                hasError(
                        result,
                        "company.social_credit_code",
                        ValidationErrorCodes.FIELD_NOT_APPLICABLE));
    }

    @Test
    void missingControlFieldProducesUnknownApplicability() throws Exception {
        String json =
                """
                {
                  "nestedModuleData": {
                    "moduleId": "company",
                    "record": [{
                      "row": {},
                      "children": [
                        {"moduleId": "contact", "record": [{"row": {}}]},
                        {"moduleId": "approval", "record": [
                          {"row": {"approval.status": "rejected"}}
                        ]}
                      ]
                    }]
                  }
                }
                """;
        ValidationResult result = validate(json, ValidationOptions.defaults());

        assertTrue(hasError(result, "company.company_type", ValidationErrorCodes.REQUIRED));
        assertTrue(
                hasError(
                        result,
                        "company.social_credit_code",
                        ValidationErrorCodes.APPLICABILITY_UNKNOWN));
        assertTrue(
                hasError(
                        result,
                        "contact.legal_person_id_card",
                        ValidationErrorCodes.APPLICABILITY_UNKNOWN));
    }

    @Test
    void invalidControlFieldProducesUnknownApplicability() throws Exception {
        String json =
                """
                {
                  "nestedModuleData": {
                    "moduleId": "company",
                    "record": [{
                      "row": {"company.company_type": "unknown-type"},
                      "children": [
                        {"moduleId": "approval", "record": [
                          {"row": {"approval.status": "rejected"}}
                        ]}
                      ]
                    }]
                  }
                }
                """;
        ValidationResult result = validate(json, ValidationOptions.defaults());

        assertTrue(hasError(result, "company.company_type", ValidationErrorCodes.ENUM_INVALID));
        assertTrue(
                hasError(
                        result,
                        "company.social_credit_code",
                        ValidationErrorCodes.APPLICABILITY_UNKNOWN));
    }

    @Test
    void moduleAnyConditionMatchesAnyRecord() throws Exception {
        String json =
                """
                {
                  "nestedModuleData": {
                    "moduleId": "company",
                    "record": [{
                      "row": {"company.company_type": "individual"},
                      "children": [
                        {"moduleId": "approval", "record": [
                          {"row": {"approval.status": "rejected"}},
                          {"row": {"approval.status": "approved"}}
                        ]}
                      ]
                    }]
                  }
                }
                """;
        ValidationResult result = validate(json, ValidationOptions.defaults());

        assertTrue(hasError(result, "company.approved_reason", ValidationErrorCodes.REQUIRED));
        assertFalse(hasError(result, "company.all_approved_reason", ValidationErrorCodes.REQUIRED));
    }

    @Test
    void moduleAllConditionRequiresEveryRecord() throws Exception {
        String json =
                """
                {
                  "nestedModuleData": {
                    "moduleId": "company",
                    "record": [{
                      "row": {"company.company_type": "individual"},
                      "children": [
                        {"moduleId": "approval", "record": [
                          {"row": {"approval.status": "approved"}},
                          {"row": {"approval.status": "approved"}}
                        ]}
                      ]
                    }]
                  }
                }
                """;
        ValidationResult result = validate(json, ValidationOptions.defaults());

        assertTrue(hasError(result, "company.approved_reason", ValidationErrorCodes.REQUIRED));
        assertTrue(hasError(result, "company.all_approved_reason", ValidationErrorCodes.REQUIRED));
    }

    @Test
    void unknownFieldIsRejectedOnlyInStrictMode() throws Exception {
        String json =
                """
                {
                  "nestedModuleData": {
                    "moduleId": "company",
                    "record": [{
                      "row": {
                        "company.company_type": "individual",
                        "company.unknown_field": "x"
                      },
                      "children": [
                        {"moduleId": "approval", "record": [
                          {"row": {"approval.status": "rejected"}}
                        ]}
                      ]
                    }]
                  }
                }
                """;

        ValidationResult lenient = validate(json, ValidationOptions.defaults());
        assertTrue(lenient.valid(), lenient.summary());

        ValidationResult strict = validate(json, ValidationOptions.strict());
        assertTrue(hasError(strict, "company.unknown_field", ValidationErrorCodes.UNKNOWN_FIELD));
    }

    @Test
    void unknownModuleIsReported() throws Exception {
        String json =
                """
                {"nestedModuleData": {"moduleId": "missing", "record": [{"row": {}}]}}
                """;
        ValidationResult result = validate(json, ValidationOptions.defaults());

        assertTrue(hasError(result, null, ValidationErrorCodes.UNKNOWN_MODULE));
    }

    @Test
    void rowMustBeObject() throws Exception {
        String json =
                """
                {"nestedModuleData": {"moduleId": "company", "record": [{"row": "bad"}]}}
                """;
        ValidationResult result = validate(json, ValidationOptions.defaults());

        assertTrue(hasError(result, "row", ValidationErrorCodes.ROW_REQUIRED));
    }

    @Test
    void updateModeAllowsMissingRequiredField() throws Exception {
        String json =
                """
                {
                  "nestedModuleData": {
                    "moduleId": "company",
                    "record": [{
                      "row": {"company.company_type": "individual"},
                      "children": [
                        {"moduleId": "approval", "record": [
                          {"row": {"approval.status": "rejected"}}
                        ]}
                      ]
                    }]
                  }
                }
                """;
        ValidationResult result = validate(json, ValidationOptions.forMode(ValidationMode.UPDATE));

        assertTrue(result.valid(), result.summary());
    }

    @Test
    void updateModeExplicitNullStillTriggersRequired() throws Exception {
        String json =
                """
                {
                  "nestedModuleData": {
                    "moduleId": "company",
                    "record": [{
                      "row": {
                        "company.company_type": "enterprise",
                        "company.social_credit_code": null
                      },
                      "children": [
                        {"moduleId": "contact", "record": [
                          {"row": {"contact.legal_person_id_card": "310101199001010011"}}
                        ]},
                        {"moduleId": "approval", "record": [
                          {"row": {"approval.status": "rejected"}}
                        ]}
                      ]
                    }]
                  }
                }
                """;
        ValidationResult result = validate(json, ValidationOptions.forMode(ValidationMode.UPDATE));

        assertTrue(hasError(result, "company.social_credit_code", ValidationErrorCodes.REQUIRED));
    }

    @Test
    void draftModeSkipsRequiredRules() throws Exception {
        String json =
                """
                {
                  "nestedModuleData": {
                    "moduleId": "company",
                    "record": [{
                      "row": {"company.company_type": "individual"},
                      "children": [
                        {"moduleId": "approval", "record": [
                          {"row": {"approval.status": "rejected"}}
                        ]}
                      ]
                    }]
                  }
                }
                """;
        ValidationResult result = validate(json, ValidationOptions.forMode(ValidationMode.DRAFT));

        assertTrue(result.valid(), result.summary());
    }

    private static String enterpriseRequest() {
        return """
                {
                  "nestedModuleData": {
                    "moduleId": "company",
                    "record": [{
                      "row": {"company.company_type": "enterprise"},
                      "children": [
                        {"moduleId": "contact", "record": [{"row": {}}]},
                        {"moduleId": "approval", "record": [
                          {"row": {"approval.status": "rejected"}}
                        ]}
                      ]
                    }]
                  }
                }
                """;
    }

    private static ValidationResult validate(String json, ValidationOptions options)
            throws Exception {
        return validator.validate(OBJECT_MAPPER.readTree(json), options);
    }

    private static boolean hasError(ValidationResult result, String fieldKey, String code) {
        return result.errors().stream()
                .anyMatch(
                        error ->
                                (fieldKey == null || fieldKey.equals(error.fieldKey()))
                                        && code.equals(error.code()));
    }

    private static ValidationError findError(
            ValidationResult result, String fieldKey, String code) {
        return result.errors().stream()
                .filter(error -> fieldKey.equals(error.fieldKey()) && code.equals(error.code()))
                .findFirst()
                .orElseThrow();
    }
}
