package com.jdec.platform.data.biz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.lenient;

import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import com.jdec.platform.config.api.dto.response.SysModuleMetaResp;
import com.jdec.platform.data.api.dto.request.DynamicSaveReq;
import com.jdec.platform.data.biz.dsl.JooqContextFactory;
import com.jdec.platform.data.biz.plan.compiler.SavePlanCompiler;
import com.jdec.platform.data.biz.plan.executor.SavePlanExecutor;
import com.jdec.platform.data.biz.service.DynamicPersistenceService;
import com.jdec.platform.data.biz.service.MetadataCacheService;
import com.jdec.platform.data.biz.service.PermissionFilterService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataEnginePersistenceTest {

    @Mock private MetadataCacheService metadataCacheService;
    @Mock private PermissionFilterService permissionFilterService;
    @Mock private JooqContextFactory jooqContextFactory;

    private SavePlanCompiler savePlanCompiler;
    private SavePlanExecutor savePlanExecutor;
    private DynamicPersistenceService dynamicPersistenceService;

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
        studentInfo.setPrimaryTable("student");
        studentInfo.setParentId(0L);

        mockStudentModule = new SysModuleMetaResp();
        mockStudentModule.setModule(studentInfo);
        mockStudentModule.setFields(
                List.of(
                        ModuleFieldDTO.builder()
                                .id(1001L)
                                .tableName("student")
                                .columnName("name")
                                .build(),
                        ModuleFieldDTO.builder()
                                .id(1002L)
                                .tableName("student")
                                .columnName("student_no")
                                .build(),
                        ModuleFieldDTO.builder()
                                .id(1003L)
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
        courseInfo.setPrimaryTable("student_course");
        courseInfo.setParentId(101L);

        mockCourseModule = new SysModuleMetaResp();
        mockCourseModule.setModule(courseInfo);
        mockCourseModule.setFields(
                List.of(
                        ModuleFieldDTO.builder()
                                .id(1004L)
                                .tableName("student_course")
                                .columnName("course_name")
                                .build(),
                        ModuleFieldDTO.builder()
                                .id(1005L)
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

        savePlanCompiler = new SavePlanCompiler(metadataCacheService);
        savePlanExecutor = new SavePlanExecutor(jooqContextFactory);
        dynamicPersistenceService =
                new DynamicPersistenceService(savePlanCompiler, savePlanExecutor);
    }

    @Test
    @DisplayName("测试单模块同构保存: records 单条主记录持久化")
    void testSaveSingleModule() {
        Map<String, Object> recordData = new HashMap<>();
        recordData.put("name", "李四");
        recordData.put("student_no", "S002");
        recordData.put("emergency_phone", "13800000002");

        DynamicSaveReq saveReq =
                DynamicSaveReq.builder().moduleId(101L).records(List.of(recordData)).build();

        Long savedId = dynamicPersistenceService.save(saveReq);

        assertNotNull(savedId);
        assertEquals(2001L, savedId);
    }

    @Test
    @DisplayName("测试树形模块级 children 级联保存: 父模块 101 关联子模块 103")
    void testSaveTreeModuleChildren() {
        Map<String, Object> studentData = new HashMap<>();
        studentData.put("name", "王五");
        studentData.put("student_no", "S003");

        Map<String, Object> courseData = new HashMap<>();
        courseData.put("course_name", "高等数学");

        DynamicSaveReq req =
                DynamicSaveReq.builder()
                        .moduleId(101L)
                        .records(List.of(studentData))
                        .children(
                                List.of(
                                        DynamicSaveReq.builder()
                                                .moduleId(103L)
                                                .records(List.of(courseData))
                                                .build()))
                        .build();

        Long savedId = dynamicPersistenceService.save(req);
        assertNotNull(savedId);
        assertEquals(2001L, savedId);
    }

    @Test
    @DisplayName("测试行内自相似读写同构级联保存: 对齐 query 响应 records[0].children 结构原样提交")
    void testSaveTreeRowLevelIsomorphic() {
        Map<String, Object> courseData = new HashMap<>();
        courseData.put("course_name", "大学英语");

        DynamicSaveReq childNode =
                DynamicSaveReq.builder().moduleId(103L).records(List.of(courseData)).build();

        Map<String, Object> studentRow = new HashMap<>();
        studentRow.put("name", "赵六");
        studentRow.put("student_no", "S004");
        // 行内直接挂载子模块树 (完全对齐 Response)
        studentRow.put("children", List.of(childNode));

        DynamicSaveReq req =
                DynamicSaveReq.builder().moduleId(101L).records(List.of(studentRow)).build();

        Long savedId = dynamicPersistenceService.save(req);
        assertNotNull(savedId);
        assertEquals(2001L, savedId);
    }
}
