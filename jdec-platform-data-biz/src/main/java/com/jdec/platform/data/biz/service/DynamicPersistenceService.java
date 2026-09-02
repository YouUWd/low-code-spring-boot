package com.jdec.platform.data.biz.service;

import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import com.jdec.platform.config.api.dto.response.SysModuleMetaResp;
import com.jdec.platform.data.api.dto.request.BatchDynamicSaveReq;
import com.jdec.platform.data.api.dto.request.DynamicSaveReq;
import com.jdec.platform.data.api.dto.response.BatchSaveResp;
import com.jdec.platform.data.biz.dsl.JooqContextFactory;
import com.jdec.platform.shared.context.AppContext;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 动态主子表物理持久化服务 (支持单模块同构保存与多模块原子批量保存) */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicPersistenceService {

    /**
     * 全局架构约定：平台级 DDL 审计列集合。
     *
     * <p>这些列由平台统一维护，不在 fields 中配置，也不对前端暴露，但需要随业务数据一起落库，白名单过滤时对此类列予以放通。
     *
     * <p>注意：subject_id / project_no 是业务字段，通过 fields 元数据管控，不在此集合中。
     */
    private static final Set<String> SYSTEM_AUDIT_COLUMNS =
            Set.of("created_by", "created_date", "updated_by", "updated_date", "deleted");

    private final MetadataCacheService metadataCacheService;
    private final PermissionFilterService permissionFilterService;
    private final JooqContextFactory jooqContextFactory;

    /** 保存单模块主子表同构物理记录 */
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Long save(DynamicSaveReq req) {
        Long moduleId = req.getModuleId();
        SysModuleMetaResp completeResp = metadataCacheService.getModuleComplete(moduleId);
        if (completeResp == null || completeResp.getModule() == null) {
            throw new IllegalArgumentException("模块 ID [" + moduleId + "] 不存在或未配置元数据");
        }

        String primaryTable = getPrimaryTableName(completeResp);
        if (primaryTable == null || primaryTable.isBlank()) {
            throw new IllegalArgumentException("模块未配置主表或字段信息");
        }

        List<Map<String, Object>> recordsToSave = req.getEffectiveRecords();
        if (recordsToSave.isEmpty()) {
            throw new IllegalArgumentException("待保存数据中必须包含有效数据 (record / records / tables)");
        }

        DSLContext dsl = jooqContextFactory.getContext();
        Long lastPrimaryId = null;

        for (Map<String, Object> recordMap : recordsToSave) {
            Long currentPrimaryId =
                    saveSingleRecordAggregate(dsl, primaryTable, recordMap, completeResp);
            if (currentPrimaryId != null) {
                lastPrimaryId = currentPrimaryId;
            }
        }

        log.info(
                "动态模块持久化保存成功: moduleId={}, primaryTable={}, primaryId={}",
                moduleId,
                primaryTable,
                lastPrimaryId);
        return lastPrimaryId;
    }

    /** 保存单个业务实体的完整主从聚合数据集 (主表及所属关联从表) */
    private Long saveSingleRecordAggregate(
            DSLContext dsl,
            String primaryTable,
            Map<String, Object> recordMap,
            SysModuleMetaResp completeResp) {
        if (recordMap == null || recordMap.isEmpty()) {
            return null;
        }

        Map<String, Object> currentRecord = new HashMap<>(recordMap);
        Object primaryObj = currentRecord.get(primaryTable);

        if (primaryObj == null && currentRecord.containsKey("id")) {
            // 如果传入的直接就是主表平铺字段
            primaryObj = currentRecord;
        }

        Long primaryId = null;
        if (primaryObj instanceof Map<?, ?> primaryMap) {
            Map<String, Object> singlePrimaryRow = new HashMap<>((Map<String, Object>) primaryMap);
            // 提取主表行内部挂载的从表 (如 row.score_items 或 row.student_course_score_item)
            Map<String, List<Map<String, Object>>> nestedSubTables =
                    extractNestedSubTables(singlePrimaryRow, completeResp);

            primaryId = saveSingleRecord(dsl, primaryTable, singlePrimaryRow, completeResp);

            // 级联落库内嵌从表
            saveNestedSubTables(dsl, primaryTable, primaryId, nestedSubTables, completeResp);
        } else if (primaryObj instanceof List<?> primaryList) {
            for (Object itemObj : primaryList) {
                if (itemObj instanceof Map<?, ?> itemMap) {
                    Map<String, Object> singleRow = new HashMap<>((Map<String, Object>) itemMap);
                    Map<String, List<Map<String, Object>>> nestedSubTables =
                            extractNestedSubTables(singleRow, completeResp);
                    Long rowId = saveSingleRecord(dsl, primaryTable, singleRow, completeResp);
                    if (primaryId == null) {
                        primaryId = rowId;
                    }
                    saveNestedSubTables(dsl, primaryTable, rowId, nestedSubTables, completeResp);
                }
            }
        }

        // 级联保存同级声明的关联从表 (即 recordMap 中与 primaryTable 同级的从表 Key)
        if (completeResp.getTableRelations() != null) {
            for (TableRelationDTO rel : completeResp.getTableRelations()) {
                if (!primaryTable.equalsIgnoreCase(rel.getMainTable())) {
                    continue;
                }
                String tableName = rel.getJoinTable();
                if (!currentRecord.containsKey(tableName)) {
                    continue;
                }

                Object tableDataObj = currentRecord.get(tableName);
                String relationType = rel.getRelationType();
                String fkField = rel.getJoinField();

                if ("1:N".equalsIgnoreCase(relationType)
                        || "ONE_TO_MANY".equalsIgnoreCase(relationType)) {
                    if (tableDataObj instanceof List<?> recordsList) {
                        for (Object itemObj : recordsList) {
                            if (itemObj instanceof Map<?, ?> itemMap) {
                                Map<String, Object> item =
                                        new HashMap<>((Map<String, Object>) itemMap);
                                if (primaryId != null
                                        && fkField != null
                                        && !fkField.isBlank()
                                        && !"id".equalsIgnoreCase(fkField)) {
                                    item.put(fkField, primaryId);
                                }
                                saveSingleRecord(dsl, tableName, item, completeResp);
                            }
                        }
                    }
                } else {
                    if (tableDataObj instanceof Map<?, ?> relMap) {
                        Map<String, Object> relRecord = new HashMap<>((Map<String, Object>) relMap);
                        if (!relRecord.isEmpty()) {
                            if (primaryId != null
                                    && fkField != null
                                    && !fkField.isBlank()
                                    && !"id".equalsIgnoreCase(fkField)) {
                                relRecord.put(fkField, primaryId);
                            }
                            saveSingleRecord(dsl, tableName, relRecord, completeResp);
                        }
                    }
                }
            }
        }

        return primaryId;
    }

    /** 提取主表行内部挂载的从表 (严格依据关系配置中的 joinTable 物理表名提取) */
    private Map<String, List<Map<String, Object>>> extractNestedSubTables(
            Map<String, Object> singleRow, SysModuleMetaResp completeResp) {
        Map<String, List<Map<String, Object>>> nestedSubTables = new LinkedHashMap<>();
        if (completeResp == null || completeResp.getTableRelations() == null) {
            return nestedSubTables;
        }

        for (TableRelationDTO rel : completeResp.getTableRelations()) {
            String tName = rel.getJoinTable();
            if (tName == null) {
                continue;
            }

            // 严格依据元数据定义的物理从表名提取
            Object subData = singleRow.remove(tName);

            if (subData instanceof List<?> subList) {
                List<Map<String, Object>> cleanSubList = new ArrayList<>();
                for (Object so : subList) {
                    if (so instanceof Map<?, ?> sm) {
                        cleanSubList.add(new HashMap<>((Map<String, Object>) sm));
                    }
                }
                nestedSubTables.put(tName, cleanSubList);
            }
        }
        return nestedSubTables;
    }

    /** 级联落库内嵌从表记录 */
    private void saveNestedSubTables(
            DSLContext dsl,
            String primaryTable,
            Long primaryId,
            Map<String, List<Map<String, Object>>> nestedSubTables,
            SysModuleMetaResp completeResp) {
        if (nestedSubTables == null || nestedSubTables.isEmpty()) {
            return;
        }

        for (Map.Entry<String, List<Map<String, Object>>> entry : nestedSubTables.entrySet()) {
            String childTable = entry.getKey();
            List<Map<String, Object>> childRows = entry.getValue();

            TableRelationDTO childRelation =
                    completeResp.getTableRelations() != null
                            ? completeResp.getTableRelations().stream()
                                    .filter(r -> childTable.equalsIgnoreCase(r.getJoinTable()))
                                    .findFirst()
                                    .orElse(null)
                            : null;
            String childFk = childRelation != null ? childRelation.getJoinField() : null;

            for (Map<String, Object> childRow : childRows) {
                if (primaryId != null
                        && childFk != null
                        && !childFk.isBlank()
                        && !"id".equalsIgnoreCase(childFk)) {
                    childRow.put(childFk, primaryId);
                }
                saveSingleRecord(dsl, childTable, childRow, completeResp);
            }
        }
    }

    /** 多模块原子批量保存 (基于物理主外键 DAG 拓扑排序与单一归属校验，单事务强一致性) */
    @Transactional(rollbackFor = Exception.class)
    public BatchSaveResp batchSave(BatchDynamicSaveReq req) {
        if (req == null || req.getModules() == null || req.getModules().isEmpty()) {
            return BatchSaveResp.of(Collections.emptyMap());
        }

        List<DynamicSaveReq> rawModules =
                req.getModules().stream()
                        .filter(Objects::nonNull)
                        .filter(m -> m.getModuleId() != null)
                        .toList();

        if (rawModules.isEmpty()) {
            return BatchSaveResp.of(Collections.emptyMap());
        }

        // 1. 前置校验: 检查是否存在跨模块重复提交相同数据表的冲突 (遵循 Single Source of Truth 原则)
        validateNoCrossModuleDuplicateTables(rawModules);

        // 2. 基于各模块数据表的物理主外键依赖关系构建 DAG 并执行拓扑排序
        List<DynamicSaveReq> sortedModules = sortModulesByTableDependencies(rawModules);

        // 3. 依次按元数据拓扑顺序落库，并建立各主表生成的物理主键上下文
        Map<String, Long> tableGeneratedIdMap = new HashMap<>();
        Map<Long, Object> results = new LinkedHashMap<>();

        for (DynamicSaveReq modReq : sortedModules) {
            Long modId = modReq.getModuleId();
            SysModuleMetaResp completeResp = metadataCacheService.getModuleComplete(modId);

            // 若当前模块中的从表依赖前序模块主表生成的主键，自动注入外键
            if (completeResp != null && completeResp.getTableRelations() != null) {
                for (Map<String, Object> rec : modReq.getEffectiveRecords()) {
                    for (TableRelationDTO rel : completeResp.getTableRelations()) {
                        String tName = rel.getJoinTable();
                        if (!rec.containsKey(tName)) {
                            continue;
                        }

                        String fk = rel.getJoinField();
                        String mainTable = rel.getMainTable();

                        if (fk != null && !fk.isBlank() && mainTable != null) {
                            Long referencedId = tableGeneratedIdMap.get(mainTable.toLowerCase());

                            if (referencedId != null) {
                                Object tData = rec.get(tName);
                                if (tData instanceof List<?> list) {
                                    List<Map<String, Object>> mutableList = new ArrayList<>();
                                    for (Object o : list) {
                                        if (o instanceof Map<?, ?> m) {
                                            Map<String, Object> mutableMap =
                                                    new HashMap<>((Map<String, Object>) m);
                                            mutableMap.putIfAbsent(fk, referencedId);
                                            mutableList.add(mutableMap);
                                        }
                                    }
                                    rec.put(tName, mutableList);
                                } else if (tData instanceof Map<?, ?> m) {
                                    Map<String, Object> mutableMap =
                                            new HashMap<>((Map<String, Object>) m);
                                    mutableMap.putIfAbsent(fk, referencedId);
                                    rec.put(tName, mutableMap);
                                }
                            }
                        }
                    }
                }
            }

            Long savedId = save(modReq);
            results.put(modId, savedId != null ? savedId : "SUCCESS");

            // 记录主表落库生成的主键
            String primaryTable = getPrimaryTableName(completeResp);
            if (primaryTable != null && !primaryTable.isBlank() && savedId != null) {
                tableGeneratedIdMap.put(primaryTable, savedId);
            }
        }

        log.info("多模块物理 DAG 拓扑批量原子保存完成: count={}, results={}", sortedModules.size(), results);
        return BatchSaveResp.of(results);
    }

    /** 校验是否存在不同模块重复提交同一张物理表的情况，防止并发覆盖与字段冲突 */
    private void validateNoCrossModuleDuplicateTables(List<DynamicSaveReq> modules) {
        Map<String, Long> tableOwnerModuleMap = new HashMap<>();

        for (DynamicSaveReq modReq : modules) {
            Long modId = modReq.getModuleId();
            List<Map<String, Object>> recs = modReq.getEffectiveRecords();
            if (recs.isEmpty()) {
                continue;
            }

            for (Map<String, Object> rec : recs) {
                for (String tableName : rec.keySet()) {
                    String lowerTable = tableName.toLowerCase();
                    if (tableOwnerModuleMap.containsKey(lowerTable)) {
                        Long existingModId = tableOwnerModuleMap.get(lowerTable);
                        if (!existingModId.equals(modId)) {
                            throw new IllegalArgumentException(
                                    String.format(
                                            "批量保存校验失败: 数据表 [%s] 同时在模块 [%d] 和模块 [%d] 中重复提交！请保持数据表的单一模块归属划分。",
                                            tableName, existingModId, modId));
                        }
                    } else {
                        tableOwnerModuleMap.put(lowerTable, modId);
                    }
                }
            }
        }
    }

    /** 基于各模块数据表的物理主外键关系，构建 DAG 有向图并执行卡恩 (Kahn) 拓扑排序 */
    private List<DynamicSaveReq> sortModulesByTableDependencies(List<DynamicSaveReq> rawModules) {
        if (rawModules.size() <= 1) {
            return rawModules;
        }

        int n = rawModules.size();
        // 1. 收集各模块的主表表名与所涉及的全部物理表
        Map<Long, String> modulePrimaryTableMap = new HashMap<>();
        Map<Long, Set<String>> moduleAllTablesMap = new HashMap<>();
        Map<Long, List<TableRelationDTO>> moduleRelationsMap = new HashMap<>();

        for (DynamicSaveReq req : rawModules) {
            Long modId = req.getModuleId();
            SysModuleMetaResp resp = metadataCacheService.getModuleComplete(modId);
            if (resp != null) {
                String pTable = getPrimaryTableName(resp);
                if (pTable != null && !pTable.isBlank()) {
                    modulePrimaryTableMap.put(modId, pTable.toLowerCase());
                }
                Set<String> allTables = new HashSet<>();
                if (pTable != null) {
                    allTables.add(pTable.toLowerCase());
                }
                if (resp.getFields() != null) {
                    resp.getFields().stream()
                            .map(ModuleFieldDTO::getTableName)
                            .filter(t -> t != null && !t.isBlank())
                            .map(String::toLowerCase)
                            .forEach(allTables::add);
                }
                moduleAllTablesMap.put(modId, allTables);
                moduleRelationsMap.put(
                        modId,
                        resp.getTableRelations() != null
                                ? resp.getTableRelations()
                                : Collections.emptyList());
            }
        }

        // 2. 构建依赖有向图与入度统计 (source -> target，即 target 依赖 source 先保存并生成主键)
        Map<Integer, List<Integer>> adj = new HashMap<>();
        int[] inDegree = new int[n];

        for (int targetIdx = 0; targetIdx < n; targetIdx++) {
            DynamicSaveReq targetReq = rawModules.get(targetIdx);
            Long targetModId = targetReq.getModuleId();
            String targetPrimaryTable = modulePrimaryTableMap.get(targetModId);
            Set<String> targetInvolvedTables =
                    moduleAllTablesMap.getOrDefault(targetModId, Collections.emptySet());
            List<TableRelationDTO> targetRelations =
                    moduleRelationsMap.getOrDefault(targetModId, Collections.emptyList());

            for (int sourceIdx = 0; sourceIdx < n; sourceIdx++) {
                if (sourceIdx == targetIdx) {
                    continue;
                }

                DynamicSaveReq sourceReq = rawModules.get(sourceIdx);
                Long sourceModId = sourceReq.getModuleId();
                if (sourceModId.equals(targetModId)) {
                    continue;
                }

                String sourcePrimaryTable = modulePrimaryTableMap.get(sourceModId);
                if (sourcePrimaryTable == null || sourcePrimaryTable.isBlank()) {
                    continue;
                }

                // 统一单向规则: mainTable 是提供主键的主表，joinTable 是持有外键的从表
                // target 依赖 source 的唯一条件: source 主表为 mainTable，且 target 包含持有外键的 joinTable
                boolean isDependent = false;
                for (TableRelationDTO rel : targetRelations) {
                    String mainT =
                            rel.getMainTable() != null ? rel.getMainTable().toLowerCase() : "";
                    String joinT =
                            rel.getJoinTable() != null ? rel.getJoinTable().toLowerCase() : "";

                    if (sourcePrimaryTable.equals(mainT) && targetInvolvedTables.contains(joinT)) {
                        isDependent = true;
                        break;
                    }
                }

                if (isDependent) {
                    adj.computeIfAbsent(sourceIdx, k -> new ArrayList<>()).add(targetIdx);
                    inDegree[targetIdx]++;
                }
            }
        }

        // 3. Kahn 拓扑排序
        Queue<Integer> queue = new LinkedList<>();
        for (int i = 0; i < n; i++) {
            if (inDegree[i] == 0) {
                queue.offer(i);
            }
        }

        List<DynamicSaveReq> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            int curr = queue.poll();
            sorted.add(rawModules.get(curr));

            for (int neighbor : adj.getOrDefault(curr, Collections.emptyList())) {
                inDegree[neighbor]--;
                if (inDegree[neighbor] == 0) {
                    queue.offer(neighbor);
                }
            }
        }

        // 4. 循环依赖检测：若未能完全排序，则降级按原始顺序执行（保证容错与通用性）
        if (sorted.size() != n) {
            log.warn("多模块拓扑排序未完全解环，按原提交顺序兜底执行");
            return rawModules;
        }

        return sorted;
    }

    /** 维护单行数据 (有 ID 则 Update，无 ID 则 Insert，内置基于元数据白名单的安全列过滤) */
    private Long saveSingleRecord(
            DSLContext dsl,
            String tableName,
            Map<String, Object> record,
            SysModuleMetaResp completeResp) {
        if (record == null || record.isEmpty()) {
            return null;
        }

        // 自动补充主体信息：仅当该物理表在模块字段配置中声明了 subject_id / project_no 列时才注入
        // （根据 fields 元数据严格校验，不对未声明的表做隐式写入）
        Set<String> declaredColumnsForTable = new HashSet<>();
        if (completeResp != null && completeResp.getFields() != null) {
            completeResp.getFields().stream()
                    .filter(f -> tableName.equalsIgnoreCase(f.getTableName()))
                    .map(f -> f.getColumnName() != null ? f.getColumnName().toLowerCase() : "")
                    .forEach(declaredColumnsForTable::add);
        }

        if (AppContext.getSubjectId() != null
                && AppContext.getSubjectId() > 0
                && !record.containsKey("subject_id")
                && declaredColumnsForTable.contains("subject_id")) {
            record.put("subject_id", AppContext.getSubjectId());
        }
        if (AppContext.getProjectNo() != null
                && !record.containsKey("project_no")
                && declaredColumnsForTable.contains("project_no")) {
            record.put("project_no", AppContext.getProjectNo());
        }

        Object idObj = record.get("id");
        Long id = idObj instanceof Number num ? num.longValue() : null;

        // 获取属于当前表 tableName 的合法列名白名单 (包含配置的业务列与关联外键列)
        Set<String> validColumns = null;
        if (completeResp != null) {
            validColumns = new HashSet<>();
            if (completeResp.getFields() != null) {
                completeResp.getFields().stream()
                        .filter(f -> tableName.equalsIgnoreCase(f.getTableName()))
                        .map(f -> f.getColumnName().toLowerCase())
                        .forEach(validColumns::add);
            }
            if (completeResp.getTableRelations() != null) {
                for (TableRelationDTO rel : completeResp.getTableRelations()) {
                    if (tableName.equalsIgnoreCase(rel.getMainTable())
                            && rel.getMainField() != null) {
                        validColumns.add(rel.getMainField().toLowerCase());
                    }
                    if (tableName.equalsIgnoreCase(rel.getJoinTable())
                            && rel.getJoinField() != null) {
                        validColumns.add(rel.getJoinField().toLowerCase());
                    }
                }
            }
        }

        Map<Field<Object>, Object> fieldValues = new HashMap<>();
        List<String> validFieldNames = new ArrayList<>();

        for (Map.Entry<String, Object> entry : record.entrySet()) {
            String colName = entry.getKey();
            if ("id".equalsIgnoreCase(colName)) {
                continue;
            }

            // 白名单过滤: 若有元数据定义，仅允许属于本表的配置字段或全局审计列写入
            if (validColumns != null && !validColumns.isEmpty()) {
                String lowerCol = colName.toLowerCase();
                if (!validColumns.contains(lowerCol) && !SYSTEM_AUDIT_COLUMNS.contains(lowerCol)) {
                    continue;
                }
            }

            validFieldNames.add(colName);
            fieldValues.put(DSL.field(DSL.name(colName)), entry.getValue());
        }

        // 执行写入权限校验
        if (completeResp != null && completeResp.getModule() != null) {
            permissionFilterService.validateWritableFields(
                    completeResp.getModule().getId(), tableName, validFieldNames);
        }

        if (id != null && id > 0) {
            // Update
            if (!fieldValues.isEmpty()) {
                dsl.update(DSL.table(DSL.name(tableName)))
                        .set(fieldValues)
                        .where(DSL.field(DSL.name("id")).eq(id))
                        .execute();
            }
            return id;
        } else {
            // Insert 并获取自增主键
            var recordResult =
                    dsl.insertInto(DSL.table(DSL.name(tableName)))
                            .set(fieldValues)
                            .returningResult(DSL.field(DSL.name("id"), Long.class))
                            .fetchOne();

            if (recordResult != null && recordResult.value1() != null) {
                return recordResult.value1();
            }
            return id;
        }
    }

    private String getPrimaryTableName(SysModuleMetaResp completeResp) {
        if (completeResp == null
                || completeResp.getFields() == null
                || completeResp.getFields().isEmpty()) {
            return "";
        }
        return completeResp.getFields().get(0).getTableName();
    }
}
