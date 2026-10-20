package com.jdec.platform.validation.externalobject.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.externalobject.api.ExternalObjectQueryApi;
import com.jdec.platform.externalobject.api.dto.ExternalObjectQueryField;
import com.jdec.platform.externalobject.api.dto.request.ExternalObjectFieldQuery;
import com.jdec.platform.externalobject.api.dto.request.ExternalObjectMemberQuery;
import com.jdec.platform.externalobject.api.dto.response.ExternalObjectMemberMatch;
import com.jdec.platform.shared.validation.dynamic.RecordNode;
import com.jdec.platform.shared.validation.dynamic.RequestTree;
import com.jdec.platform.shared.validation.dynamic.RequestTreeBuilder;
import com.jdec.platform.shared.validation.dynamic.ValidationError;
import com.jdec.platform.shared.validation.dynamic.ValidationOptions;
import com.jdec.platform.shared.validation.dynamic.ValidationState;
import com.jdec.platform.validation.externalobject.support.JsonValues;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 外部对象数据库查重。
 *
 * <p>与 {@link ExternalObjectValidationDefinition} 中的静态规则互补：本类通过 {@link ExternalObjectQueryApi}
 * 查询已保存的外部对象数据，校验字段值/人员号码是否重复：
 *
 * <ul>
 *   <li>外部对象全称、统一社会信用代码、外部对象简称、英文全称、英文简称不可与已保存数据重复；
 *   <li>人员手机号、证件号不可与已保存数据重复；姓名、证件号、手机号全部相同的视为同一人员，允许提交。
 * </ul>
 *
 * <p>变更申请单编辑时，按提交数据中的 {@code info.id} / {@code info.source_id} / {@code info.business_no}
 * 排除当前外部对象（含历史版本）。查询失败只记录日志，不阻断业务校验。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalObjectDuplicateChecker {

    /** 字段值重复 */
    public static final String DUPLICATE_VALUE = "DUPLICATE_VALUE";

    /** 人员重复 */
    public static final String DUPLICATE_MEMBER = "DUPLICATE_MEMBER";

    private static final String INFO = ExternalObjectModules.INFO;
    private static final String BASIC_INFO = ExternalObjectModules.BASIC_INFO;
    private static final String MEMBER = ExternalObjectModules.MEMBER;

    private static final String INFO_COMPANY_NAME = "info.company_name";
    private static final String INFO_BUSINESS_NO = "info.business_no";
    private static final String INFO_ID = "info.id";
    private static final String INFO_SOURCE_ID = "info.source_id";
    private static final String INFO_NATURE = "info.nature";

    private static final String BASIC_INFO_USCC = "basic_info.uscc";
    private static final String BASIC_INFO_SHORT_NAME = "basic_info.short_name";
    private static final String BASIC_INFO_EN_FULL_NAME = "basic_info.en_full_name";
    private static final String BASIC_INFO_EN_SHORT_NAME = "basic_info.en_short_name";

    private static final String MEMBER_NAME = "member.name";
    private static final String MEMBER_TYPE = "member.type";
    private static final String MEMBER_IDENTITY_NO = "member.identity_no";
    private static final String MEMBER_MOBILE = "member.mobile_phone_no";

    private static final String SAME_NAME_MOBILE_MESSAGE = "姓名与手机号码一致，但证件号码不同，请核对后重新填写!";
    private static final String SAME_NAME_IDENTITY_MESSAGE = "姓名与证件号一致，但手机号码不同，请核对后重新填写!";

    private static final Map<ExternalObjectQueryField, String> FIELD_KEYS = fieldKeys();

    private static Map<ExternalObjectQueryField, String> fieldKeys() {
        Map<ExternalObjectQueryField, String> keys = new LinkedHashMap<>();
        keys.put(ExternalObjectQueryField.COMPANY_NAME, INFO_COMPANY_NAME);
        keys.put(ExternalObjectQueryField.USCC, BASIC_INFO_USCC);
        keys.put(ExternalObjectQueryField.SHORT_NAME, BASIC_INFO_SHORT_NAME);
        keys.put(ExternalObjectQueryField.EN_FULL_NAME, BASIC_INFO_EN_FULL_NAME);
        keys.put(ExternalObjectQueryField.EN_SHORT_NAME, BASIC_INFO_EN_SHORT_NAME);
        return Collections.unmodifiableMap(keys);
    }

    private static final Map<ExternalObjectQueryField, String> FIELD_LABELS =
            Map.of(
                    ExternalObjectQueryField.COMPANY_NAME, "外部对象全称",
                    ExternalObjectQueryField.USCC, "统一社会信用代码",
                    ExternalObjectQueryField.SHORT_NAME, "外部对象简称",
                    ExternalObjectQueryField.EN_FULL_NAME, "外部对象英文全称",
                    ExternalObjectQueryField.EN_SHORT_NAME, "外部对象英文简称");

    private final ObjectMapper objectMapper;

    private final ExternalObjectQueryApi queryApi;

    /**
     * 执行数据库查重。
     *
     * @param payload 前端提交的请求体（DynamicSaveReq、Map 或 JsonNode）
     * @param subjectId 主体ID；为 null 时不按主体过滤
     * @return 查重错误；无重复或查询失败时返回空列表
     */
    public List<ValidationError> check(Object payload, Long subjectId) {
        if (payload == null) {
            return List.of();
        }
        try {
            JsonNode request =
                    payload instanceof JsonNode node ? node : objectMapper.valueToTree(payload);
            ValidationOptions options = ValidationOptions.defaults();
            RequestTree tree =
                    RequestTreeBuilder.build(request, new ValidationState(options), options);
            return doCheck(tree, subjectId);
        } catch (Exception e) {
            log.warn("外部对象数据库查重失败，已跳过数据库查重", e);
            return List.of();
        }
    }

    private List<ValidationError> doCheck(RequestTree tree, Long subjectId) {
        RecordNode infoNode = first(tree.recordsByModule(INFO));
        if (infoNode == null || infoNode.row() == null) {
            return List.of();
        }
        Long excludeCompanyId = firstLong(infoNode, INFO_ID, INFO_SOURCE_ID);
        String excludeBusinessNo =
                JsonValues.text(JsonValues.read(infoNode.row(), INFO_BUSINESS_NO));

        List<ValidationError> errors = new ArrayList<>();
        checkFields(tree, subjectId, excludeCompanyId, excludeBusinessNo, errors);
        checkMembers(tree, infoNode, subjectId, excludeCompanyId, excludeBusinessNo, errors);
        return errors;
    }

    /** 外部对象主档/基本信息字段查重。 */
    private void checkFields(
            RequestTree tree,
            Long subjectId,
            Long excludeCompanyId,
            String excludeBusinessNo,
            List<ValidationError> errors) {
        for (Map.Entry<ExternalObjectQueryField, String> entry : FIELD_KEYS.entrySet()) {
            ExternalObjectQueryField queryField = entry.getKey();
            String fieldKey = entry.getValue();
            String moduleId =
                    queryField == ExternalObjectQueryField.COMPANY_NAME ? INFO : BASIC_INFO;

            Map<String, List<RecordNode>> nodesByValue = new LinkedHashMap<>();
            for (RecordNode node : tree.recordsByModule(moduleId)) {
                String value = JsonValues.text(JsonValues.read(node.row(), fieldKey));
                if (!value.isEmpty()) {
                    nodesByValue.computeIfAbsent(value, key -> new ArrayList<>()).add(node);
                }
            }
            if (nodesByValue.isEmpty()) {
                continue;
            }

            Set<String> duplicated =
                    queryApi.findDuplicateValues(
                            new ExternalObjectFieldQuery(
                                    queryField,
                                    nodesByValue.keySet(),
                                    subjectId,
                                    excludeCompanyId,
                                    excludeBusinessNo));
            if (duplicated == null || duplicated.isEmpty()) {
                continue;
            }
            String label = FIELD_LABELS.get(queryField);
            for (Map.Entry<String, List<RecordNode>> e : nodesByValue.entrySet()) {
                if (!duplicated.contains(e.getKey())) {
                    continue;
                }
                String message = label + "【" + e.getKey() + "】已存在，请核对后重新填写!";
                for (RecordNode node : e.getValue()) {
                    errors.add(
                            new ValidationError(
                                    node.path(), moduleId, fieldKey, DUPLICATE_VALUE, message));
                }
            }
        }
    }

    /** 人员手机号/证件号查重。 */
    private void checkMembers(
            RequestTree tree,
            RecordNode infoNode,
            Long subjectId,
            Long excludeCompanyId,
            String excludeBusinessNo,
            List<ValidationError> errors) {
        List<RecordNode> members = tree.recordsByModule(MEMBER);
        if (members.isEmpty()) {
            return;
        }
        boolean individual =
                JsonValues.containsText(JsonValues.read(infoNode.row(), INFO_NATURE), "个人");

        Set<String> mobiles = new LinkedHashSet<>();
        Set<String> identities = new LinkedHashSet<>();
        for (RecordNode member : members) {
            String mobile = JsonValues.compactText(JsonValues.read(member.row(), MEMBER_MOBILE));
            if (!mobile.isEmpty() && !isDepartmentWithoutMobile(individual, member)) {
                mobiles.add(mobile);
            }
            String identity =
                    JsonValues.compactText(JsonValues.read(member.row(), MEMBER_IDENTITY_NO));
            if (!identity.isEmpty()) {
                identities.add(identity);
            }
        }
        if (mobiles.isEmpty() && identities.isEmpty()) {
            return;
        }

        List<ExternalObjectMemberMatch> matches =
                queryApi.findDuplicateMembers(
                        new ExternalObjectMemberQuery(
                                mobiles,
                                identities,
                                subjectId,
                                excludeCompanyId,
                                excludeBusinessNo));
        if (matches == null || matches.isEmpty()) {
            return;
        }

        for (RecordNode member : members) {
            String name = JsonValues.text(JsonValues.read(member.row(), MEMBER_NAME));
            String mobile = JsonValues.compactText(JsonValues.read(member.row(), MEMBER_MOBILE));
            String identity =
                    JsonValues.compactText(JsonValues.read(member.row(), MEMBER_IDENTITY_NO));

            if (!mobile.isEmpty() && !isDepartmentWithoutMobile(individual, member)) {
                String message = mobileConflictMessage(name, identity, mobile, matches);
                if (message != null) {
                    errors.add(
                            new ValidationError(
                                    member.path(),
                                    MEMBER,
                                    MEMBER_MOBILE,
                                    DUPLICATE_MEMBER,
                                    message));
                }
            }
            if (!identity.isEmpty()) {
                String message = identityConflictMessage(name, mobile, identity, matches);
                if (message != null) {
                    errors.add(
                            new ValidationError(
                                    member.path(),
                                    MEMBER,
                                    MEMBER_IDENTITY_NO,
                                    DUPLICATE_MEMBER,
                                    message));
                }
            }
        }
    }

    private String mobileConflictMessage(
            String name, String identity, String mobile, List<ExternalObjectMemberMatch> matches) {
        for (ExternalObjectMemberMatch match : matches) {
            if (!mobile.equals(normalize(match.mobilePhoneNo()))) {
                continue;
            }
            boolean sameName = !name.isEmpty() && name.equals(normalize(match.memberName()));
            boolean sameIdentity = identity.equals(normalize(match.identityNo()));
            if (sameName && sameIdentity) {
                // 姓名、手机号、证件号全部相同，视为同一人员。
                continue;
            }
            return sameName
                    ? SAME_NAME_MOBILE_MESSAGE
                    : existingMessage("手机号", mobile, match.companyName());
        }
        return null;
    }

    private String identityConflictMessage(
            String name, String mobile, String identity, List<ExternalObjectMemberMatch> matches) {
        for (ExternalObjectMemberMatch match : matches) {
            if (!identity.equals(normalize(match.identityNo()))) {
                continue;
            }
            boolean sameName = !name.isEmpty() && name.equals(normalize(match.memberName()));
            boolean sameMobile = mobile.equals(normalize(match.mobilePhoneNo()));
            if (sameName && sameMobile) {
                continue;
            }
            return sameName
                    ? SAME_NAME_IDENTITY_MESSAGE
                    : existingMessage("证件号", identity, match.companyName());
        }
        return null;
    }

    /** 对象性质非个人且人员类型为部门时，手机号不参与校验。 */
    private static boolean isDepartmentWithoutMobile(boolean individual, RecordNode member) {
        if (individual) {
            return false;
        }
        JsonNode type = JsonValues.read(member.row(), MEMBER_TYPE);
        return JsonValues.containsAnyText(type, "部门", "DEPT");
    }

    private static String existingMessage(String label, String value, String companyName) {
        String objectName = companyName == null || companyName.isBlank() ? "其他外部对象" : companyName;
        return label + "【" + value + "】在外部对象【" + objectName + "】中已存在，请核对后重新填写!";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static RecordNode first(List<RecordNode> nodes) {
        return nodes == null || nodes.isEmpty() ? null : nodes.get(0);
    }

    private static Long firstLong(RecordNode node, String... fieldKeys) {
        for (String fieldKey : fieldKeys) {
            JsonNode value = JsonValues.read(node.row(), fieldKey);
            if (value != null && value.isNumber()) {
                return value.asLong();
            }
            if (value != null && value.isTextual()) {
                String text = value.asText().trim();
                if (!text.isEmpty()) {
                    try {
                        return Long.valueOf(text);
                    } catch (NumberFormatException ignored) {
                        // 非数字按未提供处理
                    }
                }
            }
        }
        return null;
    }
}
