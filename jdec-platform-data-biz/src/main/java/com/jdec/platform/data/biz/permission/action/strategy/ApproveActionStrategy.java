package com.jdec.platform.data.biz.permission.action.strategy;

import com.jdec.platform.data.biz.permission.action.RowActionContext;
import com.jdec.platform.data.biz.permission.action.RowActionStrategy;
import org.springframework.stereotype.Component;

/** 审批通过/驳回动作策略 (APPROVE) 业务规则: 单据审批中(1)，且当前登录人属于该审批链节点的审批人列表或审批角色 */
@Component
public class ApproveActionStrategy implements RowActionStrategy {

    @Override
    public String getActionCode() {
        return "APPROVE";
    }

    @Override
    public boolean isAllowed(RowActionContext ctx) {
        if (ctx.getApprovalStatus() == null || ctx.getApprovalStatus() != 1) {
            return false;
        }

        // 校验是否为当前节点的审批人
        boolean isApproverUser =
                ctx.getCurrentApproverUserIds() != null
                        && ctx.getCurrentApproverUserIds().contains(ctx.getCurrentUserId());

        // 校验是否具备当前节点的审批角色
        boolean isApproverRole =
                ctx.getCurrentApproverRoleIds() != null
                        && ctx.getCurrentUserRoleIds() != null
                        && ctx.getCurrentApproverRoleIds().stream()
                                .anyMatch(ctx.getCurrentUserRoleIds()::contains);

        return isApproverUser || isApproverRole;
    }
}
