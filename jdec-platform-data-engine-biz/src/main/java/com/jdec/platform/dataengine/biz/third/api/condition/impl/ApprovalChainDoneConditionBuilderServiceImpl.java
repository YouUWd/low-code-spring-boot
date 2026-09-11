package com.jdec.platform.dataengine.biz.third.api.condition.impl;

import com.jdec.platform.approval.api.IApprovalEngine;
import com.jdec.platform.approval.api.req.ApprovalListReq;
import com.jdec.platform.dataengine.biz.dto.context.StatementContext;
import com.jdec.platform.dataengine.biz.enums.QueryType;
import com.jdec.platform.dataengine.biz.third.api.condition.SingleBizConditionBuilderService;
import com.jdec.platform.shared.context.AppContext;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/** 审批链 构建 已办查询 条件 */
@Service
@RequiredArgsConstructor
public class ApprovalChainDoneConditionBuilderServiceImpl
        implements SingleBizConditionBuilderService {

    private final Set<QueryType> TYPE_SET = Set.of(QueryType.LIST_APPROVAL_DONE);

    private final IApprovalEngine approvalEngine;

    @Override
    public Boolean isReferTo(QueryType queryType) {
        return TYPE_SET.contains(queryType);
    }

    @Override
    public Condition buildCondition(StatementContext context) {

        String primaryTableName =
                null; // context.getSysModuleCompleteResp().getModule().getPrimaryTable();

        Condition condition = DSL.field(DSL.name(primaryTableName, "deleted")).eq(0);

        List<Long> toDoIdList = getToDoIdList(context);

        if (!CollectionUtils.isEmpty(toDoIdList)) {
            condition.or(DSL.field(DSL.name(primaryTableName, "id")).in(toDoIdList));
        }

        return condition;
    }

    /**
     * 获取列表中待办的数据ID列表
     *
     * @param context
     * @return
     */
    private List<Long> getToDoIdList(StatementContext context) {
        ApprovalListReq approvalListReq = new ApprovalListReq();
        approvalListReq.setApprovalType(2);
        approvalListReq.setRoleId(AppContext.getRoleId());

        // TODO 数据来源于其他主体时需要拿到对应主体的详情模块ID
        approvalListReq.setModuleIdList(Arrays.asList(context.getModuleId()));

        approvalListReq.setCurrentUserId(AppContext.getUserId());
        approvalListReq.setApplicantId(AppContext.getUserId());

        //        approvalListReq.setSubjectIdList(
        //                CollectionUtils.isEmpty(
        //
        // context.getSysModuleCompleteResp().getModule().getSourceSubjects())
        //                        ? Arrays.asList(AppContext.getSubjectId())
        //                        : context
        //                                .getSysModuleCompleteResp()
        //                                .getModule()
        //                                .getSourceSubjects()
        //                                .stream()
        //                                .map(SysModuleResp.SubjectInfo::getId)
        //                                .collect(Collectors.toList()));

        List<Map<String, Object>> approvalListResult =
                approvalEngine.getBusinessList(approvalListReq);

        context.setApprovalList(approvalListResult);

        List<Long> toDoIdList = new ArrayList<>();

        // 构造recordId为key,审批数据为值的映射map，方便后面数据拼装
        for (Map<String, Object> item : approvalListResult) {
            if (item.containsKey("recordId")) {
                toDoIdList.add(Long.parseLong(String.valueOf(item.get("recordId"))));
            }
        }

        return toDoIdList;
    }
}
