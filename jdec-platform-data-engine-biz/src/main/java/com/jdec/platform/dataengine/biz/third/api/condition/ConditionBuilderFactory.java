package com.jdec.platform.dataengine.biz.third.api.condition;

import com.jdec.platform.dataengine.biz.dto.context.StatementContext;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConditionBuilderFactory {

    private final List<SingleBizConditionBuilderService> conditionBuilderServiceList;

    public List<Condition> buildConditions(StatementContext context) {
        List<Condition> conditionList = new ArrayList<>();
        for (SingleBizConditionBuilderService service : conditionBuilderServiceList) {
            if (service.isReferTo(context.getQueryType())) {
                Condition condition = service.buildCondition(context);
                if (condition != null) {
                    conditionList.add(condition);
                }
            }
        }
        return conditionList;
    }
}
