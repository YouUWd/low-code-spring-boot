package com.jdec.platform.externalobject.biz.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.shared.validation.dynamic.DynamicRequestValidator;
import com.jdec.platform.shared.validation.dynamic.ModuleDefinitionRegistry;
import com.jdec.platform.shared.validation.dynamic.ValidationErrorCodes;
import com.jdec.platform.shared.validation.dynamic.ValidationMode;
import com.jdec.platform.shared.validation.dynamic.ValidationOptions;
import com.jdec.platform.shared.validation.dynamic.ValidationResult;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ExternalObjectValidationDefinitionTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final ValidationOptions OPTIONS =
            new ValidationOptions(ValidationMode.SUBMIT, false, true, false, 50, 20000);

    private static DynamicRequestValidator validator;

    @BeforeAll
    static void setUp() {
        ModuleDefinitionRegistry registry =
                ModuleDefinitionRegistry.of(
                        new ExternalObjectValidationDefinition().moduleDefinitions());
        validator = new DynamicRequestValidator(registry, OBJECT_MAPPER);
    }

    @Test
    void validRequestPasses() throws Exception {
        ValidationResult result = validate(request(validInfoRow(), validBasicInfoRow()));

        assertTrue(result.valid(), result.summary());
    }

    @Test
    void missingRequiredFieldsProduceChineseNames() throws Exception {
        String request =
                """
                {
                  "nestedModuleData": {
                    "moduleId": 1001,
                    "record": [
                      {
                        "row": {
                          "info.nature": [{"word_describe": "企业"}],
                          "info.reg_status": [],
                          "info.cooperation_types": [{"word_describe": "客户"}],
                          "info.industry_tags": ["兰炭"],
                          "info.industry_types": ["采矿业"]
                        },
                        "children": [
                          {
                            "moduleId": 1002,
                            "record": [
                              {
                                "row": {
                                  "basic_info.insured_count": 0
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

        ValidationResult result = validate(OBJECT_MAPPER.readTree(request));
        Set<String> names = requiredNames(result);

        assertFalse(result.valid());
        assertTrue(names.contains("外部对象全称"));
        assertTrue(names.contains("登记状态"));
        assertTrue(names.contains("基本信息-统一社会信用代码"));
        assertTrue(names.contains("基本信息-法定代表人/负责人"));
        assertTrue(names.contains("基本信息-注册资本"));
        assertTrue(names.contains("基本信息-实缴资本"));
        assertTrue(names.contains("基本信息-注册登记日期"));
    }

    @Test
    void usccMustMatchNaturePrefix() throws Exception {
        String basicInfo =
                """
                {
                  "basic_info.uscc": "921234567890123456",
                  "basic_info.legal_person": "张三",
                  "basic_info.insured_count": 10,
                  "basic_info.reg_capital": 10,
                  "basic_info.paid_in_capital": 10,
                  "basic_info.reg_date": "2020-01-01"
                }
                """;

        ValidationResult result =
                validate(request(validInfoRow(), OBJECT_MAPPER.readTree(basicInfo)));

        assertTrue(
                result.errors().stream()
                        .anyMatch(
                                e ->
                                        "basic_info.uscc".equals(e.fieldKey())
                                                && ValidationErrorCodes.PATTERN_INVALID.equals(
                                                        e.code())));
    }

    @Test
    void authorityNatureMakesUsccOptional() throws Exception {
        String info =
                """
                {
                  "info.company_name": "测试机关",
                  "info.nature": [{"word_describe": "机关"}],
                  "info.reg_status": [{"word_describe": "存续"}],
                  "info.cooperation_types": [{"word_describe": "客户"}],
                  "info.industry_types": ["采矿业"]
                }
                """;
        String basicInfo =
                """
                {
                  "basic_info.legal_person": "张三"
                }
                """;

        ValidationResult result =
                validate(request(OBJECT_MAPPER.readTree(info), OBJECT_MAPPER.readTree(basicInfo)));

        assertTrue(result.errors().stream().noneMatch(e -> "basic_info.uscc".equals(e.fieldKey())));
    }

    @Test
    void regulatoryCooperationTypeRequiresAuthority() throws Exception {
        String info =
                """
                {
                  "info.company_name": "测试企业",
                  "info.nature": [{"word_describe": "企业"}],
                  "info.reg_status": [{"word_describe": "存续"}],
                  "info.cooperation_types": [{"word_describe": "监管"}],
                  "info.industry_tags": ["兰炭"],
                  "info.industry_types": ["采矿业"]
                }
                """;

        ValidationResult result =
                validate(request(OBJECT_MAPPER.readTree(info), validBasicInfoRow()));

        assertTrue(
                result.errors().stream()
                        .anyMatch(
                                e ->
                                        "info.cooperation_types".equals(e.fieldKey())
                                                && "COOPERATION_TYPE_INVALID".equals(e.code())));
    }

    @Test
    void individualRequiresGenderAndIdCard() throws Exception {
        String info =
                """
                {
                  "info.company_name": "张三",
                  "info.nature": [{"word_describe": "个人"}],
                  "info.cooperation_types": [{"word_describe": "客户"}],
                  "info.industry_types": ["采矿业"]
                }
                """;
        String basicInfo =
                """
                {
                  "basic_info.date_of_birth": "1990-01-01"
                }
                """;

        ValidationResult result =
                validate(request(OBJECT_MAPPER.readTree(info), OBJECT_MAPPER.readTree(basicInfo)));
        Set<String> names = requiredNames(result);

        assertTrue(names.contains("性别"));
        assertTrue(names.contains("基本信息-身份证号"));
        assertFalse(names.contains("登记状态"));
        assertTrue(result.errors().stream().noneMatch(e -> "basic_info.uscc".equals(e.fieldKey())));
    }

    @Test
    void individualIdCardMustMatchGender() throws Exception {
        String info =
                """
                {
                  "info.company_name": "张三",
                  "info.nature": [{"word_describe": "个人"}],
                  "info.gender": "女",
                  "info.cooperation_types": [{"word_describe": "客户"}],
                  "info.industry_types": ["采矿业"]
                }
                """;
        String basicInfo =
                """
                {
                  "basic_info.id_card_no": "11010119900101123X",
                  "basic_info.date_of_birth": "1990-01-01"
                }
                """;

        ValidationResult result =
                validate(request(OBJECT_MAPPER.readTree(info), OBJECT_MAPPER.readTree(basicInfo)));

        assertTrue(
                result.errors().stream()
                        .anyMatch(
                                e ->
                                        "basic_info.id_card_no".equals(e.fieldKey())
                                                && e.message().contains("性别")));
    }

    @Test
    void departmentNameRequiredAndMobileFormatChecked() throws Exception {
        String request =
                """
                {
                  "nestedModuleData": {
                    "moduleId": 1001,
                    "record": [
                      {
                        "row": %s,
                        "children": [
                          {
                            "moduleId": 1003,
                            "record": [
                              {
                                "row": {
                                  "member.type": "DEPT",
                                  "member.mobile_phone_no": "123"
                                }
                              },
                              {
                                "row": {
                                  "member.type": "EMPLOYEE",
                                  "member.name": "李四",
                                  "member.mobile_phone_no": "13800138000"
                                }
                              }
                            ]
                          }
                        ]
                      }
                    ]
                  }
                }
                """
                        .formatted(validInfoRow());

        ValidationResult result = validate(OBJECT_MAPPER.readTree(request));

        assertTrue(
                result.errors().stream()
                        .anyMatch(
                                e ->
                                        "member.name".equals(e.fieldKey())
                                                && ValidationErrorCodes.REQUIRED.equals(e.code())));
        assertTrue(
                result.errors().stream()
                        .anyMatch(
                                e ->
                                        "member.mobile_phone_no".equals(e.fieldKey())
                                                && ValidationErrorCodes.PATTERN_INVALID.equals(
                                                        e.code())));
    }

    @Test
    void duplicateMobileWithDifferentNameIsRejected() throws Exception {
        ValidationResult result = validate(memberRequest("13800138000", "张三", "李四"));

        assertTrue(
                result.errors().stream()
                        .anyMatch(
                                e ->
                                        "member.mobile_phone_no".equals(e.fieldKey())
                                                && e.message().contains("重复手机号")));
    }

    @Test
    void duplicateMobileWithSameNameIsAllowed() throws Exception {
        ValidationResult result = validate(memberRequest("13800138000", "张三", "张三"));

        assertTrue(
                result.errors().stream()
                        .noneMatch(e -> "member.mobile_phone_no".equals(e.fieldKey())));
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

        ValidationResult result = validate(OBJECT_MAPPER.readTree(request));

        assertTrue(
                result.errors().stream()
                        .anyMatch(
                                e ->
                                        "venue.address".equals(e.fieldKey())
                                                && ValidationErrorCodes.REQUIRED.equals(e.code())));
        assertTrue(result.errors().stream().anyMatch(e -> e.message().contains("场地名称不可重复")));
    }

    @Test
    void amountAndBusinessTermAreValidated() throws Exception {
        String basicInfo =
                """
                {
                  "basic_info.uscc": "91310000000000000X",
                  "basic_info.legal_person": "张三",
                  "basic_info.insured_count": 10,
                  "basic_info.reg_capital": "-1",
                  "basic_info.paid_in_capital": 10,
                  "basic_info.reg_date": "2020-01-01",
                  "basic_info.business_term_start": "2030-01-01",
                  "basic_info.business_term_end": "2020-01-01"
                }
                """;

        ValidationResult result =
                validate(request(validInfoRow(), OBJECT_MAPPER.readTree(basicInfo)));

        assertTrue(
                result.errors().stream()
                        .anyMatch(
                                e ->
                                        "basic_info.reg_capital".equals(e.fieldKey())
                                                && e.message().contains("非负")));
        assertTrue(
                result.errors().stream()
                        .anyMatch(
                                e ->
                                        "basic_info.business_term_end".equals(e.fieldKey())
                                                && ValidationErrorCodes.RANGE_INVALID.equals(
                                                        e.code())));
    }

    private static JsonNode memberRequest(String mobile, String firstName, String secondName)
            throws Exception {
        String request =
                """
                {
                  "nestedModuleData": {
                    "moduleId": 1001,
                    "record": [
                      {
                        "row": %s,
                        "children": [
                          {
                            "moduleId": 1003,
                            "record": [
                              {
                                "row": {
                                  "member.type": "EMPLOYEE",
                                  "member.name": "%s",
                                  "member.mobile_phone_no": "%s"
                                }
                              },
                              {
                                "row": {
                                  "member.type": "EMPLOYEE",
                                  "member.name": "%s",
                                  "member.mobile_phone_no": "%s"
                                }
                              }
                            ]
                          }
                        ]
                      }
                    ]
                  }
                }
                """
                        .formatted(validInfoRow(), firstName, mobile, secondName, mobile);
        return OBJECT_MAPPER.readTree(request);
    }

    private static ValidationResult validate(JsonNode request) {
        return validator.validate(request, OPTIONS);
    }

    private static Set<String> requiredNames(ValidationResult result) {
        return result.errors().stream()
                .filter(e -> ValidationErrorCodes.REQUIRED.equals(e.code()))
                .map(ExternalObjectFieldLabels::displayName)
                .collect(Collectors.toSet());
    }

    private static JsonNode validInfoRow() throws Exception {
        return OBJECT_MAPPER.readTree(
                """
                {
                  "info.company_name": "测试企业",
                  "info.nature": [{"word_describe": "企业"}],
                  "info.reg_status": [{"word_describe": "存续"}],
                  "info.cooperation_types": [{"word_describe": "客户"}],
                  "info.industry_tags": ["兰炭"],
                  "info.industry_types": ["采矿业"]
                }
                """);
    }

    private static JsonNode validBasicInfoRow() throws Exception {
        return OBJECT_MAPPER.readTree(
                """
                {
                  "basic_info.uscc": "91310000000000000X",
                  "basic_info.legal_person": "张三",
                  "basic_info.insured_count": 10,
                  "basic_info.reg_capital": 100,
                  "basic_info.paid_in_capital": 50,
                  "basic_info.reg_date": "2020-01-01",
                  "basic_info.business_term_start": "2020-01-01",
                  "basic_info.business_term_end": "2030-01-01",
                  "basic_info.license_url": "http://example.com/license.png"
                }
                """);
    }

    private static JsonNode request(JsonNode infoRow, JsonNode basicInfoRow) throws Exception {
        return OBJECT_MAPPER.readTree(
                """
                {
                  "nestedModuleData": {
                    "moduleId": 1001,
                    "record": [
                      {
                        "row": %s,
                        "children": [
                          {
                            "moduleId": 1002,
                            "record": [{"row": %s}]
                          }
                        ]
                      }
                    ]
                  }
                }
                """
                        .formatted(infoRow.toString(), basicInfoRow.toString()));
    }
}
