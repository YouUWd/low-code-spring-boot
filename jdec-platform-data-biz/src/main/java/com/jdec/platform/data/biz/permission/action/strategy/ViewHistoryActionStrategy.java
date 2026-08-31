package com.jdec.platform.data.biz.permission.action.strategy;

import com.jdec.platform.data.biz.permission.action.RowActionContext;
import com.jdec.platform.data.biz.permission.action.RowActionStrategy;
import org.springframework.stereotype.Component;

/** 快照版本与差异比对策略 (VIEW_HISTORY) 业务规则: 具备查阅权限即可查看历史版本快照 */
@Component
public class ViewHistoryActionStrategy implements RowActionStrategy {

    @Override
    public String getActionCode() {
        return "VIEW_HISTORY";
    }

    @Override
    public boolean isAllowed(RowActionContext ctx) {
        return true;
    }
}
