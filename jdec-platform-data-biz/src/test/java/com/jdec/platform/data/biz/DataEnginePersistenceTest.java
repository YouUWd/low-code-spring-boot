package com.jdec.platform.data.biz;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jdec.platform.config.api.dto.common.ModuleTableDTO;
import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp;
import com.jdec.platform.data.api.dto.request.BatchDynamicSaveReq;
import com.jdec.platform.data.api.dto.request.DynamicSaveReq;
import com.jdec.platform.data.api.dto.response.BatchSaveResp;
import com.jdec.platform.data.biz.dsl.JooqContextFactory;
import com.jdec.platform.data.biz.service.DynamicPersistenceService;
import com.jdec.platform.data.biz.service.MetadataCacheService;
import com.jdec.platform.data.biz.service.PermissionFilterService;
import java.util.*;
import org.jooq.DSLContext;
import org.jooq.Field;
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

    private SysModuleCompleteResp mockStudentModule;
    private SysModuleCompleteResp mockCourseModule;
    private DSLContext dslContext;

    @BeforeEach
    void setUp() {
        MockDataProvider provider =
                new MockDataProvider() {
                    @Override
                    public MockResult[] execute(MockExecuteContext ctx) {
                        DSLContext create = DSL.using(SQLDialect.MYSQL);
                        Field<Long> idField = DSL.field(DSL.name("id"), Long.class);
                        var result = create.newResult(idField);
                        var record = create.newRecord(idField);
                        record.setValue(idField, 2001L);
                        result.add(record);
                        return new MockResult[] {new MockResult(1, result)};
                    }
                };

        MockConnection connection = new MockConnection(provider);
        dslContext = DSL.using(connection, SQLDialect.POSTGRES);
        lenient().when(jooqContextFactory.getContext()).thenReturn(dslContext);

        // 模块 101: 学生档案 (包含主表 student 与 1:1 扩展表 student_profile)
        SysModuleCompleteResp.ModuleInfo studentInfo = new SysModuleCompleteResp.ModuleInfo();
        studentInfo.setId(101L);
        studentInfo.setModuleCode("MOD-STUDENT");

        mockStudentModule = new SysModuleCompleteResp();
        mockStudentModule.setModule(studentInfo);
        mockStudentModule.setModuleTables(
                List.of(
                        ModuleTableDTO.builder().tableName("student").isPrimary(1).build(),
                        ModuleTableDTO.builder()
                                .tableName("student_profile")
                                .isPrimary(0)
                                .relationType("1:1")
                                .joinLeftField("student_id")
                                .joinRightField("id")
                                .build()));

        // 模块 103: 选课管理 (从属于学生)
        SysModuleCompleteResp.ModuleInfo courseInfo = new SysModuleCompleteResp.ModuleInfo();
        courseInfo.setId(103L);
        courseInfo.setModuleCode("MOD-STUDENT-COURSE");

        mockCourseModule = new SysModuleCompleteResp();
        mockCourseModule.setModule(courseInfo);
        mockCourseModule.setModuleTables(
                List.of(ModuleTableDTO.builder().tableName("student_course").isPrimary(1).build()));

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
        verify(permissionFilterService, times(1))
                .validateWritableFields(eq(101L), eq("student"), anyList());
    }

    @Test
    @DisplayName("测试多模块同构原子批量保存: 自动外键跨模块级联传播")
    void testBatchSaveWithForeignKeyCascade() {
        // 主模块: 学生信息
        DynamicSaveReq masterReq =
                DynamicSaveReq.builder()
                        .moduleId(101L)
                        .tables(Map.of("student", Map.of("name", "王五", "student_no", "S003")))
                        .build();

        // 子模块: 选课信息 (未提供 student_id，由引擎自动注入 master 生成的主键)
        Map<String, Object> courseRecord = new HashMap<>();
        courseRecord.put("course_name", "编译原理");
        courseRecord.put("score", 96.0);

        DynamicSaveReq childReq =
                DynamicSaveReq.builder()
                        .moduleId(103L)
                        .tables(Map.of("student_course", List.of(courseRecord)))
                        .build();

        BatchDynamicSaveReq batchReq =
                BatchDynamicSaveReq.builder()
                        .master(masterReq)
                        .children(Map.of("courses", childReq))
                        .build();

        BatchSaveResp resp = dynamicPersistenceService.batchSave(batchReq);

        assertNotNull(resp);
        assertEquals(2001L, resp.getMasterId());
        assertNotNull(resp.getChildResults());
        assertTrue(resp.getChildResults().containsKey("courses"));
    }
}
