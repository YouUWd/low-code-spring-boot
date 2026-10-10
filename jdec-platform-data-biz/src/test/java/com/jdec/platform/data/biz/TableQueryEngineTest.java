package com.jdec.platform.data.biz;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.common.ProjectTableRelationsDTO;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.common.DataRow;
import com.jdec.platform.data.api.dto.request.TableDetailQueryReq;
import com.jdec.platform.data.api.dto.request.TableFilterItem;
import com.jdec.platform.data.api.dto.request.TableOptionReq;
import com.jdec.platform.data.api.dto.request.TablePageQueryReq;
import com.jdec.platform.data.api.dto.request.TableQueryReq;
import com.jdec.platform.data.api.dto.request.TableSpec;
import com.jdec.platform.data.api.dto.response.DataOption;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.biz.plan.assembler.TreeResultAssembler;
import com.jdec.platform.data.biz.plan.compiler.QueryPlanCompiler;
import com.jdec.platform.data.biz.plan.executor.QueryPlanExecutor;
import com.jdec.platform.data.biz.service.DataQueryService;
import com.jdec.platform.shared.datasource.JooqDataContextFactory;
import java.util.*;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 物理表驱动查询引擎单元测试 */
@DisplayName("物理表结构化查询引擎测试")
public class TableQueryEngineTest {

    private DataQueryService dataQueryService;
    private final List<String> executedSqls = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ModuleInfo rootInfo =
                ModuleInfo.builder()
                        .moduleId(10L)
                        .moduleName("企业信息")
                        .primaryTable("company_info")
                        .parentId(0L)
                        .fields(Collections.emptyList())
                        .build();

        ModuleInfo childInfo =
                ModuleInfo.builder()
                        .moduleId(20L)
                        .moduleName("成员信息")
                        .primaryTable("company_member")
                        .parentId(10L)
                        .fields(Collections.emptyList())
                        .build();

        Map<Long, ModuleInfo> moduleMap = new LinkedHashMap<>();
        moduleMap.put(10L, rootInfo);
        moduleMap.put(20L, childInfo);

        Map<Long, List<Long>> p2c = new LinkedHashMap<>();
        p2c.put(10L, List.of(20L));

        ModuleTreeDTO tree =
                ModuleTreeDTO.builder()
                        .rootModuleId(10L)
                        .projectNo("company")
                        .rootModule(rootInfo)
                        .moduleMap(moduleMap)
                        .parentToChildren(p2c)
                        .fieldMap(Collections.emptyMap())
                        .fieldToModuleMap(Collections.emptyMap())
                        .allowedModuleIds(Set.of(10L, 20L))
                        .build();

        TableRelationDTO r =
                TableRelationDTO.builder()
                        .mainTable("company_info")
                        .mainField("id")
                        .joinTable("company_member")
                        .joinField("company_id")
                        .relationType("ONE_TO_MANY")
                        .build();

        ProjectTableRelationsDTO relations =
                ProjectTableRelationsDTO.builder()
                        .projectNo("company")
                        .allRelations(List.of(r))
                        .forwardRelations(Map.of("company_info", List.of(r)))
                        .reverseRelations(Collections.emptyMap())
                        .build();

        ConfigApi configApi =
                mock(
                        ConfigApi.class,
                        invocation -> {
                            String name = invocation.getMethod().getName();
                            if ("getModuleTree".equals(name)) {
                                return tree;
                            }
                            if ("getProjectTableRelations".equals(name)) {
                                return relations;
                            }
                            return null;
                        });

        executedSqls.clear();
        MockDataProvider provider =
                ctx -> {
                    String rawSql = ctx.sql();
                    executedSqls.add(rawSql);
                    String sql = rawSql.toLowerCase();
                    if (sql.contains("count(")) {
                        Field<Long> countField = DSL.field("count", Long.class);
                        var res = DSL.using(SQLDialect.MYSQL).newResult(countField);
                        var record = DSL.using(SQLDialect.MYSQL).newRecord(countField);
                        record.setValue(countField, 1L);
                        res.add(record);
                        return new MockResult[] {new MockResult(1, res)};
                    }

                    if (sql.contains("select distinct")) {
                        Field<String> name = DSL.field("company_name", String.class);
                        var res = DSL.using(SQLDialect.MYSQL).newResult(name);
                        var record = DSL.using(SQLDialect.MYSQL).newRecord(name);
                        record.setValue(name, "测试企业A");
                        res.add(record);
                        return new MockResult[] {new MockResult(1, res)};
                    }

                    if (sql.contains("from `company_info`")) {
                        Field<Long> id = DSL.field("id", Long.class);
                        Field<String> name = DSL.field("company_name", String.class);
                        Field<String> code = DSL.field("company_code", String.class);
                        Field<?>[] fs = new Field<?>[] {id, name, code};
                        var res = DSL.using(SQLDialect.MYSQL).newResult(fs);
                        var record = DSL.using(SQLDialect.MYSQL).newRecord(fs);
                        record.setValue(id, 100L);
                        record.setValue(name, "测试企业A");
                        record.setValue(code, "CORP-100");
                        res.add(record);
                        return new MockResult[] {new MockResult(1, res)};
                    }

                    if (sql.contains("from `company_member`")) {
                        Field<Long> id = DSL.field("id", Long.class);
                        Field<Long> compId = DSL.field("company_id", Long.class);
                        Field<String> mName = DSL.field("member_name", String.class);
                        Field<?>[] fs = new Field<?>[] {id, compId, mName};
                        var res = DSL.using(SQLDialect.MYSQL).newResult(fs);
                        var record = DSL.using(SQLDialect.MYSQL).newRecord(fs);
                        record.setValue(id, 1L);
                        record.setValue(compId, 100L);
                        record.setValue(mName, "张三");
                        res.add(record);
                        return new MockResult[] {new MockResult(1, res)};
                    }

                    return new MockResult[] {
                        new MockResult(0, DSL.using(SQLDialect.MYSQL).newResult())
                    };
                };

        MockConnection conn = new MockConnection(provider);
        DSLContext dsl = DSL.using(conn, SQLDialect.MYSQL);

        JooqDataContextFactory dcf = new FakeFactory(dsl);
        QueryPlanCompiler compiler = new QueryPlanCompiler(configApi);
        TreeResultAssembler assembler = new TreeResultAssembler();
        QueryPlanExecutor executor = new QueryPlanExecutor(dcf, assembler);
        dataQueryService = new DataQueryService(compiler, executor);
    }

    @Test
    @DisplayName("全量物理树详情查询：自动递归抓取主表与子表全部字段")
    void testGetFullDetail() {
        TableDetailQueryReq req = TableDetailQueryReq.builder().moduleId(10L).id(100L).build();

        DataBlock block = dataQueryService.getDetailByTable(req);
        assertNotNull(block);
        assertEquals(10L, block.getModuleId());
        assertEquals(1, block.getRecords().size());

        DataRow row = block.getRecords().get(0);
        assertNotNull(row.getTable("company_info"));
        Map<String, Object> compInfo = row.getTable("company_info");
        assertEquals(100L, compInfo.get("id"));
        assertEquals("测试企业A", compInfo.get("company_name"));

        assertNotNull(row.getChildren());
        assertEquals(1, row.getChildren().size());
        DataBlock childBlock = row.getChildren().get(0);
        assertEquals(20L, childBlock.getModuleId());
        assertEquals(1, childBlock.getRecords().size());
        DataRow childRow = childBlock.getRecords().get(0);
        assertNotNull(childRow.getTable("company_member"));
        Map<String, Object> memberInfo = childRow.getTable("company_member");
        assertEquals("张三", memberInfo.get("member_name"));
    }

    @Test
    @DisplayName("结构化 TableSpec 物理投影分页查询")
    void testTablePageQuery() {
        TablePageQueryReq req =
                TablePageQueryReq.builder()
                        .moduleId(10L)
                        .pageNo(1)
                        .pageSize(10)
                        .tables(
                                List.of(
                                        TableSpec.of("company_info", List.of("id", "company_name")),
                                        TableSpec.of(
                                                "company_member", List.of("id", "member_name"))))
                        .filters(
                                List.of(
                                        TableFilterItem.builder()
                                                .tableName("company_info")
                                                .columnName("company_code")
                                                .operator("eq")
                                                .value("CORP-100")
                                                .build()))
                        .build();

        DataPage<DataBlock> page = dataQueryService.queryByTable(req);
        assertNotNull(page);
        assertEquals(1, page.getTotal());
        DataBlock data = page.getData();
        assertNotNull(data);
        assertEquals(1, data.getRecords().size());
    }

    @Test
    @DisplayName("Filter 自身条件与其 children 的自然逻辑组合 (OR 组合测试)")
    void testFilterSelfAndChildrenOrCombination() {
        executedSqls.clear();
        TableFilterItem hybridOrFilter =
                TableFilterItem.builder()
                        .tableName("company_info")
                        .columnName("company_name")
                        .operator("LIKE")
                        .value("科技")
                        .logic("OR")
                        .children(
                                List.of(
                                        TableFilterItem.builder()
                                                .tableName("company_info")
                                                .columnName("id")
                                                .operator("EQ")
                                                .value(100L)
                                                .build(),
                                        TableFilterItem.builder()
                                                .tableName("company_info")
                                                .columnName("company_code")
                                                .operator("EQ")
                                                .value("CORP-100")
                                                .build()))
                        .build();

        TablePageQueryReq req =
                TablePageQueryReq.builder()
                        .moduleId(10L)
                        .pageNo(1)
                        .pageSize(10)
                        .filters(List.of(hybridOrFilter))
                        .build();

        DataPage<DataBlock> page = dataQueryService.queryByTable(req);
        assertNotNull(page);
        assertEquals(1, page.getTotal());
        assertNotNull(page.getData());
        assertEquals(1, page.getData().getRecords().size());

        // 验证生成的 SQL 结构中：自身条件与 (child1 AND child2) 之间使用 OR 连接
        assertFalse(executedSqls.isEmpty());
        String countSql = executedSqls.get(0).toLowerCase();
        assertTrue(
                countSql.contains("or") && countSql.contains("and"),
                "Generated SQL should combine self and children with OR while children with AND: "
                        + countSql);
    }

    @Test
    @DisplayName("投影测试: 未指定列或指定 * 时生成 SELECT *")
    void testAsteriskProjection() {
        executedSqls.clear();
        TablePageQueryReq req =
                TablePageQueryReq.builder()
                        .moduleId(10L)
                        .pageNo(1)
                        .pageSize(10)
                        .tables(
                                List.of(
                                        TableSpec.builder()
                                                .tableName("company_info")
                                                .columns(List.of("*"))
                                                .build()))
                        .build();

        DataPage<DataBlock> page = dataQueryService.queryByTable(req);
        assertNotNull(page);
        assertEquals(1, page.getTotal());

        // 验证执行的查询 SQL 中包含 select *
        boolean hasSelectStar =
                executedSqls.stream()
                        .map(String::toLowerCase)
                        .anyMatch(sql -> sql.contains("select * from `company_info`"));
        assertTrue(
                hasSelectStar,
                "Generated query SQL should use SELECT * when * is requested: " + executedSqls);
    }

    @Test
    @DisplayName("投影测试: 指定特定列名时生成精准 SELECT 投影列")
    void testSpecificColumnProjection() {
        executedSqls.clear();
        TablePageQueryReq req =
                TablePageQueryReq.builder()
                        .moduleId(10L)
                        .pageNo(1)
                        .pageSize(10)
                        .tables(
                                List.of(
                                        TableSpec.builder()
                                                .tableName("company_info")
                                                .columns(List.of("company_name"))
                                                .build()))
                        .build();

        DataPage<DataBlock> page = dataQueryService.queryByTable(req);
        assertNotNull(page);
        assertEquals(1, page.getTotal());

        // 验证执行的查询 SQL 中包含精准列名且不为 select *
        boolean hasSpecificCol =
                executedSqls.stream()
                        .map(String::toLowerCase)
                        .anyMatch(
                                sql ->
                                        sql.contains("company_name")
                                                && !sql.contains("select * from `company_info`")
                                                && !sql.contains("count("));
        assertTrue(
                hasSpecificCol,
                "Generated query SQL should project specific columns: " + executedSqls);
    }

    @Test
    @DisplayName("物理表计数查询测试: countByTable")
    void testCountByTable() {
        executedSqls.clear();
        TableQueryReq req =
                TableQueryReq.builder()
                        .moduleId(10L)
                        .filters(
                                List.of(
                                        TableFilterItem.builder()
                                                .tableName("company_info")
                                                .columnName("id")
                                                .operator("EQ")
                                                .value(100L)
                                                .build()))
                        .build();

        Long count = dataQueryService.countByTable(req);
        assertNotNull(count);
        assertEquals(1L, count);

        // 验证执行了 COUNT 查询
        boolean hasCountQuery =
                executedSqls.stream()
                        .map(String::toLowerCase)
                        .anyMatch(sql -> sql.contains("count("));
        assertTrue(hasCountQuery, "Should execute COUNT query: " + executedSqls);
    }

    @Test
    @DisplayName("物理表字段下拉候选项查询测试: getOptionsByTable")
    void testOptionsByTable() {
        executedSqls.clear();
        TableOptionReq req =
                TableOptionReq.builder()
                        .moduleId(10L)
                        .tableName("company_info")
                        .columnName("company_name")
                        .direction("ASC")
                        .limit(50)
                        .build();

        List<DataOption> options = dataQueryService.getOptionsByTable(req);
        assertNotNull(options);
        assertFalse(options.isEmpty());
        assertEquals("测试企业A", options.get(0).getLabel());

        // 验证执行了 SELECT DISTINCT 查询
        boolean hasDistinctQuery =
                executedSqls.stream()
                        .map(String::toLowerCase)
                        .anyMatch(sql -> sql.contains("distinct") && sql.contains("company_name"));
        assertTrue(hasDistinctQuery, "Should execute SELECT DISTINCT query: " + executedSqls);
    }

    static class FakeFactory extends JooqDataContextFactory {
        private final DSLContext dsl;

        FakeFactory(DSLContext dsl) {
            super(null);
            this.dsl = dsl;
        }

        @Override
        public DSLContext getContext() {
            return dsl;
        }

        @Override
        public DSLContext getContext(String projectNo) {
            return dsl;
        }
    }
}
