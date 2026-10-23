package com.jdec.platform.common.biz.service.impl;

import com.jdec.platform.approval.api.dto.ApprovalIdButton;
import com.jdec.platform.approval.api.resp.ApprovalIdButtonListResp;
import com.jdec.platform.common.api.dto.response.ApprovalRowItem;
import com.jdec.platform.common.api.dto.response.StatusSimpleResp;
import com.jdec.platform.common.api.dto.response.UserSimpleResp;
import com.jdec.platform.data.api.dto.common.DataRow;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * 审批链单据条件与行级按钮解析器 (ApprovalConditionResolver)
 *
 * <p>核心职责：
 *
 * <ul>
 *   <li>构建针对审批链单据编号列表的过滤条件（{@code business_no IN (:businessNos)}）。
 *   <li>从审批引擎响应中提取去重的业务单据号（{@code business_no}）与记录主键（{@code recordId}）。
 *   <li>根据审批类型（1-待办、2-已办、3-全部）与草稿状态组装数据行级审批操作按钮（办理 / 查阅）。
 * </ul>
 */
@Component
public class ApprovalConditionResolver {

    public static final String BUSINESS_NO_COLUMN = "business_no";

    private static final List<Map<String, Object>> HANDLE_BUTTONS =
            List.of(Map.of("buttonKey", "handle", "buttonName", "办理"));
    private static final List<Map<String, Object>> VIEW_BUTTONS =
            List.of(Map.of("buttonKey", "view", "buttonName", "查阅"));

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
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        return list.stream()
                .filter(Objects::nonNull)
                .map(ApprovalIdButton::getBusinessNo)
                .filter(no -> no != null && !no.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    /** 提取审批单据列表中的去重物理记录 ID (recordId) 集合 */
    public static Set<Long> extractRecordIds(Collection<ApprovalIdButton> list) {
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptySet();
        }
        return list.stream()
                .filter(Objects::nonNull)
                .map(ApprovalIdButton::getRecordId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /** 构建分页列表每行对应的审批操作按钮列表 */
    public static List<ApprovalRowItem> buildApprovalRows(
            List<DataRow> rawRecords,
            String primaryTable,
            Integer approvalType,
            ApprovalIdButtonListResp approvalResp,
            Long currentUserId) {
        if (CollectionUtils.isEmpty(rawRecords)) {
            return Collections.emptyList();
        }
        List<ApprovalRowItem> approvalRows = new ArrayList<>(rawRecords.size());

        Set<String> todoNos = Collections.emptySet();
        Set<Long> todoIds = Collections.emptySet();
        if ((approvalType == null || approvalType == 3) && approvalResp != null) {
            todoNos = new HashSet<>(extractBusinessNos(approvalResp.getToDoList()));
            todoIds = extractRecordIds(approvalResp.getToDoList());
        }

        for (DataRow rawRow : rawRecords) {
            if (rawRow == null) {
                continue;
            }
            Long recordId = rawRow.getLong(primaryTable, "id");
            String businessNo = rawRow.getString(primaryTable, BUSINESS_NO_COLUMN);
            if (businessNo == null || businessNo.isBlank()) {
                businessNo = rawRow.getString(primaryTable, "businessNo");
            }
            if (recordId == null && (businessNo == null || businessNo.isBlank())) {
                continue;
            }

            List<Map<String, Object>> btns =
                    resolveRowButtons(
                            rawRow,
                            primaryTable,
                            recordId,
                            businessNo,
                            approvalType,
                            todoNos,
                            todoIds,
                            currentUserId);

            Map<String, Object> rowPayload = new LinkedHashMap<>();
            if (recordId != null) {
                rowPayload.put("id", recordId);
            }
            if (businessNo != null && !businessNo.isBlank()) {
                rowPayload.put("businessNo", businessNo);
            }
            rowPayload.put("btnList", btns);
            approvalRows.add(ApprovalRowItem.builder().row(rowPayload).build());
        }
        return approvalRows;
    }

    private static List<Map<String, Object>> resolveRowButtons(
            DataRow rawRow,
            String primaryTable,
            Long recordId,
            String businessNo,
            Integer approvalType,
            Set<String> todoNos,
            Set<Long> todoIds,
            Long currentUserId) {
        if (Integer.valueOf(1).equals(approvalType)) {
            return HANDLE_BUTTONS;
        }
        if (Integer.valueOf(2).equals(approvalType)) {
            return VIEW_BUTTONS;
        }
        boolean isTodoRecord =
                (businessNo != null && todoNos.contains(businessNo))
                        || (recordId != null && todoIds.contains(recordId));
        boolean isMyDraft = isCurrentUserDraft(rawRow, primaryTable, currentUserId);
        return (isTodoRecord || isMyDraft) ? HANDLE_BUTTONS : VIEW_BUTTONS;
    }

    private static boolean isCurrentUserDraft(
            DataRow rawRow, String primaryTable, Long currentUserId) {
        if (currentUserId == null) {
            return false;
        }
        Object rawStatus = rawRow.getValue(primaryTable, "business_status");
        Integer bStatus = null;
        if (rawStatus instanceof Number n) {
            bStatus = n.intValue();
        } else if (rawStatus instanceof StatusSimpleResp s) {
            bStatus = s.getStatusValue();
        }

        Object rawCreator = rawRow.getValue(primaryTable, "created_by");
        Long creator = null;
        if (rawCreator instanceof Number n) {
            creator = n.longValue();
        } else if (rawCreator instanceof UserSimpleResp u) {
            creator = u.getUserId();
        }

        return currentUserId.equals(creator) && Integer.valueOf(1).equals(bStatus);
    }
}
