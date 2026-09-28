package com.jdec.platform.dataengine.biz.third.api.data.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.dataengine.api.dto.enums.QueryType;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.biz.dsl.JooqContextFactory;
import com.jdec.platform.dataengine.biz.dto.context.DataIntegrationContext;
import com.jdec.platform.dataengine.biz.third.api.data.DetailDataProcessService;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/** 详情查看单据版本对比信息获取 */
@Service
@RequiredArgsConstructor
@Order(-99)
@Slf4j
public class HistoryVersionCompareDataProcessServiceImpl implements DetailDataProcessService {

    private final JooqContextFactory jooqContextFactory;

    private final ModuleTreeDiffService moduleTreeDiffService;

    private final Set<QueryType> TYPE_SET = Set.of(QueryType.DETAIL_APPROVAL_HISTORY);

    @Override
    public Boolean isReferTo(QueryType queryType) {
        return TYPE_SET.contains(queryType);
    }

    @Override
    public void processAssembleData(
            List<NestedModuleDataDTO> records, DataIntegrationContext context) {
        List<NestedModuleDataDTO> preVersionData = getPreVersionData(context);
        if (CollectionUtils.isEmpty(records)) {
            return;
        }
        moduleTreeDiffService.diffModuleTree(
                CollectionUtils.isEmpty(preVersionData) ? null : preVersionData.get(0),
                records.get(0),
                new HashMap<>());

        log.debug("历史版本查询版本对比方法触发。");
    }

    private List<NestedModuleDataDTO> getPreVersionData(DataIntegrationContext context) {
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
                        .lessThan(context.getVersion()));

        query.where(conditions);

        query.orderBy(
                DSL.field(DSL.name("module_data_snapshot_content", "root_table_data_version"))
                        .desc());

        query.stream().limit(1);

        Record recordsResult = query.fetchAny();

        if (Objects.nonNull(recordsResult)) {
            String jsonData = (String) recordsResult.intoMap().get("content");
            ObjectMapper objectMapper = new ObjectMapper();
            NestedModuleDataDTO nestedModuleDataDTO = null;
            try {
                nestedModuleDataDTO = objectMapper.readValue(jsonData, NestedModuleDataDTO.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

            return Arrays.asList(nestedModuleDataDTO);
        } else {
            return List.of();
        }
    }
}
