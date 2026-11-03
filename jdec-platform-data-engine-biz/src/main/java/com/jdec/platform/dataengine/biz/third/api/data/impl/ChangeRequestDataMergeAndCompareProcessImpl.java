package com.jdec.platform.dataengine.biz.third.api.data.impl;

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
import org.jooq.Result;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChangeRequestDataMergeAndCompareProcessImpl implements DetailDataProcessService {

    private final Set<QueryType> TYPE_SET = Set.of(QueryType.DETAIL_CHANGE_REQUEST);

    private final JooqContextFactory jooqContextFactory;

    private final ModuleTreeDiffService moduleTreeDiffService;

    @Override
    public void processAssembleData(
            List<NestedModuleDataDTO> records, DataIntegrationContext context) {
        List<NestedModuleDataDTO> preVersionData = context.getPreVersionData();
        String rootModuleTableName =
                context.getModuleTree().getModule(context.getModuleId()).getPrimaryTable();
        Map<String, Map<String, Map<String, List<Map<String, Object>>>>> currentVersionMap =
                buildCurrentModuleChangeIndex(context.getChangeRequestId(), rootModuleTableName);
        if (CollectionUtils.isEmpty(records)) {
            return;
        }

        // 如果有变更数据，则进行模块树数据合并
        if (!currentVersionMap.isEmpty()) {
            moduleTreeDiffService.moduleTreeDataMerge(
                    CollectionUtils.isEmpty(preVersionData) ? null : preVersionData.get(0),
                    currentVersionMap,
                    context.getModuleTree(),
                    context.getId(),
                    context.getProjectTableRelationsDTO());
        }
    }

    private Map<String, Map<String, Map<String, List<Map<String, Object>>>>>
            buildCurrentModuleChangeIndex(Long id, String rootModuleTableName) {
        DSLContext dslContext = jooqContextFactory.getContext();

        SelectJoinStep<org.jooq.Record> query =
                dslContext
                        .select(DSL.asterisk())
                        .from(DSL.name(rootModuleTableName + "_change_item"));
        // 构造SQL 查询条件
        List<Condition> conditions = new ArrayList<>();

        conditions.add(
                DSL.field(DSL.name(rootModuleTableName + "_change_item", "change_id")).eq(id));

        query.where(conditions);

        Result<org.jooq.Record> records = query.fetch();

        Map<String, Map<String, Map<String, List<Map<String, Object>>>>> returnMap =
                new HashMap<>();

        for (org.jooq.Record r : records) {
            Map<String, Object> rMap = r.intoMap();
            String tableName = String.valueOf(rMap.get("table_name"));
            String nodeId = String.valueOf(rMap.get("node_id"));
            String parentNodeId = String.valueOf(rMap.get("parent_node_id"));
            String action = String.valueOf(rMap.get("action"));

            // 按tableName、nodeId分组
            if (!"INSERT".equals(action)) {
                returnMap
                        .computeIfAbsent(tableName, k -> new HashMap<>())
                        .computeIfAbsent("update_data", k -> new HashMap<>())
                        .computeIfAbsent(nodeId, m -> new ArrayList<>())
                        .add(rMap);
            } else {

                // 按tableName、parentNodeId分组
                returnMap
                        .computeIfAbsent(tableName, k -> new HashMap<>())
                        .computeIfAbsent("insert_data", k -> new HashMap<>())
                        .computeIfAbsent(parentNodeId, m -> new ArrayList<>())
                        .add(rMap);
            }
        }

        /*
                ObjectMapper objectMapper = new ObjectMapper();
                List<Map<String, Object>> records = null;
                Map<String, Map<String, Map<String, List<Map<String, Object>>>>> returnMap =
                        new HashMap<>();
                try {
                    records =
                            objectMapper.readValue(
                                    "[\n"
                                            + "    {\n"
                                            + "        \"id\": 1,\n"
                                            + "        \"change_id\": 10001,\n"
                                            + "        \"table_name\": \"info\",\n"
                                            + "        \"node_id\": \"301\",\n"
                                            + "        \"parent_node_id\": null,\n"
                                            + "        \"action\": \"UPDATE\",\n"
                                            + "        \"change_content\": {\n"
                                            + "            \"company_name\": \"测试企业A新名称\",\n"
                                            + "            \"credit_code\": \"91420100MA4K2X8888\"\n"
                                            + "        },\n"
                                            + "        \"create_time\": \"2026-10-27 10:30:00\"\n"
                                            + "    },\n"
                                            + "    {\n"
                                            + "        \"id\": 2,\n"
                                            + "        \"change_id\": 10001,\n"
                                            + "        \"table_name\": \"member_contact\",\n"
                                            + "        \"node_id\": \"N20261027001\",\n"
                                            + "        \"parent_node_id\": \"2425\",\n"
                                            + "        \"action\": \"INSERT\",\n"
                                            + "        \"change_content\": {\n"
                                            + "            \"contact_name\": \"张三新加\",\n"
                                            + "            \"phone\": \"13800138000\"\n"
                                            + "        },\n"
                                            + "        \"create_time\": \"2026-10-27 10:30:00\"\n"
                                            + "    },\n"
                                            + "\t    {\n"
                                            + "        \"id\": 3,\n"
                                            + "        \"change_id\": 10001,\n"
                                            + "        \"table_name\": \"member_contact\",\n"
                                            + "        \"node_id\": 149,\n"
                                            + "        \"parent_node_id\": \"N20261027001\",\n"
                                            + "        \"action\": \"DELETE\",\n"
                                            + "        \"change_content\": {\n"
                                            + "            \"deleted\": 1,\n"
                                            + "            \"phone\": \"13800138000\"\n"
                                            + "        },\n"
                                            + "        \"create_time\": \"2026-10-27 10:30:00\"\n"
                                            + "    },\n"
                                            + "\t{\n"
                                            + "        \"id\": 4,\n"
                                            + "        \"change_id\": 10001,\n"
                                            + "        \"table_name\": \"member\",\n"
                                            + "        \"node_id\": \"888\",\n"
                                            + "        \"parent_node_id\": \"301\",\n"
                                            + "        \"action\": \"INSERT\",\n"
                                            + "        \"change_content\": {\n"
                                            + "\t\t\t\"business_no\": \"888\",\n"
                                            + "\t\t\t\"code\": \"W003\",\n"
                                            + "\t\t\t\"company_id\": 301,\n"
                                            + "\t\t\t\"created_by\": 296,\n"
                                            + "\t\t\t\"created_date\": \"2026-10-21 17:21:25\",\n"
                                            + "\t\t\t\"deleted\": 0,\n"
                                            + "\t\t\t\"dept_leader_flag\": 0,\n"
                                            + "\t\t\t\"id\": 888,\n"
                                            + "\t\t\t\"identity_type\": [],\n"
                                            + "\t\t\t\"member_name\": \"李四新加\",\n"
                                            + "\t\t\t\"mobile_phone_no\": \"13328765351\",\n"
                                            + "\t\t\t\"name\": \"李四新加\",\n"
                                            + "\t\t\t\"node_id\": \"abe9043e-ab6d-4df0-acf0-789f97606e57\",\n"
                                            + "\t\t\t\"parent_node_id\": \"fbb8c2f7-4a7f-49d9-890a-5c5441fe1209\",\n"
                                            + "\t\t\t\"status\": [],\n"
                                            + "\t\t\t\"subject_id\": 0,\n"
                                            + "\t\t\t\"type\": \"员工\",\n"
                                            + "\t\t\t\"updated_by\": 296,\n"
                                            + "\t\t\t\"updated_date\": \"2026-10-27 11:39:14\",\n"
                                            + "\t\t\t\"version_no\": 3\n"
                                            + "        },\n"
                                            + "        \"create_time\": \"2026-10-27 10:30:00\"\n"
                                            + "    },\n"
                                            + "\t{\n"
                                            + "        \"id\": 5,\n"
                                            + "        \"change_id\": 10001,\n"
                                            + "        \"table_name\": \"member_contact\",\n"
                                            + "        \"node_id\": \"N20261027002\",\n"
                                            + "        \"parent_node_id\": \"888\",\n"
                                            + "        \"action\": \"INSERT\",\n"
                                            + "        \"change_content\": {\n"
                                            + "            \"contact_name\": \"张三新加\",\n"
                                            + "            \"phone\": \"13800138000\"\n"
                                            + "        },\n"
                                            + "        \"create_time\": \"2026-10-27 10:30:00\"\n"
                                            + "    },\n"
                                            + "\t{\n"
                                            + "        \"id\": 6,\n"
                                            + "        \"change_id\": 10001,\n"
                                            + "        \"table_name\": \"company_contact\",\n"
                                            + "        \"node_id\": \"999\",\n"
                                            + "        \"parent_node_id\": \"301\",\n"
                                            + "        \"action\": \"INSERT\",\n"
                                            + "        \"change_content\": {\n"
                                            + "\t\t\t\"business_no\": \"CCB2026102609325800001\",\n"
                                            + "\t\t\t\"company_id\": 301,\n"
                                            + "\t\t\t\"contact_name\": {\n"
                                            + "\t\t\t\t\"id\": 2426,\n"
                                            + "\t\t\t\t\"name\": \"李四虚模块新增\",\n"
                                            + "\t\t\t\t\"avatar\": null,\n"
                                            + "\t\t\t\t\"jobName\": null,\n"
                                            + "\t\t\t\t\"jobNumber\": \"W003\"\n"
                                            + "\t\t\t},\n"
                                            + "\t\t\t\"contact_type\": [\n"
                                            + "\t\t\t\t{\n"
                                            + "\t\t\t\t\t\"uuid\": \"O4RkYhUYpd\",\n"
                                            + "\t\t\t\t\t\"color\": \"\",\n"
                                            + "\t\t\t\t\t\"paths\": [\n"
                                            + "\t\t\t\t\t\t{\n"
                                            + "\t\t\t\t\t\t\t\"uuid\": \"O4RkYhUYpd\",\n"
                                            + "\t\t\t\t\t\t\t\"color\": \"\",\n"
                                            + "\t\t\t\t\t\t\t\"bg_color\": \"\",\n"
                                            + "\t\t\t\t\t\t\t\"node_icon\": \"\",\n"
                                            + "\t\t\t\t\t\t\t\"border_color\": \"\",\n"
                                            + "\t\t\t\t\t\t\t\"word_describe\": \"\",\n"
                                            + "\t\t\t\t\t\t\t\"alias_value_no\": \"W260316102709\",\n"
                                            + "\t\t\t\t\t\t\t\"alias_value_title\": \"手机\"\n"
                                            + "\t\t\t\t\t\t}\n"
                                            + "\t\t\t\t\t],\n"
                                            + "\t\t\t\t\t\"bg_color\": \"\",\n"
                                            + "\t\t\t\t\t\"node_icon\": \"\",\n"
                                            + "\t\t\t\t\t\"border_color\": \"\",\n"
                                            + "\t\t\t\t\t\"word_describe\": \"\",\n"
                                            + "\t\t\t\t\t\"alias_value_no\": \"W260316102709\",\n"
                                            + "\t\t\t\t\t\"alias_value_title\": \"手机\"\n"
                                            + "\t\t\t\t}\n"
                                            + "\t\t\t],\n"
                                            + "\t\t\t\"contact_value\": \"13326846325\",\n"
                                            + "\t\t\t\"id\": 57,\n"
                                            + "\t\t\t\"sort_order\": 0\n"
                                            + "\t\t},\n"
                                            + "        \"create_time\": \"2026-10-27 10:30:00\"\n"
                                            + "    }\n"
                                            + "]",
                                    new TypeReference<List<Map<String, Object>>>() {});
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }

                for (Map<String, Object> rMap : records) {
                    String tableName = String.valueOf(rMap.get("table_name"));
                    String nodeId = String.valueOf(rMap.get("node_id"));
                    String parentNodeId = String.valueOf(rMap.get("parent_node_id"));
                    String action = String.valueOf(rMap.get("action"));

                    // 按tableName、nodeId分组

                    if (!"INSERT".equals(action)) {
                        returnMap
                                .computeIfAbsent(tableName, k -> new HashMap<>())
                                .computeIfAbsent("update_data", k -> new HashMap<>())
                                .computeIfAbsent(nodeId, m -> new ArrayList<>())
                                .add(rMap);
                    } else if ("INSERT".equals(action)) {

                        // 按tableName、parentNodeId分组
                        returnMap
                                .computeIfAbsent(tableName, k -> new HashMap<>())
                                .computeIfAbsent("insert_data", k -> new HashMap<>())
                                .computeIfAbsent(parentNodeId, m -> new ArrayList<>())
                                .add(rMap);
                    }
                }
        */

        return returnMap;
    }

    @Override
    public Boolean isReferTo(QueryType queryType) {
        return TYPE_SET.contains(queryType);
    }
}
