package com.jdec.platform.dataengine.biz.third.api.condition.impl;

import com.jdec.platform.dataengine.api.dto.enums.QueryType;
import com.jdec.platform.dataengine.biz.dto.context.StatementContext;
import com.jdec.platform.dataengine.biz.third.api.condition.SingleBizConditionBuilderService;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

/** 详情中的列表查寻 构建主模块数据ID查询条件，根据主模块的数据ID查询子模块的列表 */
@Service
@RequiredArgsConstructor
public class MainModuleIdConditionBuilderServiceImpl implements SingleBizConditionBuilderService {

    private final Set<QueryType> TYPE_SET = Set.of(QueryType.LIST);

    @Override
    public Boolean isReferTo(QueryType queryType) {
        return TYPE_SET.contains(queryType);
    }

    @Override
    public Condition buildCondition(StatementContext context) {
        // 详情中的列表查看
        if (Objects.nonNull(context.getRelateSearchId())) {
            String primaryTableName = null;
            // context.getSysModuleCompleteResp().getModule().getPrimaryTable();
            String fieldName = null;
            // context.getSysModuleCompleteResp().getModule().getRelateSearchField();
            return DSL.field(DSL.name(primaryTableName, fieldName)).eq(context.getRelateSearchId());
        } else {
            return DSL.trueCondition();
        }
    }
}
