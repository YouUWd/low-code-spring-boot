package com.jdec.platform.dataengine.biz.permission.action;

import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 行级动作决策引擎 (责任链 + 策略聚合) 后续开发人员可对接 ApprovalChain 审批流微服务接口获取实时审批链状态 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RowActionEngine {

    private final List<RowActionStrategy> strategies;

    /**
     * 批量为列表查询结果注入 _actions
     *
     * @param moduleId 模块 ID
     * @param records 行记录列表
     * @param currentUserId 当前登录用户 ID
     * @param userRoleIds 当前登录用户角色列表
     */
    public void populateRowActions(
            Long moduleId,
            List<Map<String, Object>> records,
            Long currentUserId,
            List<Long> userRoleIds) {

        if (records == null || records.isEmpty()) {
            return;
        }

        // TODO [待开发人员接入]: 调用审批流微服务批量获取当前页单据的审批链上下文 (避免 N+1 慢查询)
        // Map<Long, ApprovalChainInfo> approvalInfoMap =
        // approvalChainApi.getBatchApprovalInfo(moduleId, dataIds);

        for (Map<String, Object> record : records) {
            Long dataId = extractLong(record, "id");
            Integer approvalStatus = extractInteger(record, "approval_status");
            Long createdBy = extractLong(record, "created_by");

            RowActionContext ctx =
                    RowActionContext.builder()
                            .moduleId(moduleId)
                            .dataId(dataId)
                            .currentUserId(currentUserId)
                            .currentUserRoleIds(userRoleIds)
                            .approvalStatus(approvalStatus != null ? approvalStatus : 0)
                            .createdBy(createdBy)
                            .rawRecord(record)
                            // 预留审批链上下文字段
                            .currentApproverUserIds(Collections.emptyList())
                            .currentApproverRoleIds(Collections.emptyList())
                            .build();

            List<String> allowedActions =
                    strategies.stream()
                            .filter(s -> s.isAllowed(ctx))
                            .map(RowActionStrategy::getActionCode)
                            .toList();

            record.put("_actions", allowedActions);
        }
    }

    private Long extractLong(Map<String, Object> record, String key) {
        Object val = record.get(key);
        if (val instanceof Number num) {
            return num.longValue();
        }
        return null;
    }

    private Integer extractInteger(Map<String, Object> record, String key) {
        Object val = record.get(key);
        if (val instanceof Number num) {
            return num.intValue();
        }
        return null;
    }
}
