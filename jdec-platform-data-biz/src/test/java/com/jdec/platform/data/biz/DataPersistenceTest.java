package com.jdec.platform.data.biz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

import com.jdec.platform.data.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.data.api.dto.common.TableRelationDTO;
import com.jdec.platform.data.api.dto.model.ModuleInfo;
import com.jdec.platform.data.api.dto.request.SaveReq;
import com.jdec.platform.data.biz.dsl.JooqDataContextFactory;
import com.jdec.platform.data.biz.plan.compiler.SavePlanCompiler;
import com.jdec.platform.data.biz.plan.executor.SavePlanExecutor;
import com.jdec.platform.data.biz.service.DataPersistenceService;
import com.jdec.platform.data.biz.service.MetaCacheService;
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
    @Mock private JooqDataContextFactory jooqDataContextFactory;

    private SavePlanCompiler savePlanCompiler;
    private SavePlanExecutor savePlanExecutor;
    private DataPersistenceService dataPersistenceService;

    private DSLContext dslContext;
    private ModuleInfo mockStudentModule;
    private ModuleInfo mockCourseModule;

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
                ModuleInfo.builder()
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
                ModuleInfo.builder()
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
        dataPersistenceService = new DataPersistenceService(savePlanCompiler, savePlanExecutor);
    }

    @Test
    @DisplayName("测试单模块同构保存: records 单条主记录持久化")
    void testSaveSingleModule() {
        Map<String, Object> recordData = new HashMap<>();
        recordData.put("name", "李四");
        recordData.put("student_no", "S002");
        recordData.put("emergency_phone", "13800000002");

        SaveReq saveReq = SaveReq.builder().moduleId(101L).records(List.of(recordData)).build();

        Long savedId = dataPersistenceService.save(saveReq);

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

        SaveReq req =
                SaveReq.builder()
                        .moduleId(101L)
                        .records(List.of(studentData))
                        .children(
                                List.of(
                                        SaveReq.builder()
                                                .moduleId(103L)
                                                .records(List.of(courseData))
                                                .build()))
                        .build();

        Long savedId = dataPersistenceService.save(req);
        assertNotNull(savedId);
        assertEquals(2001L, savedId);
    }

    @Test
    @DisplayName("测试行内自相似读写同构级联保存: 对齐 query 响应 records[0].children 结构原样提交")
    void testSaveTreeRowLevelIsomorphic() {
        Map<String, Object> courseData = new HashMap<>();
        courseData.put("course_name", "大学英语");

        SaveReq childNode = SaveReq.builder().moduleId(103L).records(List.of(courseData)).build();

        Map<String, Object> studentRow = new HashMap<>();
        studentRow.put("name", "赵六");
        studentRow.put("student_no", "S004");
        // 行内直接挂载子模块树 (完全对齐 Response)
        studentRow.put("children", List.of(childNode));

        SaveReq req = SaveReq.builder().moduleId(101L).records(List.of(studentRow)).build();

        Long savedId = dataPersistenceService.save(req);
        assertNotNull(savedId);
        assertEquals(2001L, savedId);
    }

    @Test
    @DisplayName("测试读写同构：直接使用实体表名与数字键子模块保存 (例如 student + \"103\")")
    void testSaveIsomorphicWithChildModuleId() {
        Map<String, Object> studentRow = new HashMap<>();
        studentRow.put("student", Map.of("name", "钱七", "student_no", "S007"));
        studentRow.put("103", List.of(Map.of("student_course", Map.of("course_name", "数据结构与算法"))));

        SaveReq req = SaveReq.builder().moduleId(101L).records(List.of(studentRow)).build();

        Long savedId = dataPersistenceService.save(req);
        assertNotNull(savedId);
        assertEquals(2001L, savedId);
    }

    @Test
    @DisplayName("测试方案A读写同构：直接将 detail 接口返回的 data 载荷放入 records 进行保存 (免传 moduleId，自动推导)")
    void testSaveIsomorphicWithDetailPayloadDirectly() {
        // 模拟 detail 接口返回的 data 载荷格式：{"101": { "student": {...}, "103": [...] }}
        Map<String, Object> innerStudent = new HashMap<>();
        innerStudent.put("student", Map.of("name", "孙八", "student_no", "S008"));
        innerStudent.put("103", List.of(Map.of("student_course", Map.of("course_name", "大学物理"))));

        Map<String, Object> detailDataPayload = Map.of("101", innerStudent);

        // 前端直接包装 data 载荷，不传 moduleId
        SaveReq req = SaveReq.builder().records(List.of(detailDataPayload)).build();

        var savePlan = savePlanCompiler.compile(req);
        assertNotNull(savePlan);
        var rootPlan = savePlan.getRootNodePlan();
        assertEquals(101L, rootPlan.getModuleId());
        assertEquals("student", rootPlan.getPrimaryTable());
        assertEquals(1, rootPlan.getRecords().size());
        assertEquals("孙八", rootPlan.getRecords().get(0).get("name"));
        assertEquals(1, rootPlan.getChildren().size());
        assertEquals(103L, rootPlan.getChildren().get(0).getModuleId());

        Long savedId = dataPersistenceService.save(req);
        assertNotNull(savedId);
        assertEquals(2001L, savedId);
    }

    @Test
    @DisplayName("测试虚拟模块保存：101(学生) -> 201(虚拟模块-教务信息) -> 103(选课管理) 读写同构递归落库")
    void testSaveWithVirtualModuleContainer() {
        ModuleInfo mockVirtual201 =
                ModuleInfo.builder()
                        .moduleId(201L)
                        .moduleCode("MOD-ACADEMIC")
                        .moduleName("教务信息")
                        .primaryTable(null)
                        .parentId(101L)
                        .build();
        ModuleInfo mockCourseUnder201 = mockCourseModule.toBuilder().parentId(201L).build();

        lenient().when(metaCacheService.getModuleComplete(201L)).thenReturn(mockVirtual201);
        lenient().when(metaCacheService.getModuleComplete(103L)).thenReturn(mockCourseUnder201);

        // 读写同构结构：studentRow 内部挂载 201 容器，201 容器内部挂载 103 选课记录
        Map<String, Object> studentRow = new HashMap<>();
        studentRow.put("name", "周八");
        studentRow.put("student_no", "S008");
        studentRow.put("201", Map.of("103", List.of(Map.of("course_name", "高等数学"))));

        SaveReq req = SaveReq.builder().moduleId(101L).records(List.of(studentRow)).build();

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
        Long savedId = dataPersistenceService.save(req);
        assertNotNull(savedId);
        assertEquals(2001L, savedId);
    }
}
