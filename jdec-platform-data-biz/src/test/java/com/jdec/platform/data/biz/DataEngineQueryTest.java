package com.jdec.platform.data.biz;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jdec.platform.config.api.dto.common.ModuleSimpleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleTableDTO;
import com.jdec.platform.config.api.dto.common.ModuleTableHeaderDTO;
import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp;
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

    private SysModuleCompleteResp mockModule101;
    private DSLContext dslContext;

    @BeforeEach
    void setUp() {
        // 创建 jOOQ Mock 数据提供器
        MockDataProvider provider =
                new MockDataProvider() {
                    @Override
                    public MockResult[] execute(MockExecuteContext ctx) {
                        DSLContext create = DSL.using(SQLDialect.MYSQL);
                        String sql = ctx.sql().toLowerCase();

                        if (sql.contains("count(")) {
                            var result = create.newResult(DSL.field("count", Long.class));
                            var record = create.newRecord(DSL.field("count", Long.class));
                            record.setValue(DSL.field("count", Long.class), 1L);
                            result.add(record);
                            return new MockResult[] {new MockResult(1, result)};
                        }

                        // 模拟主表及 N:1 关联从表记录
                        var result =
                                create.newResult(
                                        DSL.field("id", Long.class),
                                        DSL.field("student_no", String.class),
                                        DSL.field("name", String.class),
                                        DSL.field("clazz_name", String.class));
                        var record =
                                create.newRecord(
                                        DSL.field("id", Long.class),
                                        DSL.field("student_no", String.class),
                                        DSL.field("name", String.class),
                                        DSL.field("clazz_name", String.class));
                        record.setValue(DSL.field("id", Long.class), 1001L);
                        record.setValue(DSL.field("student_no", String.class), "S001");
                        record.setValue(DSL.field("name", String.class), "张三");
                        record.setValue(DSL.field("clazz_name", String.class), "计科2601班");
                        result.add(record);

                        return new MockResult[] {new MockResult(1, result)};
                    }
                };

        MockConnection connection = new MockConnection(provider);
        dslContext = DSL.using(connection, SQLDialect.MYSQL);
        lenient().when(jooqContextFactory.getContext()).thenReturn(dslContext);

        // 构造模块 101 (学生全景档案) 元数据
        SysModuleCompleteResp.ModuleInfo moduleInfo = new SysModuleCompleteResp.ModuleInfo();
        moduleInfo.setId(101L);
        moduleInfo.setModuleCode("MOD-STUDENT");
        moduleInfo.setModuleName("学生综合档案");

        mockModule101 = new SysModuleCompleteResp();
        mockModule101.setModule(moduleInfo);
        mockModule101.setModuleTables(
                List.of(
                        ModuleTableDTO.builder()
                                .tableName("student")
                                .isPrimary(1)
                                .relationType(null)
                                .build(),
                        ModuleTableDTO.builder()
                                .tableName("clazz")
                                .isPrimary(0)
                                .relationType("N:1")
                                .joinLeftField("clazz_id")
                                .joinRightField("id")
                                .build(),
                        ModuleTableDTO.builder()
                                .tableName("student_course")
                                .isPrimary(0)
                                .relationType("1:N")
                                .joinLeftField("student_id")
                                .joinRightField("id")
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
        mockModule101.setSimpleFields(
                List.of(
                        ModuleSimpleFieldDTO.builder()
                                .tableName("student")
                                .columnName("student_no")
                                .displayName("学号")
                                .build(),
                        ModuleSimpleFieldDTO.builder()
                                .tableName("student")
                                .columnName("name")
                                .displayName("姓名")
                                .build(),
                        ModuleSimpleFieldDTO.builder()
                                .tableName("student_course")
                                .columnName("score")
                                .displayName("成绩")
                                .build()));

        lenient().when(metadataCacheService.getModuleComplete(101L)).thenReturn(mockModule101);
        lenient()
                .when(permissionFilterService.filterReadableHeaders(any()))
                .thenReturn(mockModule101.getModuleHeaders());
    }

    @Test
    @DisplayName("测试单模块列表查询: viewMode=LIST 仅返回表头，跳过 1:N 深度扫描")
    void testQueryListViewMode() {
        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .moduleId(101L)
                        .viewMode("LIST")
                        .pageNo(1)
                        .pageSize(20)
                        .build();

        EngineDataResult<DataPage<Map<String, Object>>> result = dynamicQueryService.query(req);

        assertNotNull(result);
        assertNotNull(result.getMeta());
        assertEquals("MOD-STUDENT", result.getMeta().getModuleCode());
        // LIST 模式下包含 headers
        assertNotNull(result.getMeta().getHeaders());
        assertFalse(result.getMeta().getHeaders().isEmpty());
        // LIST 模式下 fields 应为空/未透传
        assertNull(result.getMeta().getFields());

        // 验证分页数据
        DataPage<Map<String, Object>> page = result.getData();
        assertEquals(1, page.getTotal());
        assertEquals(1, page.getRecords().size());

        // 验证 Table-First 数据结构
        Map<String, Object> firstRow = page.getRecords().get(0);
        assertTrue(firstRow.containsKey("student"));
        @SuppressWarnings("unchecked")
        Map<String, Object> studentData = (Map<String, Object>) firstRow.get("student");
        assertEquals("张三", studentData.get("name"));
    }

    @Test
    @DisplayName("测试单模块详情查询: viewMode=DETAIL 返回字段字典 fields 与结构化数据")
    void testQueryDetailViewMode() {
        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .moduleId(101L)
                        .viewMode("DETAIL")
                        .pageNo(1)
                        .pageSize(1)
                        .filters(Map.of("id", 1001L))
                        .build();

        EngineDataResult<DataPage<Map<String, Object>>> result = dynamicQueryService.query(req);

        assertNotNull(result);
        assertNotNull(result.getMeta());
        // DETAIL 模式下包含 fields
        assertNotNull(result.getMeta().getFields());
        assertFalse(result.getMeta().getFields().isEmpty());
    }

    @Test
    @DisplayName("测试多模块批量并发查询: batchQuery 一次性带回多个模块结果")
    void testBatchQuery() {
        BatchDynamicQueryReq batchReq =
                BatchDynamicQueryReq.builder()
                        .queries(
                                Map.of(
                                        "student",
                                        DynamicQueryReq.builder()
                                                .moduleId(101L)
                                                .viewMode("DETAIL")
                                                .build(),
                                        "courses",
                                        DynamicQueryReq.builder()
                                                .moduleId(101L)
                                                .viewMode("LIST")
                                                .build()))
                        .build();

        BatchEngineDataResult batchResult = dynamicQueryService.batchQuery(batchReq);

        assertNotNull(batchResult);
        assertNotNull(batchResult.getResults());
        assertEquals(2, batchResult.getResults().size());
        assertTrue(batchResult.getResults().containsKey("student"));
        assertTrue(batchResult.getResults().containsKey("courses"));
    }

    @Test
    @DisplayName("测试单条详情快捷门面 getDetail")
    void testGetDetail() {
        DynamicDetailReq detailReq = DynamicDetailReq.builder().moduleId(101L).id(1001L).build();

        EngineDataResult<Map<String, Object>> result = dynamicQueryService.getDetail(detailReq);

        assertNotNull(result);
        assertNotNull(result.getData());
        assertTrue(result.getData().containsKey("student"));
    }
}
