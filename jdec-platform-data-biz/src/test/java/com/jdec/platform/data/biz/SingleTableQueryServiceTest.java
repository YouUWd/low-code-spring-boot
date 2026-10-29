package com.jdec.platform.data.biz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.data.api.dto.request.SingleTableQueryReq;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.biz.service.SingleTableQueryService;
import com.jdec.platform.shared.datasource.JooqDataContextFactory;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.jooq.JSON;
import org.jooq.JSONB;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SingleTableQueryServiceTest {

    @Mock private JooqDataContextFactory jooqDataContextFactory;

    private SingleTableQueryService singleTableQueryService;
    private DSLContext dslContext;

    @BeforeEach
    void setUp() {
        MockDataProvider provider =
                ctx -> {
                    String sql = ctx.sql().toLowerCase();
                    DSLContext c = DSL.using(SQLDialect.MYSQL);

                    if (sql.contains("count(")) {
                        var res = c.newResult(DSL.field("count", Long.class));
                        var r = c.newRecord(DSL.field("count", Long.class));
                        r.setValue(DSL.field("count", Long.class), 1L);
                        res.add(r);
                        return new MockResult[] {new MockResult(1, res)};
                    }

                    var fId = DSL.field("id", Long.class);
                    var fName = DSL.field("name", String.class);
                    var fJooqJson = DSL.field("tags_json", JSON.class);
                    var fJooqJsonb = DSL.field("attrs_jsonb", JSONB.class);
                    var fStringJson = DSL.field("extra_str", String.class);

                    var res = c.newResult(fId, fName, fJooqJson, fJooqJsonb, fStringJson);
                    var r = c.newRecord(fId, fName, fJooqJson, fJooqJsonb, fStringJson);
                    r.setValue(fId, 100L);
                    r.setValue(fName, "测试记录");
                    r.setValue(fJooqJson, JSON.json("[\"tag1\", \"tag2\"]"));
                    r.setValue(fJooqJsonb, JSONB.jsonb("{\"key\": \"value\", \"num\": 123}"));
                    r.setValue(fStringJson, "[\"str1\", \"str2\"]");
                    res.add(r);
                    return new MockResult[] {new MockResult(1, res)};
                };

        MockConnection conn = new MockConnection(provider);
        dslContext = DSL.using(conn, SQLDialect.MYSQL);
        when(jooqDataContextFactory.getContext()).thenReturn(dslContext);

        singleTableQueryService =
                new SingleTableQueryService(jooqDataContextFactory, new ObjectMapper());
    }

    @Test
    @DisplayName("测试单表分页查询自动解析 JSON 列为原生 List/Map")
    void testQuerySingleTablePageJsonParsing() {
        SingleTableQueryReq req =
                SingleTableQueryReq.builder()
                        .tableName("test_table")
                        .pageNo(1)
                        .pageSize(10)
                        .build();

        DataPage<List<Map<String, Object>>> page =
                singleTableQueryService.querySingleTablePage(req);
        assertNotNull(page);
        assertEquals(1L, page.getTotal());
        assertNotNull(page.getData());
        assertEquals(1, page.getData().size());

        Map<String, Object> row = page.getData().get(0);
        assertEquals(100L, row.get("id"));
        assertEquals("测试记录", row.get("name"));

        // 验证 tags_json (原 org.jooq.JSON) 转化为 List
        Object tags = row.get("tags_json");
        assertInstanceOf(List.class, tags);
        @SuppressWarnings("unchecked")
        List<String> tagsList = (List<String>) tags;
        assertEquals(List.of("tag1", "tag2"), tagsList);

        // 验证 attrs_jsonb (原 org.jooq.JSONB) 转化为 Map
        Object attrs = row.get("attrs_jsonb");
        assertInstanceOf(Map.class, attrs);
        @SuppressWarnings("unchecked")
        Map<String, Object> attrsMap = (Map<String, Object>) attrs;
        assertEquals("value", attrsMap.get("key"));
        assertEquals(123, attrsMap.get("num"));

        // 验证 extra_str (JSON 字符串) 转化为 List
        Object extra = row.get("extra_str");
        assertInstanceOf(List.class, extra);
        @SuppressWarnings("unchecked")
        List<String> extraList = (List<String>) extra;
        assertEquals(List.of("str1", "str2"), extraList);
    }

    @Test
    @DisplayName("测试单表列表查询自动解析 JSON 列为原生 List/Map")
    void testQuerySingleTableListJsonParsing() {
        SingleTableQueryReq req =
                SingleTableQueryReq.builder().tableName("test_table").limit(5).build();

        List<Map<String, Object>> list = singleTableQueryService.querySingleTableList(req);
        assertNotNull(list);
        assertEquals(1, list.size());
        Map<String, Object> row = list.get(0);
        assertInstanceOf(List.class, row.get("tags_json"));
        assertInstanceOf(Map.class, row.get("attrs_jsonb"));
    }
}
