package com.jdec.platform.data.biz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.data.api.dto.request.FilterItem;
import com.jdec.platform.data.api.dto.request.SingleTableQueryReq;
import com.jdec.platform.data.api.dto.request.SortItem;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.api.model.predicate.ColumnRef;
import com.jdec.platform.data.api.model.predicate.ComparisonPredicate;
import com.jdec.platform.data.api.model.predicate.CompoundPredicate;
import com.jdec.platform.data.api.model.predicate.FunctionExpr;
import com.jdec.platform.data.api.model.predicate.Predicate;
import com.jdec.platform.data.biz.plan.compiler.PredicateCompiler;
import com.jdec.platform.shared.datasource.JooqDataContextFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSON;
import org.jooq.JSONB;
import org.jooq.OrderField;
import org.jooq.SelectConditionStep;
import org.jooq.SelectSelectStep;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 单表轻量查询服务实现 (SingleTableQueryService)
 *
 * <p>面向单张物理表的原子操作，直接使用 jOOQ 执行原生轻量 SQL，跳过元数据加载与复杂多表装配。
 *
 * <p>契约约束：
 *
 * <ul>
 *   <li>1. 单表查询仅面向单一目标物理表；调用方应保证 filters / sorts 中仅引用当前目标表字段。
 *   <li>2. 若复合过滤项中混杂了非目标表的字段引用，在单表物理作用域内该外表分支将被自动略过，并输出 WARN 告警日志。
 *   <li>3. 软删除字段 (deleted) 完全作为普通字段参数由调用方按需传递，服务不做任何隐式追加或篡改，保持执行语义透明且正交。
 * </ul>
 */
@Slf4j
@Service
public class SingleTableQueryService {

    private final JooqDataContextFactory jooqDataContextFactory;
    private final ObjectMapper objectMapper;

    @Autowired
    public SingleTableQueryService(
            JooqDataContextFactory jooqDataContextFactory, ObjectMapper objectMapper) {
        this.jooqDataContextFactory = jooqDataContextFactory;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public SingleTableQueryService(JooqDataContextFactory jooqDataContextFactory) {
        this(jooqDataContextFactory, new ObjectMapper());
    }

    /**
     * 单表分页轻量查询 (不依赖模块树，直接在目标单表上执行原生分页查询与总条数统计)
     *
     * @param req 单表查询请求
     * @return 分页数据对象，包含当前页记录列表及总条数
     */
    public DataPage<List<Map<String, Object>>> querySingleTablePage(SingleTableQueryReq req) {
        if (req == null || req.getTableName() == null || req.getTableName().isBlank()) {
            int pageNo =
                    (req != null && req.getPageNo() != null && req.getPageNo() > 0)
                            ? req.getPageNo()
                            : 1;
            int pageSize =
                    (req != null && req.getPageSize() != null && req.getPageSize() > 0)
                            ? req.getPageSize()
                            : 20;
            return DataPage.of(pageNo, pageSize, 0L, Collections.emptyList());
        }

        int pageNo = (req.getPageNo() != null && req.getPageNo() > 0) ? req.getPageNo() : 1;
        int pageSize =
                (req.getPageSize() != null && req.getPageSize() > 0) ? req.getPageSize() : 20;
        int offset = (pageNo - 1) * pageSize;

        // 1. 统计总条数
        Long total = countSingleTable(req);
        if (total == null || total == 0L) {
            return DataPage.of(pageNo, pageSize, 0L, Collections.emptyList());
        }

        // 2. 构建投影字段
        DSLContext dsl = jooqDataContextFactory.getContext();
        String tableName = req.getTableName().trim();
        Table<?> table = DSL.table(DSL.name(tableName));

        List<Field<?>> selectFields = new ArrayList<>();
        if (req.getColumns() != null && !req.getColumns().isEmpty()) {
            for (String col : req.getColumns()) {
                if (col != null && !col.isBlank()) {
                    selectFields.add(DSL.field(DSL.name(col.trim())));
                }
            }
        }

        boolean isDistinct = Boolean.TRUE.equals(req.getDistinct());
        SelectSelectStep<?> selectStep;
        if (selectFields.isEmpty()) {
            selectStep =
                    isDistinct ? dsl.selectDistinct(DSL.asterisk()) : dsl.select(DSL.asterisk());
        } else {
            selectStep = isDistinct ? dsl.selectDistinct(selectFields) : dsl.select(selectFields);
        }
        var query = selectStep.from(table);

        // 3. 过滤条件
        Condition condition = compileFilters(req.getFilters(), tableName);
        SelectConditionStep<?> whereStep =
                (condition != null) ? query.where(condition) : query.where(DSL.noCondition());

        // 4. 排序规则
        List<OrderField<?>> orderFields = compileSorts(req.getSorts());
        var orderedQuery = orderFields.isEmpty() ? whereStep : whereStep.orderBy(orderFields);

        // 5. 分页执行
        List<Map<String, Object>> records =
                normalizeRecords(orderedQuery.offset(offset).limit(pageSize).fetchMaps());
        return DataPage.of(pageNo, pageSize, total, records);
    }

    /** 单表列表查询 (返回 List<Map<String, Object>>) */
    public List<Map<String, Object>> querySingleTableList(SingleTableQueryReq req) {
        if (req == null || req.getTableName() == null || req.getTableName().isBlank()) {
            return Collections.emptyList();
        }

        DSLContext dsl = jooqDataContextFactory.getContext();
        String tableName = req.getTableName().trim();
        Table<?> table = DSL.table(DSL.name(tableName));

        // 1. 构建投影字段
        List<Field<?>> selectFields = new ArrayList<>();
        if (req.getColumns() != null && !req.getColumns().isEmpty()) {
            for (String col : req.getColumns()) {
                if (col != null && !col.isBlank()) {
                    selectFields.add(DSL.field(DSL.name(col.trim())));
                }
            }
        }

        boolean isDistinct = Boolean.TRUE.equals(req.getDistinct());
        SelectSelectStep<?> selectStep;
        if (selectFields.isEmpty()) {
            selectStep =
                    isDistinct ? dsl.selectDistinct(DSL.asterisk()) : dsl.select(DSL.asterisk());
        } else {
            selectStep = isDistinct ? dsl.selectDistinct(selectFields) : dsl.select(selectFields);
        }
        var query = selectStep.from(table);

        // 2. 构建过滤条件
        Condition condition = compileFilters(req.getFilters(), tableName);
        SelectConditionStep<?> whereStep =
                (condition != null) ? query.where(condition) : query.where(DSL.noCondition());

        // 3. 排序规则
        List<OrderField<?>> orderFields = compileSorts(req.getSorts());
        var orderedQuery = orderFields.isEmpty() ? whereStep : whereStep.orderBy(orderFields);

        // 4. 限制条数
        if (req.getLimit() != null && req.getLimit() > 0) {
            return normalizeRecords(orderedQuery.limit(req.getLimit()).fetchMaps());
        }
        return normalizeRecords(orderedQuery.fetchMaps());
    }

    /** 单表单列值投影查询 */
    public <T> List<T> querySingleTableColumn(SingleTableQueryReq req, Class<T> targetType) {
        if (req == null || req.getTableName() == null || req.getTableName().isBlank()) {
            return Collections.emptyList();
        }

        String targetCol = null;
        if (req.getColumns() != null && !req.getColumns().isEmpty()) {
            targetCol = req.getColumns().get(0);
        }
        if (targetCol == null || targetCol.isBlank()) {
            targetCol = "id";
        }

        DSLContext dsl = jooqDataContextFactory.getContext();
        String tableName = req.getTableName().trim();
        Table<?> table = DSL.table(DSL.name(tableName));
        Field<T> field = DSL.field(DSL.name(targetCol.trim()), targetType);

        boolean isDistinct = Boolean.TRUE.equals(req.getDistinct());
        var query =
                isDistinct ? dsl.selectDistinct(field).from(table) : dsl.select(field).from(table);

        Condition condition = compileFilters(req.getFilters(), tableName);
        SelectConditionStep<?> whereStep =
                (condition != null) ? query.where(condition) : query.where(DSL.noCondition());

        List<OrderField<?>> orderFields = compileSorts(req.getSorts());
        var orderedQuery = orderFields.isEmpty() ? whereStep : whereStep.orderBy(orderFields);

        List<T> list =
                (req.getLimit() != null && req.getLimit() > 0)
                        ? orderedQuery.limit(req.getLimit()).fetch(field)
                        : orderedQuery.fetch(field);
        if (targetType == Object.class && list != null) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (T item : list) {
                normalized.add(parseIfJson(item));
            }
            @SuppressWarnings("unchecked")
            List<T> res = (List<T>) normalized;
            return res;
        }
        return list;
    }

    /** 单表条件计数 */
    public Long countSingleTable(SingleTableQueryReq req) {
        if (req == null || req.getTableName() == null || req.getTableName().isBlank()) {
            return 0L;
        }

        DSLContext dsl = jooqDataContextFactory.getContext();
        String tableName = req.getTableName().trim();
        Table<?> table = DSL.table(DSL.name(tableName));

        var query = dsl.selectCount().from(table);
        Condition condition = compileFilters(req.getFilters(), tableName);
        SelectConditionStep<?> whereStep =
                (condition != null) ? query.where(condition) : query.where(DSL.noCondition());

        Long count = whereStep.fetchOne(0, Long.class);
        return count != null ? count : 0L;
    }

    /** 批量查询单据最新启用的最大版本主键 ID 集合 */
    public List<Long> getLatestVersionIds(
            String tableName, Collection<String> businessNos, Integer businessStatus) {
        if (tableName == null || tableName.isBlank()) {
            return Collections.emptyList();
        }

        DSLContext dsl = jooqDataContextFactory.getContext();
        Table<?> t = DSL.table(DSL.name(tableName.trim()));
        Field<Long> fId = DSL.field(DSL.name("id"), Long.class);
        Field<String> fBizNo = DSL.field(DSL.name("business_no"), String.class);
        Field<Integer> fVerNo = DSL.field(DSL.name("version_no"), Integer.class);
        Field<Integer> fBizStatus = DSL.field(DSL.name("business_status"), Integer.class);

        Condition baseCond =
                (businessStatus != null) ? fBizStatus.eq(businessStatus) : DSL.noCondition();
        baseCond = baseCond.and(DSL.field(DSL.name("deleted")).eq((byte) 0));
        if (businessNos != null && !businessNos.isEmpty()) {
            baseCond = baseCond.and(fBizNo.in(businessNos));
        }

        Table<?> maxVerSubquery =
                dsl.select(fBizNo.as("b_no"), DSL.max(fVerNo).as("max_v"))
                        .from(t)
                        .where(baseCond)
                        .groupBy(fBizNo)
                        .asTable("mv");

        return dsl.select(fId)
                .from(t)
                .innerJoin(maxVerSubquery)
                .on(
                        fBizNo.eq(DSL.field(DSL.name("mv", "b_no"), String.class))
                                .and(fVerNo.eq(DSL.field(DSL.name("mv", "max_v"), Integer.class))))
                .where(baseCond)
                .fetch(fId);
    }

    private Condition compileFilters(List<FilterItem> filters, String tableName) {
        Set<String> applicableTables = new HashSet<>();
        applicableTables.add(tableName.toLowerCase());

        List<Condition> conditions = new ArrayList<>();

        if (filters != null) {
            for (FilterItem item : filters) {
                if (item != null) {
                    var pred = item.toPredicate();
                    Set<String> refTables = extractReferencedTables(pred);
                    for (String ref : refTables) {
                        if (!ref.equalsIgnoreCase(tableName)) {
                            log.warn(
                                    "单表查询 [{}] 接收到非目标表过滤字段引用 [{}], 单表物理作用域内该分支不生效", tableName, ref);
                        }
                    }

                    Condition cond = PredicateCompiler.compile(pred, tableName, applicableTables);
                    if (cond != null && !DSL.noCondition().equals(cond)) {
                        conditions.add(cond);
                    }
                }
            }
        }

        return DSL.and(conditions);
    }

    /** 从谓词 AST 中提取引用的所有物理表名称 (小写) */
    private static Set<String> extractReferencedTables(Predicate predicate) {
        Set<String> tables = new HashSet<>();
        collectReferencedTables(predicate, tables);
        return tables;
    }

    private static void collectReferencedTables(Predicate predicate, Set<String> tables) {
        if (predicate == null) {
            return;
        }
        if (predicate instanceof ComparisonPredicate comp) {
            String tbl = comp.getTableName();
            if (tbl == null && comp.getExpression() instanceof ColumnRef col) {
                tbl = col.getTableName();
            } else if (tbl == null
                    && comp.getExpression() instanceof FunctionExpr fn
                    && fn.getTargetColumn() != null) {
                tbl = fn.getTargetColumn().getTableName();
            }
            if (tbl != null && !tbl.isBlank()) {
                tables.add(tbl.trim().toLowerCase());
            }
        } else if (predicate instanceof CompoundPredicate comp) {
            if (comp.getPredicates() != null) {
                for (Predicate sub : comp.getPredicates()) {
                    collectReferencedTables(sub, tables);
                }
            }
        }
    }

    private List<OrderField<?>> compileSorts(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return Collections.emptyList();
        }
        List<OrderField<?>> orderFields = new ArrayList<>();
        for (SortItem s : sorts) {
            if (s != null && s.getColumnName() != null && !s.getColumnName().isBlank()) {
                Field<Object> f = DSL.field(DSL.name(s.getColumnName().trim()));
                orderFields.add("ASC".equalsIgnoreCase(s.getDirection()) ? f.asc() : f.desc());
            }
        }
        return orderFields;
    }

    /** 对查询记录的每一项值执行 JSON 规整化解析 */
    private List<Map<String, Object>> normalizeRecords(List<Map<String, Object>> records) {
        if (records == null || records.isEmpty()) {
            return records;
        }
        for (Map<String, Object> map : records) {
            if (map == null) {
                continue;
            }
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                entry.setValue(parseIfJson(entry.getValue()));
            }
        }
        return records;
    }

    /** 自动将 JSON 字符串或 jOOQ JSON/JSONB 对象转换为结构化的 List/Map，保持返回值纯净 */
    private Object parseIfJson(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof JSON jooqJson) {
            String jsonStr = jooqJson.data();
            if (jsonStr == null || jsonStr.isBlank()) {
                return null;
            }
            try {
                return objectMapper.readValue(jsonStr, Object.class);
            } catch (Exception e) {
                return jsonStr;
            }
        }
        if (val instanceof JSONB jooqJsonb) {
            String jsonStr = jooqJsonb.data();
            if (jsonStr == null || jsonStr.isBlank()) {
                return null;
            }
            try {
                return objectMapper.readValue(jsonStr, Object.class);
            } catch (Exception e) {
                return jsonStr;
            }
        }
        if (val instanceof String str) {
            String trimmed = str.trim();
            if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
                    || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                try {
                    return objectMapper.readValue(trimmed, Object.class);
                } catch (Exception ignored) {
                    return val;
                }
            }
        }
        return val;
    }
}
