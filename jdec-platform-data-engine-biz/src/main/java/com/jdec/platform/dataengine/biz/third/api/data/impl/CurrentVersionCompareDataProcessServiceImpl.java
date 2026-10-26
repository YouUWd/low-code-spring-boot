package com.jdec.platform.dataengine.biz.third.api.data.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.dataengine.api.dto.enums.QueryType;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.biz.dsl.JooqContextFactory;
import com.jdec.platform.dataengine.biz.dto.VersionSnapShot;
import com.jdec.platform.dataengine.biz.dto.context.DataIntegrationContext;
import com.jdec.platform.dataengine.biz.third.api.data.DetailDataProcessService;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class CurrentVersionCompareDataProcessServiceImpl implements DetailDataProcessService {

    private final JooqContextFactory jooqContextFactory;

    private final ModuleTreeDiffService moduleTreeDiffService;

    private final Set<QueryType> TYPE_SET = Set.of(QueryType.DETAIL_APPROVAL_CURRENT);

    @Override
    public void processAssembleData(
            List<NestedModuleDataDTO> records, DataIntegrationContext context) {
        List<NestedModuleDataDTO> preVersionData = context.getPreVersionData();
        Map<Long, Map<Long, Map<String, VersionSnapShot>>> currentVersionMap =
                buildCurrentModuleChangeIndex(context.getModuleId(), context.getId());
        if (CollectionUtils.isEmpty(records)) {
            return;
        }

        moduleTreeDiffService.diffModuleTree(
                CollectionUtils.isEmpty(preVersionData) ? null : preVersionData.get(0),
                records.get(0),
                currentVersionMap);
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

        query.where(conditions);

        query.orderBy(
                DSL.field(DSL.name("module_data_snapshot_content", "root_table_data_version"))
                        .desc());

        query.limit(1);

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

    @Override
    public Boolean isReferTo(QueryType queryType) {
        return TYPE_SET.contains(queryType);
    }

    private Map<Long, Map<Long, Map<String, VersionSnapShot>>> buildCurrentModuleChangeIndex(
            Long moduleId, Long id) {
        DSLContext dslContext = jooqContextFactory.getContext();

        SelectJoinStep<org.jooq.Record> query =
                dslContext
                        .select(DSL.asterisk())
                        .from(DSL.name("module_data_field_change_content"));
        // 构造SQL 查询条件
        List<Condition> conditions = new ArrayList<>();

        conditions.add(
                DSL.field(DSL.name("module_data_field_change_content", "root_module_id"))
                        .eq(moduleId));
        conditions.add(
                DSL.field(DSL.name("module_data_field_change_content", "root_table_data_id"))
                        .eq(id));

        query.where(conditions);

        query.orderBy(
                DSL.field(DSL.name("module_data_field_change_content", "created_date")).asc());

        Result<Record> records = query.fetch();

        Map<Long, Map<Long, Map<String, VersionSnapShot>>> returnMap = new HashMap<>();

        for (Record r : records) {
            Map<String, Object> rMap = r.intoMap();
            Long itemModuleId = Long.parseLong(String.valueOf(rMap.get("module_id")));
            Long dataId = Long.parseLong(String.valueOf(rMap.get("data_id")));
            returnMap
                    .computeIfAbsent(itemModuleId, k -> new HashMap<>())
                    .computeIfAbsent(dataId, m -> new HashMap<>())
                    .put(
                            String.valueOf(rMap.get("table_name"))
                                    + "."
                                    + String.valueOf(rMap.get("field_id")),
                            VersionSnapShot.builder()
                                    .firstSnapshot(
                                            rMap.get("first_snapshot") == null
                                                    ? null
                                                    : String.valueOf(rMap.get("first_snapshot")))
                                    .secondSnapshot(
                                            rMap.get("second_snapshot") == null
                                                    ? null
                                                    : String.valueOf(rMap.get("second_snapshot")))
                                    .build());
        }

        return returnMap;
    }
}
