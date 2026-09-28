package com.jdec.platform.dataengine.biz.permission.action.strategy;

import com.jdec.platform.dataengine.biz.permission.action.RowActionContext;
import com.jdec.platform.dataengine.biz.permission.action.RowActionStrategy;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** 提交审批动作策略 (SUBMIT_APPROVAL) 业务规则: 草稿(0)或驳回(-99)态，且仅提单人本人可提交审批 */
@Component
public class SubmitApprovalActionStrategy implements RowActionStrategy {

    @Override
    public String getActionCode() {
        return "SUBMIT_APPROVAL";
    }

    @Override
    public boolean isAllowed(RowActionContext ctx) {
        if (ctx.getApprovalStatus() == null) {
            return false;
        }
        return (ctx.getApprovalStatus() == 0 || ctx.getApprovalStatus() == -99)
                && Objects.equals(ctx.getCurrentUserId(), ctx.getCreatedBy());
    }
}
