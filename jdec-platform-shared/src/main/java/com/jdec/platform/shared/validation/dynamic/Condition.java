package com.jdec.platform.shared.validation.dynamic;

import java.util.List;

/** 条件表达式。 */
public interface Condition {

    ConditionResult evaluate(ConditionContext context);

    /** 条件引用到的控制字段，供启动时配置校验和循环依赖检测使用。 */
    default List<FieldRef> referencedFields() {
        return List.of();
    }
}
