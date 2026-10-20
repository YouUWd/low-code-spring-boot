package com.jdec.platform.validation.externalobject.external;

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
import com.jdec.platform.validation.externalobject.ExternalObjectFieldLabels;
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
        assertTrue(names.contains("基本信息 - 统一社会信用代码"));
        assertTrue(names.contains("基本信息 - 法定代表人/负责人"));
        assertTrue(names.contains("基本信息 - 注册资本"));
        assertTrue(names.contains("基本信息 - 实缴资本"));
        assertTrue(names.contains("基本信息 - 注册登记日期"));
    }

    @Test
    void nestedRequiredFieldsCarryModulePath() throws Exception {
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
                                "row": {"member.type": "EMPLOYEE"},
                                "children": [
                                  {"moduleId": 1004, "record": [{"row": {}}]}
                                ]
                              }
                            ]
                          },
                          {
                            "moduleId": 1020,
                            "record": [
                              {
                                "row": {},
                                "children": [
                                  {"moduleId": 1005, "record": [{"row": {}}]},
                                  {"moduleId": 1006, "record": [{"row": {}}]}
                                ]
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
        Set<String> names = requiredNames(result);

        assertTrue(names.contains("人员构成 - 手机号码"));
        assertTrue(names.contains("人员构成 - 其他联系方式 - 联系方式"));
        assertTrue(names.contains("人员构成 - 其他联系方式 - 号码"));
        assertTrue(names.contains("联系方式 - 通讯地址"));
        assertTrue(names.contains("联系方式 - 联系人"));
        assertTrue(names.contains("联系方式 - 联系方式"));
        assertTrue(names.contains("联系方式 - 号码"));
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
        assertTrue(names.contains("基本信息 - 身份证号"));
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
                                  "member.type": "DEPT"
                                }
                              },
                              {
                                "row": {
                                  "member.type": "EMPLOYEE",
                                  "member.name": "李四",
                                  "member.mobile_phone_no": "123"
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
        // 部门节点不校验手机号，也不要求必填。
        assertFalse(
                result.errors().stream()
                        .anyMatch(
                                e ->
                                        "member.mobile_phone_no".equals(e.fieldKey())
                                                && ValidationErrorCodes.REQUIRED.equals(e.code())));
    }

    @Test
    void departmentMemberSkipsMobileValidation() throws Exception {
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
                                  "member.name": "研发部",
                                  "member.mobile_phone_no": "123"
                                }
                              },
                              {
                                "row": {
                                  "member.type": "DEPT",
                                  "member.name": "测试部"
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

        assertTrue(result.valid(), result.summary());
        assertTrue(
                result.errors().stream()
                        .noneMatch(e -> "member.mobile_phone_no".equals(e.fieldKey())));
    }

    @Test
    void duplicateMobileWithDifferentNameIsRejected() throws Exception {
        ValidationResult result = validate(memberRequest("13800138000", "张三", "李四"));

        assertTrue(
                result.errors().stream()
                        .anyMatch(
                                e ->
                                        "member.mobile_phone_no".equals(e.fieldKey())
                                                && e.message()
                                                        .contains(
                                                                "手机号【13800138000】在外部对象【测试企业】中已存在")));
    }

    @Test
    void duplicateMobileWithSameNameIsAllowed() throws Exception {
        ValidationResult result = validate(memberRequest("13800138000", "张三", "张三"));

        assertTrue(
                result.errors().stream()
                        .noneMatch(e -> "member.mobile_phone_no".equals(e.fieldKey())));
    }

    @Test
    void duplicateMobileWithSameNameButDifferentIdCardIsRejected() throws Exception {
        ValidationResult result =
                validate(
                        memberRequestWithIdentities(
                                "张三",
                                "13800138000",
                                "110101199001011234",
                                "张三",
                                "13800138000",
                                "110101199001011235"));

        assertTrue(
                result.errors().stream()
                        .anyMatch(
                                e ->
                                        "member.mobile_phone_no".equals(e.fieldKey())
                                                && e.message().contains("姓名与手机号码一致，但证件号码不同")));
    }

    @Test
    void duplicateIdCardWithSameNameButDifferentMobileIsRejected() throws Exception {
        ValidationResult result =
                validate(
                        memberRequestWithIdentities(
                                "张三",
                                "13800138000",
                                "110101199001011234",
                                "张三",
                                "13900139000",
                                "110101199001011234"));

        assertTrue(
                result.errors().stream()
                        .anyMatch(
                                e ->
                                        "member.identity_no".equals(e.fieldKey())
                                                && e.message().contains("姓名与证件号一致，但手机号码不同")));
    }

    @Test
    void duplicateIdCardWithDifferentNameIsRejected() throws Exception {
        ValidationResult result =
                validate(
                        memberRequestWithIdentities(
                                "张三",
                                "13800138000",
                                "110101199001011234",
                                "李四",
                                "13900139000",
                                "110101199001011234"));

        assertTrue(
                result.errors().stream()
                        .anyMatch(
                                e ->
                                        "member.identity_no".equals(e.fieldKey())
                                                && e.message()
                                                        .contains(
                                                                "证件号【110101199001011234】在外部对象【测试企业】中已存在")));
    }

    @Test
    void individualBusinessRegStatusMustBeSurviveOrCancel() throws Exception {
        ValidationResult result = validate(individualBusinessRequest("在业"));

        assertTrue(
                result.errors().stream()
                        .anyMatch(
                                e ->
                                        "info.reg_status".equals(e.fieldKey())
                                                && "REG_STATUS_INVALID".equals(e.code())));
    }

    @Test
    void individualBusinessRegStatusAllowsCancel() throws Exception {
        ValidationResult result = validate(individualBusinessRequest("注销"));

        assertTrue(result.valid(), result.summary());
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

    private static JsonNode memberRequestWithIdentities(
            String firstName,
            String firstMobile,
            String firstIdentity,
            String secondName,
            String secondMobile,
            String secondIdentity)
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
                                  "member.mobile_phone_no": "%s",
                                  "member.identity_no": "%s"
                                }
                              },
                              {
                                "row": {
                                  "member.type": "EMPLOYEE",
                                  "member.name": "%s",
                                  "member.mobile_phone_no": "%s",
                                  "member.identity_no": "%s"
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
                        .formatted(
                                validInfoRow(),
                                firstName,
                                firstMobile,
                                firstIdentity,
                                secondName,
                                secondMobile,
                                secondIdentity);
        return OBJECT_MAPPER.readTree(request);
    }

    private static JsonNode individualBusinessRequest(String regStatus) throws Exception {
        String info =
                """
                {
                  "info.company_name": "某某商行",
                  "info.nature": [{"word_describe": "个体工商户"}],
                  "info.reg_status": [{"word_describe": "%s"}],
                  "info.cooperation_types": [{"word_describe": "客户"}],
                  "info.industry_types": ["采矿业"]
                }
                """
                        .formatted(regStatus);
        String basicInfo =
                """
                {
                  "basic_info.uscc": "921234567890123456",
                  "basic_info.legal_person": "张三"
                }
                """;
        return request(OBJECT_MAPPER.readTree(info), OBJECT_MAPPER.readTree(basicInfo));
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
