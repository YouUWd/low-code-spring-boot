package com.jdec.platform.common.biz.postprocess.action.approval;

import com.jdec.platform.approval.api.dto.ApprovalIdButton;
import com.jdec.platform.approval.api.resp.ApprovalBusinessNoResp;
import com.jdec.platform.approval.api.resp.ApprovalIdButtonListResp;
import com.jdec.platform.common.api.dto.response.ActionButtonDTO;
import com.jdec.platform.common.api.dto.response.ActionRowItem;
import com.jdec.platform.common.api.dto.response.UserSimpleResp;
import com.jdec.platform.common.biz.support.RowValueReader;
import com.jdec.platform.data.api.dto.common.DataRow;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.CollectionUtils;

/**
 * 审批链行级操作按钮构建器 (ApprovalActionBuilder)
 *
 * <p>核心职责：根据审批类型（1-待办、2-已办、3-全部）与草稿状态组装面向对象的强类型审批操作按钮：
 *
 * <ul>
 *   <li>待办：{buttonKey: "handle", buttonName: "办理"}
 *   <li>已办：{buttonKey: "view", buttonName: "查阅"}
 *   <li>全部：属于待办单号或本人草稿装配【办理】，其余装配【查阅】
 * </ul>
 */
public final class ApprovalActionBuilder {

    public static final String BUSINESS_NO_COLUMN = "business_no";

    public static final ActionButtonDTO HANDLE_BUTTON = ActionButtonDTO.of("handle", "办理");
    public static final ActionButtonDTO VIEW_BUTTON = ActionButtonDTO.of("view", "查阅");

    private static final List<ActionButtonDTO> HANDLE_BUTTONS = List.of(HANDLE_BUTTON);
    private static final List<ActionButtonDTO> VIEW_BUTTONS = List.of(VIEW_BUTTON);

    private ApprovalActionBuilder() {}

    /** 构建分页列表每行对应的审批操作按钮列表 (基于轻量级 ApprovalBusinessNoResp) */
    public static List<ActionRowItem> buildApprovalRows(
            List<DataRow> rawRecords,
            String primaryTable,
            Integer approvalType,
            List<ApprovalBusinessNoResp> approvalResp,
            Long currentUserId) {
        if (CollectionUtils.isEmpty(rawRecords)) {
            return Collections.emptyList();
        }
        List<ActionRowItem> approvalRows = new ArrayList<>(rawRecords.size());

        Set<String> todoNos = Collections.emptySet();
        if ((approvalType == null || approvalType == 3) && !CollectionUtils.isEmpty(approvalResp)) {
            todoNos =
                    approvalResp.stream()
                            .filter(Objects::nonNull)
                            .filter(item -> Integer.valueOf(1).equals(item.getApprovalType()))
                            .map(ApprovalBusinessNoResp::getBusinessNumber)
                            .filter(no -> no != null && !no.isBlank())
                            .map(String::trim)
                            .collect(Collectors.toSet());
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

            List<ActionButtonDTO> btns =
                    resolveRowButtons(
                            rawRow,
                            primaryTable,
                            recordId,
                            businessNo,
                            approvalType,
                            todoNos,
                            Collections.emptySet(),
                            currentUserId);

            approvalRows.add(ActionRowItem.ofRow(recordId, businessNo, btns));
        }
        return approvalRows;
    }

    /** 兼容历史基于 ApprovalIdButtonListResp 的重载方法 */
    public static List<ActionRowItem> buildApprovalRows(
            List<DataRow> rawRecords,
            String primaryTable,
            Integer approvalType,
            ApprovalIdButtonListResp approvalResp,
            Long currentUserId) {
        if (CollectionUtils.isEmpty(rawRecords)) {
            return Collections.emptyList();
        }
        List<ActionRowItem> approvalRows = new ArrayList<>(rawRecords.size());

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

            List<ActionButtonDTO> btns =
                    resolveRowButtons(
                            rawRow,
                            primaryTable,
                            recordId,
                            businessNo,
                            approvalType,
                            todoNos,
                            todoIds,
                            currentUserId);

            approvalRows.add(ActionRowItem.ofRow(recordId, businessNo, btns));
        }
        return approvalRows;
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

    private static List<ActionButtonDTO> resolveRowButtons(
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
        Integer bStatus = RowValueReader.parseStatusValue(rawStatus);

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
