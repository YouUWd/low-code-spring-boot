package com.jdec.platform.data.biz;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleTableHeaderDTO;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import com.jdec.platform.config.api.dto.response.SysModuleMetaResp;
import com.jdec.platform.data.api.dto.model.EngineModuleMeta;
import com.jdec.platform.data.api.dto.request.BatchDynamicQueryReq;
import com.jdec.platform.data.api.dto.request.DynamicDetailReq;
import com.jdec.platform.data.api.dto.request.DynamicQueryReq;
import com.jdec.platform.data.api.dto.response.BatchEngineDataResult;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.api.dto.response.EngineDataResult;
import com.jdec.platform.data.biz.dsl.JooqConditionBuilder;
import com.jdec.platform.data.biz.dsl.JooqContextFactory;
import com.jdec.platform.data.biz.dsl.JooqSqlBuilder;
import com.jdec.platform.data.biz.service.DynamicQueryService;
import com.jdec.platform.data.biz.service.MetadataCacheService;
import com.jdec.platform.data.biz.service.PermissionFilterService;
import java.util.*;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataEngineQueryTest {

    @Mock private MetadataCacheService metadataCacheService;
    @Mock private PermissionFilterService permissionFilterService;
    @Mock private JooqContextFactory jooqContextFactory;
    @Spy private JooqSqlBuilder jooqSqlBuilder = new JooqSqlBuilder();
    @Spy private JooqConditionBuilder jooqConditionBuilder = new JooqConditionBuilder();

    @InjectMocks private DynamicQueryService dynamicQueryService;

    private DSLContext dslContext;
    private SysModuleMetaResp mockModule101;

    @BeforeEach
    void setUp() {
        MockDataProvider provider =
                new MockDataProvider() {
                    @Override
                    public MockResult[] execute(MockExecuteContext ctx) {
                        String sql = ctx.sql().toLowerCase();
                        DSLContext create = DSL.using(SQLDialect.MYSQL);

                        if (sql.contains("count(*)")) {
                            var result = create.newResult(DSL.field("count", Long.class));
                            var record = create.newRecord(DSL.field("count", Long.class));
                            record.setValue(DSL.field("count", Long.class), 1L);
                            result.add(record);
                            return new MockResult[] {new MockResult(1, result)};
                        }

                        if (sql.contains("from `student_course`")) {
                            var result =
                                    create.newResult(
                                            DSL.field("id", Long.class),
                                            DSL.field("student_id", Long.class),
                                            DSL.field("course_name", String.class));
                            var record =
                                    create.newRecord(
                                            DSL.field("id", Long.class),
                                            DSL.field("student_id", Long.class),
                                            DSL.field("course_name", String.class));
                            record.setValue(DSL.field("id", Long.class), 301L);
                            record.setValue(DSL.field("student_id", Long.class), 1001L);
                            record.setValue(DSL.field("course_name", String.class), "大学英语");
                            result.add(record);
                            return new MockResult[] {new MockResult(1, result)};
                        }

                        var result =
                                create.newResult(
                                        DSL.field("id", Long.class),
                                        DSL.field("student_no", String.class),
                                        DSL.field("name", String.class),
                                        DSL.field("class_name", String.class),
                                        DSL.field("clazz__id", Long.class));
                        var record =
                                create.newRecord(
                                        DSL.field("id", Long.class),
                                        DSL.field("student_no", String.class),
                                        DSL.field("name", String.class),
                                        DSL.field("class_name", String.class),
                                        DSL.field("clazz__id", Long.class));
                        record.setValue(DSL.field("id", Long.class), 1001L);
                        record.setValue(DSL.field("student_no", String.class), "S001");
                        record.setValue(DSL.field("name", String.class), "张三");
                        record.setValue(DSL.field("class_name", String.class), "高三(1)班");
                        record.setValue(DSL.field("clazz__id", Long.class), 201L);
                        result.add(record);

                        return new MockResult[] {new MockResult(1, result)};
                    }
                };

        MockConnection connection = new MockConnection(provider);
        dslContext = DSL.using(connection, SQLDialect.MYSQL);
        lenient().when(jooqContextFactory.getContext()).thenReturn(dslContext);

        // 构造模块 101 (学生全景档案) 元数据
        SysModuleMetaResp.ModuleInfo moduleInfo = new SysModuleMetaResp.ModuleInfo();
        moduleInfo.setId(101L);
        moduleInfo.setModuleCode("MOD-STUDENT");
        moduleInfo.setModuleName("学生综合档案");
        moduleInfo.setParentId(0L);

        mockModule101 = new SysModuleMetaResp();
        mockModule101.setModule(moduleInfo);
        mockModule101.setFields(
                List.of(
                        ModuleFieldDTO.builder()
                                .tableName("student")
                                .columnName("student_no")
                                .build(),
                        ModuleFieldDTO.builder().tableName("student").columnName("name").build(),
                        ModuleFieldDTO.builder()
                                .tableName("clazz")
                                .columnName("class_name")
                                .build(),
                        ModuleFieldDTO.builder()
                                .tableName("student_course")
                                .columnName("course_name")
                                .build()));
        mockModule101.setTableRelations(
                List.of(
                        TableRelationDTO.builder()
                                .mainTable("student")
                                .mainField("clazz_id")
                                .joinTable("clazz")
                                .joinField("id")
                                .relationType("N:1")
                                .build(),
                        TableRelationDTO.builder()
                                .mainTable("student")
                                .mainField("id")
                                .joinTable("student_course")
                                .joinField("student_id")
                                .relationType("1:N")
                                .build()));
        mockModule101.setModuleHeaders(
                List.of(
                        ModuleTableHeaderDTO.builder()
                                .table("student")
                                .field("student_no")
                                .name("学号")
                                .build(),
                        ModuleTableHeaderDTO.builder()
                                .table("student")
                                .field("name")
                                .name("姓名")
                                .build()));

        lenient().when(metadataCacheService.getModuleComplete(101L)).thenReturn(mockModule101);
        lenient()
                .when(permissionFilterService.filterReadableHeaders(mockModule101))
                .thenReturn(mockModule101.getModuleHeaders());
    }

    @Test
    @DisplayName("测试 viewMode=LIST: 列表模式轻量响应 (携带 headers, 不深查 1:N 从表)")
    void testQueryListViewMode() {
        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .moduleId(101L)
                        .viewMode("LIST")
                        .pageNo(1)
                        .pageSize(10)
                        .build();

        EngineDataResult<DataPage<Map<String, Object>>> result = dynamicQueryService.query(req);

        assertNotNull(result);
        EngineModuleMeta meta = result.getMeta();
        assertNotNull(meta);
        assertEquals(101L, meta.getModuleId());
        assertEquals("student", meta.getPrimaryTable());
        assertNotNull(meta.getHeaders());
        assertNull(meta.getFields());

        DataPage<Map<String, Object>> page = result.getData();
        assertEquals(1, page.getTotal());
        assertEquals(1, page.getRecords().size());

        Map<String, Object> row = page.getRecords().get(0);
        assertTrue(row.containsKey("student"));
        assertTrue(row.containsKey("clazz"));
        assertFalse(row.containsKey("student_course"));
    }

    @Test
    @DisplayName("测试 viewMode=DETAIL: 详情模式深度加载 1:N 从表且携带 fields")
    void testQueryDetailViewMode() {
        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .moduleId(101L)
                        .viewMode("DETAIL")
                        .pageNo(1)
                        .pageSize(10)
                        .build();

        EngineDataResult<DataPage<Map<String, Object>>> result = dynamicQueryService.query(req);

        assertNotNull(result);
        EngineModuleMeta meta = result.getMeta();
        assertNotNull(meta.getFields());
        assertNull(meta.getHeaders());

        DataPage<Map<String, Object>> page = result.getData();
        Map<String, Object> row = page.getRecords().get(0);
        assertTrue(row.containsKey("student"));
        assertTrue(row.containsKey("student_course"));

        Object courseObj = row.get("student_course");
        assertTrue(courseObj instanceof List);
        List<?> courseList = (List<?>) courseObj;
        assertEquals(1, courseList.size());
    }

    @Test
    @DisplayName("测试 getDetail 门面方法")
    void testGetDetailFacade() {
        DynamicDetailReq req = DynamicDetailReq.builder().moduleId(101L).id(1001L).build();

        EngineDataResult<Map<String, Object>> result = dynamicQueryService.getDetail(req);

        assertNotNull(result);
        assertNotNull(result.getData());
        assertTrue(result.getData().containsKey("student"));
    }

    @Test
    @DisplayName("测试多模块 batchQuery 并发查询")
    void testBatchQuery() {
        Map<String, DynamicQueryReq> queries = new HashMap<>();
        queries.put(
                "mainStudent", DynamicQueryReq.builder().moduleId(101L).viewMode("LIST").build());

        BatchDynamicQueryReq batchReq = BatchDynamicQueryReq.builder().queries(queries).build();

        BatchEngineDataResult batchResult = dynamicQueryService.batchQuery(batchReq);

        assertNotNull(batchResult);
        assertNotNull(batchResult.getResults());
        assertTrue(batchResult.getResults().containsKey("mainStudent"));
    }
}
