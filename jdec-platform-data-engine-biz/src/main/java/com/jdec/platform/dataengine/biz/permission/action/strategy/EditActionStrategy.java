package com.jdec.platform.dataengine.biz.permission.action.strategy;

import com.jdec.platform.dataengine.biz.permission.action.RowActionContext;
import com.jdec.platform.dataengine.biz.permission.action.RowActionStrategy;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** 编辑动作策略 (EDIT) 业务规则: 审批通过归档后(99)或审批中(1)严禁编辑; 草稿(0)或驳回(-99)时仅提单人可编辑 */
@Component
public class EditActionStrategy implements RowActionStrategy {

    @Override
    public String getActionCode() {
        return "EDIT";
    }

    @Override
    public boolean isAllowed(RowActionContext ctx) {
        if (ctx.getApprovalStatus() == null) {
            return true;
        }
        // 审批通过归档后(99)或审批中(1)，严禁编辑
        if (ctx.getApprovalStatus() == 99 || ctx.getApprovalStatus() == 1) {
            return false;
        }
        // 草稿(0)或驳回(-99)态: 仅提单人本人可编辑
        return Objects.equals(ctx.getCurrentUserId(), ctx.getCreatedBy());
    }
}
