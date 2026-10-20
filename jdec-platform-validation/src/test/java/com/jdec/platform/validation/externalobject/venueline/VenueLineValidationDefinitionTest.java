package com.jdec.platform.validation.externalobject.venueline;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.shared.validation.dynamic.DynamicRequestValidator;
import com.jdec.platform.shared.validation.dynamic.ModuleDefinitionRegistry;
import com.jdec.platform.shared.validation.dynamic.ValidationErrorCodes;
import com.jdec.platform.shared.validation.dynamic.ValidationMode;
import com.jdec.platform.shared.validation.dynamic.ValidationOptions;
import com.jdec.platform.shared.validation.dynamic.ValidationResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VenueLineValidationDefinitionTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final ValidationOptions OPTIONS =
            new ValidationOptions(ValidationMode.SUBMIT, false, true, false, 50, 20000);

    private static DynamicRequestValidator validator;

    @BeforeAll
    static void setUp() {
        ModuleDefinitionRegistry registry =
                ModuleDefinitionRegistry.of(
                        new VenueLineValidationDefinition().moduleDefinitions());
        validator = new DynamicRequestValidator(registry, OBJECT_MAPPER);
    }

    @Test
    void venueRequiresAddressAndUniqueName() throws Exception {
        String request =
                """
                {
                  "nestedModuleData": {
                    "moduleId": 1021,
                    "record": [
                      {
                        "row": {"venue_line.company_name": [{"name": "测试企业"}]},
                        "children": [
                          {
                            "moduleId": 1032,
                            "record": [
                              {"row": {"venue.name": "一号场地"}},
                              {
                                "row": {
                                  "venue.name": "一号场地",
                                  "venue.address": {"lng": 1, "lat": 2}
                                }
                              }
                            ]
                          }
                        ]
                      }
                    ]
                  }
                }
                """;

        ValidationResult result = validator.validate(OBJECT_MAPPER.readTree(request), OPTIONS);

        assertTrue(
                result.errors().stream()
                        .anyMatch(
                                e ->
                                        "venue.address".equals(e.fieldKey())
                                                && ValidationErrorCodes.REQUIRED.equals(e.code())));
        assertTrue(result.errors().stream().anyMatch(e -> e.message().contains("场地名称不可重复")));
    }

    @Test
    void venueLineCompanyNameIsRequired() throws Exception {
        String request =
                """
                {
                  "nestedModuleData": {
                    "moduleId": 1021,
                    "record": [{"row": {}}]
                  }
                }
                """;

        ValidationResult result = validator.validate(OBJECT_MAPPER.readTree(request), OPTIONS);

        assertTrue(
                result.errors().stream()
                        .anyMatch(
                                e ->
                                        "venue_line.company_name".equals(e.fieldKey())
                                                && ValidationErrorCodes.REQUIRED.equals(e.code())));
    }
}
