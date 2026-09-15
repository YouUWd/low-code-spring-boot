package com.jdec.platform.data.biz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

import com.jdec.platform.data.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.data.api.dto.common.TableRelationDTO;
import com.jdec.platform.data.api.dto.model.SysModuleInfo;
import com.jdec.platform.data.api.dto.request.DynamicSaveReq;
import com.jdec.platform.data.biz.dsl.JooqDataContextFactory;
import com.jdec.platform.data.biz.plan.compiler.SavePlanCompiler;
import com.jdec.platform.data.biz.plan.executor.SavePlanExecutor;
import com.jdec.platform.data.biz.service.DynamicDataPersistenceService;
import com.jdec.platform.data.biz.service.MetaCacheService;
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
class DataPersistenceTest {

    @Mock private MetaCacheService metaCacheService;
    @Mock private PermissionFilterService permissionFilterService;
    @Mock private JooqDataContextFactory jooqDataContextFactory;

    private SavePlanCompiler savePlanCompiler;
    private SavePlanExecutor savePlanExecutor;
    private DynamicDataPersistenceService dynamicDataPersistenceService;

    private DSLContext dslContext;
    private SysModuleInfo mockStudentModule;
    private SysModuleInfo mockCourseModule;

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
        lenient().when(jooqDataContextFactory.getContext()).thenReturn(dslContext);

        // 模块 101: 学生档案 (主表 student，从表 student_profile)
        mockStudentModule =
                SysModuleInfo.builder()
                        .moduleId(101L)
                        .moduleCode("MOD-STUDENT")
                        .primaryTable("student")
                        .parentId(0L)
                        .fields(
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
                                                .build()))
                        .tableRelations(
                                List.of(
                                        TableRelationDTO.builder()
                                                .mainTable("student")
                                                .mainField("id")
                                                .joinTable("student_profile")
                                                .joinField("student_id")
                                                .relationType("1:1")
                                                .build()))
                        .build();

        // 模块 103: 选课管理 (主表 student_course)
        mockCourseModule =
                SysModuleInfo.builder()
                        .moduleId(103L)
                        .moduleCode("MOD-STUDENT-COURSE")
                        .primaryTable("student_course")
                        .parentId(101L)
                        .fields(
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
                                                .build()))
                        .tableRelations(
                                List.of(
                                        TableRelationDTO.builder()
                                                .mainTable("student")
                                                .mainField("id")
                                                .joinTable("student_course")
                                                .joinField("student_id")
                                                .relationType("1:N")
                                                .build()))
                        .build();

        lenient().when(metaCacheService.getModuleComplete(101L)).thenReturn(mockStudentModule);
        lenient().when(metaCacheService.getModuleComplete(103L)).thenReturn(mockCourseModule);

        savePlanCompiler = new SavePlanCompiler(metaCacheService);
        savePlanExecutor = new SavePlanExecutor(jooqDataContextFactory);
        dynamicDataPersistenceService =
                new DynamicDataPersistenceService(savePlanCompiler, savePlanExecutor);
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

        Long savedId = dynamicDataPersistenceService.save(saveReq);

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

        Long savedId = dynamicDataPersistenceService.save(req);
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

        Long savedId = dynamicDataPersistenceService.save(req);
        assertNotNull(savedId);
        assertEquals(2001L, savedId);
    }

    @Test
    @DisplayName("测试读写同构：直接使用实体表名与数字键子模块保存 (例如 student + \"103\")")
    void testSaveIsomorphicWithChildModuleId() {
        Map<String, Object> studentRow = new HashMap<>();
        studentRow.put("student", Map.of("name", "钱七", "student_no", "S007"));
        studentRow.put("103", List.of(Map.of("student_course", Map.of("course_name", "数据结构与算法"))));

        DynamicSaveReq req =
                DynamicSaveReq.builder().moduleId(101L).records(List.of(studentRow)).build();

        Long savedId = dynamicDataPersistenceService.save(req);
        assertNotNull(savedId);
        assertEquals(2001L, savedId);
    }

    @Test
    @DisplayName("测试虚拟模块保存：101(学生) -> 201(虚拟模块-教务信息) -> 103(选课管理) 读写同构递归落库")
    void testSaveWithVirtualModuleContainer() {
        SysModuleInfo mockVirtual201 =
                SysModuleInfo.builder()
                        .moduleId(201L)
                        .moduleCode("MOD-ACADEMIC")
                        .moduleName("教务信息")
                        .primaryTable(null)
                        .parentId(101L)
                        .build();
        SysModuleInfo mockCourseUnder201 = mockCourseModule.toBuilder().parentId(201L).build();

        lenient().when(metaCacheService.getModuleComplete(201L)).thenReturn(mockVirtual201);
        lenient().when(metaCacheService.getModuleComplete(103L)).thenReturn(mockCourseUnder201);

        // 读写同构结构：studentRow 内部挂载 201 容器，201 容器内部挂载 103 选课记录
        Map<String, Object> studentRow = new HashMap<>();
        studentRow.put("name", "周八");
        studentRow.put("student_no", "S008");
        studentRow.put("201", Map.of("103", List.of(Map.of("course_name", "高等数学"))));

        DynamicSaveReq req =
                DynamicSaveReq.builder().moduleId(101L).records(List.of(studentRow)).build();

        // 验证编译计划
        var savePlan = savePlanCompiler.compile(req);
        assertNotNull(savePlan);
        var rootNode = savePlan.getRootNodePlan();
        assertEquals(101L, rootNode.getModuleId());
        assertEquals("student", rootNode.getPrimaryTable());

        var child201 = rootNode.getChildren().get(0);
        assertEquals(201L, child201.getModuleId());
        assertTrue(child201.getPrimaryTable() == null || child201.getPrimaryTable().isBlank());

        var child103 = child201.getChildren().get(0);
        assertEquals(103L, child103.getModuleId());
        assertEquals("student_course", child103.getPrimaryTable());
        assertEquals("student_id", child103.getParentForeignKey());

        // 验证落库执行
        Long savedId = dynamicDataPersistenceService.save(req);
        assertNotNull(savedId);
        assertEquals(2001L, savedId);
    }
}
