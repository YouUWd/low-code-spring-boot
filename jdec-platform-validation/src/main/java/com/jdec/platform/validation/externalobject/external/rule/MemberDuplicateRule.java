package com.jdec.platform.validation.externalobject.external.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.jdec.platform.shared.validation.dynamic.FieldDefinition;
import com.jdec.platform.shared.validation.dynamic.FieldRule;
import com.jdec.platform.shared.validation.dynamic.RecordNode;
import com.jdec.platform.shared.validation.dynamic.ValidationContext;
import com.jdec.platform.shared.validation.dynamic.ValidationState;

/**
 * 人员重复校验规则。
 *
 * <p>姓名、证件号、手机号三项全部相同的记录视为同一人员，允许提交；否则按以下规则提示：
 *
 * <ul>
 *   <li>姓名与当前标识（手机号/证件号）一致，但另一标识不同：提示两项一致、另一项不同；
 *   <li>其余重复情况：提示当前值在该外部对象中已存在。
 * </ul>
 */
public final class MemberDuplicateRule implements FieldRule {

    public static final String DUPLICATE_MEMBER = "DUPLICATE_MEMBER";

    private static final String MEMBER_NAME = "member.name";

    private final String otherFieldKey;
    private final String fieldLabel;
    private final String sameNameMessage;

    private MemberDuplicateRule(String otherFieldKey, String fieldLabel, String sameNameMessage) {
        this.otherFieldKey = otherFieldKey;
        this.fieldLabel = fieldLabel;
        this.sameNameMessage = sameNameMessage;
    }

    /** 手机号重复：姓名相同但证件号不同，或手机号已存在。 */
    public static MemberDuplicateRule mobile() {
        return new MemberDuplicateRule("member.identity_no", "手机号", "姓名与手机号码一致，但证件号码不同，请核对后重新填写!");
    }

    /** 证件号重复：姓名相同但手机号不同，或证件号已存在。 */
    public static MemberDuplicateRule identityNo() {
        return new MemberDuplicateRule(
                "member.mobile_phone_no", "证件号", "姓名与证件号一致，但手机号码不同，请核对后重新填写!");
    }

    @Override
    public void validate(
            JsonNode value,
            FieldDefinition field,
            ValidationContext context,
            ValidationState state) {
        String currentValue = normalize(value);
        if (currentValue.isEmpty()) {
            return;
        }
        RecordNode record = context.current();
        if (record.row() == null) {
            return;
        }

        String currentName = normalize(record.row().get(MEMBER_NAME));
        String currentOther = normalize(record.row().get(otherFieldKey));
        RecordNode root = rootOf(record);

        for (RecordNode other : context.tree().recordsByModule(record.moduleId())) {
            if (other == record || other.row() == null || rootOf(other) != root) {
                continue;
            }
            String otherValue = normalize(other.row().get(field.fieldKey()));
            if (!currentValue.equals(otherValue)) {
                continue;
            }
            String otherName = normalize(other.row().get(MEMBER_NAME));
            String otherOther = normalize(other.row().get(otherFieldKey));
            boolean sameName = !currentName.isEmpty() && currentName.equals(otherName);
            if (sameName && currentOther.equals(otherOther)) {
                // 姓名、证件号、手机号全部相同，视为同一人员。
                continue;
            }
            String message =
                    sameName
                            ? sameNameMessage
                            : fieldLabel
                                    + "【"
                                    + currentValue
                                    + "】在外部对象【"
                                    + JsonValueSupport.resolveExternalObjectName(context)
                                    + "】中已存在，请核对后重新填写!";
            state.addError(
                    context.recordPath(),
                    field.moduleId(),
                    field.fieldKey(),
                    DUPLICATE_MEMBER,
                    message);
            return;
        }
    }

    private static RecordNode rootOf(RecordNode node) {
        RecordNode current = node;
        while (current.parent() != null) {
            current = current.parent();
        }
        return current;
    }

    private static String normalize(JsonNode value) {
        return JsonValueSupport.compactText(value);
    }
}
