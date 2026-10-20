package com.jdec.platform.validation.externalobject.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.externalobject.api.ExternalObjectQueryApi;
import com.jdec.platform.externalobject.api.dto.ExternalObjectQueryField;
import com.jdec.platform.externalobject.api.dto.request.ExternalObjectFieldQuery;
import com.jdec.platform.externalobject.api.dto.request.ExternalObjectMemberQuery;
import com.jdec.platform.externalobject.api.dto.response.ExternalObjectMemberMatch;
import com.jdec.platform.shared.validation.dynamic.ValidationError;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExternalObjectDuplicateCheckerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void duplicateCompanyNameIsRejected() throws Exception {
        FakeQueryApi api =
                new FakeQueryApi().duplicate(ExternalObjectQueryField.COMPANY_NAME, "测试企业");
        List<ValidationError> errors =
                checker(api).check(request(OBJECT_MAPPER.readTree(validInfoRow()), null), 0L);

        assertTrue(
                errors.stream()
                        .anyMatch(
                                e ->
                                        "info.company_name".equals(e.fieldKey())
                                                && ExternalObjectDuplicateChecker.DUPLICATE_VALUE
                                                        .equals(e.code())
                                                && e.message().contains("测试企业")));
    }

    @Test
    void duplicateUsccAndShortNameAreRejected() throws Exception {
        FakeQueryApi api =
                new FakeQueryApi()
                        .duplicate(ExternalObjectQueryField.USCC, "91310000000000000X")
                        .duplicate(ExternalObjectQueryField.SHORT_NAME, "测试简称");
        String basicInfo =
                """
                {
                  "basic_info.uscc": "91310000000000000X",
                  "basic_info.short_name": "测试简称"
                }
                """;
        List<ValidationError> errors =
                checker(api)
                        .check(
                                request(
                                        OBJECT_MAPPER.readTree(validInfoRow()),
                                        OBJECT_MAPPER.readTree(basicInfo)),
                                0L);

        assertTrue(errors.stream().anyMatch(e -> "basic_info.uscc".equals(e.fieldKey())));
        assertTrue(errors.stream().anyMatch(e -> "basic_info.short_name".equals(e.fieldKey())));
    }

    @Test
    void selfIsExcludedWhenEditing() throws Exception {
        FakeQueryApi api = new FakeQueryApi();
        JsonNode info =
                OBJECT_MAPPER.readTree(
                        """
                        {
                          "info.company_name": "测试企业",
                          "info.id": 12,
                          "info.business_no": "CR001"
                        }
                        """);
        checker(api).check(request(info, null), 3L);

        assertEquals(12L, api.lastFieldQuery.excludeCompanyId());
        assertEquals("CR001", api.lastFieldQuery.excludeBusinessNo());
        assertEquals(3L, api.lastFieldQuery.subjectId());
    }

    @Test
    void duplicateMobileWithDifferentNameIsRejected() throws Exception {
        FakeQueryApi api =
                new FakeQueryApi()
                        .member(new ExternalObjectMemberMatch("13800138000", null, "李四", "其他企业"));
        List<ValidationError> errors =
                checker(api).check(memberRequest("张三", "13800138000", null), 0L);

        assertTrue(
                errors.stream()
                        .anyMatch(
                                e ->
                                        "member.mobile_phone_no".equals(e.fieldKey())
                                                && e.message()
                                                        .contains(
                                                                "手机号【13800138000】在外部对象【其他企业】中已存在")));
    }

    @Test
    void sameMemberIsAllowed() throws Exception {
        FakeQueryApi api =
                new FakeQueryApi()
                        .member(
                                new ExternalObjectMemberMatch(
                                        "13800138000", "110101199001011234", "张三", "其他企业"));
        List<ValidationError> errors =
                checker(api).check(memberRequest("张三", "13800138000", "110101199001011234"), 0L);

        assertTrue(errors.isEmpty(), () -> errors.toString());
    }

    @Test
    void sameNameWithDifferentIdentityIsRejected() throws Exception {
        FakeQueryApi api =
                new FakeQueryApi()
                        .member(
                                new ExternalObjectMemberMatch(
                                        "13800138000", "110101199001011234", "张三", "其他企业"));
        List<ValidationError> errors =
                checker(api).check(memberRequest("张三", "13800138000", "110101199001011235"), 0L);

        assertTrue(
                errors.stream()
                        .anyMatch(
                                e ->
                                        "member.mobile_phone_no".equals(e.fieldKey())
                                                && e.message().contains("姓名与手机号码一致，但证件号码不同")));
    }

    @Test
    void departmentMobileIsNotCheckedForCompany() throws Exception {
        FakeQueryApi api = new FakeQueryApi();
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

        List<ValidationError> errors = checker(api).check(OBJECT_MAPPER.readTree(request), 0L);

        assertTrue(errors.isEmpty());
        assertTrue(api.memberQueries.isEmpty());
    }

    @Test
    void queryFailureIsIgnored() throws Exception {
        ExternalObjectQueryApi failing =
                new ExternalObjectQueryApi() {
                    @Override
                    public Set<String> findDuplicateValues(ExternalObjectFieldQuery query) {
                        throw new IllegalStateException("db down");
                    }

                    @Override
                    public List<ExternalObjectMemberMatch> findDuplicateMembers(
                            ExternalObjectMemberQuery query) {
                        throw new IllegalStateException("db down");
                    }
                };

        List<ValidationError> errors =
                new ExternalObjectDuplicateChecker(OBJECT_MAPPER, failing)
                        .check(request(OBJECT_MAPPER.readTree(validInfoRow()), null), 0L);

        assertTrue(errors.isEmpty());
    }

    private static ExternalObjectDuplicateChecker checker(ExternalObjectQueryApi api) {
        return new ExternalObjectDuplicateChecker(OBJECT_MAPPER, api);
    }

    private static JsonNode request(JsonNode infoRow, JsonNode basicInfoRow) throws Exception {
        String children =
                basicInfoRow == null
                        ? ""
                        : """
                          ,"children": [
                            {"moduleId": 1002, "record": [{"row": %s}]}
                          ]
                          """
                                .formatted(basicInfoRow.toString());
        String request =
                """
                {
                  "nestedModuleData": {
                    "moduleId": 1001,
                    "record": [{"row": %s%s}]
                  }
                }
                """
                        .formatted(infoRow.toString(), children);
        return OBJECT_MAPPER.readTree(request);
    }

    private static JsonNode memberRequest(String name, String mobile, String identity)
            throws Exception {
        String identityField =
                identity == null ? "" : ",\"member.identity_no\": \"" + identity + "\"";
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
                                  "member.mobile_phone_no": "%s"%s
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
                        .formatted(validInfoRow(), name, mobile, identityField);
        return OBJECT_MAPPER.readTree(request);
    }

    private static String validInfoRow() {
        return """
                {
                  "info.company_name": "测试企业",
                  "info.nature": [{"word_describe": "企业"}],
                  "info.reg_status": [{"word_describe": "存续"}],
                  "info.cooperation_types": [{"word_describe": "客户"}],
                  "info.industry_types": ["采矿业"]
                }
                """;
    }

    /** 测试用查询桩：按预设值返回命中结果，并记录最后一次查询参数。 */
    private static final class FakeQueryApi implements ExternalObjectQueryApi {

        private final Set<String> duplicated = new HashSet<>();
        private final List<ExternalObjectMemberMatch> members = new ArrayList<>();
        private final List<ExternalObjectMemberQuery> memberQueries = new ArrayList<>();
        private ExternalObjectFieldQuery lastFieldQuery;

        private FakeQueryApi duplicate(ExternalObjectQueryField field, String value) {
            duplicated.add(field + "|" + value);
            return this;
        }

        private FakeQueryApi member(ExternalObjectMemberMatch match) {
            members.add(match);
            return this;
        }

        @Override
        public Set<String> findDuplicateValues(ExternalObjectFieldQuery query) {
            lastFieldQuery = query;
            Set<String> result = new HashSet<>();
            for (String value : query.values()) {
                if (duplicated.contains(query.field() + "|" + value)) {
                    result.add(value);
                }
            }
            return result;
        }

        @Override
        public List<ExternalObjectMemberMatch> findDuplicateMembers(
                ExternalObjectMemberQuery query) {
            memberQueries.add(query);
            List<ExternalObjectMemberMatch> result = new ArrayList<>();
            for (ExternalObjectMemberMatch match : members) {
                if ((match.mobilePhoneNo() != null
                                && query.mobilePhoneNos().contains(match.mobilePhoneNo()))
                        || (match.identityNo() != null
                                && query.identityNos().contains(match.identityNo()))) {
                    result.add(match);
                }
            }
            return result;
        }
    }
}
