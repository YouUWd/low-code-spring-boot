package com.jdec.platform.data.biz.service;

import com.jdec.platform.config.api.dto.common.ModuleTableDTO;
import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp;
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

    private final MetadataCacheService metadataCacheService;
    private final PermissionFilterService permissionFilterService;
    private final JooqContextFactory jooqContextFactory;

    /** 保存单模块主子表同构物理记录 */
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Long save(DynamicSaveReq req) {
        Long moduleId = req.getModuleId();
        SysModuleCompleteResp completeResp = metadataCacheService.getModuleComplete(moduleId);
        if (completeResp == null || completeResp.getModule() == null) {
            throw new IllegalArgumentException("模块 ID [" + moduleId + "] 不存在或未配置元数据");
        }

        List<ModuleTableDTO> moduleTables = completeResp.getModuleTables();
        ModuleTableDTO primaryTableDto =
                moduleTables != null
                        ? moduleTables.stream()
                                .filter(t -> t.getIsPrimary() != null && t.getIsPrimary() == 1)
                                .findFirst()
                                .orElse(moduleTables.isEmpty() ? null : moduleTables.get(0))
                        : null;

        if (primaryTableDto == null) {
            throw new IllegalArgumentException("模块未配置主表信息");
        }
        String primaryTable = primaryTableDto.getTableName();
        Map<String, Object> tables = req.getTables();

        if (tables == null || !tables.containsKey(primaryTable)) {
            throw new IllegalArgumentException("待保存数据中必须包含主表 [" + primaryTable + "] 的数据");
        }

        DSLContext dsl = jooqContextFactory.getContext();
        Object primaryObj = tables.get(primaryTable);
        if (primaryObj instanceof List<?> list) {
            // 支持批量多行主表保存
            Long lastId = null;
            for (Object itemObj : list) {
                if (itemObj instanceof Map<?, ?> itemMap) {
                    Map<String, Object> singleRow = (Map<String, Object>) itemMap;
                    lastId = saveSingleRecord(dsl, primaryTable, singleRow, completeResp);
                }
            }
            return lastId;
        }

        if (!(primaryObj instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("主表 [" + primaryTable + "] 数据格式错误，应为 Map 或 List");
        }
        Map<String, Object> primaryRecord = (Map<String, Object>) primaryObj;

        // 1. 保存/更新主表数据
        Long primaryId = saveSingleRecord(dsl, primaryTable, primaryRecord, completeResp);

        // 2. 级联保存各关联从表
        if (completeResp.getModuleTables() != null) {
            for (ModuleTableDTO tableDto : completeResp.getModuleTables()) {
                String tableName = tableDto.getTableName();
                if (tableDto.getIsPrimary() != null && tableDto.getIsPrimary() == 1) {
                    continue;
                }
                if (!tables.containsKey(tableName)) {
                    continue;
                }

                Object tableDataObj = tables.get(tableName);
                String relationType = tableDto.getRelationType();
                String fkField =
                        tableDto.getJoinRightField() != null
                                        && !tableDto.getJoinRightField().isBlank()
                                ? tableDto.getJoinRightField()
                                : tableDto.getJoinLeftField();

                if ("1:N".equalsIgnoreCase(relationType)
                        || "ONE_TO_MANY".equalsIgnoreCase(relationType)) {
                    // 1:N 从表多行批量维护
                    if (tableDataObj instanceof List<?> recordsList) {
                        for (Object itemObj : recordsList) {
                            if (itemObj instanceof Map<?, ?> itemMap) {
                                Map<String, Object> item =
                                        new HashMap<>((Map<String, Object>) itemMap);
                                if (primaryId != null && fkField != null && !fkField.isBlank()) {
                                    item.put(fkField, primaryId);
                                }
                                saveSingleRecord(dsl, tableName, item, completeResp);
                            }
                        }
                    }
                } else {
                    // 1:1 或 N:1 从表单行维护
                    if (tableDataObj instanceof Map<?, ?> relMap) {
                        Map<String, Object> relRecord = new HashMap<>((Map<String, Object>) relMap);
                        if (!relRecord.isEmpty()) {
                            if (primaryId != null && fkField != null && !fkField.isBlank()) {
                                relRecord.put(fkField, primaryId);
                            }
                            saveSingleRecord(dsl, tableName, relRecord, completeResp);
                        }
                    }
                }
            }
        }

        log.info(
                "动态主子表保存成功: moduleId={}, primaryTable={}, primaryId={}",
                moduleId,
                primaryTable,
                primaryId);
        return primaryId;
    }

    /** 多模块同构原子批量保存 (主子模块跨表原子事务落库，自动外键传播与强一致性保证) */
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public BatchSaveResp batchSave(BatchDynamicSaveReq req) {
        if (req == null) {
            throw new IllegalArgumentException("批量保存请求不能为空");
        }

        Long masterId = null;
        if (req.getMaster() != null) {
            masterId = save(req.getMaster());
        }

        Map<String, Object> childResults = new LinkedHashMap<>();
        if (req.getChildren() != null && !req.getChildren().isEmpty()) {
            for (Map.Entry<String, DynamicSaveReq> entry : req.getChildren().entrySet()) {
                String alias = entry.getKey();
                DynamicSaveReq childReq = entry.getValue();

                if (childReq == null) {
                    continue;
                }

                // 若子模块数据中缺失外键，且 master 已生成 masterId，自动向从表注入外键
                if (masterId != null && childReq.getTables() != null) {
                    SysModuleCompleteResp childComplete =
                            metadataCacheService.getModuleComplete(childReq.getModuleId());
                    if (childComplete != null && childComplete.getModuleTables() != null) {
                        for (ModuleTableDTO childTableDto : childComplete.getModuleTables()) {
                            String cTableName = childTableDto.getTableName();
                            if (childReq.getTables().containsKey(cTableName)) {
                                Object cData = childReq.getTables().get(cTableName);
                                String fk =
                                        childTableDto.getJoinLeftField() != null
                                                ? childTableDto.getJoinLeftField()
                                                : childTableDto.getJoinRightField();
                                if (fk != null && !fk.isBlank()) {
                                    if (cData instanceof List<?> cList) {
                                        for (Object o : cList) {
                                            if (o instanceof Map<?, ?> m) {
                                                ((Map<String, Object>) m).putIfAbsent(fk, masterId);
                                            }
                                        }
                                    } else if (cData instanceof Map<?, ?> m) {
                                        ((Map<String, Object>) m).putIfAbsent(fk, masterId);
                                    }
                                }
                            }
                        }
                    }
                }

                Long childId = save(childReq);
                childResults.put(alias, childId != null ? childId : "SUCCESS");
            }
        }

        log.info("多模块同构批量原子保存完成: masterId={}, childrenCount={}", masterId, childResults.size());
        return BatchSaveResp.of(masterId, childResults);
    }

    /** 维护单行数据 (有 ID 则 Update，无 ID 则 Insert，内置基于元数据白名单的安全列过滤) */
    private Long saveSingleRecord(
            DSLContext dsl,
            String tableName,
            Map<String, Object> record,
            SysModuleCompleteResp completeResp) {
        if (record == null || record.isEmpty()) {
            return null;
        }

        // 自动补充主体信息与审计信息
        if (AppContext.getSubjectId() != null
                && AppContext.getSubjectId() > 0
                && !record.containsKey("subject_id")) {
            record.put("subject_id", AppContext.getSubjectId());
        }
        if (AppContext.getProjectNo() != null && !record.containsKey("project_no")) {
            record.put("project_no", AppContext.getProjectNo());
        }

        Object idObj = record.get("id");
        Long id = idObj instanceof Number num ? num.longValue() : null;

        // 获取属于当前表 tableName 的合法列名白名单 (包含配置的业务列与关联外键列)
        Set<String> validColumns = null;
        if (completeResp != null) {
            validColumns = new HashSet<>();
            if (completeResp.getSimpleFields() != null) {
                completeResp.getSimpleFields().stream()
                        .filter(f -> tableName.equalsIgnoreCase(f.getTableName()))
                        .map(f -> f.getColumnName().toLowerCase())
                        .forEach(validColumns::add);
            }
            if (completeResp.getModuleTables() != null) {
                for (ModuleTableDTO tDto : completeResp.getModuleTables()) {
                    if (tableName.equalsIgnoreCase(tDto.getTableName())) {
                        if (tDto.getJoinLeftField() != null && !tDto.getJoinLeftField().isBlank()) {
                            validColumns.add(tDto.getJoinLeftField().toLowerCase());
                        }
                        if (tDto.getJoinRightField() != null
                                && !tDto.getJoinRightField().isBlank()) {
                            validColumns.add(tDto.getJoinRightField().toLowerCase());
                        }
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

            // 白名单过滤: 若有元数据定义，仅允许属于本表的字段与通用标准审计列写入
            if (validColumns != null && !validColumns.isEmpty()) {
                String lowerCol = colName.toLowerCase();
                if (!validColumns.contains(lowerCol)
                        && !"subject_id".equals(lowerCol)
                        && !"project_no".equals(lowerCol)
                        && !"created_by".equals(lowerCol)
                        && !"created_date".equals(lowerCol)
                        && !"updated_by".equals(lowerCol)
                        && !"updated_date".equals(lowerCol)
                        && !"deleted".equals(lowerCol)) {
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
}
