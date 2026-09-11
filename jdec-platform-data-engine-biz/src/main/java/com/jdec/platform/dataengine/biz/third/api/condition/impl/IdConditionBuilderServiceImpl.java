package com.jdec.platform.dataengine.biz.third.api.condition.impl;

import com.jdec.platform.dataengine.biz.dto.context.StatementContext;
import com.jdec.platform.dataengine.biz.enums.QueryType;
import com.jdec.platform.dataengine.biz.third.api.condition.SingleBizConditionBuilderService;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

/** 构建ID 查询查询条件 */
@Service
@RequiredArgsConstructor
public class IdConditionBuilderServiceImpl implements SingleBizConditionBuilderService {

    private final Set<QueryType> TYPE_SET =
            Set.of(QueryType.DETAIL, QueryType.DETAIL_APPROVAL_CURRENT);

    @Override
    public Boolean isReferTo(QueryType queryType) {
        return TYPE_SET.contains(queryType);
    }

    @Override
    public Condition buildCondition(StatementContext context) {

        String primaryTableName =
                null; // context.getSysModuleCompleteResp().getModule().getPrimaryTable();

        return DSL.field(DSL.name(primaryTableName, "id")).eq(context.getId());
    }
}
