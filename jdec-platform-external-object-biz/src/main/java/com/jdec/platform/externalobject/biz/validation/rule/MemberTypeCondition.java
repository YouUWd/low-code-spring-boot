package com.jdec.platform.externalobject.biz.validation.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.jdec.platform.shared.validation.dynamic.Condition;
import com.jdec.platform.shared.validation.dynamic.ConditionContext;
import com.jdec.platform.shared.validation.dynamic.ConditionResult;
import com.jdec.platform.shared.validation.dynamic.FieldRef;
import java.util.List;

/**
 * 人员构成节点类型条件。
 *
 * <p>兼容 {@code DEPT/EMPLOYEE} 与 {@code 部门/员工} 两种取值。
 */
public final class MemberTypeCondition implements Condition {

    private static final String MEMBER_TYPE = "member.type";

    private final boolean department;

    private MemberTypeCondition(boolean department) {
        this.department = department;
    }

    public static MemberTypeCondition department() {
        return new MemberTypeCondition(true);
    }

    public static MemberTypeCondition employee() {
        return new MemberTypeCondition(false);
    }

    @Override
    public ConditionResult evaluate(ConditionContext context) {
        JsonNode type = JsonValueSupport.read(context.current().row(), MEMBER_TYPE);
        if (JsonValueSupport.isBlank(type)) {
            return ConditionResult.unknown("人员类型缺失，无法判断字段是否适用");
        }
        boolean isDepartment = JsonValueSupport.containsAnyText(type, "部门", "DEPT");
        boolean isEmployee = JsonValueSupport.containsAnyText(type, "员工", "EMPLOYEE");
        if (!isDepartment && !isEmployee) {
            return ConditionResult.unknown("无法识别人员类型");
        }
        return (department ? isDepartment : isEmployee)
                ? ConditionResult.yes()
                : ConditionResult.no();
    }

    @Override
    public List<FieldRef> referencedFields() {
        return List.of(FieldRef.current(MEMBER_TYPE));
    }
}
