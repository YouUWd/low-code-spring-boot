package com.jdec.platform.data.biz.dsl;

import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleTableHeaderDTO;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import com.jdec.platform.config.api.dto.response.SysModuleMetaResp;
import com.jdec.platform.data.api.dto.request.DynamicFilterItem;
import com.jdec.platform.data.api.dto.request.DynamicQueryReq;
import java.util.*;
import org.jooq.Condition;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * jOOQ 动态条件组装器 (严密自洽版)
 *
 * <ul>
 *   <li>基于 JoinPathResolver 树路径解析，彻底消除 BFS 盲搜与多路径二义性；
 *   <li>模块搜索语义封闭（Module Scoping Invariance）：搜索条件严格限定归属模块生效，杜绝跨模块同表同字段碰撞；
 *   <li>多级子模块支路融合（Branch Merging）：同一分支主干下的多层子孙模块合并为单一连贯 EXISTS 子查询；
 *   <li>表别名严格采用语义化模块前缀 (如 101_clazz, 103_student_course, 106_student_award_detail)，无多余 m 前缀。
 * </ul>
 */
@Component
public class JooqConditionBuilder {

    private final JoinPathResolver joinPathResolver;

    public JooqConditionBuilder() {
        this.joinPathResolver = new JoinPathResolver();
    }

    public JooqConditionBuilder(JoinPathResolver joinPathResolver) {
        this.joinPathResolver =
                joinPathResolver != null ? joinPathResolver : new JoinPathResolver();
    }

    /** 结构化请求参数重载入口 */
    public Condition buildConditions(
            String primaryTable,
            DynamicQueryReq req,
            List<ModuleTableHeaderDTO> headers,
            Long subjectId,
            SysModuleMetaResp completeResp) {

        if (req == null) {
            return doBuildConditions(
                    primaryTable, Collections.emptyList(), subjectId, completeResp);
        }

        List<FilterItem> filterItems = new ArrayList<>();
        if (req.getFilters() != null && !req.getFilters().isEmpty()) {
            filterItems.addAll(
                    parseStructuredFilterItems(
                            primaryTable, req.getFilters(), headers, completeResp));
        }

        return doBuildConditions(primaryTable, filterItems, subjectId, completeResp);
    }

    /** 为第二阶段 1:N / 1:N:N 从表及孙表拉取构建专属于该子表的过滤条件 (例如过滤 student_course.course_name) */
    public Condition buildSubTableConditions(
            String targetSubTable,
            DynamicQueryReq req,
            List<ModuleTableHeaderDTO> headers,
            SysModuleMetaResp completeResp) {

        if (targetSubTable == null
                || targetSubTable.isBlank()
                || req == null
                || req.getFilters() == null
                || req.getFilters().isEmpty()) {
            return DSL.noCondition();
        }

        List<FilterItem> filterItems =
                parseStructuredFilterItems(targetSubTable, req.getFilters(), headers, completeResp);

        List<Condition> subConditions = new ArrayList<>();
        for (FilterItem item : filterItems) {
            if (targetSubTable.equalsIgnoreCase(item.tableName)) {
                subConditions.add(
                        buildSingleCondition(
                                targetSubTable,
                                item.fieldName,
                                item.searchType,
                                item.operator,
                                item.value));
            }
        }

        return subConditions.isEmpty() ? DSL.noCondition() : DSL.and(subConditions);
    }

    /** 传统 Map 请求参数入口 (兼容旧代码) */
    public Condition buildConditions(
            String primaryTable,
            Map<String, Object> filters,
            List<ModuleTableHeaderDTO> headers,
            Long subjectId,
            SysModuleMetaResp completeResp) {

        List<FilterItem> filterItems =
                parseFilterItems(primaryTable, filters, headers, completeResp);
        return doBuildConditions(primaryTable, filterItems, subjectId, completeResp);
    }

    private Condition doBuildConditions(
            String primaryTable,
            List<FilterItem> filterItems,
            Long subjectId,
            SysModuleMetaResp completeResp) {

        List<Condition> conditions = new ArrayList<>();

        // 1. 基础主体隔离（仅在主表配置了 subject_id 字段时才添加过滤条件）
        boolean hasSubjectId =
                completeResp != null
                        && completeResp.getFields() != null
                        && completeResp.getFields().stream()
                                .anyMatch(
                                        f ->
                                                primaryTable.equalsIgnoreCase(f.getTableName())
                                                        && "subject_id"
                                                                .equalsIgnoreCase(
                                                                        f.getColumnName()));

        if (hasSubjectId && subjectId != null && subjectId > 0) {
            conditions.add(DSL.field(DSL.name(primaryTable, "subject_id")).eq(subjectId));
        }

        if (filterItems.isEmpty()) {
            return conditions.isEmpty() ? DSL.noCondition() : DSL.and(conditions);
        }

        List<TableRelationDTO> relations =
                completeResp != null && completeResp.getTableRelations() != null
                        ? completeResp.getTableRelations()
                        : Collections.emptyList();

        // 计算伴生表集合（1:1 或当前主表作为从表持外键的 N:1 关联表）
        Set<String> companionTables = getCompanionTables(primaryTable, relations);

        Long rootModuleId =
                completeResp != null && completeResp.getModule() != null
                        ? completeResp.getModule().getId()
                        : null;

        // 2. 将过滤条件分类：
        // (1) 外层直连主表/伴生表条件：必须归属于根模块作用域
        // (2) 1:N 子模块/孙模块条件：按关联路径的分支根（Branch Key）聚合，实施支路融合 (Branch Merging)
        List<Condition> directConditions = new ArrayList<>();
        Map<String, BranchGroup> branchGroups = new LinkedHashMap<>();

        for (FilterItem item : filterItems) {
            String targetTable = item.tableName.toLowerCase();
            Long itemModId = item.moduleId;
            if (item.modulePath != null && !item.modulePath.isEmpty()) {
                itemModId = item.modulePath.get(item.modulePath.size() - 1);
            }
            if (itemModId == null) {
                itemModId = rootModuleId;
            }

            // 必须严格属于根模块且目标表为主表或伴生维表，才作为外层直连 WHERE 条件
            boolean isRootTable =
                    targetTable.equalsIgnoreCase(primaryTable)
                            || companionTables.contains(targetTable);
            boolean isRootModule =
                    item.modulePath == null
                            || item.modulePath.isEmpty()
                            || (item.modulePath.size() == 1
                                    && Objects.equals(item.modulePath.get(0), rootModuleId));

            if (isRootModule && isRootTable) {
                // 主模块自身表或伴生平铺表：外层直连查询直接使用物理表名
                String effectiveAlias =
                        item.tableName != null && !item.tableName.isBlank()
                                ? item.tableName
                                : primaryTable;

                directConditions.add(
                        buildSingleCondition(
                                effectiveAlias,
                                item.fieldName,
                                item.searchType,
                                item.operator,
                                item.value));
            } else {
                // 属于子模块或更深层孙模块：基于 JoinPathResolver 严格解析路径
                List<JoinPathResolver.Step> path =
                        joinPathResolver.resolvePath(itemModId, item.tableName, completeResp);

                if (path.isEmpty()) {
                    // 若拓扑未直接连通，兜底以当前模块独立作用域
                    String fallbackBranchKey = "branch_" + itemModId;
                    branchGroups
                            .computeIfAbsent(fallbackBranchKey, k -> new BranchGroup())
                            .items
                            .add(new PathFilterItem(item, null));
                    continue;
                }

                // 支路融合核心：以从根节点出发的第一条 1:N 边（或直属第一跳从表）作为该支路的 branchKey
                JoinPathResolver.Step firstStep = path.get(0);
                String branchKey =
                        firstStep.getToModuleId() != null
                                ? String.valueOf(firstStep.getToModuleId())
                                : firstStep.getToTable().toLowerCase();

                BranchGroup group = branchGroups.computeIfAbsent(branchKey, k -> new BranchGroup());
                group.items.add(new PathFilterItem(item, path));
            }
        }

        conditions.addAll(directConditions);

        // 3. 为各支路构建融合后的单一 EXISTS 子查询 (Branch Merging)
        if (!branchGroups.isEmpty()) {
            for (Map.Entry<String, BranchGroup> entry : branchGroups.entrySet()) {
                Condition mergedExists =
                        buildMergedExistsCondition(
                                primaryTable,
                                rootModuleId,
                                entry.getValue(),
                                relations,
                                completeResp);
                if (mergedExists != null) {
                    conditions.add(mergedExists);
                }
            }
        }

        return conditions.isEmpty() ? DSL.noCondition() : DSL.and(conditions);
    }

    /** 单个支路分组包含的过滤条件项集合 */
    private static class BranchGroup {
        List<PathFilterItem> items = new ArrayList<>();
    }

    private static class PathFilterItem {
        FilterItem item;
        List<JoinPathResolver.Step> path;

        PathFilterItem(FilterItem item, List<JoinPathResolver.Step> path) {
            this.item = item;
            this.path = path;
        }
    }

    /** 支路融合算法：将属于同一分支的多级从表、孙表及伴生表条件融合为单一多表关联的 EXISTS 子查询 */
    private Condition buildMergedExistsCondition(
            String primaryTable,
            Long rootModuleId,
            BranchGroup group,
            List<TableRelationDTO> relations,
            SysModuleMetaResp completeResp) {

        if (group.items.isEmpty()) {
            return null;
        }

        // 提取该支路所有路径步骤并进行拓扑树合并
        List<JoinPathResolver.Step> longestPath = null;
        Map<String, JoinPathResolver.Step> allStepsMap = new LinkedHashMap<>();

        for (PathFilterItem pItem : group.items) {
            if (pItem.path != null && !pItem.path.isEmpty()) {
                if (longestPath == null || pItem.path.size() > longestPath.size()) {
                    longestPath = pItem.path;
                }
                for (JoinPathResolver.Step s : pItem.path) {
                    String stepKey =
                            s.getFromTable().toLowerCase() + "->" + s.getToTable().toLowerCase();
                    allStepsMap.putIfAbsent(stepKey, s);
                }
            }
        }

        // 若全部无法解析出路径，走兜底逻辑
        if (longestPath == null || longestPath.isEmpty()) {
            PathFilterItem first = group.items.get(0);
            return buildDirectFallbackExists(primaryTable, first.item, relations, rootModuleId);
        }

        // 1. 构建首个从表 FROM firstTable AS firstAlias
        JoinPathResolver.Step firstStep = longestPath.get(0);
        String firstRawTable = firstStep.getToTable();
        Long firstModId = firstStep.getToModuleId();
        String firstAlias = firstModId != null ? firstModId + "_" + firstRawTable : firstRawTable;

        Table<?> fromTable = DSL.table(DSL.name(firstRawTable)).as(DSL.name(firstAlias));

        // 根主表关联条件: firstAlias.fk = primaryTable.pk
        String firstFk = firstStep.getToJoinField();
        String rootPk = firstStep.getFromJoinField();
        Condition correlation =
                DSL.field(DSL.name(firstAlias, firstFk))
                        .eq(DSL.field(DSL.name(primaryTable, rootPk)))
                        .and(DSL.field(DSL.name(firstAlias, "deleted")).eq((byte) 0));

        // 2. 依次拓扑连入支路内的所有后续从表/孙表/伴生表 (JOIN nextTable AS nextAlias)
        Set<String> joinedTables = new HashSet<>();
        joinedTables.add(firstRawTable.toLowerCase());

        Map<String, String> tableToAliasMap = new HashMap<>();
        tableToAliasMap.put(firstRawTable.toLowerCase(), firstAlias);

        for (JoinPathResolver.Step step : allStepsMap.values()) {
            String toT = step.getToTable().toLowerCase();
            String fromT = step.getFromTable().toLowerCase();

            if (fromT.equalsIgnoreCase(primaryTable)) {
                continue; // 第一跳已由 correlation 关联
            }

            if (!joinedTables.contains(toT)) {
                String prevAlias = tableToAliasMap.get(fromT);
                if (prevAlias == null) {
                    prevAlias = firstAlias;
                }

                String nextRawTable = step.getToTable();
                Long nextModId = step.getToModuleId();
                String nextAlias =
                        nextModId != null ? nextModId + "_" + nextRawTable : nextRawTable;

                fromTable =
                        fromTable
                                .join(DSL.table(DSL.name(nextRawTable)).as(DSL.name(nextAlias)))
                                .on(
                                        DSL.field(DSL.name(nextAlias, step.getToJoinField()))
                                                .eq(
                                                        DSL.field(
                                                                DSL.name(
                                                                        prevAlias,
                                                                        step.getFromJoinField()))))
                                .and(DSL.field(DSL.name(nextAlias, "deleted")).eq((byte) 0));

                joinedTables.add(toT);
                tableToAliasMap.put(toT, nextAlias);
            }
        }

        // 3. 将该支路下的所有过滤条件作用到对应的表别名上
        Condition branchFilters = DSL.noCondition();
        for (PathFilterItem pItem : group.items) {
            FilterItem item = pItem.item;
            String targetT = item.tableName.toLowerCase();
            String targetAlias = tableToAliasMap.get(targetT);
            if (targetAlias == null) {
                targetAlias =
                        item.moduleId != null
                                ? item.moduleId + "_" + item.tableName
                                : item.tableName;
            }

            branchFilters =
                    branchFilters.and(
                            buildSingleCondition(
                                    targetAlias,
                                    item.fieldName,
                                    item.searchType,
                                    item.operator,
                                    item.value));
        }

        return DSL.exists(DSL.selectOne().from(fromTable).where(correlation).and(branchFilters));
    }

    /** 兜底单表直接外键关联 EXISTS */
    private Condition buildDirectFallbackExists(
            String primaryTable,
            FilterItem item,
            List<TableRelationDTO> relations,
            Long rootModuleId) {

        for (TableRelationDTO rel : relations) {
            if ("1:N".equalsIgnoreCase(rel.getRelationType())
                    && primaryTable.equalsIgnoreCase(rel.getMainTable())
                    && item.tableName.equalsIgnoreCase(rel.getJoinTable())) {

                String fkField = rel.getJoinField();
                String targetAlias =
                        item.moduleId != null
                                ? item.moduleId + "_" + item.tableName
                                : item.tableName;

                Condition correlation =
                        DSL.field(DSL.name(targetAlias, fkField))
                                .eq(DSL.field(DSL.name(primaryTable, rel.getMainField())))
                                .and(DSL.field(DSL.name(targetAlias, "deleted")).eq((byte) 0));

                Condition filterCond =
                        buildSingleCondition(
                                targetAlias,
                                item.fieldName,
                                item.searchType,
                                item.operator,
                                item.value);

                return DSL.exists(
                        DSL.selectOne()
                                .from(DSL.table(DSL.name(item.tableName)).as(DSL.name(targetAlias)))
                                .where(correlation)
                                .and(filterCond));
            }
        }
        return null;
    }

    /** 结构化单项过滤条件元数据 */
    private static class FilterItem {
        Long moduleId;
        List<Long> modulePath;
        String tableName;
        String fieldName;
        String searchType;
        String operator;
        Object value;

        FilterItem(
                Long moduleId,
                List<Long> modulePath,
                String tableName,
                String fieldName,
                String searchType,
                String operator,
                Object value) {
            this.moduleId = moduleId;
            this.modulePath = modulePath;
            this.tableName = tableName;
            this.fieldName = fieldName;
            this.searchType = searchType;
            this.operator = operator;
            this.value = value;
        }
    }

    /** 解析结构化 DynamicFilterItem 列表 */
    private List<FilterItem> parseStructuredFilterItems(
            String primaryTable,
            List<DynamicFilterItem> filterList,
            List<ModuleTableHeaderDTO> headers,
            SysModuleMetaResp completeResp) {

        List<FilterItem> items = new ArrayList<>();
        Map<String, ModuleFieldDTO> fieldMetaMap = new HashMap<>();
        if (completeResp != null && completeResp.getFields() != null) {
            for (ModuleFieldDTO f : completeResp.getFields()) {
                if (f.getColumnName() != null) {
                    String col = f.getColumnName().toLowerCase();
                    boolean isPrimaryTableField =
                            f.getTableName() != null
                                    && f.getTableName().equalsIgnoreCase(primaryTable);

                    if (!fieldMetaMap.containsKey(col) || isPrimaryTableField) {
                        fieldMetaMap.put(col, f);
                    }
                    if (f.getTableName() != null) {
                        fieldMetaMap.put(
                                (f.getTableName() + "." + f.getColumnName()).toLowerCase(), f);
                    }
                }
            }
        }

        Map<String, ModuleTableHeaderDTO> headerMetaMap = new HashMap<>();
        if (headers != null) {
            for (ModuleTableHeaderDTO h : headers) {
                if (h.getField() != null) {
                    headerMetaMap.put(h.getField().toLowerCase(), h);
                    if (h.getTable() != null) {
                        headerMetaMap.put((h.getTable() + "." + h.getField()).toLowerCase(), h);
                    }
                }
            }
        }

        for (DynamicFilterItem rawItem : filterList) {
            if (rawItem == null
                    || rawItem.getColumnName() == null
                    || rawItem.getValue() == null
                    || "".equals(rawItem.getValue())) {
                continue;
            }

            String tableName = rawItem.getTableName();
            String fieldName = rawItem.getColumnName();
            String searchType = "";
            List<Long> modulePath = rawItem.getModulePath();
            Long moduleId = rawItem.getModuleId();

            if (tableName == null
                    || tableName.isBlank()
                    || modulePath == null
                    || modulePath.isEmpty()) {
                ModuleTableHeaderDTO h =
                        headerMetaMap.get(
                                (tableName != null ? tableName + "." : "")
                                        + fieldName.toLowerCase());
                if (h == null) {
                    h = headerMetaMap.get(fieldName.toLowerCase());
                }
                if (h != null) {
                    if (tableName == null || tableName.isBlank()) {
                        tableName = h.getTable();
                    }
                    if (searchType.isBlank()) {
                        searchType = h.getSearchType() != null ? h.getSearchType() : "";
                    }
                    if (modulePath == null || modulePath.isEmpty()) {
                        modulePath = h.getModulePath();
                    }
                    if (h.getModulePath() != null && !h.getModulePath().isEmpty()) {
                        moduleId = h.getModulePath().get(h.getModulePath().size() - 1);
                    }
                } else {
                    ModuleFieldDTO f = fieldMetaMap.get(fieldName.toLowerCase());
                    if (f != null && f.getTableName() != null && !f.getTableName().isBlank()) {
                        tableName = f.getTableName();
                    } else if (tableName == null || tableName.isBlank()) {
                        tableName = primaryTable;
                    }
                }
            }

            if (searchType.isBlank()) {
                ModuleTableHeaderDTO h =
                        headerMetaMap.get((tableName + "." + fieldName).toLowerCase());
                if (h == null) {
                    h = headerMetaMap.get(fieldName.toLowerCase());
                }
                if (h != null && h.getSearchType() != null) {
                    searchType = h.getSearchType();
                }
            }

            items.add(
                    new FilterItem(
                            moduleId,
                            modulePath,
                            tableName,
                            fieldName,
                            searchType,
                            rawItem.getOperator(),
                            rawItem.getValue()));
        }
        return items;
    }

    /** 解析传统 Map 键值对过滤条件 */
    private List<FilterItem> parseFilterItems(
            String primaryTable,
            Map<String, Object> filters,
            List<ModuleTableHeaderDTO> headers,
            SysModuleMetaResp completeResp) {

        if (filters == null || filters.isEmpty()) {
            return Collections.emptyList();
        }

        List<DynamicFilterItem> filterList = new ArrayList<>();
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            if (val == null || "".equals(val)) {
                continue;
            }

            String tableName = null;
            String fieldName = key;
            if (key.contains(".")) {
                String[] parts = key.split("\\.", 2);
                tableName = parts[0];
                fieldName = parts[1];
            }

            filterList.add(
                    DynamicFilterItem.builder()
                            .tableName(tableName)
                            .columnName(fieldName)
                            .value(val)
                            .build());
        }

        return parseStructuredFilterItems(primaryTable, filterList, headers, completeResp);
    }

    /** 识别当前主表的 1:1 与 N:1 伴生平铺表 */
    private Set<String> getCompanionTables(String primaryTable, List<TableRelationDTO> relations) {
        Set<String> companion = new HashSet<>();
        for (TableRelationDTO rel : relations) {
            String mainT = rel.getMainTable() != null ? rel.getMainTable().toLowerCase() : "";
            String joinT = rel.getJoinTable() != null ? rel.getJoinTable().toLowerCase() : "";
            String rType = rel.getRelationType() != null ? rel.getRelationType() : "";

            if ("1:1".equalsIgnoreCase(rType)) {
                if (primaryTable.equalsIgnoreCase(mainT)) {
                    companion.add(joinT);
                } else if (primaryTable.equalsIgnoreCase(joinT)) {
                    companion.add(mainT);
                }
            } else if ("N:1".equalsIgnoreCase(rType)) {
                // 主表 (mainTable) 持有被关联维表 (joinTable) 的外键，joinTable 是伴生维表 (如 student -> clazz)
                if (primaryTable.equalsIgnoreCase(mainT)) {
                    companion.add(joinT);
                }
            } else if ("1:N".equalsIgnoreCase(rType)) {
                // 当前表是从表 (joinTable)，主表 (mainTable) 作为维表是伴生平铺表
                if (primaryTable.equalsIgnoreCase(joinT)) {
                    companion.add(mainT);
                }
            }
        }
        return companion;
    }

    /** 构建单字段匹配条件 (优先支持 searchType 自动推导，亦支持显式 operator 覆盖) */
    private Condition buildSingleCondition(
            String tableName, String fieldName, String searchType, String operator, Object value) {

        // 1. 若显式指定了 operator，优先按照 operator 执行
        if (operator != null && !operator.isBlank()) {
            String op = operator.trim().toUpperCase();
            switch (op) {
                case "NEQ", "!=" -> {
                    return DSL.field(DSL.name(tableName, fieldName)).ne(value);
                }
                case "LIKE" -> {
                    String pattern =
                            value.toString().contains("%") ? value.toString() : "%" + value + "%";
                    return DSL.field(DSL.name(tableName, fieldName), String.class).like(pattern);
                }
                case "GT", ">" -> {
                    return DSL.field(DSL.name(tableName, fieldName)).gt(value);
                }
                case "GTE", ">=" -> {
                    return DSL.field(DSL.name(tableName, fieldName)).ge(value);
                }
                case "LT", "<" -> {
                    return DSL.field(DSL.name(tableName, fieldName)).lt(value);
                }
                case "LTE", "<=" -> {
                    return DSL.field(DSL.name(tableName, fieldName)).le(value);
                }
                case "IS_NULL" -> {
                    return DSL.field(DSL.name(tableName, fieldName)).isNull();
                }
                case "IS_NOT_NULL" -> {
                    return DSL.field(DSL.name(tableName, fieldName)).isNotNull();
                }
                case "IN" -> {
                    if (value instanceof Collection<?> coll) {
                        return DSL.field(DSL.name(tableName, fieldName)).in(coll);
                    }
                }
                case "BETWEEN" -> {
                    if (value instanceof List<?> listVal && listVal.size() == 2) {
                        return DSL.field(DSL.name(tableName, fieldName))
                                .between(listVal.get(0), listVal.get(1));
                    }
                }
                case "EQ", "=" -> {
                    return DSL.field(DSL.name(tableName, fieldName)).eq(value);
                }
            }
        }

        // 2. 默认依据表头配置的 searchType 自动推导
        if ("singleFuzzySelect".equalsIgnoreCase(searchType)
                || "text".equalsIgnoreCase(searchType)
                || (value instanceof String strVal
                        && (strVal.startsWith("%") || strVal.endsWith("%")))) {
            String pattern = value.toString().contains("%") ? value.toString() : "%" + value + "%";
            return DSL.field(DSL.name(tableName, fieldName), String.class).like(pattern);
        } else if (value instanceof List<?> listVal) {
            if (listVal.size() == 2
                    && ("dateRange".equalsIgnoreCase(searchType)
                            || "between".equalsIgnoreCase(searchType))) {
                return DSL.field(DSL.name(tableName, fieldName))
                        .between(listVal.get(0), listVal.get(1));
            } else if (!listVal.isEmpty()) {
                return DSL.field(DSL.name(tableName, fieldName)).in(listVal);
            }
        }

        return DSL.field(DSL.name(tableName, fieldName)).eq(value);
    }
}
