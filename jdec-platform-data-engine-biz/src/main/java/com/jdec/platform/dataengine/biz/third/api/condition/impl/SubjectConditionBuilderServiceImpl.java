package com.jdec.platform.dataengine.biz.third.api.condition.impl;

import com.jdec.platform.dataengine.biz.dto.context.StatementContext;
import com.jdec.platform.dataengine.biz.enums.QueryType;
import com.jdec.platform.dataengine.biz.third.api.condition.SingleBizConditionBuilderService;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.springframework.stereotype.Service;

/** 构建主题 查询条件 */
@Service
@RequiredArgsConstructor
public class SubjectConditionBuilderServiceImpl implements SingleBizConditionBuilderService {
    @Override
    public Boolean isReferTo(QueryType queryType) {
        return Boolean.TRUE;
    }

    @Override
    public Condition buildCondition(StatementContext context) {

        // 数据来源于其他主体
        //        List<SysModuleResp.SubjectInfo> sourceSubject =
        //                context.getSysModuleCompleteResp().getModule().getSourceSubjects();
        //
        //        if (CollectionUtils.isEmpty(sourceSubject)) {
        //            return DSL.field(DSL.name(context.getTableName(), "subject_id"))
        //                    .eq(context.getSubjectId());
        //        } else {
        //            Set<Long> subjectIds =
        //                    sourceSubject.stream()
        //                            .map(SysModuleResp.SubjectInfo::getId)
        //                            .collect(Collectors.toSet());
        //            return DSL.field(DSL.name(context.getTableName(),
        // "subject_id")).in(subjectIds);
        //        }
        return null;
    }
}
