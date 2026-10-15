package com.jdec.platform.dataengine.biz.third.api.condition.impl;

import com.jdec.platform.config.api.dto.common.ProjectTableRelationsDTO;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.dataengine.api.dto.enums.QueryType;
import com.jdec.platform.dataengine.biz.dto.context.StatementContext;
import com.jdec.platform.dataengine.biz.third.api.condition.SingleBizConditionBuilderService;
import com.jdec.platform.shared.context.AppContext;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
@RequiredArgsConstructor
public class SubModuleRelateIdConditionBuilderServiceImpl
        implements SingleBizConditionBuilderService {

    private final ConfigApi configApi;

    private final Set<QueryType> TYPE_SET = Set.of(QueryType.DETAIL_SUBMODULE_QUERY);

    @Override
    public Condition buildCondition(StatementContext context) {
        ProjectTableRelationsDTO projectTableRelationsDTO =
                configApi.getProjectTableRelations(AppContext.getProjectNo());

        List<TableRelationDTO> relationList =
                projectTableRelationsDTO.getReverseRelations().get(context.getTableName());

        if (CollectionUtils.isEmpty(relationList)
                || CollectionUtils.isEmpty(context.getRelateIds())) {
            return DSL.falseCondition();
        } else {
            return DSL.field(DSL.name(context.getTableName(), relationList.get(0).getJoinField()))
                    .in(context.getRelateIds())
                    .and(DSL.field(DSL.name(context.getTableName(), "deleted")).eq(0));
        }
    }

    @Override
    public Boolean isReferTo(QueryType queryType) {
        return TYPE_SET.contains(queryType);
    }
}
