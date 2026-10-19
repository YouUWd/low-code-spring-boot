package com.jdec.platform.shared.validation.dynamic;

import java.util.List;

/** 字段适用性策略。 */
public interface ApplicabilityPolicy {

    ApplicabilityResult evaluate(ConditionContext context);

    /** 策略引用到的控制字段，供启动时配置校验和循环依赖检测使用。 */
    default List<FieldRef> referencedFields() {
        return List.of();
    }
}
