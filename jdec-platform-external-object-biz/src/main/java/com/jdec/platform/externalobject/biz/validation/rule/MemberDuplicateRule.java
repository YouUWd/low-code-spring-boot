package com.jdec.platform.externalobject.biz.validation.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.jdec.platform.shared.validation.dynamic.FieldDefinition;
import com.jdec.platform.shared.validation.dynamic.FieldRule;
import com.jdec.platform.shared.validation.dynamic.RecordNode;
import com.jdec.platform.shared.validation.dynamic.ValidationContext;
import com.jdec.platform.shared.validation.dynamic.ValidationState;

/**
 * 人员重复校验规则。
 *
 * <p>当手机号或证件号重复时，只有当姓名、证件号、手机号三项全部相同时才视为同一人员； 任意一项不一致即不可提交。
 *
 * @param otherFieldKey 与当前字段一起用于判定同一人员的另一个标识字段
 * @param message 错误提示
 */
public final class MemberDuplicateRule implements FieldRule {

    public static final String DUPLICATE_MEMBER = "DUPLICATE_MEMBER";

    private static final String MEMBER_NAME = "member.name";

    private final String otherFieldKey;
    private final String message;

    public MemberDuplicateRule(String otherFieldKey, String message) {
        this.otherFieldKey = otherFieldKey;
        this.message = message;
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
        if (record.parent() == null) {
            return;
        }

        String currentName = normalize(record.row().get(MEMBER_NAME));
        String currentOther = normalize(record.row().get(otherFieldKey));

        for (RecordNode sibling : record.parent().children()) {
            if (sibling == record || !sibling.moduleId().equals(record.moduleId())) {
                continue;
            }
            String siblingValue = normalize(sibling.row().get(field.fieldKey()));
            if (!currentValue.equals(siblingValue)) {
                continue;
            }
            String siblingName = normalize(sibling.row().get(MEMBER_NAME));
            String siblingOther = normalize(sibling.row().get(otherFieldKey));
            if (!currentName.equals(siblingName) || !currentOther.equals(siblingOther)) {
                state.addError(
                        context.recordPath(),
                        field.moduleId(),
                        field.fieldKey(),
                        DUPLICATE_MEMBER,
                        message);
                return;
            }
        }
    }

    private static String normalize(JsonNode value) {
        return JsonValueSupport.compactText(value);
    }
}
