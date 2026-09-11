package com.jdec.platform.dataengine.biz.third.api.condition.impl;

import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp;
import com.jdec.platform.dataengine.biz.dto.context.StatementContext;
import com.jdec.platform.dataengine.biz.enums.QueryType;
import com.jdec.platform.dataengine.biz.third.api.condition.SingleBizConditionBuilderService;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

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
        List<SysModuleCompleteResp.SubjectInfo> sourceSubject =
                context.getSysModuleCompleteResp().getModule().getSourceSubjects();

        if (CollectionUtils.isEmpty(sourceSubject)) {
            return DSL.field(DSL.name(context.getTableName(), "subject_id"))
                    .eq(context.getSubjectId());
        } else {
            Set<Long> subjectIds =
                    sourceSubject.stream()
                            .map(SysModuleCompleteResp.SubjectInfo::getId)
                            .collect(Collectors.toSet());
            return DSL.field(DSL.name(context.getTableName(), "subject_id")).in(subjectIds);
        }
    }
}
