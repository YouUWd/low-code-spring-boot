package com.jdec.platform.dataengine.biz.service;

import com.jdec.platform.config.api.dto.common.ModuleSimpleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleTableDTO;
import com.jdec.platform.config.api.dto.common.ModuleTableHeaderDTO;
import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp;
import com.jdec.platform.dataengine.api.dto.model.DetailMeta;
import com.jdec.platform.dataengine.api.dto.model.TablePermission;
import com.jdec.platform.dataengine.api.dto.request.DynamicQueryReq;
import com.jdec.platform.dataengine.api.dto.response.DynamicDetailResp;
import com.jdec.platform.dataengine.api.dto.response.DynamicQueryResp;
import com.jdec.platform.dataengine.biz.dsl.JooqConditionBuilder;
import com.jdec.platform.dataengine.biz.dsl.JooqContextFactory;
import com.jdec.platform.dataengine.biz.dsl.JooqSqlBuilder;
import com.jdec.platform.shared.context.AppContext;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

/** 动态查询服务 (基于 jOOQ 两阶段分页与 Table-First 极简组装) */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicQueryService {

    private final MetadataCacheService metadataCacheService;
    private final PermissionFilterService permissionFilterService;
    private final FieldTransformService fieldTransformService;
    private final JooqContextFactory jooqContextFactory;
    private final JooqSqlBuilder jooqSqlBuilder;
    private final JooqConditionBuilder jooqConditionBuilder;

    /** 一体化列表分页查询 */
    public DynamicQueryResp query(DynamicQueryReq req) {
        Long moduleId = req.getModuleId();
        SysModuleCompleteResp completeResp = metadataCacheService.getModuleComplete(moduleId);
        String primaryTable = completeResp.getModule().getPrimaryTable();
        List<ModuleTableHeaderDTO> headers =
                permissionFilterService.filterReadableHeaders(completeResp);

        DSLContext dsl = jooqContextFactory.getContext();
        Condition condition =
                jooqConditionBuilder.buildConditions(
                        primaryTable, req.getFilters(), headers, AppContext.getSubjectId());

        // 1. 第一阶段: 查询主表精确总数 COUNT
        int total =
                dsl.fetchCount(jooqSqlBuilder.buildSelectFrom(dsl, completeResp).where(condition));

        // 2. 分页数据查询
        int pageIndex = req.getPageIndex() > 0 ? req.getPageIndex() : 1;
        int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 20;
        int offset = (pageIndex - 1) * pageSize;

        var selectStep = jooqSqlBuilder.buildSelectFrom(dsl, completeResp).where(condition);
        if (req.getOrderBy() != null && !req.getOrderBy().isBlank()) {
            if ("ASC".equalsIgnoreCase(req.getOrderDirection())) {
                selectStep.orderBy(DSL.field(DSL.name(req.getOrderBy())).asc());
            } else {
                selectStep.orderBy(DSL.field(DSL.name(req.getOrderBy())).desc());
            }
        }
        Result<Record> recordsResult = selectStep.limit(pageSize).offset(offset).fetch();

        // 3. 提取主表记录并进行内存转换
        List<Map<String, Object>> primaryRows = new ArrayList<>();
        List<Long> primaryIds = new ArrayList<>();
        for (Record r : recordsResult) {
            Map<String, Object> map = new HashMap<>(r.intoMap());
            primaryRows.add(map);
            Object idVal = map.get("id");
            if (idVal instanceof Number num) {
                primaryIds.add(num.longValue());
            }
        }
        fieldTransformService.transformRecords(primaryRows, completeResp);

        // 4. 第二阶段: 批量下钻查询 1:N 关联从表
        Map<String, Map<Long, List<Map<String, Object>>>> multiTableDataMap =
                fetchOneToManyTables(dsl, completeResp, primaryIds);

        // 5. 组装行记录列表 (Table-First 纯粹 Map: record[tableName] = record/records)
        List<Map<String, Object>> structuredRecords = new ArrayList<>();
        for (Map<String, Object> row : primaryRows) {
            Map<String, Object> rowTables = new LinkedHashMap<>();
            Long rowId = row.get("id") instanceof Number num ? num.longValue() : null;

            // 主表数据
            rowTables.put(primaryTable, row);

            // 关联表数据 (1:1 / N:1 为单对象, 1:N 为对象列表)
            if (completeResp.getModuleTables() != null) {
                for (ModuleTableDTO tableDto : completeResp.getModuleTables()) {
                    String tName = tableDto.getTableName();
                    String rType = tableDto.getRelationType();
                    if ("1:N".equalsIgnoreCase(rType) || "ONE_TO_MANY".equalsIgnoreCase(rType)) {
                        List<Map<String, Object>> subRows =
                                multiTableDataMap
                                        .getOrDefault(tName, Collections.emptyMap())
                                        .getOrDefault(rowId, Collections.emptyList());
                        rowTables.put(tName, subRows);
                    } else {
                        // 1:1 或 N:1
                        rowTables.put(tName, row);
                    }
                }
            }
            structuredRecords.add(rowTables);
        }

        // 6. 组装 Meta 与 Pagination
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("moduleId", moduleId);
        meta.put("moduleCode", completeResp.getModule().getModuleCode());
        meta.put("moduleName", completeResp.getModule().getModuleName());
        meta.put("primaryTable", primaryTable);
        meta.put("detailModuleId", completeResp.getModule().getDetailModuleId());
        meta.put("tableHeader", headers);

        Map<String, Object> pagination = new LinkedHashMap<>();
        pagination.put("pageIndex", pageIndex);
        pagination.put("pageSize", pageSize);
        pagination.put("total", total);
        pagination.put("totalPages", (int) Math.ceil((double) total / pageSize));

        return DynamicQueryResp.builder()
                .meta(meta)
                .pagination(pagination)
                .records(structuredRecords)
                .build();
    }

    /** 动态主子表详情查询 (顶层包含 moduleId 等标识，元数据包含 fieldNames 与 permissions) */
    public DynamicDetailResp getDetail(Long moduleId, Long id) {
        SysModuleCompleteResp completeResp = metadataCacheService.getModuleComplete(moduleId);
        String primaryTable = completeResp.getModule().getPrimaryTable();
        DSLContext dsl = jooqContextFactory.getContext();

        // 1. 查询主表及 1:1 关联从表物理记录
        Record mainRecord =
                jooqSqlBuilder
                        .buildSelectFrom(dsl, completeResp)
                        .where(DSL.field(DSL.name(primaryTable, "id")).eq(id))
                        .fetchOne();

        Map<String, Object> mainRow =
                mainRecord != null ? new HashMap<>(mainRecord.intoMap()) : new HashMap<>();
        fieldTransformService.transformRecord(mainRow, completeResp);

        // 2. 查询当前模块 1:N 关联从表
        Map<String, Map<Long, List<Map<String, Object>>>> multiTableDataMap =
                fetchOneToManyTables(dsl, completeResp, List.of(id));

        Map<String, Object> tables = new LinkedHashMap<>();
        tables.put(primaryTable, mainRow);

        if (completeResp.getModuleTables() != null) {
            for (ModuleTableDTO tableDto : completeResp.getModuleTables()) {
                String tName = tableDto.getTableName();
                String rType = tableDto.getRelationType();
                if ("1:N".equalsIgnoreCase(rType) || "ONE_TO_MANY".equalsIgnoreCase(rType)) {
                    List<Map<String, Object>> subRows =
                            multiTableDataMap
                                    .getOrDefault(tName, Collections.emptyMap())
                                    .getOrDefault(id, Collections.emptyList());
                    tables.put(tName, subRows);
                } else {
                    tables.put(tName, mainRow);
                }
            }
        }

        // 3. 构建 DetailMeta (包含 fieldNames 与 permissions 字典)
        Map<String, Map<String, String>> fieldNames = new LinkedHashMap<>();
        Map<String, TablePermission> permissions = new LinkedHashMap<>();

        List<String> allTables = new ArrayList<>();
        allTables.add(primaryTable);
        if (completeResp.getModuleTables() != null) {
            for (ModuleTableDTO td : completeResp.getModuleTables()) {
                if (!allTables.contains(td.getTableName())) {
                    allTables.add(td.getTableName());
                }
            }
        }

        for (String tName : allTables) {
            fieldNames.put(tName, new LinkedHashMap<>());
            permissions.put(
                    tName,
                    TablePermission.builder()
                            .view(new ArrayList<>())
                            .apply(new ArrayList<>())
                            .edit(new ArrayList<>())
                            .build());
        }

        if (completeResp.getSimpleFields() != null) {
            for (ModuleSimpleFieldDTO fieldDto : completeResp.getSimpleFields()) {
                String tName = fieldDto.getTableName();
                String colName = fieldDto.getColumnName();
                String dispName =
                        fieldDto.getDisplayName() != null ? fieldDto.getDisplayName() : colName;

                if (fieldNames.containsKey(tName)) {
                    fieldNames.get(tName).put(colName, dispName);
                    TablePermission perm = permissions.get(tName);
                    perm.getView().add(colName);
                    perm.getApply().add(colName);
                    perm.getEdit().add(colName);
                }
            }
        }

        DetailMeta detailMeta =
                DetailMeta.builder().fieldNames(fieldNames).permissions(permissions).build();

        return DynamicDetailResp.builder()
                .moduleId(moduleId)
                .moduleCode(completeResp.getModule().getModuleCode())
                .moduleName(completeResp.getModule().getModuleName())
                .moduleType(
                        completeResp.getModule().getModuleType() != null
                                ? completeResp.getModule().getModuleType().name()
                                : "DETAIL")
                .primaryTable(primaryTable)
                .tables(tables)
                .meta(detailMeta)
                .subModules(Collections.emptyList())
                .build();
    }

    /** 批量抓取当前页主表 ID 集合关联的 1:N 从表记录 */
    private Map<String, Map<Long, List<Map<String, Object>>>> fetchOneToManyTables(
            DSLContext dsl, SysModuleCompleteResp completeResp, List<Long> primaryIds) {

        Map<String, Map<Long, List<Map<String, Object>>>> result = new HashMap<>();
        if (primaryIds == null || primaryIds.isEmpty() || completeResp.getModuleTables() == null) {
            return result;
        }

        for (ModuleTableDTO tableDto : completeResp.getModuleTables()) {
            String rType = tableDto.getRelationType();
            if ("1:N".equalsIgnoreCase(rType) || "ONE_TO_MANY".equalsIgnoreCase(rType)) {
                String tableName = tableDto.getTableName();
                String rightField = tableDto.getJoinRightField(); // 外键字段, 如 student_id

                Result<Record> subRecords =
                        dsl.select(DSL.asterisk())
                                .from(DSL.table(DSL.name(tableName)))
                                .where(DSL.field(DSL.name(tableName, rightField)).in(primaryIds))
                                .fetch();

                Map<Long, List<Map<String, Object>>> grouped = new HashMap<>();
                for (Record r : subRecords) {
                    Map<String, Object> rMap = r.intoMap();
                    Object fkVal = rMap.get(rightField);
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
}
