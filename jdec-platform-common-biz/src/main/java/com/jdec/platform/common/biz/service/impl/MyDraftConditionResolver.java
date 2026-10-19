package com.jdec.platform.common.biz.service.impl;

import com.jdec.platform.shared.context.UserContext;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * 我的自建/草稿单据条件解析器 (MyDraftConditionResolver)
 *
 * <p>核心职责：
 *
 * <ul>
 *   <li>构建针对当前登录人创建的单据过滤条件（{@code created_by = :userId}）。
 *   <li>支持可选择的状态约束（如待办时仅查草稿 {@code business_status = 1}，全部时传 null 表示不限状态）。
 * </ul>
 */
@Component
public class MyDraftConditionResolver {

    public static final String CREATED_BY_COLUMN = "created_by";
    public static final String BUSINESS_STATUS_COLUMN = "business_status";

    /**
     * 构建当前登录人创建单据的 jOOQ 条件分支（自动从 UserContext 提取当前生效用户）
     *
     * @param businessStatus 业务状态过滤值 (如草稿传 1，不限状态传 null)
     * @return 包含 created_by [AND business_status] 的 Condition
     */
    public Optional<Condition> resolve(Integer businessStatus) {
        Long userId =
                UserContext.isMockLogin() ? UserContext.getMockUserId() : UserContext.getUserId();
        if (userId == null) {
            return Optional.empty();
        }
        return resolve(userId, businessStatus);
    }

    /**
     * 构建指定用户创建单据的 jOOQ 条件分支
     *
     * @param userId 创建人用户 ID (必填，若为空则返回 empty)
     * @param businessStatus 业务状态过滤值 (如草稿传 1，不限状态传 null)
     * @return 包含 created_by [AND business_status] 的 Condition
     */
    public Optional<Condition> resolve(Long userId, Integer businessStatus) {
        if (userId == null) {
            return Optional.empty();
        }
        Condition cond = DSL.field(DSL.name(CREATED_BY_COLUMN), Long.class).eq(userId);
        if (businessStatus != null) {
            cond =
                    cond.and(
                            DSL.field(DSL.name(BUSINESS_STATUS_COLUMN), Integer.class)
                                    .eq(businessStatus));
        }
        return Optional.of(cond);
    }
}
