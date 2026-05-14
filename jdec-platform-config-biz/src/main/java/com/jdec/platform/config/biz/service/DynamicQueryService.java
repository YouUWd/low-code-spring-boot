package com.jdec.platform.config.biz.service;

import static org.jooq.impl.DSL.*;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.jdec.platform.config.api.DataQueryApi;
import com.jdec.platform.config.api.dto.request.DataQueryReq;
import com.jdec.platform.config.api.dto.request.FilterRule;
import com.jdec.platform.config.api.dto.request.SortRule;
import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp;
import com.jdec.platform.shared.datasource.DataSourceResolver;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.model.PageResult;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

/** 动态查询服务 使用 jOOQ 构建类型安全的 SQL 查询，避免 SQL 注入 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicQueryService implements DataQueryApi {

    private final DataSourceResolver dataSourceResolver;
    private final SysModuleService sysModuleService;

    // 默认配置常量
    private static final String DEFAULT_PRIMARY_KEY = "id";
    private static final String DEFAULT_SORT_DIRECTION = "DESC";
    private static final String SORT_DIRECTION_ASC = "ASC";
    private static final String SORT_DIRECTION_DESC = "DESC";

    @Override
    public PageResult<Map<String, Object>> executeQuery(
            String moduleCode, String projectNo, Long subjectId, DataQueryReq request) {
        log.info(
                "执行动态查询: moduleCode={}, page={}, pageSize={}, sorts={}, filters={}",
                moduleCode,
                request.getPage(),
                request.getPageSize(),
                request.getSorts(),
                request.getFilters());

        // 根据模块编码查询模块配置
        SysModuleCompleteResp moduleConfig =
                sysModuleService.getModuleComplete(projectNo, subjectId, moduleCode);

        if (moduleConfig == null) {
            throw new BusinessException("模块不存在: " + moduleCode);
        }

        // 验证过滤条件字段是否合法
        if (request.getFilters() != null && !request.getFilters().isEmpty()) {
            validateFilters(request.getFilters(), moduleConfig);
        }

        // 验证排序字段是否合法
        if (request.getSorts() != null && !request.getSorts().isEmpty()) {
            validateSorts(request.getSorts(), moduleConfig);
        }

        // 执行动态查询
        return executeQuery(
                projectNo,
                moduleConfig,
                request.getPage(),
                request.getPageSize(),
                request.getSorts(),
                request.getFilters());
    }

    /**
     * 验证过滤条件字段是否合法
     *
     * @param filters 过滤条件
     * @param moduleConfig 模块配置
     * @throws BusinessException 如果存在非法字段
     */
    private void validateFilters(List<FilterRule> filters, SysModuleCompleteResp moduleConfig) {
        for (FilterRule filter : filters) {
            if (!isValidField(filter.getField(), moduleConfig)) {
                throw new BusinessException("非法的过滤字段: " + filter.getField());
            }
        }
    }

    /**
     * 验证排序字段是否合法
     *
     * @param sorts 排序规则
     * @param moduleConfig 模块配置
     * @throws BusinessException 如果存在非法字段
     */
    private void validateSorts(List<SortRule> sorts, SysModuleCompleteResp moduleConfig) {
        for (SortRule sort : sorts) {
            if (!isValidField(sort.getField(), moduleConfig)) {
                throw new BusinessException("非法的排序字段: " + sort.getField());
            }
        }
    }

    /**
     * 根据模块配置执行动态查询
     *
     * @param moduleConfig 模块完整配置
     * @param page 页码（从1开始）
     * @param pageSize 每页大小
     * @param sorts 排序规则列表
     * @param filters 过滤条件列表
     * @return 分页查询结果
     */
    public PageResult<Map<String, Object>> executeQuery(
            String projectNo,
            SysModuleCompleteResp moduleConfig,
            Integer page,
            Integer pageSize,
            List<SortRule> sorts,
            List<FilterRule> filters) {

        DSLContext dsl = DSL.using(dataSourceResolver.resolve(projectNo), SQLDialect.MYSQL);

        log.info("========== 动态查询开始 ==========");
        log.info("模块: {}", moduleConfig.getModule().getModuleName());
        log.info("主表: {}", moduleConfig.getModule().getPrimaryTable());

        // 分离关系类型
        List<SysModuleCompleteResp.ModuleTableInfo> oneToOneEntities = new ArrayList<>();
        List<SysModuleCompleteResp.ModuleTableInfo> oneToManyEntities = new ArrayList<>();

        if (moduleConfig.getModuleTables() != null) {
            for (SysModuleCompleteResp.ModuleTableInfo table : moduleConfig.getModuleTables()) {
                if ("1:1".equals(table.getRelationType())
                        || "N:1".equals(table.getRelationType())) {
                    oneToOneEntities.add(table);
                } else if ("1:N".equals(table.getRelationType())) {
                    oneToManyEntities.add(table);
                }
            }
        }

        log.info(
                "[关系分析] 1:1/N:1: {} 个, 1:N: {} 个",
                oneToOneEntities.size(),
                oneToManyEntities.size());

        // 执行主查询（包含 1:1/N:1 关系）
        List<Map<String, Object>> mainData =
                executeMainQuery(
                        dsl, moduleConfig, oneToOneEntities, page, pageSize, sorts, filters);

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
        return PageResult.of(page.longValue(), pageSize.longValue(), total, mainData);
    }

    /** 执行主查询（包含 1:1/N:1 关系） */
    private List<Map<String, Object>> executeMainQuery(
            DSLContext dsl,
            SysModuleCompleteResp moduleConfig,
            List<SysModuleCompleteResp.ModuleTableInfo> oneToOneEntities,
            Integer page,
            Integer pageSize,
            List<SortRule> sorts,
            List<FilterRule> filters) {

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
            Field<Object> rightField =
                    field(name(entity.getTableName(), entity.getJoinRightField()));

            query = query.leftJoin(joinTable).on(leftField.eq(rightField));

            log.info(
                    "  ✓ LEFT JOIN {} ON {}.{} = {}.{}",
                    entity.getTableName(),
                    primaryTable,
                    entity.getJoinLeftField(),
                    entity.getTableName(),
                    entity.getJoinRightField());
        }

        // 应用 WHERE 条件
        SelectConditionStep<?> conditionQuery = applyWhereClause(query, filters, primaryTable);

        // 应用排序和分页
        Result<?> result =
                applyOrderByAndPagination(
                        conditionQuery, sorts, page, pageSize, primaryTable, moduleConfig);

        log.info("[执行结果] {} 条记录", result.size());

        // 转换结果
        return transformResults(result, moduleConfig, usedEntities);
    }

    /** 应用排序和分页 */
    private Result<?> applyOrderByAndPagination(
            SelectConditionStep<?> query,
            List<SortRule> sorts,
            Integer page,
            Integer pageSize,
            String primaryTable,
            SysModuleCompleteResp moduleConfig) {

        // 构建排序字段列表
        List<org.jooq.SortField<?>> sortFields = new ArrayList<>();

        if (sorts == null || sorts.isEmpty()) {
            // 默认按主键降序排序
            Field<Object> defaultSortField = field(name(primaryTable, DEFAULT_PRIMARY_KEY));
            sortFields.add(defaultSortField.desc());
            log.info(
                    "  ✓ [排序] 默认: {}.{} {}",
                    primaryTable,
                    DEFAULT_PRIMARY_KEY,
                    DEFAULT_SORT_DIRECTION);
        } else {
            // 应用多字段排序
            for (SortRule sort : sorts) {
                String fieldName = sort.getField();
                String direction =
                        sort.getOrder() != null
                                ? sort.getOrder().toUpperCase()
                                : SORT_DIRECTION_ASC;

                // 验证排序方向
                if (!SORT_DIRECTION_ASC.equals(direction)
                        && !SORT_DIRECTION_DESC.equals(direction)) {
                    log.warn("  ⚠ [排序] 无效的排序方向: {}, 使用默认 {}", direction, SORT_DIRECTION_ASC);
                    direction = SORT_DIRECTION_ASC;
                }

                // 验证字段名是否存在于模块配置中（防止 SQL 注入）
                if (!isValidField(fieldName, moduleConfig)) {
                    log.warn("  ⚠ [排序] 字段不存在于模块配置中: {}, 跳过", fieldName);
                    continue;
                }

                Field<Object> sortField = field(name(primaryTable, fieldName));
                if (SORT_DIRECTION_DESC.equals(direction)) {
                    sortFields.add(sortField.desc());
                } else {
                    sortFields.add(sortField.asc());
                }

                log.info("  ✓ [排序] {}.{} {}", primaryTable, fieldName, direction);
            }

            // 如果所有排序字段都无效，使用默认排序
            if (sortFields.isEmpty()) {
                Field<Object> defaultSortField = field(name(primaryTable, DEFAULT_PRIMARY_KEY));
                sortFields.add(defaultSortField.desc());
                log.info(
                        "  ✓ [排序] 所有字段无效，使用默认: {}.{} {}",
                        primaryTable,
                        DEFAULT_PRIMARY_KEY,
                        DEFAULT_SORT_DIRECTION);
            }
        }

        // 应用排序和分页
        int offset = (page - 1) * pageSize;
        log.info("[SQL] {}", query.orderBy(sortFields).limit(pageSize).offset(offset).getSQL());
        return query.orderBy(sortFields).limit(pageSize).offset(offset).fetch();
    }

    /** 执行统计查询 */
    private Long executeCountQuery(
            DSLContext dsl,
            SysModuleCompleteResp moduleConfig,
            List<SysModuleCompleteResp.ModuleTableInfo> oneToOneEntities,
            List<FilterRule> filters) {

        String primaryTable = moduleConfig.getModule().getPrimaryTable();
        Table<?> mainTable = table(name(primaryTable));

        // 构建查询
        SelectJoinStep<Record1<Integer>> query = dsl.selectCount().from(mainTable);

        // 应用 LEFT JOIN
        for (SysModuleCompleteResp.ModuleTableInfo entity : oneToOneEntities) {
            Table<?> joinTable = table(name(entity.getTableName()));
            Field<Object> leftField = field(name(primaryTable, entity.getJoinLeftField()));
            Field<Object> rightField =
                    field(name(entity.getTableName(), entity.getJoinRightField()));

            query = query.leftJoin(joinTable).on(leftField.eq(rightField));
        }

        // 应用 WHERE 条件
        SelectConditionStep<Record1<Integer>> conditionQuery =
                applyWhereClause(query, filters, primaryTable);

        // 执行查询
        Integer count = conditionQuery.fetchOne(0, Integer.class);
        return count != null ? count.longValue() : 0L;
    }

    /** 构建 SELECT 字段列表 */
    private List<Field<?>> buildSelectFields(
            SysModuleCompleteResp moduleConfig, Set<String> usedEntities) {

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
            for (SysModuleCompleteResp.CombineFieldInfo fieldInfo :
                    moduleConfig.getCombineFields()) {
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

    /** 应用 WHERE 条件 */
    private <R extends org.jooq.Record> SelectConditionStep<R> applyWhereClause(
            SelectJoinStep<R> query, List<FilterRule> filters, String primaryTable) {

        if (filters == null || filters.isEmpty()) {
            return query.where(DSL.noCondition());
        }

        List<Condition> conditions = new ArrayList<>();

        for (FilterRule filter : filters) {
            String fieldName = filter.getField();
            String operator =
                    filter.getOperator() != null ? filter.getOperator().toLowerCase() : "eq";
            Object value = filter.getValue();

            // 使用 jOOQ 的参数绑定，避免 SQL 注入
            Field<Object> field = field(name(primaryTable, fieldName));

            try {
                Condition condition = buildCondition(field, operator, value);
                conditions.add(condition);
                log.info("  ✓ [过滤条件] {}.{} {} {}", primaryTable, fieldName, operator, value);
            } catch (IllegalArgumentException e) {
                log.warn("  ⚠ [过滤条件] 不支持的操作符: {}, 跳过", operator);
            }
        }

        if (conditions.isEmpty()) {
            return query.where(DSL.noCondition());
        }

        return query.where(conditions);
    }

    /**
     * 根据操作符构建 jOOQ 条件
     *
     * @param field 字段
     * @param operator 操作符
     * @param value 值
     * @return jOOQ 条件
     */
    private Condition buildCondition(Field<Object> field, String operator, Object value) {
        switch (operator) {
            case "eq":
                return field.eq(value);

            case "neq":
                return field.ne(value);

            case "gt":
                return field.gt(value);

            case "gte":
                return field.ge(value);

            case "lt":
                return field.lt(value);

            case "lte":
                return field.le(value);

            case "like":
                // jOOQ 的 like 需要手动添加通配符
                return field.like("%" + value + "%");

            case "in":
                // 处理 IN 操作符，value 应该是 List
                if (value instanceof List) {
                    return field.in((List<?>) value);
                } else {
                    throw new IllegalArgumentException("IN 操作符的值必须是数组");
                }

            case "notin":
                // 处理 NOT IN 操作符
                if (value instanceof List) {
                    return field.notIn((List<?>) value);
                } else {
                    throw new IllegalArgumentException("NOT IN 操作符的值必须是数组");
                }

            case "isnull":
                return field.isNull();

            case "isnotnull":
                return field.isNotNull();

            case "between":
                // 处理 BETWEEN 操作符，value 应该是包含两个元素的数组
                if (value instanceof List) {
                    List<?> values = (List<?>) value;
                    if (values.size() == 2) {
                        return field.between(values.get(0), values.get(1));
                    } else {
                        throw new IllegalArgumentException("BETWEEN 操作符的值必须包含两个元素");
                    }
                } else {
                    throw new IllegalArgumentException("BETWEEN 操作符的值必须是数组");
                }

            default:
                throw new IllegalArgumentException("不支持的操作符: " + operator);
        }
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

    /** 转换查询结果 */
    private List<Map<String, Object>> transformResults(
            Result<?> result, SysModuleCompleteResp moduleConfig, Set<String> usedEntities) {

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

    /** 附加 1:N 关系数据 */
    private void attachOneToManyRelations(
            DSLContext dsl,
            List<Map<String, Object>> mainData,
            List<SysModuleCompleteResp.ModuleTableInfo> entities,
            SysModuleCompleteResp moduleConfig) {

        for (SysModuleCompleteResp.ModuleTableInfo entity : entities) {
            log.info("[子查询] {}...", entity.getTableName());

            // 获取父记录的关联字段值（joinLeftField）
            // 例如：class 表的 id 字段
            List<Object> parentKeys =
                    mainData.stream()
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
            List<Map<String, Object>> childRecords =
                    queryChildRecords(dsl, entity, parentKeys, moduleConfig);

            log.info("  查询到子记录数: {}", childRecords.size());

            // 附加到主记录
            for (Map<String, Object> row : mainData) {
                Object parentKey = row.get(entity.getJoinLeftField());

                // 找到所有 joinRightField 等于 parentKey 的子记录
                List<Map<String, Object>> children =
                        childRecords.stream()
                                .filter(
                                        child ->
                                                Objects.equals(
                                                        child.get(entity.getJoinRightField()),
                                                        parentKey))
                                .map(
                                        child -> {
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
                    log.info(
                            "  ✓ 为父记录 {}={} 附加了 {} 条子记录到 '{}'",
                            entity.getJoinLeftField(),
                            parentKey,
                            children.size(),
                            relationKey);
                }
            }
        }
    }

    /** 查询子记录 */
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
        SelectConditionStep<?> query =
                dsl.select(selectFields).from(childTable).where(joinField.in(parentKeys));

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

    /** 构建子表 SELECT 字段 */
    private List<Field<?>> buildChildSelectFields(
            SysModuleCompleteResp.ModuleTableInfo entity, SysModuleCompleteResp moduleConfig) {

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
            for (SysModuleCompleteResp.CombineFieldInfo fieldInfo :
                    moduleConfig.getCombineFields()) {
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
