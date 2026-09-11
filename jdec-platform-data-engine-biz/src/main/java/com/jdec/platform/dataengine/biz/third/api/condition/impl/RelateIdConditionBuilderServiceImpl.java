package com.jdec.platform.dataengine.biz.third.api.condition.impl;

import com.jdec.platform.config.api.dto.common.ModuleTableDTO;
import com.jdec.platform.dataengine.biz.dto.context.StatementContext;
import com.jdec.platform.dataengine.biz.enums.QueryType;
import com.jdec.platform.dataengine.biz.third.api.condition.SingleBizConditionBuilderService;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RelateIdConditionBuilderServiceImpl implements SingleBizConditionBuilderService {

    private final Set<QueryType> TYPE_SET = Set.of(QueryType.DETAIL_SUBTABLE_QUERY);

    @Override
    public Condition buildCondition(StatementContext context) {
        ModuleTableDTO targetModuleTable =
                context.getSysModuleCompleteResp().getModuleTables().stream()
                        .filter(ele -> context.getTableName().equals(ele.getTableName()))
                        .findFirst()
                        .orElse(null);
        if (Objects.isNull(targetModuleTable)) {
            return DSL.trueCondition();
        } else {
            return DSL.field(DSL.name(context.getTableName(), targetModuleTable.getJoinLeftField()))
                    .in(context.getRelateIds());
        }
    }

    @Override
    public Boolean isReferTo(QueryType queryType) {
        return TYPE_SET.contains(queryType);
    }
}
