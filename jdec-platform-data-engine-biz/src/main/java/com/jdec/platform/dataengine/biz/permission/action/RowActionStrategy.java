package com.jdec.platform.dataengine.biz.permission.action;

/** 行级动作决策策略接口 (策略模式 Strategy Pattern) (每个操作按钮对应一个独立的实现类，后续开发人员可按需扩展更多操作) */
public interface RowActionStrategy {

    /** 获取动作编码 (如 VIEW, EDIT, SUBMIT_APPROVAL, APPROVE, RECALL, VIEW_HISTORY) */
    String getActionCode();

    /**
     * 结合单据态与审批链上下文判定当前操作是否允许展示与执行
     *
     * @param ctx 动作决策上下文
     * @return true-允许, false-不允许
     */
    boolean isAllowed(RowActionContext ctx);
}
