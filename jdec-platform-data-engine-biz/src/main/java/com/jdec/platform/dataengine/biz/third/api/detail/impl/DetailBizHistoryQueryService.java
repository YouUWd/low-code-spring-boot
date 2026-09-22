package com.jdec.platform.dataengine.biz.third.api.detail.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.dataengine.api.dto.enums.QueryType;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.dataengine.biz.dsl.JooqContextFactory;
import com.jdec.platform.dataengine.biz.dto.context.DetailQueryContext;
import com.jdec.platform.dataengine.biz.third.api.detail.DetailBizReferQueryService;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DetailBizHistoryQueryService implements DetailBizReferQueryService {

    private final Set<QueryType> TYPE_SET = Set.of(QueryType.DETAIL_APPROVAL_HISTORY);

    private final JooqContextFactory jooqContextFactory;

    @Override
    public Boolean isReferTo(QueryType queryType) {
        return TYPE_SET.contains(queryType);
    }

    @Override
    public List<RecordDataDTO> queryDetailBizRefer(DetailQueryContext context) {

        DSLContext dslContext = jooqContextFactory.getContext();

        SelectJoinStep<org.jooq.Record> query =
                dslContext.select(DSL.asterisk()).from(DSL.name("module_data_snapshot_content"));
        // 构造SQL 查询条件
        List<Condition> conditions = new ArrayList<>();

        conditions.add(
                DSL.field(DSL.name("module_data_snapshot_content", "root_module_id"))
                        .eq(context.getModuleId()));
        conditions.add(
                DSL.field(DSL.name("module_data_snapshot_content", "root_table_data_id"))
                        .eq(context.getId()));

        conditions.add(
                DSL.field(DSL.name("module_data_snapshot_content", "root_table_data_version"))
                        .eq(context.getVersion()));

        query.where(conditions);

        Record recordsResult = query.fetchOne();

        if (Objects.nonNull(recordsResult)) {

            ObjectMapper objectMapper = new ObjectMapper();

            Map<String, Object> resultMap = recordsResult.intoMap();

            String jsonData = String.valueOf(resultMap.get("content"));

            try {
                NestedModuleDataDTO nestedModuleDataDTO =
                        objectMapper.readValue(
                                jsonData, new TypeReference<NestedModuleDataDTO>() {});

                if (Objects.nonNull(nestedModuleDataDTO)) {
                    return nestedModuleDataDTO.getRecord();
                }
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        return List.of();
    }
}
