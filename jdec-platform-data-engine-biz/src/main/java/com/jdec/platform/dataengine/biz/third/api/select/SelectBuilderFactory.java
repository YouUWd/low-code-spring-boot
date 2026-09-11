package com.jdec.platform.dataengine.biz.third.api.select;

import com.jdec.platform.dataengine.biz.dsl.JooqSqlBuilder;
import com.jdec.platform.dataengine.biz.dto.context.StatementContext;
import lombok.RequiredArgsConstructor;
import org.jooq.Record;
import org.jooq.SelectJoinStep;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SelectBuilderFactory {

    private final JooqSqlBuilder jooqSqlBuilder;

    public SelectJoinStep<Record> buildSelectStatement(StatementContext context) {
        return jooqSqlBuilder.buildSelectFrom(
                context.getDsl(), context.getModuleFilterInfo(), context.getTableName());
    }
}
