package com.jdec.platform.dataengine.biz.third.api.condition;

import com.jdec.platform.dataengine.biz.dto.context.StatementContext;
import com.jdec.platform.dataengine.biz.third.api.ReferService;
import org.jooq.Condition;

public interface SingleBizConditionBuilderService extends ReferService {

    public Condition buildCondition(StatementContext context);
}
