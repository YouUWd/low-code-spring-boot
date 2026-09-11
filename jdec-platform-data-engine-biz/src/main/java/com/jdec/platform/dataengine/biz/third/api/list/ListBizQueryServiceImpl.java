package com.jdec.platform.dataengine.biz.third.api.list;

import com.jdec.platform.dataengine.biz.dsl.JooqConditionBuilder;
import com.jdec.platform.dataengine.biz.dsl.JooqContextFactory;
import com.jdec.platform.dataengine.biz.dsl.JooqSqlBuilder;
import com.jdec.platform.dataengine.biz.dto.ModuleTableHeaderDTO;
import com.jdec.platform.dataengine.biz.dto.context.DataIntegrationContext;
import com.jdec.platform.dataengine.biz.dto.context.ListQueryContext;
import com.jdec.platform.dataengine.biz.dto.context.StatementContext;
import com.jdec.platform.dataengine.biz.enums.QueryType;
import com.jdec.platform.dataengine.biz.service.FieldTransformService;
import com.jdec.platform.dataengine.biz.service.MetadataCacheService;
import com.jdec.platform.dataengine.biz.service.PermissionFilterService;
import com.jdec.platform.dataengine.biz.third.api.ListBizQueryService;
import com.jdec.platform.dataengine.biz.third.api.condition.ConditionBuilderFactory;
import com.jdec.platform.dataengine.biz.third.api.data.ListDataProcessFactory;
import com.jdec.platform.dataengine.biz.third.api.order.OrderByBuilderFactory;
import com.jdec.platform.dataengine.biz.third.api.select.SelectBuilderFactory;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.*;
import org.jooq.Record;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListBizQueryServiceImpl implements ListBizQueryService {

    private final MetadataCacheService metadataCacheService;
    private final PermissionFilterService permissionFilterService;
    private final FieldTransformService fieldTransformService;
    private final JooqContextFactory jooqContextFactory;
    private final JooqSqlBuilder jooqSqlBuilder;
    private final JooqConditionBuilder jooqConditionBuilder;

    private final ConditionBuilderFactory conditionBuilderFactory;
    private final SelectBuilderFactory selectBuilderFactory;
    private final OrderByBuilderFactory orderByBuilderFactory;

    private final ListDataProcessFactory listDataProcessFactory;

    private final Set<QueryType> TYPE_SET =
            Set.of(
                    QueryType.LIST,
                    QueryType.LIST_APPROVAL_TODO,
                    QueryType.LIST_APPROVAL_DONE,
                    QueryType.LIST_APPROVAL_ALL);

    @Override
    public Map<Object, Object> queryListBiz(ListQueryContext context) {

        StatementContext statementContext = StatementContext.builder().build();

        BeanUtils.copyProperties(context, statementContext);

        statementContext.setMainDataId(context.getMenuId());

        Long moduleId = context.getModuleId();
        SysModuleResp completeResp = metadataCacheService.getModuleComplete(moduleId);
        String primaryTable = completeResp.getModule().getPrimaryTable();
        List<ModuleTableHeaderDTO> headers =
                permissionFilterService.filterReadableHeaders(completeResp);

        statementContext.setSysModuleCompleteResp(completeResp);
        statementContext.setQueryType(context.getQueryType());

        DSLContext dsl = jooqContextFactory.getContext();

        // 构造筛选条件
        List<Condition> conditions = conditionBuilderFactory.buildConditions(statementContext);

        statementContext.setDsl(dsl);

        // 构造select字段
        SelectJoinStep<Record> selectStep =
                selectBuilderFactory.buildSelectStatement(statementContext);

        selectStep.where(conditions);

        // 1. 第一阶段: 查询主表精确总数 COUNT
        int total = dsl.fetchCount(selectStep);

        // 2. 分页数据查询
        int pageIndex = context.getPageIndex() > 0 ? context.getPageIndex() : 1;
        int pageSize = context.getPageSize() > 0 ? context.getPageSize() : 20;
        int offset = (pageIndex - 1) * pageSize;

        // 构造排序字段
        selectStep.orderBy(orderByBuilderFactory.buildOrderBy(statementContext));

        Result<org.jooq.Record> recordsResult = selectStep.limit(pageSize).offset(offset).fetch();

        Map<Object, Object> resultMap = new HashMap<>();

        List<Map<String, Object>> dataList =
                recordsResult.stream().map(Record::intoMap).collect(Collectors.toList());

        DataIntegrationContext dataIntegrationContext = DataIntegrationContext.builder().build();

        listDataProcessFactory.processData(dataList, resultMap, dataIntegrationContext);

        resultMap.put("records", dataList);
        resultMap.put("total", total);

        return resultMap;

        /**
         * // 1. 第一阶段: 查询主表精确总数 COUNT int total = dsl.fetchCount(jooqSqlBuilder.buildSelectFrom(dsl,
         * completeResp).where(conditions));
         *
         * <p>// 2. 分页数据查询 int pageIndex = context.getPageIndex() > 0 ? context.getPageIndex() : 1;
         * int pageSize = context.getPageSize() > 0 ? context.getPageSize() : 20; int offset =
         * (pageIndex - 1) * pageSize;
         *
         * <p>// var selectStep = jooqSqlBuilder.buildSelectFrom(dsl,
         * completeResp).where(condition); if (context.getOrderBy() != null &&
         * !context.getOrderBy().isBlank()) { if
         * ("ASC".equalsIgnoreCase(context.getOrderDirection())) {
         * selectStep.orderBy(DSL.field(DSL.name(req.getOrderBy())).asc()); } else {
         * selectStep.orderBy(DSL.field(DSL.name(context.getOrderBy())).desc()); } }
         * Result<org.jooq.Record> recordsResult =
         * selectStep.limit(pageSize).offset(offset).fetch();
         *
         * <p>// 3. 提取主表记录并进行内存转换 List<Map<String, Object>> primaryRows = new ArrayList<>();
         * List<Long> primaryIds = new ArrayList<>(); for (Record r : recordsResult) { Map<String,
         * Object> map = new HashMap<>(r.intoMap()); primaryRows.add(map); Object idVal =
         * map.get("id"); if (idVal instanceof Number num) { primaryIds.add(num.longValue()); } }
         * fieldTransformService.transformRecords(primaryRows, completeResp);
         *
         * <p>// 4. 第二阶段: 批量下钻查询 1:N 关联从表 Map<String, Map<Long, List<Map<String, Object>>>>
         * multiTableDataMap = fetchOneToManyTables(dsl, completeResp, primaryIds);
         *
         * <p>// 5. 组装行记录列表 (Table-First 纯粹 Map: record[tableName] = record/records)
         * List<Map<String, Object>> structuredRecords = new ArrayList<>(); for (Map<String, Object>
         * row : primaryRows) { Map<String, Object> rowTables = new LinkedHashMap<>(); Long rowId =
         * row.get("id") instanceof Number num ? num.longValue() : null;
         *
         * <p>// 主表数据 rowTables.put(primaryTable, row);
         *
         * <p>// 关联表数据 (1:1 / N:1 为单对象, 1:N 为对象列表) if (completeResp.getModuleTables() != null) { for
         * (ModuleTableDTO tableDto : completeResp.getModuleTables()) { String tName =
         * tableDto.getTableName(); String rType = tableDto.getRelationType(); if
         * ("1:N".equalsIgnoreCase(rType) || "ONE_TO_MANY".equalsIgnoreCase(rType)) {
         * List<Map<String, Object>> subRows = multiTableDataMap .getOrDefault(tName,
         * Collections.emptyMap()) .getOrDefault(rowId, Collections.emptyList());
         * rowTables.put(tName, subRows); } else { // 1:1 或 N:1 rowTables.put(tName, row); } } }
         * structuredRecords.add(rowTables); }
         *
         * <p>return Map.of();
         */
    }

    @Override
    public Boolean isReferTo(QueryType queryType) {
        return TYPE_SET.contains(queryType);
    }
}
