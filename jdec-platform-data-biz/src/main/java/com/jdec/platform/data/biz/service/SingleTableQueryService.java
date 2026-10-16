package com.jdec.platform.data.biz.service;

import com.jdec.platform.data.api.dto.request.FilterItem;
import com.jdec.platform.data.api.dto.request.SingleTableQueryReq;
import com.jdec.platform.data.api.dto.request.SortItem;
import com.jdec.platform.data.biz.plan.compiler.PredicateCompiler;
import com.jdec.platform.shared.datasource.JooqDataContextFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.SelectConditionStep;
import org.jooq.SelectSelectStep;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

/**
 * 单表轻量查询服务实现 (SingleTableQueryService)
 *
 * <p>面向单张物理表的原子操作，直接使用 jOOQ 执行原生轻量 SQL，跳过元数据加载与复杂多表装配。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SingleTableQueryService {

    private final JooqDataContextFactory jooqDataContextFactory;

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

        SelectSelectStep<?> selectStep =
                selectFields.isEmpty() ? dsl.select(DSL.asterisk()) : dsl.select(selectFields);
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
            return orderedQuery.limit(req.getLimit()).fetchMaps();
        }
        return orderedQuery.fetchMaps();
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

        var query = dsl.select(field).from(table);

        Condition condition = compileFilters(req.getFilters(), tableName);
        SelectConditionStep<?> whereStep =
                (condition != null) ? query.where(condition) : query.where(DSL.noCondition());

        List<OrderField<?>> orderFields = compileSorts(req.getSorts());
        var orderedQuery = orderFields.isEmpty() ? whereStep : whereStep.orderBy(orderFields);

        if (req.getLimit() != null && req.getLimit() > 0) {
            return orderedQuery.limit(req.getLimit()).fetch(field);
        }
        return orderedQuery.fetch(field);
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
        if (filters == null || filters.isEmpty()) {
            return null;
        }

        Set<String> applicableTables = new HashSet<>();
        applicableTables.add(tableName.toLowerCase());

        List<Condition> conditions = new ArrayList<>();
        for (FilterItem item : filters) {
            if (item != null) {
                var pred = item.toPredicate();
                Condition cond = PredicateCompiler.compile(pred, null, tableName, applicableTables);
                if (cond != null && !DSL.noCondition().equals(cond)) {
                    conditions.add(cond);
                }
            }
        }
        return conditions.isEmpty() ? null : DSL.and(conditions);
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
}
