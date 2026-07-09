package com.jdec.platform.config.biz.check;

import java.util.List;

public interface ReferenceChecker {
    /**
     * 被删除对象类型
     *
     * @return
     */
    List<String> targetType();

    /**
     * 检察引用
     *
     * @param referenceContext
     * @return
     */
    ReferenceCheckResult check(ReferenceContext referenceContext);
}
