package com.jdec.platform.dataengine.biz.referencequery.impl;

import com.jdec.platform.dataengine.biz.context.VersionDataQueryContext;
import com.jdec.platform.dataengine.biz.dsl.JooqContextFactory;
import com.jdec.platform.dataengine.biz.referencequery.VersionDataQueryStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

// 内部引用数据查询实现类
@Service
@AllArgsConstructor
public class InternalRefVersionDataQueryStrategy implements VersionDataQueryStrategy {

    private final JooqContextFactory jooqContextFactory;

    @Override
    public List<Map<String, Object>> queryVersionDatas(
            VersionDataQueryContext versionDataQueryContext) {
        DSLContext dslContext = jooqContextFactory.getContext();

        String tableName = versionDataQueryContext.getTableName();

        List<Field<?>> fields = new ArrayList<>();
        fields.add(DSL.max(DSL.field(DSL.name(tableName, "version_no"))).as("max_version"));
        fields.add(DSL.field(DSL.name(tableName, "business_no")).as("temp_business_no"));

        SelectJoinStep<Record> groupByQuery = dslContext.select(fields).from(tableName);

        List<Condition> conditions = new ArrayList<>();

        // business_no相同的
        conditions.add(
                DSL.field(DSL.name(tableName, "business_no"))
                        .in(versionDataQueryContext.getBusinessNos()));

        // 状态为启用状态
        //        conditions.add(DSL.field(DSL.name(tableName, "business_status")).eq(99));

        groupByQuery.where(conditions);

        groupByQuery.groupBy(DSL.field(DSL.name(tableName, "business_no")));

        SelectJoinStep<Record> maxVersionQuery =
                dslContext
                        .select(DSL.asterisk())
                        .from(tableName)
                        .join(groupByQuery)
                        .on(
                                DSL.field(DSL.name(tableName, "business_no"))
                                        .eq(groupByQuery.field("temp_business_no", String.class)))
                        .and(
                                DSL.field(DSL.name(tableName, "version_no"))
                                        .eq(groupByQuery.field("max_version", Integer.class)));

        Result<Record> result = maxVersionQuery.fetch();

        return result.stream().map(Record::intoMap).collect(Collectors.toList());
    }

    @Override
    public Boolean isRefTo(String bizType) {
        return "INTERNAL_REF".equals(bizType);
    }
}
