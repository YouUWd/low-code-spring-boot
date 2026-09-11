package com.jdec.platform.dataengine.biz.third.api.detail.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleTableDTO;
import com.jdec.platform.config.api.dto.response.SysModuleResp;
import com.jdec.platform.dataengine.biz.dsl.JooqContextFactory;
import com.jdec.platform.dataengine.biz.dsl.JooqSqlBuilder;
import com.jdec.platform.dataengine.biz.dto.context.DetailModuleQueryContext;
import com.jdec.platform.dataengine.biz.dto.context.StatementContext;
import com.jdec.platform.dataengine.biz.service.MetadataCacheService;
import com.jdec.platform.dataengine.biz.third.api.condition.ConditionBuilderFactory;
import com.jdec.platform.dataengine.biz.third.api.detail.DetailModuleDataQueryService;
import com.jdec.platform.dataengine.biz.third.api.select.SelectBuilderFactory;
import java.util.*;
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

    private final JooqSqlBuilder jooqSqlBuilder;

    private final MetadataCacheService metadataCacheService;

    @Override
    public Map<Object, Object> queryDetailModuleData(DetailModuleQueryContext context) {
        DSLContext dsl = jooqContextFactory.getContext();

        SysModuleResp sysModuleCompleteResp = new SysModuleResp();

        List<ModuleFieldDTO> simpleFieldDTOS = new ArrayList<>();
        simpleFieldDTOS.add(ModuleFieldDTO.builder().tableName("student").columnName("id").build());
        simpleFieldDTOS.add(
                ModuleFieldDTO.builder().tableName("student").columnName("student_no").build());
        simpleFieldDTOS.add(ModuleFieldDTO.builder().tableName("score").columnName("id").build());
        simpleFieldDTOS.add(
                ModuleFieldDTO.builder().tableName("score").columnName("student_id").build());
        simpleFieldDTOS.add(
                ModuleFieldDTO.builder().tableName("score").columnName("score").build());
        simpleFieldDTOS.add(
                ModuleFieldDTO.builder().tableName("score").columnName("semester").build());

        sysModuleCompleteResp.setFields(simpleFieldDTOS);

        SysModuleResp.ModuleInfo moduleInfo = new SysModuleResp.ModuleInfo();
        // TODO 主表数据要从模块配置获取
        moduleInfo.setPrimaryTable("student");
        String primaryTable = moduleInfo.getPrimaryTable();

        sysModuleCompleteResp.setModule(moduleInfo);

        List<ModuleTableDTO> moduleTables = new ArrayList<>();
        moduleTables.add(
                ModuleTableDTO.builder()
                        .tableName("score")
                        .joinLeftField("student_id")
                        .joinRightField("id")
                        .relationType("1:N")
                        .build());

        // sysModuleCompleteResp.setModuleTables(moduleTables);

        StatementContext statementContext = StatementContext.builder().build();

        // 构建 SQL拼装 上下文对象
        BeanUtil.copyProperties(
                context, statementContext, CopyOptions.create().ignoreError().ignoreNullValue());
        statementContext.setMenuId(context.getMenuId());
        statementContext.setId(context.getId());
        DSLContext dslContext = jooqContextFactory.getContext();
        statementContext.setDsl(dslContext);
        statementContext.setQueryType(context.getQueryType());
        // statementContext.setSysModuleCompleteResp(sysModuleCompleteResp);
        statementContext.setTableName(sysModuleCompleteResp.getModule().getPrimaryTable());

        // 构建SQL select 语句
        SelectJoinStep<Record> selectStep =
                selectBuilderFactory.buildSelectStatement(statementContext);
        // 构造SQL 查询条件
        List<Condition> conditions = conditionBuilderFactory.buildConditions(statementContext);
        selectStep.where(conditions);

        // 获取查询结果
        Result<org.jooq.Record> recordsResult = selectStep.fetch();

        Map<Object, Object> tableData = new HashMap<>();

        // 3. 提取主表记录 ID 集合 (用于 1:N 从表批量查询)
        List<Long> relateIds = new ArrayList<>();
        List<Map<String, Object>> mainRow = new ArrayList<>();
        for (Record r : recordsResult) {
            for (org.jooq.Field<?> field : r.fields()) {
                String tName = primaryTable;
                org.jooq.Name qualifiedName = field.getQualifiedName();
                org.jooq.Name[] parts = qualifiedName.parts();
                if (parts.length > 1) {
                    tName = parts[parts.length - 2].first();
                }
                String colName = field.getName();
                Object val = r.get(field);

                if (primaryTable.equalsIgnoreCase(tName) && "id".equalsIgnoreCase(colName)) {
                    if (val instanceof Number num) {
                        relateIds.add(num.longValue());
                    }
                    break;
                }
            }
            mainRow.add(r.intoMap());
        }

        // 放入主表数据
        tableData.put(primaryTable, mainRow);

        Map<String, Map<Long, List<Map<String, Object>>>> multiTableDataMap =
                fetchOneToManyTables(context, dsl, null, relateIds);

        //        if (statementContext.getSysModuleCompleteResp().getModuleTables() != null) {
        //            for (ModuleTableDTO tableDto :
        //                    statementContext.getSysModuleCompleteResp().getModuleTables()) {
        //                String tName = tableDto.getTableName();
        //                String rType = tableDto.getRelationType();
        //                List<Map<String, Object>> subRows =
        //                        multiTableDataMap
        //                                .getOrDefault(tName, Collections.emptyMap())
        //                                .getOrDefault(statementContext.getId(),
        // Collections.emptyList());
        //                tableData.put(tName, subRows);
        //            }
        //        }

        return tableData;
    }

    /** 批量抓取当前页主表 ID 集合关联的 1:N 从表记录 (从表同样应用主体作用域与行级数据权限) */
    private Map<String, Map<Long, List<Map<String, Object>>>> fetchOneToManyTables(
            DetailModuleQueryContext context,
            DSLContext dsl,
            SysModuleResp completeResp,
            List<Long> primaryIds) {

        Map<String, Map<Long, List<Map<String, Object>>>> result = new HashMap<>();
        //        if (primaryIds == null || primaryIds.isEmpty() || completeResp.getModuleTables()
        // == null) {
        //            return result;
        //        }

        //        for (ModuleTableDTO tableDto : completeResp.getModuleTables()) {
        //            String rType = tableDto.getRelationType();
        //            if ("1:N".equalsIgnoreCase(rType) || "ONE_TO_MANY".equalsIgnoreCase(rType)) {
        //                String tableName = tableDto.getTableName();
        //                String leftJoinField = tableDto.getJoinLeftField(); // 外键字段, 如 student_id
        //
        //                StatementContext statementContext = StatementContext.builder().build();
        //
        //                // 构建 SQL拼装 上下文对象
        //                BeanUtil.copyProperties(
        //                        context,
        //                        statementContext,
        //                        CopyOptions.create().ignoreError().ignoreNullValue());
        //                statementContext.setMenuId(context.getMenuId());
        //                statementContext.setId(context.getId());
        //                DSLContext dslContext = jooqContextFactory.getContext();
        //                statementContext.setDsl(dslContext);
        //                statementContext.setQueryType(QueryType.DETAIL_SUBTABLE_QUERY);
        //                statementContext.setSysModuleCompleteResp(completeResp);
        //                statementContext.setTableName(tableName);
        //                statementContext.setRelateIds(primaryIds);
        //
        //                // 构建SQL select 语句
        //                SelectJoinStep<Record> selectStep =
        //                        selectBuilderFactory.buildSelectStatement(statementContext);
        //                // 构造SQL 查询条件
        //                List<Condition> conditions =
        //                        conditionBuilderFactory.buildConditions(statementContext);
        //                selectStep.where(conditions);
        //
        //                Result<Record> subRecords = selectStep.fetch();
        //
        //                Map<Long, List<Map<String, Object>>> grouped = new HashMap<>();
        //                for (Record r : subRecords) {
        //                    Map<String, Object> rMap = r.intoMap();
        //                    Object fkVal = rMap.get(leftJoinField);
        //                    if (fkVal instanceof Number fkNum) {
        //                        grouped.computeIfAbsent(fkNum.longValue(), k -> new ArrayList<>())
        //                                .add(rMap);
        //                    }
        //                }
        //                result.put(tableName, grouped);
        //            }
        //        }
        return result;
    }
}
