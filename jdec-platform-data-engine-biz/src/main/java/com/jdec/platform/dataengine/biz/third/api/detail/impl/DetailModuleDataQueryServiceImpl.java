package com.jdec.platform.dataengine.biz.third.api.detail.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.jdec.platform.dataengine.biz.common.ResultSetConstantKey;
import com.jdec.platform.dataengine.biz.dsl.JooqContextFactory;
import com.jdec.platform.dataengine.biz.dto.context.DetailModuleQueryContext;
import com.jdec.platform.dataengine.biz.dto.context.StatementContext;
import com.jdec.platform.dataengine.biz.third.api.condition.ConditionBuilderFactory;
import com.jdec.platform.dataengine.biz.third.api.detail.DetailModuleDataQueryService;
import com.jdec.platform.dataengine.biz.third.api.select.SelectBuilderFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jooq.*;
import org.jooq.Record;
import org.springframework.stereotype.Service;

/** 查询 db 模块的业务数据 详情数据 包含 非审批链 和 审批链的 */
@Service
@RequiredArgsConstructor
public class DetailModuleDataQueryServiceImpl implements DetailModuleDataQueryService {

    private final JooqContextFactory jooqContextFactory;

    private final ConditionBuilderFactory conditionBuilderFactory;

    private final SelectBuilderFactory selectBuilderFactory;

    @Override
    public Map<Object, Object> queryDetailModuleData(DetailModuleQueryContext context) {

        StatementContext statementContext = StatementContext.builder().build();

        // 构建 SQL拼装 上下文对象
        BeanUtil.copyProperties(
                context, statementContext, CopyOptions.create().ignoreError().ignoreNullValue());
        statementContext.setMenuId(context.getMenuId());
        statementContext.setId(context.getId());
        DSLContext dslContext = jooqContextFactory.getContext();
        statementContext.setDsl(dslContext);

        // 构建SQL select 语句
        SelectJoinStep<Record> selectStep =
                selectBuilderFactory.buildSelectStatement(statementContext);
        // 构造SQL 查询条件
        List<Condition> conditions = conditionBuilderFactory.buildConditions(statementContext);
        selectStep.where(conditions);
        // 获取查询结果
        org.jooq.Record recordsResult = selectStep.fetchOne();

        Map<Object, Object> result = new HashMap<>();
        if (recordsResult != null) {
            result.put(ResultSetConstantKey.RESULT_SET_KEY_DATA, recordsResult.intoMap());
        }
        return result;
    }
}
