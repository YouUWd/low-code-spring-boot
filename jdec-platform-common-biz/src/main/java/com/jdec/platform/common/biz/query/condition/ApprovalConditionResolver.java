package com.jdec.platform.common.biz.query.condition;

import com.jdec.platform.approval.api.dto.ApprovalIdButton;
import com.jdec.platform.approval.api.resp.ApprovalBusinessNoResp;
import com.jdec.platform.approval.api.resp.ApprovalIdButtonListResp;
import com.jdec.platform.common.api.dto.response.ActionRowItem;
import com.jdec.platform.common.biz.postprocess.action.approval.ApprovalActionBuilder;
import com.jdec.platform.data.api.dto.common.DataRow;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * 审批链单据条件解析器 (ApprovalConditionResolver)
 *
 * <p>核心职责：构建针对审批链单据编号列表的查询过滤条件（{@code business_no IN (:businessNos)}）。 行级按钮装配逻辑已拆分至 {@link
 * ApprovalActionBuilder}。
 */
@Component
public class ApprovalConditionResolver {

    public static final String BUSINESS_NO_COLUMN = ApprovalActionBuilder.BUSINESS_NO_COLUMN;

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

    /** 提取审批单据列表中的去重业务单据号 (business_no) 集合 */
    public static List<String> extractBusinessNos(Collection<ApprovalIdButton> list) {
        return ApprovalActionBuilder.extractBusinessNos(list);
    }

    /** 提取审批单据列表中的去重物理记录 ID (recordId) 集合 */
    public static Set<Long> extractRecordIds(Collection<ApprovalIdButton> list) {
        return ApprovalActionBuilder.extractRecordIds(list);
    }

    /** 构建分页列表每行对应的审批操作按钮列表 (委托至 ApprovalActionBuilder) */
    public static List<ActionRowItem> buildApprovalRows(
            List<DataRow> rawRecords,
            String primaryTable,
            Integer approvalType,
            List<ApprovalBusinessNoResp> approvalResp,
            Long currentUserId) {
        return ApprovalActionBuilder.buildApprovalRows(
                rawRecords, primaryTable, approvalType, approvalResp, currentUserId);
    }

    /** 兼容历史基于 ApprovalIdButtonListResp 的重载方法 */
    public static List<ActionRowItem> buildApprovalRows(
            List<DataRow> rawRecords,
            String primaryTable,
            Integer approvalType,
            ApprovalIdButtonListResp approvalResp,
            Long currentUserId) {
        return ApprovalActionBuilder.buildApprovalRows(
                rawRecords, primaryTable, approvalType, approvalResp, currentUserId);
    }
}
