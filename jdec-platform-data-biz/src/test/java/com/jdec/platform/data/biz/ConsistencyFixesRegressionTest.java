package com.jdec.platform.data.biz;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.data.api.dto.common.DataRow;
import com.jdec.platform.data.api.dto.request.FilterItem;
import com.jdec.platform.data.api.dto.request.OptionReq;
import com.jdec.platform.data.api.dto.request.PageQueryReq;
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.data.api.dto.request.SingleTableQueryReq;
import com.jdec.platform.data.api.dto.request.SortItem;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.api.model.predicate.ColumnRef;
import com.jdec.platform.data.api.model.predicate.ComparisonPredicate;
import com.jdec.platform.data.api.model.predicate.CompoundPredicate;
import com.jdec.platform.data.api.model.predicate.FunctionExpr;
import com.jdec.platform.data.api.model.predicate.Predicate;
import com.jdec.platform.data.biz.plan.compiler.PredicateCompiler;
import com.jdec.platform.data.biz.service.SingleTableQueryService;
import com.jdec.platform.shared.datasource.JooqDataContextFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("7 处自洽性修复回归校验测试")
class ConsistencyFixesRegressionTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("P0: FilterDeserializer 生效校验 - 支持 and 复合对象、数组简写及原子比较对象")
    void testFilterDeserializerEffectiveness() throws Exception {
        // 1. 测试 {"and": [...]} 复合逻辑
        String jsonAnd =
                """
                {
                    "view": "COMPANY",
                    "filter": {
                        "and": [
                            {"field": "info.status", "op": "EQ", "value": 1},
                            {"field": "info.name", "op": "LIKE", "value": "test"}
                        ]
                    }
                }
                """;
        QueryReq req1 = mapper.readValue(jsonAnd, QueryReq.class);
        assertNotNull(req1.getFilter());
        assertInstanceOf(CompoundPredicate.class, req1.getFilter());
        CompoundPredicate comp1 = (CompoundPredicate) req1.getFilter();
        assertEquals("AND", comp1.getOperator());
        assertEquals(2, comp1.getPredicates().size());

        // 2. 测试 [...] 隐式 AND 数组简写
        String jsonArray =
                """
                {
                    "view": "COMPANY",
                    "filter": [
                        {"field": "info.status", "op": "EQ", "value": 1},
                        {"field": "info.name", "op": "LIKE", "value": "test"}
                    ]
                }
                """;
        QueryReq req2 = mapper.readValue(jsonArray, QueryReq.class);
        assertNotNull(req2.getFilter());
        assertInstanceOf(CompoundPredicate.class, req2.getFilter());

        // 3. 测试单原子比较对象
        String jsonAtom =
                """
                {
                    "view": "COMPANY",
                    "filter": {"field": "info.status", "op": "EQ", "value": 1}
                }
                """;
        QueryReq req3 = mapper.readValue(jsonAtom, QueryReq.class);
        assertNotNull(req3.getFilter());
        assertInstanceOf(ComparisonPredicate.class, req3.getFilter());
        ComparisonPredicate comp3 = (ComparisonPredicate) req3.getFilter();
        assertEquals("info.status", comp3.getField());
        assertEquals(1, comp3.getValue());

        // 4. 测试 PageQueryReq 继承
        PageQueryReq pageReq = mapper.readValue(jsonAnd, PageQueryReq.class);
        assertNotNull(pageReq.getFilter());
        assertInstanceOf(CompoundPredicate.class, pageReq.getFilter());
    }

    @Test
    @DisplayName("P1: 非法表过滤校验拦截 - 引用不属于模块树的物理表时由 TableQueryValidator 快速失败")
    void testIllegalTableFilterThrowsException() {
        ComparisonPredicate foreignPred = ComparisonPredicate.eq("unknown_table.name", "张三");
        QueryReq req =
                QueryReq.builder()
                        .view("COMPANY")
                        .columns(List.of("info.id"))
                        .filter(foreignPred)
                        .build();

        // 构造最小空模块树
        com.jdec.platform.config.api.dto.common.ModuleTreeDTO tree =
                new com.jdec.platform.config.api.dto.common.ModuleTreeDTO();
        com.jdec.platform.config.api.dto.common.ModuleInfo root =
                new com.jdec.platform.config.api.dto.common.ModuleInfo();
        root.setModuleId(1L);
        root.setModuleCode("COMPANY");
        root.setPrimaryTable("info");
        tree.setRootModule(root);
        tree.setModuleMap(Map.of(1L, root));

        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                com.jdec.platform.data.biz.plan.compiler.TableQueryValidator
                                        .validate(req, tree));
        assertTrue(ex.getMessage().contains("unknown_table"));
        assertTrue(ex.getMessage().contains("不属于当前模块查询范围"));
    }

    @Test
    @DisplayName(
            "P1: Predicate 序列化闭环 (Roundtrip) - 序列化后的 JSON 能够 100% 读回无 UnrecognizedPropertyException")
    void testPredicateRoundtripSerialization() throws Exception {
        ComparisonPredicate pred = ComparisonPredicate.eq("info.company_name", "科技公司");
        String json = mapper.writeValueAsString(pred);

        // 验证写出的 JSON 不破坏回读
        Predicate roundtrip = mapper.readValue(json, Predicate.class);
        assertNotNull(roundtrip);
        assertInstanceOf(ComparisonPredicate.class, roundtrip);
        ComparisonPredicate casted = (ComparisonPredicate) roundtrip;
        assertEquals("info.company_name", casted.getField());
        assertEquals("科技公司", casted.getValue());

        // 验证 ColumnRef 直接回读
        ColumnRef col = ColumnRef.of("info", "name");
        String colJson = mapper.writeValueAsString(col);
        ColumnRef roundtripCol = mapper.readValue(colJson, ColumnRef.class);
        assertEquals("info", roundtripCol.getTableName());
        assertEquals("name", roundtripCol.getColumnName());

        // 验证 SortItem 虚拟 getter 不导致反序列化报错
        SortItem sort = SortItem.desc("info.created_date");
        String sortJson = mapper.writeValueAsString(sort);
        SortItem roundtripSort = mapper.readValue(sortJson, SortItem.class);
        assertEquals("info.created_date", roundtripSort.getField());
        assertEquals("DESC", roundtripSort.getDirection());
    }

    @Test
    @DisplayName(
            "P2: DataPage.getRecords() 安全转换 - List<Map> 能够平滑转为 List<DataRow>，避免 ClassCastException")
    void testDataPageRecordsSafeCast() {
        Map<String, Object> record = Map.of("id", 1001L, "name", "测试数据");
        DataPage<List<Map<String, Object>>> page = DataPage.of(1, 20, 1L, List.of(record));

        List<DataRow> rows = page.getRecords();
        assertNotNull(rows);
        assertEquals(1, rows.size());

        DataRow firstRow = rows.get(0);
        assertNotNull(firstRow);
        assertEquals(1001L, firstRow.getValue("id"));
        assertEquals("测试数据", firstRow.getValue("name"));
    }

    @Test
    @DisplayName("N5 / P0: OptionReq 支持复合 filter 解析与反序列化")
    void testOptionReqCompoundFilter() throws Exception {
        String jsonOption =
                """
                {
                    "view": "COMPANY",
                    "tableName": "info",
                    "columnName": "status",
                    "filter": {
                        "or": [
                            {"field": "info.status", "op": "EQ", "value": 1},
                            {"field": "info.status", "op": "EQ", "value": 2}
                        ]
                    }
                }
                """;
        OptionReq optionReq = mapper.readValue(jsonOption, OptionReq.class);
        assertNotNull(optionReq.getFilter());
        assertInstanceOf(CompoundPredicate.class, optionReq.getFilter());
        CompoundPredicate orPred = (CompoundPredicate) optionReq.getFilter();
        assertEquals("OR", orPred.getOperator());
        assertEquals(2, orPred.getPredicates().size());
    }

    @Test
    @DisplayName("N3 / N5: FunctionExpr 序列化无重复 exprType 且 100% 回读为 FunctionExpr")
    void testFunctionExprSerializationRoundtrip() throws Exception {
        FunctionExpr fn = FunctionExpr.jsonExtract("info", "extra", "$.title");
        ComparisonPredicate fnPred =
                ComparisonPredicate.builder().expression(fn).operator("EQ").value("科技").build();

        String fnJson = mapper.writeValueAsString(fnPred);

        // 验证 exprType 不重复写出两次 (FunctionExpr 本身 1 次，内部嵌套的 targetColumn 1 次)
        String colJson = mapper.writeValueAsString(ColumnRef.of("info", "extra"));
        int firstColExpr = colJson.indexOf("\"exprType\"");
        assertEquals(
                -1, colJson.indexOf("\"exprType\"", firstColExpr + 1), "ColumnRef 不应重复输出 exprType");

        int fnOccurrences = countOccurrences(fnJson, "\"exprType\":\"function\"");
        int colOccurrences = countOccurrences(fnJson, "\"exprType\":\"column\"");
        assertEquals(1, fnOccurrences, "FunctionExpr 自身 exprType 只能出现一次");
        assertEquals(1, colOccurrences, "嵌套 targetColumn 的 exprType 只能出现一次");
        assertFalse(
                fnJson.contains("\"field\":null"),
                "FunctionExpr 序列化不应输出 \"field\":null，实际 JSON=" + fnJson);

        // 验证回读后 expression 保留为 FunctionExpr 实体而不是丢失退化为 ColumnRef
        Predicate roundtrip = mapper.readValue(fnJson, Predicate.class);
        assertNotNull(roundtrip);
        assertInstanceOf(ComparisonPredicate.class, roundtrip);
        ComparisonPredicate casted = (ComparisonPredicate) roundtrip;
        assertNotNull(casted.getExpression());
        assertInstanceOf(FunctionExpr.class, casted.getExpression());
        FunctionExpr parsedFn = (FunctionExpr) casted.getExpression();
        assertEquals("JSON_EXTRACT", parsedFn.getFunctionName());
        assertNotNull(parsedFn.getTargetColumn());
        assertEquals("info", parsedFn.getTargetColumn().getTableName());
        assertEquals("extra", parsedFn.getTargetColumn().getColumnName());
        assertEquals("科技", casted.getValue());
    }

    @Test
    @DisplayName("R4-2: PredicateCompiler 契约 - 独立编译时仅提取本地物理可见子集，不误抛不可达异常")
    void testPredicateCompilerReturnsLocallyVisibleSubsetForOrCompound() {
        CompoundPredicate orPred =
                CompoundPredicate.or(
                        ComparisonPredicate.eq("info.status", 1),
                        ComparisonPredicate.like("member_contact.contact_value", "1358"));

        // PredicateCompiler 在 info 上编译时仅提取本地可见子集，整树跨表/EXISTS 由 QueryPlanCompiler 统一处理
        org.jooq.Condition cond = PredicateCompiler.compile(orPred, "info", Set.of("info"));
        assertNotNull(cond);
        assertTrue(cond.toString().contains("status"));
        assertFalse(cond.toString().contains("contact_value"));
    }

    @Test
    @DisplayName("方案A契约: 外表 deleted 条件在单表作用域内被略过，且服务不隐式追加主表 deleted")
    void testSingleTableQueryWithOtherTableDeletedDoesNotDropBaseDeleted() {
        List<String> executedSqls = new ArrayList<>();
        MockDataProvider provider =
                ctx -> {
                    executedSqls.add(ctx.sql());
                    DSLContext c = DSL.using(SQLDialect.MYSQL);
                    var fId = DSL.field("id", Long.class);
                    var fSourceId = DSL.field("source_id", Long.class);
                    var res = c.newResult(fId, fSourceId);
                    var r = c.newRecord(fId, fSourceId);
                    r.setValue(fId, 100L);
                    r.setValue(fSourceId, 1L);
                    res.add(r);
                    return new MockResult[] {new MockResult(1, res)};
                };

        MockConnection conn = new MockConnection(provider);
        JooqDataContextFactory mockFactory = mock(JooqDataContextFactory.class);
        when(mockFactory.getContext()).thenReturn(DSL.using(conn, SQLDialect.MYSQL));

        SingleTableQueryService service = new SingleTableQueryService(mockFactory);

        // 传入属于 other_table 的 deleted 过滤项
        service.querySingleTableList(
                SingleTableQueryReq.builder()
                        .tableName("info_change")
                        .columns(List.of("id", "source_id"))
                        .filters(List.of(FilterItem.eq("other_table", "deleted", (byte) 0)))
                        .limit(1)
                        .build());

        assertFalse(executedSqls.isEmpty());
        String sql = executedSqls.get(0);
        assertFalse(sql.contains("other_table"), "外表 other_table 过滤项在单表物理作用域内必须被略过，实际 SQL=" + sql);
        assertFalse(
                sql.contains("`info_change`.`deleted` = ?"),
                "调用方未传入 info_change.deleted 时，服务不隐式注入，实际 SQL=" + sql);
    }

    @Test
    @DisplayName("方案A契约: 单表查询将 deleted 视为普通字段参数，不隐式追加 deleted = 0，忠实按调用方 AST 执行")
    void testSingleTableQueryDoesNotDuplicateDeletedPredicate() {
        List<String> executedSqls = new ArrayList<>();
        MockDataProvider provider =
                ctx -> {
                    executedSqls.add(ctx.sql());
                    DSLContext c = DSL.using(SQLDialect.MYSQL);
                    var fId = DSL.field("id", Long.class);
                    var fSourceId = DSL.field("source_id", Long.class);
                    var res = c.newResult(fId, fSourceId);
                    var r = c.newRecord(fId, fSourceId);
                    r.setValue(fId, 100L);
                    r.setValue(fSourceId, 1L);
                    res.add(r);
                    return new MockResult[] {new MockResult(1, res)};
                };

        MockConnection conn = new MockConnection(provider);
        JooqDataContextFactory mockFactory = mock(JooqDataContextFactory.class);
        when(mockFactory.getContext()).thenReturn(DSL.using(conn, SQLDialect.MYSQL));

        SingleTableQueryService service = new SingleTableQueryService(mockFactory);

        // 1. 调用方显式传 deleted = 0 场景
        service.querySingleTableList(
                SingleTableQueryReq.builder()
                        .tableName("info_change")
                        .columns(List.of("id", "source_id"))
                        .filters(
                                List.of(
                                        FilterItem.eq("info_change", "id", 100L),
                                        FilterItem.eq("info_change", "deleted", (byte) 0)))
                        .limit(1)
                        .build());

        assertFalse(executedSqls.isEmpty());
        String sqlWithExplicit = executedSqls.get(0);
        int occurrences = countOccurrences(sqlWithExplicit, "`info_change`.`deleted` = ?");
        assertEquals(1, occurrences, "显式传 deleted 过滤项时，SQL 中 deleted 谓词出现一次，实际=" + sqlWithExplicit);

        // 2. 调用方未传 deleted 时，服务作为通用原子查询器，不隐式追加 deleted 谓词，保持执行透明
        executedSqls.clear();
        service.querySingleTableList(
                SingleTableQueryReq.builder()
                        .tableName("info_change")
                        .columns(List.of("id", "source_id"))
                        .filters(List.of(FilterItem.eq("info_change", "id", 100L)))
                        .limit(1)
                        .build());

        assertFalse(executedSqls.isEmpty());
        String sqlWithoutExplicit = executedSqls.get(0);
        int occurrencesWithout =
                countOccurrences(sqlWithoutExplicit, "`info_change`.`deleted` = ?");
        assertEquals(
                0,
                occurrencesWithout,
                "方案A契约：未显式传 deleted 时，服务不自动隐式注入 deleted，避免特例魔法与副作用，实际=" + sqlWithoutExplicit);
    }

    @Test
    @DisplayName("方案A契约: 嵌套 FilterItem 显式携带 deleted 时，SQL 忠实按 AST 输出")
    void testSingleTableQueryWithNestedDeletedFilterDoesNotDuplicateDeleted() {
        List<String> executedSqls = new ArrayList<>();
        MockDataProvider provider =
                ctx -> {
                    executedSqls.add(ctx.sql());
                    DSLContext c = DSL.using(SQLDialect.MYSQL);
                    var fId = DSL.field("id", Long.class);
                    var res = c.newResult(fId);
                    var r = c.newRecord(fId);
                    r.setValue(fId, 100L);
                    res.add(r);
                    return new MockResult[] {new MockResult(1, res)};
                };

        MockConnection conn = new MockConnection(provider);
        JooqDataContextFactory mockFactory = mock(JooqDataContextFactory.class);
        when(mockFactory.getContext()).thenReturn(DSL.using(conn, SQLDialect.MYSQL));

        SingleTableQueryService service = new SingleTableQueryService(mockFactory);

        // 构造嵌套 FilterItem: children=[info_change.deleted = 0]
        FilterItem nestedItem =
                FilterItem.builder()
                        .logic("AND")
                        .children(List.of(FilterItem.eq("info_change", "deleted", (byte) 0)))
                        .build();

        service.querySingleTableList(
                SingleTableQueryReq.builder()
                        .tableName("info_change")
                        .columns(List.of("id"))
                        .filters(List.of(nestedItem))
                        .limit(1)
                        .build());

        assertFalse(executedSqls.isEmpty());
        String sql = executedSqls.get(0);
        int occurrences = countOccurrences(sql, "`info_change`.`deleted` = ?");
        assertEquals(
                1, occurrences, "嵌套 FilterItem 显式携带当前表 deleted 时，SQL 中 deleted 只出现一次，实际=" + sql);
    }

    @Test
    @DisplayName("R6-2: 单表查询复合过滤混杂外表字段时，仅编译保留本地可见子集且不抛异常")
    void testSingleTableQueryWithForeignTableKeepsLocalSubset() {
        List<String> executedSqls = new ArrayList<>();
        MockDataProvider provider =
                ctx -> {
                    executedSqls.add(ctx.sql());
                    DSLContext c = DSL.using(SQLDialect.MYSQL);
                    var fId = DSL.field("id", Long.class);
                    var res = c.newResult(fId);
                    var r = c.newRecord(fId);
                    r.setValue(fId, 100L);
                    res.add(r);
                    return new MockResult[] {new MockResult(1, res)};
                };

        MockConnection conn = new MockConnection(provider);
        JooqDataContextFactory mockFactory = mock(JooqDataContextFactory.class);
        when(mockFactory.getContext()).thenReturn(DSL.using(conn, SQLDialect.MYSQL));

        SingleTableQueryService service = new SingleTableQueryService(mockFactory);

        // 构造复合 FilterItem: OR(info_change.id=100, other_table.status=1)
        FilterItem mixedOrItem =
                FilterItem.builder()
                        .logic("OR")
                        .children(
                                List.of(
                                        FilterItem.eq("info_change", "id", 100L),
                                        FilterItem.eq("other_table", "status", 1)))
                        .build();

        service.querySingleTableList(
                SingleTableQueryReq.builder()
                        .tableName("info_change")
                        .columns(List.of("id"))
                        .filters(List.of(mixedOrItem))
                        .limit(1)
                        .build());

        assertFalse(executedSqls.isEmpty());
        String sql = executedSqls.get(0);
        assertTrue(sql.contains("`info_change`.`id` = ?"), "本地可见的 info_change.id 应被保留，实际=" + sql);
        assertFalse(sql.contains("other_table"), "外表 other_table 在单表作用域内不应进入 SQL，实际=" + sql);
    }

    @Test
    @DisplayName("方案A契约: 调用方传入含 deleted 过滤的复合 OR 条件时，SQL 忠实翻译无魔法副作用")
    void testSingleTableQueryWithOrCompoundFaithfullyExecutes() {
        List<String> executedSqls = new ArrayList<>();
        MockDataProvider provider =
                ctx -> {
                    executedSqls.add(ctx.sql());
                    DSLContext c = DSL.using(SQLDialect.MYSQL);
                    var fId = DSL.field("id", Long.class);
                    var res = c.newResult(fId);
                    var r = c.newRecord(fId);
                    r.setValue(fId, 100L);
                    res.add(r);
                    return new MockResult[] {new MockResult(1, res)};
                };

        MockConnection conn = new MockConnection(provider);
        JooqDataContextFactory mockFactory = mock(JooqDataContextFactory.class);
        when(mockFactory.getContext()).thenReturn(DSL.using(conn, SQLDialect.MYSQL));

        SingleTableQueryService service = new SingleTableQueryService(mockFactory);

        // 构造复合 FilterItem: OR(info_change.id=100, info_change.deleted=1)
        FilterItem orWithDeleted =
                FilterItem.builder()
                        .logic("OR")
                        .children(
                                List.of(
                                        FilterItem.eq("info_change", "id", 100L),
                                        FilterItem.eq("info_change", "deleted", (byte) 1)))
                        .build();

        service.querySingleTableList(
                SingleTableQueryReq.builder()
                        .tableName("info_change")
                        .columns(List.of("id"))
                        .filters(List.of(orWithDeleted))
                        .limit(1)
                        .build());

        assertFalse(executedSqls.isEmpty());
        String sql = executedSqls.get(0);
        assertTrue(
                sql.contains("or `info_change`.`deleted` = ?"), "OR 中的 deleted 必须忠实保留，实际=" + sql);
        assertEquals(
                1,
                countOccurrences(sql, "`info_change`.`deleted` = ?"),
                "deleted 仅来自调用方 AST，无额外隐式追加");
    }

    private static int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
