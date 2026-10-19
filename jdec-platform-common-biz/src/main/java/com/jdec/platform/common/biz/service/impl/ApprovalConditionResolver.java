package com.jdec.platform.common.biz.service.impl;

import java.util.Collection;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * 审批链单据条件解析器 (ApprovalConditionResolver)
 *
 * <p>核心职责：
 *
 * <ul>
 *   <li>构建针对审批链单据编号列表的过滤条件（{@code business_no IN (:businessNos)}）。
 *   <li>专注于单据号维度的 SQL 条件表达式生成。
 * </ul>
 */
@Component
public class ApprovalConditionResolver {

    public static final String BUSINESS_NO_COLUMN = "business_no";

    /**
     * 构建审批单据号匹配的 jOOQ 条件分支
     *
     * @param businessNos 业务单据号集合 (若为空则返回 empty)
     * @return 包含 business_no IN (:businessNos) 的 Condition
     */
    public Optional<Condition> resolve(Collection<String> businessNos) {
        if (CollectionUtils.isEmpty(businessNos)) {
            return Optional.empty();
        }
        return Optional.of(DSL.field(DSL.name(BUSINESS_NO_COLUMN), String.class).in(businessNos));
    }
}
