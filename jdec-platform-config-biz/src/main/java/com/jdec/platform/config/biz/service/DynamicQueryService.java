package com.jdec.platform.config.biz.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp;
import com.jdec.platform.shared.model.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.*;
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.*;

/**
 * 动态查询服务
 * 使用 jOOQ 构建类型安全的 SQL 查询，避免 SQL 注入
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicQueryService {

    private final DataSource dataSource;
    
    // 默认配置常量
    private static final String DEFAULT_PRIMARY_KEY = "id";
    private static final String DEFAULT_SORT_DIRECTION = "DESC";
    private static final String SORT_DIRECTION_ASC = "ASC";
    private static final String SORT_DIRECTION_DESC = "DESC";

    /**
     * 根据模块配置执行动态查询
     *
     * @param moduleConfig 模块完整配置
     * @param page 页码（从1开始）
     * @param pageSize 每页大小
     * @param orderBy 排序字段，格式：字段名,排序方向（例如：id,desc）
     * @param filters 过滤条件
     * @return 分页查询结果
     */
    public PageResult<Map<String, Object>> executeQuery(SysModuleCompleteResp moduleConfig, 
                                                         Integer page, 
                                                         Integer pageSize,
                                                         String orderBy,
                                                         Map<String, Object> filters) {
        
        DSLContext dsl = DSL.using(dataSource, SQLDialect.MYSQL);
        
        log.info("========== 动态查询开始 ==========");
        log.info("模块: {}", moduleConfig.getModule().getModuleName());
        log.info("主表: {}", moduleConfig.getModule().getPrimaryTable());
        
        // 分离关系类型
        List<SysModuleCompleteResp.ModuleTableInfo> oneToOneEntities = new ArrayList<>();
        List<SysModuleCompleteResp.ModuleTableInfo> oneToManyEntities = new ArrayList<>();
        
        if (moduleConfig.getModuleTables() != null) {
            for (SysModuleCompleteResp.ModuleTableInfo table : moduleConfig.getModuleTables()) {
                if ("1:1".equals(table.getRelationType()) || "N:1".equals(table.getRelationType())) {
                    oneToOneEntities.add(table);
                } else if ("1:N".equals(table.getRelationType())) {
                    oneToManyEntities.add(table);
                }
            }
        }
        
        log.info("[关系分析] 1:1/N:1: {} 个, 1:N: {} 个", 
                oneToOneEntities.size(), oneToManyEntities.size());
        
        // 执行主查询（包含 1:1/N:1 关系）
        List<Map<String, Object>> mainData = executeMainQuery(
                dsl, moduleConfig, oneToOneEntities, page, pageSize, orderBy, filters);
        
        log.info("[主查询结果] {} 条记录", mainData.size());
        
        // 附加 1:N 关系数据
        if (!oneToManyEntities.isEmpty() && !mainData.isEmpty()) {
            log.info("[关联查询] 开始查询 {} 个 1:N 关系", oneToManyEntities.size());
            attachOneToManyRelations(dsl, mainData, oneToManyEntities, moduleConfig);
        }
        
        // 统计总数
        Long total = executeCountQuery(dsl, moduleConfig, oneToOneEntities, filters);
        
        log.info("========== 动态查询结束 ==========");
        
        // 构建分页结果
        return PageResult.of(
                page.longValue(), 
                pageSize.longValue(), 
                total, 
                mainData
        );
    }

    /**
     * 执行主查询（包含 1:1/N:1 关系）
     */
    private List<Map<String, Object>> executeMainQuery(
            DSLContext dsl,
            SysModuleCompleteResp moduleConfig,
            List<SysModuleCompleteResp.ModuleTableInfo> oneToOneEntities,
            Integer page,
            Integer pageSize,
            String orderBy,
            Map<String, Object> filters) {
        
        log.info("[主查询] 构建查询...");
        
        String primaryTable = moduleConfig.getModule().getPrimaryTable();
        Table<?> mainTable = table(name(primaryTable));
        
        // 识别使用的表（主表 + 1:1/N:1 关系表）
        Set<String> usedEntities = new HashSet<>();
        usedEntities.add(primaryTable);
        oneToOneEntities.forEach(e -> usedEntities.add(e.getTableName()));
        
        log.info("  使用的表: {}", String.join(", ", usedEntities));
        
        // 构建 SELECT 字段
        List<Field<?>> selectFields = buildSelectFields(moduleConfig, usedEntities);
        
        // 构建查询
        SelectJoinStep<?> query = dsl.select(selectFields).from(mainTable);
        
        // 应用 LEFT JOIN
        for (SysModuleCompleteResp.ModuleTableInfo entity : oneToOneEntities) {
            Table<?> joinTable = table(name(entity.getTableName()));
            Field<Object> leftField = field(name(primaryTable, entity.getJoinLeftField()));
            Field<Object> rightField = field(name(entity.getTableName(), entity.getJoinRightField()));
            
            query = query.leftJoin(joinTable).on(leftField.eq(rightField));
            
            log.info("  ✓ LEFT JOIN {} ON {}.{} = {}.{}", 
                    entity.getTableName(), 
                    primaryTable, entity.getJoinLeftField(),
                    entity.getTableName(), entity.getJoinRightField());
        }
        
        // 应用 WHERE 条件
        SelectConditionStep<?> conditionQuery = applyWhereClause(query, filters, primaryTable);
        
        // 应用排序和分页
        Result<?> result = applyOrderByAndPagination(conditionQuery, orderBy, page, pageSize, primaryTable, moduleConfig);
        
        log.info("[执行结果] {} 条记录", result.size());
        
        // 转换结果
        return transformResults(result, moduleConfig, usedEntities);
    }

    /**
     * 应用排序和分页
     */
    private Result<?> applyOrderByAndPagination(
            SelectConditionStep<?> query,
            String orderBy,
            Integer page,
            Integer pageSize,
            String primaryTable,
            SysModuleCompleteResp moduleConfig) {
        
        // 解析排序参数
        String fieldName;
        String direction;
        
        if (orderBy == null || orderBy.trim().isEmpty()) {
            // 默认按主键降序排序
            fieldName = DEFAULT_PRIMARY_KEY;
            direction = DEFAULT_SORT_DIRECTION;
            log.info("  ✓ [排序] 默认: {}.{} {}", primaryTable, fieldName, direction);
        } else {
            String[] parts = orderBy.split(",");
            fieldName = parts[0].trim();
            direction = parts.length > 1 ? parts[1].trim().toUpperCase() : "ASC";
            
            // 验证排序方向
            if (!SORT_DIRECTION_ASC.equals(direction) && !SORT_DIRECTION_DESC.equals(direction)) {
                log.warn("  ⚠ [排序] 无效的排序方向: {}, 使用默认 {}", direction, DEFAULT_SORT_DIRECTION);
                direction = DEFAULT_SORT_DIRECTION;
            }
            
            // 验证字段名是否存在于模块配置中（防止 SQL 注入）
            if (!isValidField(fieldName, moduleConfig)) {
                log.warn("  ⚠ [排序] 字段不存在于模块配置中: {}, 使用默认排序", fieldName);
                fieldName = DEFAULT_PRIMARY_KEY;
                direction = DEFAULT_SORT_DIRECTION;
            } else {
                log.info("  ✓ [排序] {}.{} {}", primaryTable, fieldName, direction);
            }
        }
        
        // 构建排序字段
        Field<Object> sortField = field(name(primaryTable, fieldName));
        
        // 应用排序和分页
        if (page != null && pageSize != null) {
            int offset = (page - 1) * pageSize;
            log.info("[分页] page={}, pageSize={}, offset={}", page, pageSize, offset);
            
            if (SORT_DIRECTION_DESC.equals(direction)) {
                Result<?> result = query.orderBy(sortField.desc()).limit(pageSize).offset(offset).fetch();
                log.info("[SQL] {}", query.orderBy(sortField.desc()).limit(pageSize).offset(offset).getSQL());
                return result;
            } else {
                Result<?> result = query.orderBy(sortField.asc()).limit(pageSize).offset(offset).fetch();
                log.info("[SQL] {}", query.orderBy(sortField.asc()).limit(pageSize).offset(offset).getSQL());
                return result;
            }
        } else {
            // 无分页，只排序
            if (SORT_DIRECTION_DESC.equals(direction)) {
                Result<?> result = query.orderBy(sortField.desc()).fetch();
                log.info("[SQL] {}", query.orderBy(sortField.desc()).getSQL());
                return result;
            } else {
                Result<?> result = query.orderBy(sortField.asc()).fetch();
                log.info("[SQL] {}", query.orderBy(sortField.asc()).getSQL());
                return result;
            }
        }
    }

    /**
     * 执行统计查询
     */
    private Long executeCountQuery(
            DSLContext dsl,
            SysModuleCompleteResp moduleConfig,
            List<SysModuleCompleteResp.ModuleTableInfo> oneToOneEntities,
            Map<String, Object> filters) {
        
        String primaryTable = moduleConfig.getModule().getPrimaryTable();
        Table<?> mainTable = table(name(primaryTable));
        
        // 构建查询
        SelectJoinStep<Record1<Integer>> query = dsl.selectCount().from(mainTable);
        
        // 应用 LEFT JOIN
        for (SysModuleCompleteResp.ModuleTableInfo entity : oneToOneEntities) {
            Table<?> joinTable = table(name(entity.getTableName()));
            Field<Object> leftField = field(name(primaryTable, entity.getJoinLeftField()));
            Field<Object> rightField = field(name(entity.getTableName(), entity.getJoinRightField()));
            
            query = query.leftJoin(joinTable).on(leftField.eq(rightField));
        }
        
        // 应用 WHERE 条件
        SelectConditionStep<Record1<Integer>> conditionQuery = applyWhereClause(query, filters, primaryTable);
        
        // 执行查询
        Integer count = conditionQuery.fetchOne(0, Integer.class);
        return count != null ? count.longValue() : 0L;
    }

    /**
     * 构建 SELECT 字段列表
     */
    private List<Field<?>> buildSelectFields(
            SysModuleCompleteResp moduleConfig, 
            Set<String> usedEntities) {
        
        List<Field<?>> fields = new ArrayList<>();
        String primaryTable = moduleConfig.getModule().getPrimaryTable();
        
        // 添加主键字段
        fields.add(field(name(primaryTable, DEFAULT_PRIMARY_KEY)).as(DEFAULT_PRIMARY_KEY));
        log.info("  ✓ [主键] {}.{}", primaryTable, DEFAULT_PRIMARY_KEY);
        
        // 收集所有需要查询的字段（简单字段 + 组合字段依赖的源字段）
        Set<String> requiredFields = new HashSet<>();
        
        // 1. 添加简单字段
        if (moduleConfig.getSimpleFields() != null) {
            for (SysModuleCompleteResp.SimpleFieldInfo fieldInfo : moduleConfig.getSimpleFields()) {
                if (!usedEntities.contains(fieldInfo.getTableName())) {
                    continue; // 跳过 1:N 关系字段
                }
                
                String fieldKey = fieldInfo.getTableName() + "." + fieldInfo.getColumnName();
                requiredFields.add(fieldKey);
            }
        }
        
        // 2. 收集组合字段依赖的源字段
        if (moduleConfig.getCombineFields() != null) {
            for (SysModuleCompleteResp.CombineFieldInfo fieldInfo : moduleConfig.getCombineFields()) {
                if (fieldInfo.getEnabled() != 1) {
                    continue;
                }
                
                // 解析 sourceMapping，提取所有源字段
                JSONArray sourceMappings = JSON.parseArray(fieldInfo.getSourceMapping());
                for (int i = 0; i < sourceMappings.size(); i++) {
                    JSONObject mapping = sourceMappings.getJSONObject(i);
                    String entity = mapping.getString("entity");
                    String fieldName = mapping.getString("field");
                    
                    // 只添加在使用的实体中的字段
                    if (usedEntities.contains(entity)) {
                        String fieldKey = entity + "." + fieldName;
                        requiredFields.add(fieldKey);
                    }
                }
            }
        }
        
        // 3. 构建 SELECT 字段（不处理 transformer，直接查询原始字段）
        for (String fieldKey : requiredFields) {
            String[] parts = fieldKey.split("\\.");
            String tableName = parts[0];
            String columnName = parts[1];
            
            fields.add(field(name(tableName, columnName)));
            log.info("  ✓ [字段] {}.{}", tableName, columnName);
        }
        
        if (fields.size() == 1) { // 只有主键
            fields.add(field("*"));
        }
        
        return fields;
    }



    /**
     * 应用 WHERE 条件
     */
    private <R extends org.jooq.Record> SelectConditionStep<R> applyWhereClause(
            SelectJoinStep<R> query, 
            Map<String, Object> filters,
            String primaryTable) {
        
        if (filters == null || filters.isEmpty()) {
            return query.where(DSL.noCondition());
        }
        
        List<Condition> conditions = new ArrayList<>();
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();
            
            if (value != null) {
                // 使用 jOOQ 的参数绑定，避免 SQL 注入
                Field<Object> field = field(name(primaryTable, fieldName));
                conditions.add(field.eq(value));
                log.info("  ✓ [过滤条件] {}.{} = {}", primaryTable, fieldName, value);
            }
        }
        
        if (conditions.isEmpty()) {
            return query.where(DSL.noCondition());
        }
        
        return query.where(conditions);
    }

    /**
     * 验证字段是否存在于模块配置中
     * 
     * @param fieldName 字段名
     * @param moduleConfig 模块配置
     * @return 是否有效
     */
    private boolean isValidField(String fieldName, SysModuleCompleteResp moduleConfig) {
        // 检查简单字段
        if (moduleConfig.getSimpleFields() != null) {
            for (SysModuleCompleteResp.SimpleFieldInfo field : moduleConfig.getSimpleFields()) {
                if (fieldName.equals(field.getColumnName())) {
                    return true;
                }
            }
        }
        
        // 检查组合字段
        if (moduleConfig.getCombineFields() != null) {
            for (SysModuleCompleteResp.CombineFieldInfo field : moduleConfig.getCombineFields()) {
                if (fieldName.equals(field.getLogicalField()) && field.getEnabled() == 1) {
                    return true;
                }
            }
        }
        
        // 检查主键字段
        return DEFAULT_PRIMARY_KEY.equals(fieldName);
    }

    /**
     * 转换查询结果
     */
    private List<Map<String, Object>> transformResults(
            Result<?> result, 
            SysModuleCompleteResp moduleConfig,
            Set<String> usedEntities) {
        
        List<Map<String, Object>> data = new ArrayList<>();
        
        for (org.jooq.Record record : result) {
            Map<String, Object> row = new HashMap<>();
            
            // 转换所有字段
            for (Field<?> field : record.fields()) {
                row.put(field.getName(), record.get(field));
            }
            
            data.add(row);
        }
        
        return data;
    }

    /**
     * 附加 1:N 关系数据
     */
    private void attachOneToManyRelations(
            DSLContext dsl,
            List<Map<String, Object>> mainData,
            List<SysModuleCompleteResp.ModuleTableInfo> entities,
            SysModuleCompleteResp moduleConfig) {
        
        for (SysModuleCompleteResp.ModuleTableInfo entity : entities) {
            log.info("[子查询] {}...", entity.getTableName());
            
            // 获取父记录的关联字段值（joinLeftField）
            // 例如：class 表的 id 字段
            List<Object> parentKeys = mainData.stream()
                    .map(row -> row.get(entity.getJoinLeftField()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            
            if (parentKeys.isEmpty()) {
                log.info("  ⊗ 没有父记录关联字段值，跳过");
                continue;
            }
            
            log.info("  父记录关联字段 {} 值数: {}", entity.getJoinLeftField(), parentKeys.size());
            
            // 查询子记录（使用 joinRightField 作为查询条件）
            // 例如：WHERE student.class_id IN (parentKeys)
            List<Map<String, Object>> childRecords = queryChildRecords(
                    dsl, entity, parentKeys, moduleConfig);
            
            log.info("  查询到子记录数: {}", childRecords.size());
            
            // 附加到主记录
            for (Map<String, Object> row : mainData) {
                Object parentKey = row.get(entity.getJoinLeftField());
                
                // 找到所有 joinRightField 等于 parentKey 的子记录
                List<Map<String, Object>> children = childRecords.stream()
                        .filter(child -> Objects.equals(
                                child.get(entity.getJoinRightField()),
                                parentKey))
                        .map(child -> {
                            Map<String, Object> cleaned = new HashMap<>(child);
                            // 移除关联字段，避免冗余
                            cleaned.remove(entity.getJoinRightField());
                            return cleaned;
                        })
                        .collect(Collectors.toList());
                
                if (!children.isEmpty()) {
                    // 使用表名 + "s" 作为关系键（例如：student -> students）
                    String relationKey = entity.getTableName() + "s";
                    row.put(relationKey, children);
                    log.info("  ✓ 为父记录 {}={} 附加了 {} 条子记录到 '{}'", 
                            entity.getJoinLeftField(), parentKey, children.size(), relationKey);
                }
            }
        }
    }

    /**
     * 查询子记录
     */
    private List<Map<String, Object>> queryChildRecords(
            DSLContext dsl,
            SysModuleCompleteResp.ModuleTableInfo entity,
            List<Object> parentKeys,
            SysModuleCompleteResp moduleConfig) {
        
        Table<?> childTable = table(name(entity.getTableName()));
        Field<Object> joinField = field(name(entity.getTableName(), entity.getJoinRightField()));
        
        // 构建子表字段
        List<Field<?>> selectFields = buildChildSelectFields(entity, moduleConfig);
        
        // 添加关联键
        selectFields.add(joinField);
        
        // 构建查询 - 使用 jOOQ 的 IN 条件，自动处理参数绑定
        SelectConditionStep<?> query = dsl.select(selectFields)
                .from(childTable)
                .where(joinField.in(parentKeys));
        
        log.info("  子查询 SQL: {}", query.getSQL());
        
        // 执行查询
        Result<?> result = query.fetch();
        log.info("  子查询结果数: {}", result.size());
        
        // 转换结果
        List<Map<String, Object>> data = new ArrayList<>();
        for (org.jooq.Record record : result) {
            Map<String, Object> row = new HashMap<>();
            for (Field<?> field : record.fields()) {
                row.put(field.getName(), record.get(field));
            }
            data.add(row);
        }
        
        return data;
    }

    /**
     * 构建子表 SELECT 字段
     */
    private List<Field<?>> buildChildSelectFields(
            SysModuleCompleteResp.ModuleTableInfo entity,
            SysModuleCompleteResp moduleConfig) {
        
        List<Field<?>> fields = new ArrayList<>();
        String tableName = entity.getTableName();
        
        // 收集所有需要查询的字段（简单字段 + 组合字段依赖的源字段）
        Set<String> requiredFields = new HashSet<>();
        
        // 1. 添加简单字段
        if (moduleConfig.getSimpleFields() != null) {
            for (SysModuleCompleteResp.SimpleFieldInfo fieldInfo : moduleConfig.getSimpleFields()) {
                if (tableName.equals(fieldInfo.getTableName())) {
                    requiredFields.add(fieldInfo.getColumnName());
                }
            }
        }
        
        // 2. 收集组合字段依赖的源字段
        if (moduleConfig.getCombineFields() != null) {
            for (SysModuleCompleteResp.CombineFieldInfo fieldInfo : moduleConfig.getCombineFields()) {
                if (fieldInfo.getEnabled() != 1) {
                    continue;
                }
                
                // 解析 sourceMapping，提取属于当前子表的源字段
                JSONArray sourceMappings = JSON.parseArray(fieldInfo.getSourceMapping());
                for (int i = 0; i < sourceMappings.size(); i++) {
                    JSONObject mapping = sourceMappings.getJSONObject(i);
                    String entityName = mapping.getString("entity");
                    String fieldName = mapping.getString("field");
                    
                    if (tableName.equals(entityName)) {
                        requiredFields.add(fieldName);
                    }
                }
            }
        }
        
        // 3. 构建 SELECT 字段（不处理 transformer，直接查询原始字段）
        for (String columnName : requiredFields) {
            fields.add(field(name(tableName, columnName)));
        }
        
        if (fields.isEmpty()) {
            fields.add(field("*"));
        }
        
        return fields;
    }
}
