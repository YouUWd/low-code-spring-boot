package com.jdec.platform.data.biz.service;

import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleTableHeaderDTO;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import com.jdec.platform.config.api.dto.response.SysModuleMetaResp;
import com.jdec.platform.data.api.dto.model.EngineModuleMeta;
import com.jdec.platform.data.api.dto.request.BatchDynamicQueryReq;
import com.jdec.platform.data.api.dto.request.DynamicDetailReq;
import com.jdec.platform.data.api.dto.request.DynamicFilterItem;
import com.jdec.platform.data.api.dto.request.DynamicOptionReq;
import com.jdec.platform.data.api.dto.request.DynamicQueryReq;
import com.jdec.platform.data.api.dto.request.DynamicSortItem;
import com.jdec.platform.data.api.dto.response.BatchEngineDataResult;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.api.dto.response.DynamicOptionItem;
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

    /** 通用字段搜索下拉候选项查询 (获取当前模块作用域下的去重候选值列表，支持关键字模糊匹配与数据隔离) */
    public List<DynamicOptionItem> getOptions(DynamicOptionReq req) {
        if (req == null) {
            throw new IllegalArgumentException("搜索候选项请求参数不能为空");
        }
        Long moduleId = req.getModuleId();
        if (moduleId == null) {
            throw new IllegalArgumentException("模块 ID (moduleId) 不能为空");
        }
        String tableName = req.getTableName();
        String columnName = req.getColumnName();
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("物理表名 (tableName) 不能为空");
        }
        if (columnName == null || columnName.isBlank()) {
            throw new IllegalArgumentException("物理列名 (columnName) 不能为空");
        }

        // 基础标识符安全性校验 (防 SQL 注入)
        if (!tableName.matches("^[a-zA-Z0-9_]+$") || !columnName.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException("非法的表名或列名参数: " + tableName + "." + columnName);
        }

        SysModuleMetaResp completeResp = metadataCacheService.getModuleComplete(moduleId);
        if (completeResp == null || completeResp.getModule() == null) {
            throw new IllegalArgumentException("模块 ID [" + moduleId + "] 不存在或未配置元数据");
        }

        DSLContext dsl = jooqContextFactory.getContext();
        Field<Object> targetField = DSL.field(DSL.name(tableName, columnName));

        // 基础有效性过滤：非 NULL 且 非空字符串
        Condition condition = targetField.isNotNull().and(targetField.ne(DSL.inline("")));

        // 软删除过滤 (过滤 deleted = 0)
        condition = condition.and(DSL.field(DSL.name(tableName, "deleted")).eq(0));

        // 主体/租户隔离 (若上下文存在 subjectId)
        Long subjectId = AppContext.getSubjectId();
        if (subjectId != null && subjectId > 0) {
            condition = condition.and(DSL.field(DSL.name(tableName, "subject_id")).eq(subjectId));
        }

        // 关键字模糊匹配过滤
        if (req.getKeyword() != null && !req.getKeyword().trim().isEmpty()) {
            condition = condition.and(targetField.like("%" + req.getKeyword().trim() + "%"));
        }

        Result<? extends Record> records =
                dsl.selectDistinct(targetField)
                        .from(DSL.table(DSL.name(tableName)))
                        .where(condition)
                        .orderBy(targetField.asc())
                        .fetch();

        List<DynamicOptionItem> result = new ArrayList<>();
        for (Record r : records) {
            Object rawVal = r.get(0);
            if (rawVal != null) {
                DynamicOptionItem item = formatOption(tableName, columnName, rawVal, completeResp);
                if (item != null) {
                    result.add(item);
                }
            }
        }
        return result;
    }

    /** 选项加工扩展点方法 (根据元数据与字段类型对原始值进行加工处理，如字典映射、枚举标签、外键名称等) */
    public DynamicOptionItem formatOption(
            String tableName, String columnName, Object rawVal, SysModuleMetaResp meta) {
        if (rawVal == null) {
            return null;
        }
        String strVal = String.valueOf(rawVal).trim();
        if (strVal.isEmpty()) {
            return null;
        }
        // 现阶段保持 label 与 value 一致，预留后续加工逻辑
        return DynamicOptionItem.builder().label(strVal).value(rawVal).build();
    }

    /** 通用动态数据集查询 (支持 viewMode: LIST / DETAIL / ALL 自适应视图结构与多层级结构化数据组装) */
    public EngineDataResult<DataPage<Map<String, Object>>> query(DynamicQueryReq req) {
        Long moduleId = req.getModuleId();
        SysModuleMetaResp completeResp = metadataCacheService.getModuleComplete(moduleId);
        if (completeResp == null || completeResp.getModule() == null) {
            throw new IllegalArgumentException("模块 ID [" + moduleId + "] 不存在或未配置元数据");
        }

        // 0. 解析统一视图模式 (废除 ALL，仅支持 LIST 与 DETAIL，支持请求参数独立指定)
        String viewMode = normalizeViewMode(req.getViewMode());
        boolean isDetailMode = "DETAIL".equalsIgnoreCase(viewMode);

        String primaryTable = jooqSqlBuilder.getPrimaryTableName(completeResp);

        // DETAIL 视图模式下无需加载列表表头 headers，严格仅由 fields 驱动
        List<ModuleTableHeaderDTO> headers =
                isDetailMode
                        ? Collections.emptyList()
                        : permissionFilterService.filterReadableHeaders(completeResp);

        // 0. 虚拟空白模块或未配置模块防御：若 primaryTable 为空，安全返回空分页数据
        if (primaryTable == null || primaryTable.isBlank()) {
            EngineModuleMeta meta = buildEngineModuleMeta(completeResp, headers, viewMode);
            return EngineDataResult.of(meta, DataPage.empty(req.getPageNo(), req.getPageSize()));
        }

        DSLContext dsl = jooqContextFactory.getContext();
        Condition condition =
                jooqConditionBuilder.buildConditions(
                        primaryTable, req, headers, AppContext.getSubjectId(), completeResp);

        // 1. 第一阶段: 查询主表精确总数 COUNT (由 viewMode 驱动物理表与 Join)
        long total = jooqSqlBuilder.fetchCount(dsl, completeResp, condition, viewMode);

        // 2. 数据查询 (LIST 模式执行分页 limit/offset，DETAIL 模式全量返回无分页)
        int pageNo = (req.getPageNo() != null && req.getPageNo() > 0) ? req.getPageNo() : 1;
        int pageSize =
                (req.getPageSize() != null && req.getPageSize() > 0) ? req.getPageSize() : 20;
        int offset = (pageNo - 1) * pageSize;

        var selectStep =
                jooqSqlBuilder.buildSelectFrom(dsl, completeResp, viewMode).where(condition);
        List<OrderField<?>> orderFields = new ArrayList<>();
        if (req.getSorts() != null && !req.getSorts().isEmpty()) {
            for (DynamicSortItem sortItem : req.getSorts()) {
                if (sortItem == null
                        || sortItem.getColumnName() == null
                        || sortItem.getColumnName().isBlank()) {
                    continue;
                }
                String targetTable = sortItem.getTableName();
                if (targetTable == null || targetTable.isBlank()) {
                    targetTable = primaryTable;
                }
                // 仅当目标表为主表或外层直接 JOIN 的伴生表时才加入外层 ORDER BY
                if (isMainOrDirectJoinTable(targetTable, completeResp, primaryTable)) {
                    Field<Object> field =
                            DSL.field(DSL.name(targetTable, sortItem.getColumnName()));
                    orderFields.add(
                            "DESC".equalsIgnoreCase(sortItem.getDirection())
                                    ? field.desc()
                                    : field.asc());
                }
            }
        }
        if (orderFields.isEmpty()) {
            orderFields.add(DSL.field(DSL.name(primaryTable, "id")).desc());
        }

        Result<Record> records =
                isDetailMode
                        ? selectStep.orderBy(orderFields).fetch()
                        : selectStep.orderBy(orderFields).limit(pageSize).offset(offset).fetch();

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

        // 4. 第二阶段: 判断与收集激活的物理表集合 (Active Tables)
        Set<String> involvedTables = getInvolvedTables(completeResp, primaryTable);
        Set<String> activeTables =
                getActiveTables(req, headers, completeResp, primaryTable, viewMode);

        // 5. 多层级 1:N:N 深度递归/自底向上批量数据提取 (携带局部子表过滤条件)
        Map<String, Map<Long, List<Map<String, Object>>>> hierarchicalDataMap =
                fetchHierarchicalOneToManyTables(
                        dsl, primaryTable, completeResp, primaryIds, activeTables, req, headers);

        // 6. 按照 Table-First 规范组装返回行结构 (将物理 Join 字段精准归集至所属表)
        List<Map<String, Object>> structuredRecords = new ArrayList<>();

        // 计算当前主查询实际物理 Join 连入的全部单行/伴生表集合 (绝对权威，绝不被判定为 1:N 从表)
        Set<String> physicalJoinedTables =
                jooqSqlBuilder.calculateJoinedTables(primaryTable, completeResp, viewMode);

        Set<String> oneToManyTables = new HashSet<>();
        if (completeResp.getTableRelations() != null) {
            for (TableRelationDTO rel : completeResp.getTableRelations()) {
                if ("1:N".equalsIgnoreCase(rel.getRelationType())
                        && rel.getJoinTable() != null
                        && !primaryTable.equalsIgnoreCase(rel.getJoinTable())) {
                    String subT = rel.getJoinTable().toLowerCase();
                    // 仅当该表未参与物理 Join（即不是平铺单行实体或父维表）时，才作为 1:N 递归从表
                    if (!physicalJoinedTables.contains(subT)) {
                        oneToManyTables.add(subT);
                    }
                }
            }
        }

        for (Map<String, Object> mainRow : mainTableRows) {
            Long primaryId = mainRow.get("id") instanceof Number num ? num.longValue() : null;

            // 建立主表与伴生单行表 (1:1 / N:1 / 物理 Join 维表) 属性子 Map
            Map<String, Map<String, Object>> tableMaps = new LinkedHashMap<>();
            for (String tName : involvedTables) {
                if (!oneToManyTables.contains(tName.toLowerCase())) {
                    tableMaps.put(tName, new LinkedHashMap<>());
                }
            }
            tableMaps.putIfAbsent(primaryTable, new LinkedHashMap<>());

            if ("DETAIL".equalsIgnoreCase(viewMode)) {
                // DETAIL 模式：严格从当前模块 sys_module_field 中取表及字段信息归类
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
                        if (colName != null) {
                            String qualKey = tName + "__" + colName;
                            Object val =
                                    mainRow.containsKey(qualKey)
                                            ? mainRow.get(qualKey)
                                            : mainRow.get(colName);
                            if (val != null
                                    || mainRow.containsKey(qualKey)
                                    || mainRow.containsKey(colName)) {
                                tableMaps
                                        .computeIfAbsent(tName, k -> new LinkedHashMap<>())
                                        .put(colName, val);
                            }
                        }
                    }
                } else {
                    tableMaps.get(primaryTable).putAll(mainRow);
                }
            } else {
                // LIST 模式：严格从 sys_module_header 中取模块、表及字段信息归类
                if (headers != null && !headers.isEmpty()) {
                    for (ModuleTableHeaderDTO h : headers) {
                        String tName = h.getTable();
                        if (tName == null || tName.isBlank()) {
                            throw new IllegalStateException(
                                    String.format(
                                            "模块表头元数据配置异常：物理表名不能为空 (headerName=%s, field=%s)",
                                            h.getName(), h.getField()));
                        }
                        if (oneToManyTables.contains(tName.toLowerCase())) {
                            continue;
                        }
                        String colName = h.getField();
                        if (colName != null) {
                            String qualKey = tName + "__" + colName;
                            Object val =
                                    mainRow.containsKey(qualKey)
                                            ? mainRow.get(qualKey)
                                            : mainRow.get(colName);
                            if (val != null
                                    || mainRow.containsKey(qualKey)
                                    || mainRow.containsKey(colName)) {
                                tableMaps
                                        .computeIfAbsent(tName, k -> new LinkedHashMap<>())
                                        .put(colName, val);
                            }
                        }
                    }
                }
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
                    // 从 Join 结果别名或外键关系中稳健提取从表物理主键 (如 clazz__id / CLAZZ__ID / clazz_id)
                    Object subPkVal =
                            extractJoinTablePk(mainRow, tName, completeResp, primaryTable);
                    if (subPkVal != null && (!tMap.containsKey("id") || tMap.get("id") == null)) {
                        tMap.put("id", subPkVal);
                    }
                }
            }

            // 构建 table -> sourceModuleId 业务模块归属映射 (以 modulePath 链路末端为准)
            Map<String, Long> tableModuleMap = new HashMap<>();
            List<ModuleTableHeaderDTO> sourceHeaders =
                    (headers != null && !headers.isEmpty())
                            ? headers
                            : completeResp.getModuleHeaders();

            if (sourceHeaders != null && !sourceHeaders.isEmpty()) {
                for (ModuleTableHeaderDTO h : sourceHeaders) {
                    if (h.getTable() != null) {
                        Long sMid =
                                (h.getModulePath() != null && !h.getModulePath().isEmpty())
                                        ? h.getModulePath().get(h.getModulePath().size() - 1)
                                        : moduleId;
                        tableModuleMap.putIfAbsent(h.getTable().toLowerCase(), sMid);
                    }
                }
            } else if (completeResp.getFields() != null) {
                // 若完全无 headers，本模块字段涉及的表归属于当前业务模块
                for (ModuleFieldDTO f : completeResp.getFields()) {
                    if (f.getTableName() != null) {
                        tableModuleMap.putIfAbsent(f.getTableName().toLowerCase(), moduleId);
                    }
                }
            }
            tableModuleMap.put(primaryTable.toLowerCase(), moduleId);

            // 建立纯对象多模块作用域行记录: record -> moduleId (纯对象) -> table (对象/列表) -> field
            Map<String, Object> rowRecord = new LinkedHashMap<>();

            // 1. 放入各单行/伴生表 (1:1 / N:1) 纯对象空间
            for (Map.Entry<String, Map<String, Object>> entry : tableMaps.entrySet()) {
                String tName = entry.getKey();
                Long targetModId = tableModuleMap.getOrDefault(tName.toLowerCase(), moduleId);
                String modKey = String.valueOf(targetModId);

                @SuppressWarnings("unchecked")
                Map<String, Object> moduleSpace =
                        (Map<String, Object>)
                                rowRecord.computeIfAbsent(modKey, k -> new LinkedHashMap<>());
                moduleSpace.put(tName, entry.getValue());
            }

            // 2. 放入 1:N 从表多行结构化数据，归入对应模块对象空间
            if (completeResp.getTableRelations() != null) {
                for (TableRelationDTO rel : completeResp.getTableRelations()) {
                    String rType = rel.getRelationType();
                    if ("1:N".equalsIgnoreCase(rType)
                            && primaryTable.equalsIgnoreCase(rel.getMainTable())) {
                        String tName = rel.getJoinTable();
                        boolean isDirectActive = activeTables.contains(tName.toLowerCase());
                        boolean hasChildActive =
                                hasActiveDescendant(
                                        tName, completeResp.getTableRelations(), activeTables);

                        if ((isDirectActive || hasChildActive)
                                && !primaryTable.equalsIgnoreCase(tName)) {
                            List<Map<String, Object>> subRows =
                                    hierarchicalDataMap
                                            .getOrDefault(tName, Collections.emptyMap())
                                            .getOrDefault(primaryId, Collections.emptyList());

                            Long targetModId = tableModuleMap.get(tName.toLowerCase());
                            if (targetModId == null) {
                                // 若中间从表未显式在表头配置，优先探查其下级孙表所属业务模块并继承
                                if (completeResp.getTableRelations() != null) {
                                    for (TableRelationDTO subRel :
                                            completeResp.getTableRelations()) {
                                        if ("1:N".equalsIgnoreCase(subRel.getRelationType())
                                                && tName.equalsIgnoreCase(subRel.getMainTable())) {
                                            String childTable = subRel.getJoinTable();
                                            if (childTable != null
                                                    && tableModuleMap.containsKey(
                                                            childTable.toLowerCase())) {
                                                targetModId =
                                                        tableModuleMap.get(
                                                                childTable.toLowerCase());
                                                break;
                                            }
                                        }
                                    }
                                }
                                if (targetModId == null) {
                                    targetModId = moduleId;
                                }
                            }
                            String modKey = String.valueOf(targetModId);

                            @SuppressWarnings("unchecked")
                            Map<String, Object> moduleSpace =
                                    (Map<String, Object>)
                                            rowRecord.computeIfAbsent(
                                                    modKey, k -> new LinkedHashMap<>());
                            moduleSpace.put(tName, subRows);
                        }
                    }
                }
            }

            structuredRecords.add(rowRecord);
        }

        DataPage<Map<String, Object>> page =
                isDetailMode
                        ? DataPage.of(1, structuredRecords.size(), total, structuredRecords)
                        : DataPage.of(pageNo, pageSize, total, structuredRecords);
        EngineModuleMeta meta = buildEngineModuleMeta(completeResp, headers, viewMode);

        return EngineDataResult.of(meta, page);
    }

    /** 收集当前请求中被激活的物理表集合 (Active Tables) */
    private Set<String> getActiveTables(
            DynamicQueryReq req,
            List<ModuleTableHeaderDTO> headers,
            SysModuleMetaResp completeResp,
            String primaryTable,
            String viewMode) {

        Set<String> active = new HashSet<>();
        if (primaryTable != null && !primaryTable.isBlank()) {
            active.add(primaryTable.toLowerCase());
        }

        boolean isDetail = "DETAIL".equalsIgnoreCase(viewMode);
        boolean hasConfiguredFields =
                completeResp.getFields() != null && !completeResp.getFields().isEmpty();

        // 详情模式且存在物理字段配置：严格从当前模块 sys_module_field 中取表信息，并激活关联从表
        if (isDetail && hasConfiguredFields) {
            for (ModuleFieldDTO f : completeResp.getFields()) {
                if (f.getTableName() != null && !f.getTableName().isBlank()) {
                    active.add(f.getTableName().toLowerCase());
                }
            }
            if (completeResp.getTableRelations() != null) {
                for (TableRelationDTO rel : completeResp.getTableRelations()) {
                    if (rel.getJoinTable() != null && !rel.getJoinTable().isBlank()) {
                        active.add(rel.getJoinTable().toLowerCase());
                    }
                }
            }
            return active;
        }

        // 列表模式 (或虚拟空白模块自适应回退)：严格从 sys_module_header 中取模块与表信息
        if (headers != null) {
            for (ModuleTableHeaderDTO h : headers) {
                if (h.getTable() == null || h.getTable().isBlank()) {
                    throw new IllegalStateException(
                            String.format(
                                    "模块表头元数据配置异常：物理表名不能为空 (headerName=%s, field=%s)",
                                    h.getName(), h.getField()));
                }
                active.add(h.getTable().toLowerCase());
            }
        }

        if (req.getFilters() != null) {
            for (DynamicFilterItem item : req.getFilters()) {
                if (item != null) {
                    if (item.getTableName() != null && !item.getTableName().isBlank()) {
                        active.add(item.getTableName().toLowerCase());
                    } else if (item.getColumnName() != null && item.getColumnName().contains(".")) {
                        active.add(item.getColumnName().split("\\.")[0].toLowerCase());
                    }
                }
            }
        }

        return active;
    }

    /** 批量分层拉取并自底向上递归装配 1:N:N 多层级从表数据 (携带局部子表过滤条件) */
    private Map<String, Map<Long, List<Map<String, Object>>>> fetchHierarchicalOneToManyTables(
            DSLContext dsl,
            String primaryTable,
            SysModuleMetaResp completeResp,
            List<Long> primaryIds,
            Set<String> activeTables,
            DynamicQueryReq req,
            List<ModuleTableHeaderDTO> headers) {

        Map<String, Map<Long, List<Map<String, Object>>>> result = new HashMap<>();
        if (primaryIds == null
                || primaryIds.isEmpty()
                || completeResp.getTableRelations() == null) {
            return result;
        }

        List<TableRelationDTO> relations = completeResp.getTableRelations();

        // 1. 获取主表的直属 1:N 从表列表
        for (TableRelationDTO rel : relations) {
            if ("1:N".equalsIgnoreCase(rel.getRelationType())
                    && primaryTable.equalsIgnoreCase(rel.getMainTable())) {

                String subTable = rel.getJoinTable();
                if (!activeTables.contains(subTable.toLowerCase())) {
                    // 若直属从表未被激活，但其下级孙表被激活，依然需要作为中间桥梁加载
                    boolean childActive = hasActiveDescendant(subTable, relations, activeTables);
                    if (!childActive) {
                        continue;
                    }
                }

                String fkField = rel.getJoinField();
                Condition subTableFilter =
                        jooqConditionBuilder.buildSubTableConditions(
                                subTable, req, headers, completeResp);

                var queryStep =
                        dsl.select(DSL.asterisk())
                                .from(DSL.table(DSL.name(subTable)))
                                .where(DSL.field(DSL.name(subTable, fkField)).in(primaryIds))
                                .and(DSL.field(DSL.name(subTable, "deleted")).eq((byte) 0));

                if (subTableFilter != null && !DSL.noCondition().equals(subTableFilter)) {
                    queryStep = queryStep.and(subTableFilter);
                }

                List<OrderField<?>> subOrderFields = new ArrayList<>();
                if (req != null && req.getSorts() != null) {
                    for (DynamicSortItem sortItem : req.getSorts()) {
                        if (sortItem != null
                                && subTable.equalsIgnoreCase(sortItem.getTableName())
                                && sortItem.getColumnName() != null
                                && !sortItem.getColumnName().isBlank()) {
                            Field<Object> f =
                                    DSL.field(DSL.name(subTable, sortItem.getColumnName()));
                            subOrderFields.add(
                                    "DESC".equalsIgnoreCase(sortItem.getDirection())
                                            ? f.desc()
                                            : f.asc());
                        }
                    }
                }
                if (subOrderFields.isEmpty()) {
                    subOrderFields.add(DSL.field(DSL.name(subTable, "id")).asc());
                }

                Result<Record> subRecords = queryStep.orderBy(subOrderFields).fetch();

                Map<Long, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
                List<Long> subPrimaryIds = new ArrayList<>();

                for (Record r : subRecords) {
                    Map<String, Object> rMap = new LinkedHashMap<>(r.intoMap());
                    Object fkVal = rMap.get(fkField);
                    if (fkVal instanceof Number fkNum) {
                        grouped.computeIfAbsent(fkNum.longValue(), k -> new ArrayList<>())
                                .add(rMap);
                    }
                    Object pkVal = rMap.get("id");
                    if (pkVal instanceof Number pkNum) {
                        subPrimaryIds.add(pkNum.longValue());
                    }
                }

                // 2. 递归拉取孙表及更深层数据 (1:N:N)
                if (!subPrimaryIds.isEmpty()) {
                    assembleGrandchildTables(
                            dsl,
                            subTable,
                            subPrimaryIds,
                            relations,
                            activeTables,
                            grouped,
                            req,
                            headers,
                            completeResp);
                }

                result.put(subTable, grouped);
            }
        }
        return result;
    }

    /** 递归拉取并自底向上挂载孙表数据 */
    private void assembleGrandchildTables(
            DSLContext dsl,
            String parentTable,
            List<Long> parentPrimaryIds,
            List<TableRelationDTO> relations,
            Set<String> activeTables,
            Map<Long, List<Map<String, Object>>> parentRowsByFk,
            DynamicQueryReq req,
            List<ModuleTableHeaderDTO> headers,
            SysModuleMetaResp completeResp) {

        for (TableRelationDTO rel : relations) {
            if ("1:N".equalsIgnoreCase(rel.getRelationType())
                    && parentTable.equalsIgnoreCase(rel.getMainTable())) {

                String grandChildTable = rel.getJoinTable();
                if (!activeTables.contains(grandChildTable.toLowerCase())) {
                    boolean childActive =
                            hasActiveDescendant(grandChildTable, relations, activeTables);
                    if (!childActive) {
                        continue;
                    }
                }

                String grandChildFk = rel.getJoinField();
                Condition grandChildFilter =
                        jooqConditionBuilder.buildSubTableConditions(
                                grandChildTable, req, headers, completeResp);

                var queryStep =
                        dsl.select(DSL.asterisk())
                                .from(DSL.table(DSL.name(grandChildTable)))
                                .where(
                                        DSL.field(DSL.name(grandChildTable, grandChildFk))
                                                .in(parentPrimaryIds))
                                .and(DSL.field(DSL.name(grandChildTable, "deleted")).eq((byte) 0));

                if (grandChildFilter != null && !DSL.noCondition().equals(grandChildFilter)) {
                    queryStep = queryStep.and(grandChildFilter);
                }

                List<OrderField<?>> grandChildOrderFields = new ArrayList<>();
                if (req != null && req.getSorts() != null) {
                    for (DynamicSortItem sortItem : req.getSorts()) {
                        if (sortItem != null
                                && grandChildTable.equalsIgnoreCase(sortItem.getTableName())
                                && sortItem.getColumnName() != null
                                && !sortItem.getColumnName().isBlank()) {
                            Field<Object> f =
                                    DSL.field(DSL.name(grandChildTable, sortItem.getColumnName()));
                            grandChildOrderFields.add(
                                    "DESC".equalsIgnoreCase(sortItem.getDirection())
                                            ? f.desc()
                                            : f.asc());
                        }
                    }
                }
                if (grandChildOrderFields.isEmpty()) {
                    grandChildOrderFields.add(DSL.field(DSL.name(grandChildTable, "id")).asc());
                }

                Result<Record> grandChildRecords = queryStep.orderBy(grandChildOrderFields).fetch();

                Map<Long, List<Map<String, Object>>> grandChildGrouped = new LinkedHashMap<>();
                List<Long> grandChildIds = new ArrayList<>();

                for (Record r : grandChildRecords) {
                    Map<String, Object> rMap = new LinkedHashMap<>(r.intoMap());
                    Object fkVal = rMap.get(grandChildFk);
                    if (fkVal instanceof Number fkNum) {
                        grandChildGrouped
                                .computeIfAbsent(fkNum.longValue(), k -> new ArrayList<>())
                                .add(rMap);
                    }
                    Object pkVal = rMap.get("id");
                    if (pkVal instanceof Number pkNum) {
                        grandChildIds.add(pkNum.longValue());
                    }
                }

                // 递归深入曾孙表
                if (!grandChildIds.isEmpty()) {
                    assembleGrandchildTables(
                            dsl,
                            grandChildTable,
                            grandChildIds,
                            relations,
                            activeTables,
                            grandChildGrouped,
                            req,
                            headers,
                            completeResp);
                }

                // 自底向上挂载到父行 Map 中
                for (List<Map<String, Object>> pRows : parentRowsByFk.values()) {
                    for (Map<String, Object> pRow : pRows) {
                        Object pId = pRow.get("id");
                        if (pId instanceof Number pNum) {
                            List<Map<String, Object>> matchedGrandChildren =
                                    grandChildGrouped.getOrDefault(
                                            pNum.longValue(), Collections.emptyList());
                            pRow.put(grandChildTable, matchedGrandChildren);
                        }
                    }
                }
            }
        }
    }

    /** 判断某个表是否具有被激活的后代表 */
    private boolean hasActiveDescendant(
            String currentTable, List<TableRelationDTO> relations, Set<String> activeTables) {
        for (TableRelationDTO rel : relations) {
            if ("1:N".equalsIgnoreCase(rel.getRelationType())
                    && currentTable.equalsIgnoreCase(rel.getMainTable())) {
                String child = rel.getJoinTable();
                if (child != null && !child.equalsIgnoreCase(currentTable)) {
                    if (activeTables.contains(child.toLowerCase())) {
                        return true;
                    }
                    if (hasActiveDescendant(child, relations, activeTables)) {
                        return true;
                    }
                }
            }
        }
        return false;
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
        List<com.jdec.platform.data.api.dto.request.DynamicFilterItem> filters =
                req.getFilters() != null ? new ArrayList<>(req.getFilters()) : new ArrayList<>();
        if (req.getId() != null && req.getId() > 0) {
            filters.add(
                    com.jdec.platform.data.api.dto.request.DynamicFilterItem.builder()
                            .columnName("id")
                            .value(req.getId())
                            .build());
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
                        .statuses(completeResp.getModuleStatuses());

        if ("DETAIL".equalsIgnoreCase(viewMode)) {
            metaBuilder.fields(completeResp.getFields());
        } else {
            // LIST 模式：严格仅透传具备完整 modulePath 血缘链路的 headers 与当前模块下的子孙模块节点
            metaBuilder.headers(headers);
            metaBuilder.moduleNodes(completeResp.getModuleNodes());
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
        if (completeResp.getModuleHeaders() != null) {
            for (ModuleTableHeaderDTO h : completeResp.getModuleHeaders()) {
                if (h.getTable() == null || h.getTable().isBlank()) {
                    throw new IllegalStateException(
                            String.format(
                                    "模块表头元数据配置异常：物理表名不能为空 (headerName=%s, field=%s)",
                                    h.getName(), h.getField()));
                }
                set.add(h.getTable().toLowerCase());
            }
        }
        return set;
    }

    private Object extractJoinTablePk(
            Map<String, Object> mainRow,
            String tName,
            SysModuleMetaResp completeResp,
            String primaryTable) {
        if (mainRow == null || tName == null) {
            return null;
        }

        // 1. 优先从 Join 别名提取 (如 clazz__id / CLAZZ__ID)
        String targetSuffix = (tName + "__id").toLowerCase();
        for (Map.Entry<String, Object> e : mainRow.entrySet()) {
            if (e.getKey() != null) {
                String k = e.getKey().toLowerCase();
                if (k.equals(targetSuffix)
                        || k.endsWith("." + targetSuffix)
                        || k.endsWith("_" + targetSuffix)) {
                    if (e.getValue() != null) {
                        return e.getValue();
                    }
                }
            }
        }

        // 2. 次选：若是 N:1 关系 (主表持有从表外键，如 student.clazz_id)，直接从主表外键列提取
        if (completeResp != null && completeResp.getTableRelations() != null) {
            for (TableRelationDTO rel : completeResp.getTableRelations()) {
                if ("N:1".equalsIgnoreCase(rel.getRelationType())
                        || "1:1".equalsIgnoreCase(rel.getRelationType())) {
                    if (primaryTable.equalsIgnoreCase(rel.getMainTable())
                            && tName.equalsIgnoreCase(rel.getJoinTable())) {
                        String fkCol = rel.getMainField();
                        if (fkCol != null
                                && mainRow.containsKey(fkCol)
                                && mainRow.get(fkCol) != null) {
                            return mainRow.get(fkCol);
                        }
                    }
                }
            }
        }

        // 3. 通用兜底：尝试 tName + "_id" (如 clazz_id)
        String fallbackFk = (tName + "_id").toLowerCase();
        for (Map.Entry<String, Object> e : mainRow.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(fallbackFk)) {
                return e.getValue();
            }
        }

        return null;
    }

    /** 判断目标表是否为主表或外层直接 JOIN 的 1:1 / N:1 伴生表 */
    private boolean isMainOrDirectJoinTable(
            String targetTable, SysModuleMetaResp completeResp, String primaryTable) {
        if (targetTable == null
                || targetTable.isBlank()
                || targetTable.equalsIgnoreCase(primaryTable)) {
            return true;
        }
        if (completeResp != null && completeResp.getTableRelations() != null) {
            for (TableRelationDTO rel : completeResp.getTableRelations()) {
                if (primaryTable.equalsIgnoreCase(rel.getMainTable())
                        && targetTable.equalsIgnoreCase(rel.getJoinTable())
                        && ("1:1".equalsIgnoreCase(rel.getRelationType())
                                || "N:1".equalsIgnoreCase(rel.getRelationType()))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 统一视图模式校验与归一化：废除 ALL，仅支持 LIST 与 DETAIL，默认 LIST */
    private String normalizeViewMode(String rawMode) {
        if (rawMode != null && "DETAIL".equalsIgnoreCase(rawMode.trim())) {
            return "DETAIL";
        }
        return "LIST";
    }
}
