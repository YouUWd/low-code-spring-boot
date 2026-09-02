package com.jdec.platform.data.biz.service;

import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleTableHeaderDTO;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import com.jdec.platform.config.api.dto.response.SysModuleMetaResp;
import com.jdec.platform.data.api.dto.model.EngineModuleMeta;
import com.jdec.platform.data.api.dto.request.BatchDynamicQueryReq;
import com.jdec.platform.data.api.dto.request.DynamicDetailReq;
import com.jdec.platform.data.api.dto.request.DynamicQueryReq;
import com.jdec.platform.data.api.dto.response.BatchEngineDataResult;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.api.dto.response.EngineDataResult;
import com.jdec.platform.data.biz.dsl.JooqConditionBuilder;
import com.jdec.platform.data.biz.dsl.JooqContextFactory;
import com.jdec.platform.data.biz.dsl.JooqSqlBuilder;
import com.jdec.platform.shared.context.AppContext;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

/** 动态查询服务 (基于 jOOQ 两阶段分页与 Table-First 纯数据读模型，由 viewMode 驱动结构化视图与元数据透传) */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicQueryService {

    private final MetadataCacheService metadataCacheService;
    private final PermissionFilterService permissionFilterService;
    private final JooqContextFactory jooqContextFactory;
    private final JooqSqlBuilder jooqSqlBuilder;
    private final JooqConditionBuilder jooqConditionBuilder;

    /** 通用动态数据集查询 (支持 viewMode: LIST / DETAIL / ALL 自适应视图结构与元数据透传) */
    public EngineDataResult<DataPage<Map<String, Object>>> query(DynamicQueryReq req) {
        Long moduleId = req.getModuleId();
        SysModuleMetaResp completeResp = metadataCacheService.getModuleComplete(moduleId);
        if (completeResp == null || completeResp.getModule() == null) {
            throw new IllegalArgumentException("模块 ID [" + moduleId + "] 不存在或未配置元数据");
        }

        String primaryTable = jooqSqlBuilder.getPrimaryTableName(completeResp);
        List<ModuleTableHeaderDTO> headers =
                permissionFilterService.filterReadableHeaders(completeResp);

        DSLContext dsl = jooqContextFactory.getContext();
        Condition condition =
                jooqConditionBuilder.buildConditions(
                        primaryTable,
                        req.getFilters(),
                        headers,
                        AppContext.getSubjectId(),
                        completeResp);

        // 1. 第一阶段: 查询主表精确总数 COUNT
        long total = jooqSqlBuilder.fetchCount(dsl, completeResp, condition);

        // 2. 分页数据查询
        int pageNo = (req.getPageNo() != null && req.getPageNo() > 0) ? req.getPageNo() : 1;
        int pageSize =
                (req.getPageSize() != null && req.getPageSize() > 0) ? req.getPageSize() : 20;
        int offset = (pageNo - 1) * pageSize;

        var selectStep = jooqSqlBuilder.buildSelectFrom(dsl, completeResp).where(condition);
        OrderField<?> orderField;
        if (req.getOrderBy() != null && !req.getOrderBy().isBlank()) {
            Field<Object> field = DSL.field(DSL.name(req.getOrderBy()));
            orderField =
                    "ASC".equalsIgnoreCase(req.getOrderDirection()) ? field.asc() : field.desc();
        } else {
            orderField = DSL.field(DSL.name(primaryTable, "id")).desc();
        }

        Result<Record> records =
                selectStep.orderBy(orderField).limit(pageSize).offset(offset).fetch();

        // 3. 收集当前页主表主键 ID 集合与行数据
        List<Long> primaryIds = new ArrayList<>();
        List<Map<String, Object>> mainTableRows = new ArrayList<>();
        for (Record r : records) {
            Map<String, Object> row = new HashMap<>(r.intoMap());
            mainTableRows.add(row);

            Object idVal = row.get("id");
            if (idVal instanceof Number num) {
                primaryIds.add(num.longValue());
            }
        }

        // 4. 第二阶段: 判断是否需要深度加载 1:N 从表数据
        String viewMode =
                (req.getViewMode() != null && !req.getViewMode().isBlank())
                        ? req.getViewMode().toUpperCase()
                        : "LIST";

        boolean shouldFetch1N = "DETAIL".equals(viewMode) || "ALL".equals(viewMode);
        Map<String, Map<Long, List<Map<String, Object>>>> multiTableDataMap =
                shouldFetch1N
                        ? fetchOneToManyTables(dsl, primaryTable, completeResp, primaryIds)
                        : Collections.emptyMap();

        // 5. 按照 Table-First 规范组装返回行结构 (将物理 Join 字段精准归集至所属表)
        List<Map<String, Object>> structuredRecords = new ArrayList<>();
        Set<String> involvedTables = getInvolvedTables(completeResp, primaryTable);

        Set<String> oneToManyTables = new HashSet<>();
        if (completeResp.getTableRelations() != null) {
            for (TableRelationDTO rel : completeResp.getTableRelations()) {
                if ("1:N".equalsIgnoreCase(rel.getRelationType())
                        && primaryTable.equalsIgnoreCase(rel.getMainTable())
                        && rel.getJoinTable() != null
                        && !primaryTable.equalsIgnoreCase(rel.getJoinTable())) {
                    oneToManyTables.add(rel.getJoinTable().toLowerCase());
                }
            }
        }

        for (Map<String, Object> mainRow : mainTableRows) {
            Long primaryId = mainRow.get("id") instanceof Number num ? num.longValue() : null;
            Map<String, Object> rowTables = new LinkedHashMap<>();

            // 建立主表与伴生单行表 (1:1 / N:1) 属性子 Map
            Map<String, Map<String, Object>> tableMaps = new LinkedHashMap<>();
            for (String tName : involvedTables) {
                if (!oneToManyTables.contains(tName.toLowerCase())) {
                    tableMaps.put(tName, new LinkedHashMap<>());
                }
            }
            tableMaps.putIfAbsent(primaryTable, new LinkedHashMap<>());

            // 根据 fields 将主行字段精准归类到对应的所属表 (仅归类主表及伴生单行表)
            if (completeResp.getFields() != null && !completeResp.getFields().isEmpty()) {
                for (ModuleFieldDTO f : completeResp.getFields()) {
                    String tName =
                            (f.getTableName() != null && !f.getTableName().isBlank())
                                    ? f.getTableName()
                                    : primaryTable;
                    if (oneToManyTables.contains(tName.toLowerCase())) {
                        continue;
                    }
                    String colName = f.getColumnName();
                    if (colName != null && mainRow.containsKey(colName)) {
                        tableMaps
                                .computeIfAbsent(tName, k -> new LinkedHashMap<>())
                                .put(colName, mainRow.get(colName));
                    }
                }
            } else {
                tableMaps.get(primaryTable).putAll(mainRow);
            }

            // 确保每张物理表（主表及 1:1/N:1 伴生从表）默认在查询结果中返回其自身的主键 id
            for (Map.Entry<String, Map<String, Object>> entry : tableMaps.entrySet()) {
                String tName = entry.getKey();
                Map<String, Object> tMap = entry.getValue();

                if (tName.equalsIgnoreCase(primaryTable)) {
                    if (primaryId != null && (!tMap.containsKey("id") || tMap.get("id") == null)) {
                        tMap.put("id", primaryId);
                    }
                } else {
                    // 从 Join 结果别名中提取从表物理主键 (如 student_profile__id)
                    Object subPkVal = mainRow.get(tName + "__id");
                    if (subPkVal != null && (!tMap.containsKey("id") || tMap.get("id") == null)) {
                        tMap.put("id", subPkVal);
                    }
                }
            }

            // 将各单行/伴生表 Map 放入 rowTables
            for (Map.Entry<String, Map<String, Object>> entry : tableMaps.entrySet()) {
                rowTables.put(entry.getKey(), entry.getValue());
            }

            // 放入相对于当前主表的 1:N 从表多行数组 (仅 DETAIL/ALL 模式)
            if (completeResp.getTableRelations() != null) {
                for (TableRelationDTO rel : completeResp.getTableRelations()) {
                    String rType = rel.getRelationType();
                    if ("1:N".equalsIgnoreCase(rType)
                            && primaryTable.equalsIgnoreCase(rel.getMainTable())) {
                        String tName = rel.getJoinTable();
                        if (shouldFetch1N
                                && involvedTables.contains(tName.toLowerCase())
                                && !primaryTable.equalsIgnoreCase(tName)) {
                            List<Map<String, Object>> subRows =
                                    multiTableDataMap
                                            .getOrDefault(tName, Collections.emptyMap())
                                            .getOrDefault(primaryId, Collections.emptyList());
                            rowTables.put(tName, subRows);
                        }
                    }
                }
            }

            structuredRecords.add(rowTables);
        }

        DataPage<Map<String, Object>> page =
                DataPage.of(pageNo, pageSize, total, structuredRecords);
        EngineModuleMeta meta = buildEngineModuleMeta(completeResp, headers, viewMode);

        return EngineDataResult.of(meta, page);
    }

    /** 多模块批量并发查询 (用于多 Tab 详情页、仪表盘等场景一次性加载) */
    public BatchEngineDataResult batchQuery(BatchDynamicQueryReq req) {
        if (req == null || req.getQueries() == null || req.getQueries().isEmpty()) {
            return BatchEngineDataResult.of(Collections.emptyMap());
        }

        Map<String, EngineDataResult<DataPage<Map<String, Object>>>> resultMap =
                new ConcurrentHashMap<>();
        req.getQueries().entrySet().parallelStream()
                .forEach(
                        entry -> {
                            String alias = entry.getKey();
                            DynamicQueryReq itemReq = entry.getValue();
                            EngineDataResult<DataPage<Map<String, Object>>> singleResult =
                                    query(itemReq);
                            resultMap.put(alias, singleResult);
                        });

        return BatchEngineDataResult.of(resultMap);
    }

    /** 动态数据集/单条详情查询门面 (内部转为 query 且 pageSize=1) */
    public EngineDataResult<Map<String, Object>> getDetail(DynamicDetailReq req) {
        Map<String, Object> filters =
                req.getFilters() != null ? new HashMap<>(req.getFilters()) : new HashMap<>();
        if (req.getId() != null && req.getId() > 0) {
            filters.put("id", req.getId());
        }

        DynamicQueryReq queryReq =
                DynamicQueryReq.builder()
                        .moduleId(req.getModuleId())
                        .viewMode("DETAIL")
                        .pageNo(1)
                        .pageSize(1)
                        .filters(filters)
                        .build();

        EngineDataResult<DataPage<Map<String, Object>>> pageResult = query(queryReq);
        DataPage<Map<String, Object>> page = pageResult.getData();
        Map<String, Object> detailData =
                (page != null && page.getRecords() != null && !page.getRecords().isEmpty())
                        ? page.getRecords().get(0)
                        : Collections.emptyMap();

        return EngineDataResult.of(pageResult.getMeta(), detailData);
    }

    /** 构造自适应引擎元数据视图 (根据 viewMode 按需透传 headers / fields) */
    private EngineModuleMeta buildEngineModuleMeta(
            SysModuleMetaResp completeResp, List<ModuleTableHeaderDTO> headers, String viewMode) {
        if (completeResp == null || completeResp.getModule() == null) {
            return null;
        }

        var metaBuilder =
                EngineModuleMeta.builder()
                        .moduleId(completeResp.getModule().getId())
                        .moduleCode(completeResp.getModule().getModuleCode())
                        .moduleName(completeResp.getModule().getModuleName())
                        .moduleDesc(completeResp.getModule().getModuleDesc())
                        .primaryTable(jooqSqlBuilder.getPrimaryTableName(completeResp))
                        .tableRelations(completeResp.getTableRelations())
                        .statuses(completeResp.getModuleStatuses());

        if ("LIST".equalsIgnoreCase(viewMode)) {
            metaBuilder.headers(headers);
        } else if ("DETAIL".equalsIgnoreCase(viewMode)) {
            metaBuilder.fields(completeResp.getFields());
        } else {
            // ALL 或其他混合模式同时携带
            metaBuilder.headers(headers);
            metaBuilder.fields(completeResp.getFields());
        }

        // 装配当前用户角色在当前模块下的真实字段权限规则 (统一在 /engine/query meta 中透传)
        Long currentRoleId =
                (com.jdec.platform.shared.security.UserContext.getLoginUser() != null
                                && com.jdec.platform.shared.security.UserContext.getLoginUser()
                                                .getRoleId()
                                        != null)
                        ? com.jdec.platform.shared.security.UserContext.getLoginUser().getRoleId()
                        : 1L;
        metaBuilder.permissions(permissionFilterService.getFieldPermissionsByRoleId(currentRoleId));

        return metaBuilder.build();
    }

    /** 批量抓取主表 ID 集合关联的 1:N 从表记录 */
    private Map<String, Map<Long, List<Map<String, Object>>>> fetchOneToManyTables(
            DSLContext dsl,
            String primaryTable,
            SysModuleMetaResp completeResp,
            List<Long> primaryIds) {

        Map<String, Map<Long, List<Map<String, Object>>>> result = new HashMap<>();
        if (primaryIds == null
                || primaryIds.isEmpty()
                || completeResp.getTableRelations() == null) {
            return result;
        }

        Set<String> involvedTables = getInvolvedTables(completeResp, primaryTable);

        for (TableRelationDTO rel : completeResp.getTableRelations()) {
            String rType = rel.getRelationType();
            if ("1:N".equalsIgnoreCase(rType)
                    && primaryTable.equalsIgnoreCase(rel.getMainTable())) {
                String tableName = rel.getJoinTable();
                if (!involvedTables.contains(tableName.toLowerCase())) {
                    continue;
                }

                String fkField = rel.getJoinField();
                if (fkField == null || fkField.isBlank()) {
                    continue;
                }

                Result<Record> subRecords =
                        dsl.select(DSL.asterisk())
                                .from(DSL.table(DSL.name(tableName)))
                                .where(DSL.field(DSL.name(tableName, fkField)).in(primaryIds))
                                .fetch();

                Map<Long, List<Map<String, Object>>> grouped = new HashMap<>();
                for (Record r : subRecords) {
                    Map<String, Object> rMap = r.intoMap();
                    Object fkVal = rMap.get(fkField);
                    if (fkVal instanceof Number fkNum) {
                        grouped.computeIfAbsent(fkNum.longValue(), k -> new ArrayList<>())
                                .add(rMap);
                    }
                }
                result.put(tableName, grouped);
            }
        }
        return result;
    }

    private Set<String> getInvolvedTables(SysModuleMetaResp completeResp, String primaryTable) {
        Set<String> set = new HashSet<>();
        if (primaryTable != null && !primaryTable.isBlank()) {
            set.add(primaryTable.toLowerCase());
        }
        if (completeResp.getFields() != null) {
            for (ModuleFieldDTO f : completeResp.getFields()) {
                if (f.getTableName() != null && !f.getTableName().isBlank()) {
                    set.add(f.getTableName().toLowerCase());
                }
            }
        }
        return set;
    }
}
