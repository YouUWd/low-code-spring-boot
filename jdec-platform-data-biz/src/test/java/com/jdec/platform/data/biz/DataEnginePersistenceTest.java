package com.jdec.platform.data.biz;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import com.jdec.platform.config.api.dto.response.SysModuleMetaResp;
import com.jdec.platform.data.api.dto.request.BatchDynamicSaveReq;
import com.jdec.platform.data.api.dto.request.DynamicSaveReq;
import com.jdec.platform.data.api.dto.response.BatchSaveResp;
import com.jdec.platform.data.biz.dsl.JooqContextFactory;
import com.jdec.platform.data.biz.service.DynamicPersistenceService;
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
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataEnginePersistenceTest {

    @Mock private MetadataCacheService metadataCacheService;
    @Mock private PermissionFilterService permissionFilterService;
    @Mock private JooqContextFactory jooqContextFactory;

    @InjectMocks private DynamicPersistenceService dynamicPersistenceService;

    private DSLContext dslContext;
    private SysModuleMetaResp mockStudentModule;
    private SysModuleMetaResp mockCourseModule;

    @BeforeEach
    void setUp() {
        MockDataProvider provider =
                new MockDataProvider() {
                    @Override
                    public MockResult[] execute(MockExecuteContext ctx) {
                        DSLContext create = DSL.using(SQLDialect.POSTGRES);
                        var result = create.newResult(DSL.field("id", Long.class));
                        var record = create.newRecord(DSL.field("id", Long.class));
                        record.setValue(DSL.field("id", Long.class), 2001L);
                        result.add(record);
                        return new MockResult[] {new MockResult(1, result)};
                    }
                };

        MockConnection connection = new MockConnection(provider);
        dslContext = DSL.using(connection, SQLDialect.POSTGRES);
        lenient().when(jooqContextFactory.getContext()).thenReturn(dslContext);

        // 模块 101: 学生档案 (主表 student，从表 student_profile)
        SysModuleMetaResp.ModuleInfo studentInfo = new SysModuleMetaResp.ModuleInfo();
        studentInfo.setId(101L);
        studentInfo.setModuleCode("MOD-STUDENT");
        studentInfo.setParentId(0L);

        mockStudentModule = new SysModuleMetaResp();
        mockStudentModule.setModule(studentInfo);
        mockStudentModule.setFields(
                List.of(
                        ModuleFieldDTO.builder().tableName("student").columnName("name").build(),
                        ModuleFieldDTO.builder()
                                .tableName("student")
                                .columnName("student_no")
                                .build(),
                        ModuleFieldDTO.builder()
                                .tableName("student_profile")
                                .columnName("emergency_phone")
                                .build()));
        mockStudentModule.setTableRelations(
                List.of(
                        TableRelationDTO.builder()
                                .mainTable("student")
                                .mainField("id")
                                .joinTable("student_profile")
                                .joinField("student_id")
                                .relationType("1:1")
                                .build()));

        // 模块 103: 选课管理 (主表 student_course)
        SysModuleMetaResp.ModuleInfo courseInfo = new SysModuleMetaResp.ModuleInfo();
        courseInfo.setId(103L);
        courseInfo.setModuleCode("MOD-STUDENT-COURSE");
        courseInfo.setParentId(101L);

        mockCourseModule = new SysModuleMetaResp();
        mockCourseModule.setModule(courseInfo);
        mockCourseModule.setFields(
                List.of(
                        ModuleFieldDTO.builder()
                                .tableName("student_course")
                                .columnName("course_name")
                                .build(),
                        ModuleFieldDTO.builder()
                                .tableName("student_course")
                                .columnName("student_id")
                                .build()));
        mockCourseModule.setTableRelations(
                List.of(
                        TableRelationDTO.builder()
                                .mainTable("student")
                                .mainField("id")
                                .joinTable("student_course")
                                .joinField("student_id")
                                .relationType("1:N")
                                .build()));

        lenient().when(metadataCacheService.getModuleComplete(101L)).thenReturn(mockStudentModule);
        lenient().when(metadataCacheService.getModuleComplete(103L)).thenReturn(mockCourseModule);
    }

    @Test
    @DisplayName("测试单模块同构保存: 包含主表与 1:1 伴生从表")
    void testSaveSingleModule() {
        Map<String, Object> tables = new HashMap<>();
        tables.put("student", Map.of("name", "李四", "student_no", "S002"));
        tables.put("student_profile", Map.of("emergency_phone", "13800000002"));

        DynamicSaveReq saveReq = DynamicSaveReq.builder().moduleId(101L).tables(tables).build();

        Long savedId = dynamicPersistenceService.save(saveReq);

        assertNotNull(savedId);
        assertEquals(2001L, savedId);
    }

    @Test
    @DisplayName("测试多模块原子批量保存: 基于 DAG 拓扑外键自动注入")
    void testBatchSaveDAGOrder() {
        Map<String, Object> studentData = new HashMap<>();
        studentData.put("student", Map.of("name", "王五", "student_no", "S003"));

        Map<String, Object> courseData = new HashMap<>();
        courseData.put("student_course", List.of(Map.of("course_name", "高等数学")));

        BatchDynamicSaveReq req =
                BatchDynamicSaveReq.builder()
                        .modules(
                                List.of(
                                        DynamicSaveReq.builder()
                                                .moduleId(103L)
                                                .tables(courseData)
                                                .build(),
                                        DynamicSaveReq.builder()
                                                .moduleId(101L)
                                                .tables(studentData)
                                                .build()))
                        .build();

        BatchSaveResp resp = dynamicPersistenceService.batchSave(req);

        assertNotNull(resp);
        assertEquals(2, resp.getResults().size());
        assertTrue(resp.getResults().containsKey(101L));
        assertTrue(resp.getResults().containsKey(103L));
    }

    @Test
    @DisplayName("测试跨模块重复物理表提交异常拦截")
    void testValidateNoCrossModuleDuplicateTables() {
        Map<String, Object> data1 = Map.of("student", Map.of("name", "测试1"));
        Map<String, Object> data2 = Map.of("student", Map.of("name", "测试2"));

        BatchDynamicSaveReq req =
                BatchDynamicSaveReq.builder()
                        .modules(
                                List.of(
                                        DynamicSaveReq.builder()
                                                .moduleId(101L)
                                                .tables(data1)
                                                .build(),
                                        DynamicSaveReq.builder()
                                                .moduleId(103L)
                                                .tables(data2)
                                                .build()))
                        .build();

        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> dynamicPersistenceService.batchSave(req));
        assertTrue(ex.getMessage().contains("同时在模块"));
    }
}
