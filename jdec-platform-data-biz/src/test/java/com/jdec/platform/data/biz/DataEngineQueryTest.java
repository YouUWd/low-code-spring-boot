package com.jdec.platform.data.biz;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleNodeDTO;
import com.jdec.platform.config.api.dto.common.ModuleTableHeaderDTO;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import com.jdec.platform.config.api.dto.response.SysModuleMetaResp;
import com.jdec.platform.data.api.dto.request.DynamicFilterItem;
import com.jdec.platform.data.api.dto.request.DynamicOptionReq;
import com.jdec.platform.data.api.dto.request.DynamicQueryReq;
import com.jdec.platform.data.api.dto.request.DynamicSortItem;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.api.dto.response.DynamicOptionItem;
import com.jdec.platform.data.api.dto.response.EngineDataResult;
import com.jdec.platform.data.biz.dsl.JooqContextFactory;
import com.jdec.platform.data.biz.plan.assembler.TreeResultAssembler;
import com.jdec.platform.data.biz.plan.compiler.QueryPlanCompiler;
import com.jdec.platform.data.biz.plan.executor.QueryPlanExecutor;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 数据引擎模块树自相似驱动与读写同构单元测试
 *
 * <p>模块 101（学生档案）：主表 student，伴生表 clazz
 *
 * <p>子模块 103（选课）：从表 student_course (1:N)
 *
 * <p>子模块 104（考核分项）：孙表 student_course_score_item (1:N:N)
 *
 * <p>子模块 105（奖惩）：从表 student_reward (1:N)
 */
@ExtendWith(MockitoExtension.class)
class DataEngineQueryTest {

    @Mock private MetadataCacheService metadataCacheService;
    @Mock private PermissionFilterService permissionFilterService;
    @Mock private JooqContextFactory jooqContextFactory;

    private final TreeResultAssembler treeResultAssembler = new TreeResultAssembler();

    private QueryPlanCompiler queryPlanCompiler;
    private QueryPlanExecutor queryPlanExecutor;
    private DynamicQueryService dynamicQueryService;

    private DSLContext dslContext;
    private SysModuleMetaResp mockModule101;
    private SysModuleMetaResp mockModule103;
    private SysModuleMetaResp mockModule104;
    private SysModuleMetaResp mockModule105;

    @BeforeEach
    void setUp() {
        MockDataProvider provider =
                new MockDataProvider() {
                    @Override
                    public MockResult[] execute(MockExecuteContext ctx) {
                        String sql = ctx.sql().toLowerCase();
                        DSLContext create = DSL.using(SQLDialect.MYSQL);

                        // 0. SELECT DISTINCT 候选值查询响应
                        if (sql.contains("select distinct")) {
                            if (sql.contains("course_name")) {
                                var res = create.newResult(DSL.field("course_name", String.class));
                                var r1 = create.newRecord(DSL.field("course_name", String.class));
                                r1.setValue(DSL.field("course_name", String.class), "大学英语");
                                var r2 = create.newRecord(DSL.field("course_name", String.class));
                                r2.setValue(DSL.field("course_name", String.class), "高等数学");
                                res.add(r1);
                                res.add(r2);
                                return new MockResult[] {new MockResult(2, res)};
                            }
                            var res = create.newResult(DSL.field("val", String.class));
                            var r1 = create.newRecord(DSL.field("val", String.class));
                            r1.setValue(DSL.field("val", String.class), "候选值A");
                            res.add(r1);
                            return new MockResult[] {new MockResult(1, res)};
                        }

                        // 1. COUNT 查询响应
                        if (sql.contains("count(*)")) {
                            var result = create.newResult(DSL.field("count", Long.class));
                            var record = create.newRecord(DSL.field("count", Long.class));
                            record.setValue(DSL.field("count", Long.class), 1L);
                            result.add(record);
                            return new MockResult[] {new MockResult(1, result)};
                        }

                        // 2. 子模块 104 孙表 student_course_score_item 批量查询响应
                        if (sql.contains("from `student_course_score_item` where")
                                && !sql.contains("from `student_course`")
                                && !sql.contains("from `student`")) {
                            var result =
                                    create.newResult(
                                            DSL.field("id", Long.class),
                                            DSL.field("course_id", Long.class),
                                            DSL.field("item_name", String.class),
                                            DSL.field("score", Double.class),
                                            DSL.field("deleted", Byte.class));
                            var record =
                                    create.newRecord(
                                            DSL.field("id", Long.class),
                                            DSL.field("course_id", Long.class),
                                            DSL.field("item_name", String.class),
                                            DSL.field("score", Double.class),
                                            DSL.field("deleted", Byte.class));
                            record.setValue(DSL.field("id", Long.class), 801L);
                            record.setValue(DSL.field("course_id", Long.class), 301L);
                            record.setValue(DSL.field("item_name", String.class), "期末大作业");
                            record.setValue(DSL.field("score", Double.class), 95.0);
                            record.setValue(DSL.field("deleted", Byte.class), (byte) 0);
                            result.add(record);
                            return new MockResult[] {new MockResult(1, result)};
                        }

                        // 3. 子模块 103 从表 student_course 批量查询响应
                        if (sql.contains("from `student_course` where")
                                && !sql.contains("from `student`")) {
                            var result =
                                    create.newResult(
                                            DSL.field("id", Long.class),
                                            DSL.field("student_id", Long.class),
                                            DSL.field("course_name", String.class),
                                            DSL.field("teacher_name", String.class),
                                            DSL.field("deleted", Byte.class));
                            var record =
                                    create.newRecord(
                                            DSL.field("id", Long.class),
                                            DSL.field("student_id", Long.class),
                                            DSL.field("course_name", String.class),
                                            DSL.field("teacher_name", String.class),
                                            DSL.field("deleted", Byte.class));
                            record.setValue(DSL.field("id", Long.class), 301L);
                            record.setValue(DSL.field("student_id", Long.class), 1001L);
                            record.setValue(DSL.field("course_name", String.class), "大学英语");
                            record.setValue(DSL.field("teacher_name", String.class), "李老师");
                            record.setValue(DSL.field("deleted", Byte.class), (byte) 0);
                            result.add(record);
                            return new MockResult[] {new MockResult(1, result)};
                        }

                        // 4. 子模块 104/105 从表 student_reward / student_award 批量查询响应
                        if ((sql.contains("from `student_reward` where")
                                        || sql.contains("from `student_award` where"))
                                && !sql.contains("from `student`")) {
                            var result =
                                    create.newResult(
                                            DSL.field("id", Long.class),
                                            DSL.field("student_id", Long.class),
                                            DSL.field("reward_name", String.class),
                                            DSL.field("reward_level", String.class),
                                            DSL.field("reward_date", String.class),
                                            DSL.field("award_name", String.class),
                                            DSL.field("deleted", Byte.class));
                            var record =
                                    create.newRecord(
                                            DSL.field("id", Long.class),
                                            DSL.field("student_id", Long.class),
                                            DSL.field("reward_name", String.class),
                                            DSL.field("reward_level", String.class),
                                            DSL.field("reward_date", String.class),
                                            DSL.field("award_name", String.class),
                                            DSL.field("deleted", Byte.class));
                            record.setValue(DSL.field("id", Long.class), 501L);
                            record.setValue(DSL.field("student_id", Long.class), 1001L);
                            record.setValue(DSL.field("reward_name", String.class), "国家一等奖学金");
                            record.setValue(DSL.field("award_name", String.class), "国家一等奖学金");
                            record.setValue(DSL.field("reward_level", String.class), "国家级");
                            record.setValue(DSL.field("reward_date", String.class), "2026-06-15");
                            record.setValue(DSL.field("deleted", Byte.class), (byte) 0);
                            result.add(record);
                            return new MockResult[] {new MockResult(1, result)};
                        }

                        // 4.1 孙模块 106 从表 student_award_detail 批量查询响应
                        if (sql.contains("from `student_award_detail` where")
                                && !sql.contains("from `student_award`")
                                && !sql.contains("from `student`")) {
                            var result =
                                    create.newResult(
                                            DSL.field("id", Long.class),
                                            DSL.field("award_id", Long.class),
                                            DSL.field("evidence_name", String.class),
                                            DSL.field("deleted", Byte.class));
                            var record =
                                    create.newRecord(
                                            DSL.field("id", Long.class),
                                            DSL.field("award_id", Long.class),
                                            DSL.field("evidence_name", String.class),
                                            DSL.field("deleted", Byte.class));
                            record.setValue(DSL.field("id", Long.class), 7401L);
                            record.setValue(DSL.field("award_id", Long.class), 501L);
                            record.setValue(DSL.field("evidence_name", String.class), "国家级证书扫描件");
                            record.setValue(DSL.field("deleted", Byte.class), (byte) 0);
                            result.add(record);
                            return new MockResult[] {new MockResult(1, result)};
                        }

                        // 5. 主表与伴生平铺表查询响应
                        var result =
                                create.newResult(
                                        DSL.field("id", Long.class),
                                        DSL.field("student_no", String.class),
                                        DSL.field("name", String.class),
                                        DSL.field("clazz_id", Long.class),
                                        DSL.field("class_name", String.class));
                        var record =
                                create.newRecord(
                                        DSL.field("id", Long.class),
                                        DSL.field("student_no", String.class),
                                        DSL.field("name", String.class),
                                        DSL.field("clazz_id", Long.class),
                                        DSL.field("class_name", String.class));

                        record.setValue(DSL.field("id", Long.class), 1001L);
                        record.setValue(DSL.field("student_no", String.class), "S001");
                        record.setValue(DSL.field("name", String.class), "张三");
                        record.setValue(DSL.field("clazz_id", Long.class), 201L);
                        record.setValue(DSL.field("class_name", String.class), "高三(1)班");
                        result.add(record);

                        return new MockResult[] {new MockResult(1, result)};
                    }
                };

        MockConnection connection = new MockConnection(provider);
        dslContext = DSL.using(connection, SQLDialect.MYSQL);
        lenient().when(jooqContextFactory.getContext()).thenReturn(dslContext);

        // 模块 101: 学生综合档案 (主表 student，伴生表 clazz)
        SysModuleMetaResp.ModuleInfo moduleInfo101 = new SysModuleMetaResp.ModuleInfo();
        moduleInfo101.setId(101L);
        moduleInfo101.setModuleCode("MOD-STUDENT");
        moduleInfo101.setModuleName("学生综合档案");
        moduleInfo101.setPrimaryTable("student");
        moduleInfo101.setParentId(0L);

        mockModule101 = new SysModuleMetaResp();
        mockModule101.setModule(moduleInfo101);
        mockModule101.setFields(
                List.of(
                        ModuleFieldDTO.builder()
                                .id(1011L)
                                .moduleId(101L)
                                .tableName("student")
                                .columnName("student_no")
                                .displayName("学号")
                                .sortOrder(1)
                                .build(),
                        ModuleFieldDTO.builder()
                                .id(1012L)
                                .moduleId(101L)
                                .tableName("student")
                                .columnName("name")
                                .displayName("姓名")
                                .sortOrder(2)
                                .build(),
                        ModuleFieldDTO.builder()
                                .id(1013L)
                                .moduleId(101L)
                                .tableName("student")
                                .columnName("clazz_id")
                                .displayName("班级ID")
                                .sortOrder(3)
                                .build(),
                        ModuleFieldDTO.builder()
                                .id(1014L)
                                .moduleId(101L)
                                .tableName("clazz")
                                .columnName("class_name")
                                .displayName("班级名称")
                                .sortOrder(4)
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
                                .build(),
                        TableRelationDTO.builder()
                                .mainTable("student")
                                .mainField("id")
                                .joinTable("student_reward")
                                .joinField("student_id")
                                .relationType("1:N")
                                .build()));
        mockModule101.setModuleNodes(
                List.of(
                        ModuleNodeDTO.builder()
                                .id(103L)
                                .parentId(101L)
                                .moduleCode("MOD-COURSE")
                                .moduleName("选课管理")
                                .primaryTable("student_course")
                                .sortOrder(1)
                                .build(),
                        ModuleNodeDTO.builder()
                                .id(104L)
                                .parentId(103L)
                                .moduleCode("MOD-SCORE")
                                .moduleName("成绩明细")
                                .primaryTable("student_course_score_item")
                                .sortOrder(2)
                                .build(),
                        ModuleNodeDTO.builder()
                                .id(105L)
                                .parentId(101L)
                                .moduleCode("MOD-REWARD")
                                .moduleName("奖惩荣誉")
                                .primaryTable("student_reward")
                                .sortOrder(3)
                                .build()));
        mockModule101.setModuleHeaders(
                List.of(
                        ModuleTableHeaderDTO.builder()
                                .modulePath(List.of(101L))
                                .table("student")
                                .field("student_no")
                                .name("学号")
                                .searchType("text")
                                .build(),
                        ModuleTableHeaderDTO.builder()
                                .modulePath(List.of(101L))
                                .table("student")
                                .field("name")
                                .name("姓名")
                                .searchType("singleFuzzySelect")
                                .build()));

        // 模块 103: 选课管理 (主表 student_course)
        SysModuleMetaResp.ModuleInfo moduleInfo103 = new SysModuleMetaResp.ModuleInfo();
        moduleInfo103.setId(103L);
        moduleInfo103.setModuleCode("MOD-COURSE");
        moduleInfo103.setModuleName("选课管理");
        moduleInfo103.setPrimaryTable("student_course");
        moduleInfo103.setParentId(101L);

        mockModule103 = new SysModuleMetaResp();
        mockModule103.setModule(moduleInfo103);
        mockModule103.setFields(
                List.of(
                        ModuleFieldDTO.builder()
                                .id(1031L)
                                .moduleId(103L)
                                .tableName("student_course")
                                .columnName("course_name")
                                .displayName("课程名称")
                                .sortOrder(1)
                                .build(),
                        ModuleFieldDTO.builder()
                                .id(1032L)
                                .moduleId(103L)
                                .tableName("student_course")
                                .columnName("teacher_name")
                                .displayName("任课老师")
                                .sortOrder(2)
                                .build(),
                        ModuleFieldDTO.builder()
                                .id(1033L)
                                .moduleId(103L)
                                .tableName("student_course")
                                .columnName("student_id")
                                .displayName("学生ID")
                                .sortOrder(3)
                                .build()));
        mockModule103.setTableRelations(
                List.of(
                        TableRelationDTO.builder()
                                .mainTable("student")
                                .mainField("id")
                                .joinTable("student_course")
                                .joinField("student_id")
                                .relationType("1:N")
                                .build(),
                        TableRelationDTO.builder()
                                .mainTable("student_course")
                                .mainField("id")
                                .joinTable("student_course_score_item")
                                .joinField("course_id")
                                .relationType("1:N")
                                .build()));

        // 模块 104: 成绩明细 (主表 student_course_score_item)
        SysModuleMetaResp.ModuleInfo moduleInfo104 = new SysModuleMetaResp.ModuleInfo();
        moduleInfo104.setId(104L);
        moduleInfo104.setModuleCode("MOD-SCORE");
        moduleInfo104.setModuleName("成绩明细");
        moduleInfo104.setPrimaryTable("student_course_score_item");
        moduleInfo104.setParentId(103L);

        mockModule104 = new SysModuleMetaResp();
        mockModule104.setModule(moduleInfo104);
        mockModule104.setFields(
                List.of(
                        ModuleFieldDTO.builder()
                                .id(1041L)
                                .moduleId(104L)
                                .tableName("student_course_score_item")
                                .columnName("item_name")
                                .displayName("考核项")
                                .sortOrder(1)
                                .build(),
                        ModuleFieldDTO.builder()
                                .id(1042L)
                                .moduleId(104L)
                                .tableName("student_course_score_item")
                                .columnName("score")
                                .displayName("得分")
                                .sortOrder(2)
                                .build(),
                        ModuleFieldDTO.builder()
                                .id(1043L)
                                .moduleId(104L)
                                .tableName("student_course_score_item")
                                .columnName("course_id")
                                .displayName("选课ID")
                                .sortOrder(3)
                                .build()));
        mockModule104.setTableRelations(
                List.of(
                        TableRelationDTO.builder()
                                .mainTable("student_course")
                                .mainField("id")
                                .joinTable("student_course_score_item")
                                .joinField("course_id")
                                .relationType("1:N")
                                .build()));

        // 模块 105: 奖惩荣誉 (主表 student_reward)
        SysModuleMetaResp.ModuleInfo moduleInfo105 = new SysModuleMetaResp.ModuleInfo();
        moduleInfo105.setId(105L);
        moduleInfo105.setModuleCode("MOD-REWARD");
        moduleInfo105.setModuleName("奖惩荣誉");
        moduleInfo105.setPrimaryTable("student_reward");
        moduleInfo105.setParentId(101L);

        mockModule105 = new SysModuleMetaResp();
        mockModule105.setModule(moduleInfo105);
        mockModule105.setFields(
                List.of(
                        ModuleFieldDTO.builder()
                                .id(1051L)
                                .moduleId(105L)
                                .tableName("student_reward")
                                .columnName("reward_name")
                                .displayName("奖惩名称")
                                .sortOrder(1)
                                .build(),
                        ModuleFieldDTO.builder()
                                .id(1052L)
                                .moduleId(105L)
                                .tableName("student_reward")
                                .columnName("reward_level")
                                .displayName("级别")
                                .sortOrder(2)
                                .build(),
                        ModuleFieldDTO.builder()
                                .id(1053L)
                                .moduleId(105L)
                                .tableName("student_reward")
                                .columnName("reward_date")
                                .displayName("获奖日期")
                                .sortOrder(3)
                                .build(),
                        ModuleFieldDTO.builder()
                                .id(1054L)
                                .moduleId(105L)
                                .tableName("student_reward")
                                .columnName("student_id")
                                .displayName("学生ID")
                                .sortOrder(4)
                                .build()));
        mockModule105.setTableRelations(
                List.of(
                        TableRelationDTO.builder()
                                .mainTable("student")
                                .mainField("id")
                                .joinTable("student_reward")
                                .joinField("student_id")
                                .relationType("1:N")
                                .build()));

        lenient().when(metadataCacheService.getModuleComplete(101L)).thenReturn(mockModule101);
        lenient().when(metadataCacheService.getModuleComplete(103L)).thenReturn(mockModule103);
        lenient().when(metadataCacheService.getModuleComplete(104L)).thenReturn(mockModule104);
        lenient().when(metadataCacheService.getModuleComplete(105L)).thenReturn(mockModule105);

        List<ModuleFieldDTO> allMockFields = new ArrayList<>();
        allMockFields.addAll(mockModule101.getFields());
        allMockFields.addAll(mockModule103.getFields());
        allMockFields.addAll(mockModule104.getFields());
        allMockFields.addAll(mockModule105.getFields());

        lenient()
                .when(metadataCacheService.listFieldsByIds(anyList()))
                .thenAnswer(
                        invocation -> {
                            List<Long> ids = invocation.getArgument(0);
                            return allMockFields.stream()
                                    .filter(f -> ids.contains(f.getId()))
                                    .toList();
                        });

        lenient()
                .when(permissionFilterService.filterReadableHeaders(mockModule101))
                .thenReturn(mockModule101.getModuleHeaders());
        lenient()
                .when(permissionFilterService.filterReadableFields(mockModule101))
                .thenReturn(mockModule101.getFields());
        lenient()
                .when(permissionFilterService.filterReadableFields(mockModule103))
                .thenReturn(mockModule103.getFields());
        lenient()
                .when(permissionFilterService.filterReadableFields(mockModule104))
                .thenReturn(mockModule104.getFields());
        lenient()
                .when(permissionFilterService.filterReadableFields(mockModule105))
                .thenReturn(mockModule105.getFields());

        queryPlanCompiler = new QueryPlanCompiler(metadataCacheService, permissionFilterService);
        queryPlanExecutor = new QueryPlanExecutor(jooqContextFactory, treeResultAssembler);
        dynamicQueryService =
                new DynamicQueryService(
                        metadataCacheService,
                        jooqContextFactory,
                        queryPlanCompiler,
                        queryPlanExecutor);
    }

    @Test
    @DisplayName("测试自相似模块树驱动查询: 101 包含 103(含104) 与 105，行内挂载 DynamicDataNode")
    void testStandardListQueryWithSelfSimilarTree() {
        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .moduleId(101L)
                        .pageNo(1)
                        .pageSize(10)
                        .fields(List.of(1011L, 1012L, 1013L, 1014L))
                        .sorts(
                                List.of(
                                        DynamicSortItem.builder()
                                                .fieldId(1011L)
                                                .direction("DESC")
                                                .build()))
                        .children(
                                List.of(
                                        DynamicQueryReq.builder()
                                                .moduleId(103L)
                                                .fields(List.of(1031L, 1032L))
                                                .children(
                                                        List.of(
                                                                DynamicQueryReq.builder()
                                                                        .moduleId(104L)
                                                                        .fields(
                                                                                List.of(
                                                                                        1041L,
                                                                                        1042L))
                                                                        .build()))
                                                .build(),
                                        DynamicQueryReq.builder()
                                                .moduleId(105L)
                                                .fields(List.of(1051L, 1052L, 1053L))
                                                .build()))
                        .build();

        DataPage<Map<String, Object>> response = dynamicQueryService.query(req);

        // 1. 验证响应与根数据页 (根记录严格由 101 根模块包裹)
        assertNotNull(response);
        assertEquals(1, response.getRecords().size());
        Map<String, Object> rootRow = response.getRecords().get(0);
        assertTrue(rootRow.containsKey("101"), "根记录必须由当前根模块 101 包裹");
        @SuppressWarnings("unchecked")
        Map<String, Object> studentRecord = (Map<String, Object>) rootRow.get("101");
        assertNotNull(studentRecord);

        // 2. 验证主表与伴生表字段在根实体下包装展开 (无冗余平铺 id)
        assertNull(studentRecord.get("id"));

        @SuppressWarnings("unchecked")
        Map<String, Object> student = (Map<String, Object>) studentRecord.get("student");
        assertNotNull(student);
        assertEquals(1001L, ((Number) student.get("id")).longValue());
        assertEquals("S001", student.get("student_no"));
        assertEquals("张三", student.get("name"));

        @SuppressWarnings("unchecked")
        Map<String, Object> clazz = (Map<String, Object>) studentRecord.get("clazz");
        assertNotNull(clazz);
        assertEquals("高三(1)班", clazz.get("class_name"));

        // 3. 验证子模块 103 选课从表 (直接挂载在 101 实体行内)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courseList = (List<Map<String, Object>>) studentRecord.get("103");
        assertNotNull(courseList);
        assertEquals(1, courseList.size());
        Map<String, Object> course0 = courseList.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> courseTable = (Map<String, Object>) course0.get("student_course");
        assertNotNull(courseTable);
        assertEquals(301L, ((Number) courseTable.get("id")).longValue());
        assertEquals("大学英语", courseTable.get("course_name"));
        assertEquals("李老师", courseTable.get("teacher_name"));
        // 确认外键 student_id 未被显式请求已干净剥离
        assertNull(courseTable.get("student_id"));

        // 4. 验证孙模块 104 直接挂载在选课实体 course0 行内
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scoreItemList = (List<Map<String, Object>>) course0.get("104");
        assertNotNull(scoreItemList);
        assertEquals(1, scoreItemList.size());
        Map<String, Object> item0 = scoreItemList.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> itemTable =
                (Map<String, Object>) item0.get("student_course_score_item");
        assertNotNull(itemTable);
        assertEquals(801L, ((Number) itemTable.get("id")).longValue());
        assertEquals("期末大作业", itemTable.get("item_name"));
        assertEquals(95.0, itemTable.get("score"));
        assertNull(itemTable.get("course_id"));

        // 5. 验证子模块 105 奖惩荣誉从表 (直接挂载在学生实体行内)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rewardList = (List<Map<String, Object>>) studentRecord.get("105");
        assertNotNull(rewardList);
        assertEquals(1, rewardList.size());
        Map<String, Object> reward0 = rewardList.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> rewardTable = (Map<String, Object>) reward0.get("student_reward");
        assertNotNull(rewardTable);
        assertEquals(501L, ((Number) rewardTable.get("id")).longValue());
        assertEquals("国家一等奖学金", rewardTable.get("reward_name"));
        assertEquals("国家级", rewardTable.get("reward_level"));
        assertEquals("2026-06-15", rewardTable.get("reward_date"));
        assertNull(rewardTable.get("student_id"));
    }

    @Test
    @DisplayName("测试纯 fields 数组驱动：无需传递 moduleId 与 children，全自动推导多级查询树")
    void testPureFieldsQueryWithAutomaticTreeInference() {
        // 请求中完全不传 moduleId，也不传 children，直接平铺请求跨 101, 103, 104, 105 的字段
        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .pageNo(1)
                        .pageSize(10)
                        .fields(
                                List.of(
                                        1011L,
                                        1012L, // 模块 101 (根)
                                        1031L,
                                        1032L, // 模块 103 (子)
                                        1041L,
                                        1042L, // 模块 104 (孙)
                                        1051L // 模块 105 (子)
                                        ))
                        .filters(
                                List.of(
                                        DynamicFilterItem.builder()
                                                .fieldId(1012L)
                                                .operator("EQ")
                                                .value("张三")
                                                .build()))
                        .sorts(
                                List.of(
                                        DynamicSortItem.builder()
                                                .fieldId(1011L)
                                                .direction("DESC")
                                                .build()))
                        .build();

        DataPage<Map<String, Object>> response = dynamicQueryService.query(req);

        assertNotNull(response);
        assertEquals(1, response.getRecords().size());
        Map<String, Object> rootRow = response.getRecords().get(0);
        assertTrue(rootRow.containsKey("101"));
        @SuppressWarnings("unchecked")
        Map<String, Object> studentRecord = (Map<String, Object>) rootRow.get("101");
        assertNotNull(studentRecord);

        // 验证主表字段
        assertNull(studentRecord.get("id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> student = (Map<String, Object>) studentRecord.get("student");
        assertNotNull(student);
        assertEquals(1001L, ((Number) student.get("id")).longValue());
        assertEquals("S001", student.get("student_no"));
        assertEquals("张三", student.get("name"));

        // 验证自动推导并层级挂载的子孙模块 (直接挂载在 studentRecord 行内)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courses = (List<Map<String, Object>>) studentRecord.get("103");
        assertNotNull(courses);
        Map<String, Object> course0 = courses.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> courseTable = (Map<String, Object>) course0.get("student_course");
        assertNotNull(courseTable);
        assertEquals("大学英语", courseTable.get("course_name"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scoreItems = (List<Map<String, Object>>) course0.get("104");
        assertNotNull(scoreItems);
        Map<String, Object> item0 = scoreItems.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> itemTable =
                (Map<String, Object>) item0.get("student_course_score_item");
        assertNotNull(itemTable);
        assertEquals("期末大作业", itemTable.get("item_name"));
        assertEquals(95.0, itemTable.get("score"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rewards = (List<Map<String, Object>>) studentRecord.get("105");
        assertNotNull(rewards);
        Map<String, Object> reward0 = rewards.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> rewardTable = (Map<String, Object>) reward0.get("student_reward");
        assertNotNull(rewardTable);
        assertEquals("国家一等奖学金", rewardTable.get("reward_name"));
    }

    @Test
    @DisplayName("测试动态表头配置接口 getHeader")
    void testGetHeader() {
        com.jdec.platform.data.api.dto.request.EngineHeaderReq req =
                com.jdec.platform.data.api.dto.request.EngineHeaderReq.builder()
                        .fields(List.of(1011L, 1031L))
                        .build();

        com.jdec.platform.data.api.dto.response.EngineHeaderResp resp =
                dynamicQueryService.getHeader(req);

        assertNotNull(resp);
        assertNotNull(resp.getFields());
        assertEquals(2, resp.getFields().size());
        assertEquals("student_no", resp.getFields().get(0).getColumnName());
        assertEquals("学号", resp.getFields().get(0).getDisplayName());
        assertEquals("course_name", resp.getFields().get(1).getColumnName());
        assertEquals("课程名称", resp.getFields().get(1).getDisplayName());
    }

    @Test
    @DisplayName("测试独立节点基于纯 fieldId 过滤: 杜绝表名/列名推导")
    void testNodeFilteringWithFieldId() {
        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .moduleId(101L)
                        .pageNo(1)
                        .pageSize(10)
                        .fields(List.of(1011L, 1012L))
                        .filters(
                                List.of(
                                        DynamicFilterItem.builder()
                                                .fieldId(1012L)
                                                .operator("eq")
                                                .value("张三")
                                                .build()))
                        .children(
                                List.of(
                                        DynamicQueryReq.builder()
                                                .moduleId(103L)
                                                .fields(List.of(1031L))
                                                .filters(
                                                        List.of(
                                                                DynamicFilterItem.builder()
                                                                        .fieldId(1031L)
                                                                        .operator("like")
                                                                        .value("英语")
                                                                        .build()))
                                                .build()))
                        .build();

        DataPage<Map<String, Object>> response = dynamicQueryService.query(req);

        assertNotNull(response);
        assertEquals(1, response.getRecords().size());
        Map<String, Object> rootRow = response.getRecords().get(0);
        assertTrue(rootRow.containsKey("101"));
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) rootRow.get("101");
        assertNotNull(root);
        assertNull(root.get("id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> student = (Map<String, Object>) root.get("student");
        assertNotNull(student);
        assertEquals(1001L, ((Number) student.get("id")).longValue());
        assertEquals("张三", student.get("name"));
    }

    @Test
    @DisplayName("测试多节点独立 sorts 排序配置")
    void testNodeSortingWithFieldId() {
        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .moduleId(101L)
                        .fields(List.of(1011L, 1012L))
                        .sorts(
                                List.of(
                                        DynamicSortItem.builder()
                                                .fieldId(1011L)
                                                .direction("ASC")
                                                .build(),
                                        DynamicSortItem.builder()
                                                .fieldId(1012L)
                                                .direction("DESC")
                                                .build()))
                        .children(
                                List.of(
                                        DynamicQueryReq.builder()
                                                .moduleId(103L)
                                                .fields(List.of(1031L))
                                                .sorts(
                                                        List.of(
                                                                DynamicSortItem.builder()
                                                                        .fieldId(1031L)
                                                                        .direction("ASC")
                                                                        .build()))
                                                .build()))
                        .build();

        DataPage<Map<String, Object>> response = dynamicQueryService.query(req);
        assertNotNull(response);
        assertEquals(1, response.getRecords().size());
    }

    @Test
    @DisplayName("测试单模块查询 (无子节点) 纯净运行")
    void testSingleModuleQuery() {
        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .moduleId(101L)
                        .pageNo(1)
                        .pageSize(20)
                        .fields(List.of(1011L, 1012L))
                        .build();

        DataPage<Map<String, Object>> response = dynamicQueryService.query(req);
        assertNotNull(response);
        assertEquals(1, response.getRecords().size());
        Map<String, Object> rootRow = response.getRecords().get(0);
        assertTrue(rootRow.containsKey("101"));
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) rootRow.get("101");
        assertNotNull(root);
        assertNull(root.get("id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> student = (Map<String, Object>) root.get("student");
        assertNotNull(student);
        assertEquals(1001L, ((Number) student.get("id")).longValue());
        assertEquals("S001", student.get("student_no"));
    }

    @Test
    @DisplayName("测试单据详情查询: 基于 filters 主键过滤与 pageSize=1 统一定位")
    void testQuerySingleRecordByFilter() {
        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .moduleId(101L)
                        .pageNo(1)
                        .pageSize(1)
                        .fields(List.of(1011L, 1012L))
                        .filters(
                                List.of(
                                        DynamicFilterItem.builder()
                                                .fieldId(1011L)
                                                .operator("EQ")
                                                .value("S001")
                                                .build()))
                        .build();
        DataPage<Map<String, Object>> result = dynamicQueryService.query(req);
        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
        Map<String, Object> rootRow = result.getRecords().get(0);
        assertTrue(rootRow.containsKey("101"));
        @SuppressWarnings("unchecked")
        Map<String, Object> record = (Map<String, Object>) rootRow.get("101");
        assertNotNull(record);
        assertNull(record.get("id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> student = (Map<String, Object>) record.get("student");
        assertNotNull(student);
        assertEquals(1001L, ((Number) student.get("id")).longValue());
        assertEquals("S001", student.get("student_no"));
        assertEquals("张三", student.get("name"));
    }

    @Test
    @DisplayName("测试按 ID 查询单据详情: getDetail 返回单行 Map")
    void testGetDetailById() {
        EngineDataResult<Map<String, Object>> result = dynamicQueryService.getDetail(101L, 1001L);
        assertNotNull(result);
        assertNotNull(result.getData());
        assertTrue(result.getData().containsKey("101"), "getDetail 单据详情单行也应保持 101 模块包裹");
        @SuppressWarnings("unchecked")
        Map<String, Object> data101 = (Map<String, Object>) result.getData().get("101");
        assertNotNull(data101);
        assertNull(data101.get("id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> student = (Map<String, Object>) data101.get("student");
        assertNotNull(student);
        assertEquals(1001L, ((Number) student.get("id")).longValue());
        assertEquals("张三", student.get("name"));
        assertEquals("S001", student.get("student_no"));
    }

    @Test
    @DisplayName("测试选项候选值查询 getOptions")
    void testGetOptions() {
        DynamicOptionReq req =
                DynamicOptionReq.builder()
                        .moduleId(103L)
                        .tableName("student_course")
                        .columnName("course_name")
                        .keyword("英语")
                        .build();

        List<DynamicOptionItem> options = dynamicQueryService.getOptions(req);
        assertNotNull(options);
        assertEquals(2, options.size());
        assertEquals("大学英语", options.get(0).getLabel());
    }

    @Test
    @DisplayName("测试模块元数据为空时抛出合规异常")
    void testNonExistentModuleThrows() {
        DynamicQueryReq req = DynamicQueryReq.builder().moduleId(9999L).build();
        assertThrows(IllegalArgumentException.class, () -> dynamicQueryService.query(req));
    }

    @Test
    @DisplayName("测试显式白名单模式严格零推测：请求仅包含子模块字段时根模块业务字段不被推测全选")
    void testWhitelistModeStrictProjectionWithZeroInference() {
        // 请求根模块 101，但 fields 只请求了子模块 103 的字段 1031L (course_name) 与 1032L (teacher_name)
        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .moduleId(101L)
                        .pageNo(1)
                        .pageSize(10)
                        .fields(List.of(1031L, 1032L))
                        .build();

        DataPage<Map<String, Object>> response = dynamicQueryService.query(req);
        assertNotNull(response);
        assertEquals(1, response.getRecords().size());
        Map<String, Object> rootRow = response.getRecords().get(0);
        assertTrue(rootRow.containsKey("101"));
        @SuppressWarnings("unchecked")
        Map<String, Object> record = (Map<String, Object>) rootRow.get("101");
        assertNotNull(record);

        @SuppressWarnings("unchecked")
        Map<String, Object> student = (Map<String, Object>) record.get("student");
        assertNotNull(student);
        // 主表仅作为实体标识保留主键 id，绝不推测全选未请求的业务字段 (student_no, name, clazz_id 等)
        assertEquals(1001L, ((Number) student.get("id")).longValue());
        assertNull(student.get("student_no"));
        assertNull(student.get("name"));
        assertNull(student.get("clazz_id"));

        // 验证子模块 103 直接挂载在 record 行内
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courses = (List<Map<String, Object>>) record.get("103");
        assertNotNull(courses);
        assertEquals(1, courses.size());
        Map<String, Object> c0 = courses.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> courseTable = (Map<String, Object>) c0.get("student_course");
        assertNotNull(courseTable);
        assertEquals(301L, ((Number) courseTable.get("id")).longValue());
        assertEquals("大学英语", courseTable.get("course_name"));
        assertEquals("李老师", courseTable.get("teacher_name"));
        // 未请求的外键字段 student_id 干净剥离
        assertNull(courseTable.get("student_id"));
    }

    @Test
    @DisplayName("测试模块树多层传递闭包与单向TableRelation严格推导：缺少关系定义时必须抛出自洽异常，严禁推测")
    void testModuleTreeTransitiveClosureAndZeroGuessRelation() {
        // 构造一个没有在 tableRelations 中配置关联的伪造子模块
        SysModuleMetaResp.ModuleInfo orphanInfo = new SysModuleMetaResp.ModuleInfo();
        orphanInfo.setId(999L);
        orphanInfo.setModuleCode("MOD-ORPHAN");
        orphanInfo.setPrimaryTable("orphan_table");
        orphanInfo.setParentId(101L);

        SysModuleMetaResp orphanModule = new SysModuleMetaResp();
        orphanModule.setModule(orphanInfo);
        orphanModule.setFields(
                List.of(
                        ModuleFieldDTO.builder()
                                .id(9991L)
                                .moduleId(999L)
                                .tableName("orphan_table")
                                .columnName("content")
                                .build()));
        // 未配置 sys_table_relation
        orphanModule.setTableRelations(Collections.emptyList());

        lenient().when(metadataCacheService.getModuleComplete(999L)).thenReturn(orphanModule);
        lenient()
                .when(metadataCacheService.listFieldsByIds(List.of(9991L)))
                .thenReturn(orphanModule.getFields());

        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .moduleId(101L)
                        .pageNo(1)
                        .pageSize(10)
                        .fields(List.of(9991L))
                        .build();

        // 必须抛出 IllegalStateException，提示未自洽的元数据关联关系，决不允许约定俗成推测
        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> dynamicQueryService.query(req));
        assertTrue(ex.getMessage().contains("元数据关联关系未自洽"));
        assertTrue(ex.getMessage().contains("orphan_table"));
    }

    @Test
    @DisplayName("测试三层模块树 101 -> 104 -> 106：验证 106 模块 ID 完整保留，不丢失且不被错误扁平化嵌套")
    void testThreeLevelModuleHierarchyAwardAndDetail_106RetainsModuleId() {
        // 构造模块 106: 荣誉材料与佐证明细 (挂在 104 下)
        SysModuleMetaResp.ModuleInfo moduleInfo106 = new SysModuleMetaResp.ModuleInfo();
        moduleInfo106.setId(106L);
        moduleInfo106.setModuleCode("MOD-STUDENT-AWARD-DETAIL");
        moduleInfo106.setModuleName("荣誉材料与佐证明细");
        moduleInfo106.setPrimaryTable("student_award_detail");
        moduleInfo106.setParentId(104L);

        SysModuleMetaResp mockModule106 = new SysModuleMetaResp();
        mockModule106.setModule(moduleInfo106);
        mockModule106.setFields(
                List.of(
                        ModuleFieldDTO.builder()
                                .id(74L)
                                .moduleId(106L)
                                .tableName("student_award_detail")
                                .columnName("id")
                                .displayName("材料ID")
                                .sortOrder(1)
                                .build(),
                        ModuleFieldDTO.builder()
                                .id(75L)
                                .moduleId(106L)
                                .tableName("student_award_detail")
                                .columnName("award_id")
                                .displayName("荣誉ID")
                                .sortOrder(2)
                                .build(),
                        ModuleFieldDTO.builder()
                                .id(76L)
                                .moduleId(106L)
                                .tableName("student_award_detail")
                                .columnName("evidence_name")
                                .displayName("材料名称")
                                .sortOrder(3)
                                .build()));
        mockModule106.setTableRelations(
                List.of(
                        TableRelationDTO.builder()
                                .mainTable("student_award")
                                .mainField("id")
                                .joinTable("student_award_detail")
                                .joinField("award_id")
                                .relationType("1:N")
                                .build()));

        // 将 104 模块的主表修正为 student_award
        SysModuleMetaResp.ModuleInfo moduleInfo104Real = new SysModuleMetaResp.ModuleInfo();
        moduleInfo104Real.setId(104L);
        moduleInfo104Real.setModuleCode("MOD-STUDENT-AWARD");
        moduleInfo104Real.setModuleName("荣誉与奖惩管理");
        moduleInfo104Real.setPrimaryTable("student_award");
        moduleInfo104Real.setParentId(101L);

        SysModuleMetaResp mockModule104Real = new SysModuleMetaResp();
        mockModule104Real.setModule(moduleInfo104Real);
        mockModule104Real.setFields(
                List.of(
                        ModuleFieldDTO.builder()
                                .id(37L)
                                .moduleId(104L)
                                .tableName("student_award")
                                .columnName("id")
                                .displayName("荣誉ID")
                                .sortOrder(1)
                                .build(),
                        ModuleFieldDTO.builder()
                                .id(38L)
                                .moduleId(104L)
                                .tableName("student_award")
                                .columnName("award_name")
                                .displayName("荣誉名称")
                                .sortOrder(2)
                                .build()));
        mockModule104Real.setTableRelations(
                List.of(
                        TableRelationDTO.builder()
                                .mainTable("student")
                                .mainField("id")
                                .joinTable("student_award")
                                .joinField("student_id")
                                .relationType("1:N")
                                .build()));

        lenient().when(metadataCacheService.getModuleComplete(106L)).thenReturn(mockModule106);
        lenient().when(metadataCacheService.getModuleComplete(104L)).thenReturn(mockModule104Real);
        lenient()
                .when(permissionFilterService.filterReadableFields(mockModule106))
                .thenReturn(mockModule106.getFields());
        lenient()
                .when(permissionFilterService.filterReadableFields(mockModule104Real))
                .thenReturn(mockModule104Real.getFields());
        lenient()
                .when(
                        metadataCacheService.listFieldsByIds(
                                List.of(1011L, 1012L, 37L, 38L, 74L, 76L)))
                .thenReturn(
                        List.of(
                                mockModule101.getFields().get(0),
                                mockModule101.getFields().get(1),
                                mockModule104Real.getFields().get(0),
                                mockModule104Real.getFields().get(1),
                                mockModule106.getFields().get(0),
                                mockModule106.getFields().get(2)));

        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .moduleId(101L)
                        .pageNo(1)
                        .pageSize(10)
                        .fields(List.of(1011L, 1012L, 37L, 38L, 74L, 76L))
                        .build();

        DataPage<Map<String, Object>> response = dynamicQueryService.query(req);

        assertNotNull(response);
        assertEquals(1, response.getRecords().size());
        Map<String, Object> rootRow = response.getRecords().get(0);
        assertTrue(rootRow.containsKey("101"));
        @SuppressWarnings("unchecked")
        Map<String, Object> studentRecord = (Map<String, Object>) rootRow.get("101");
        assertNotNull(studentRecord);

        // 1. 验证 101 根模块主表
        @SuppressWarnings("unchecked")
        Map<String, Object> student = (Map<String, Object>) studentRecord.get("student");
        assertNotNull(student);

        // 2. 验证 104 子模块直接挂载在 101 实体行内
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> awardList = (List<Map<String, Object>>) studentRecord.get("104");
        assertNotNull(awardList);
        assertEquals(1, awardList.size());
        Map<String, Object> award0 = awardList.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> awardTable = (Map<String, Object>) award0.get("student_award");
        assertNotNull(awardTable);
        assertEquals(501L, ((Number) awardTable.get("id")).longValue());
        assertEquals("国家一等奖学金", awardTable.get("award_name"));

        // 3. 🌟 核心验证：106 孙模块直接自包含挂载在 104 荣誉实体行内 (award0["106"])
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> detailList = (List<Map<String, Object>>) award0.get("106");
        assertNotNull(detailList, "104 荣誉行内必须自包含 106 佐证材料子模块列表");
        assertEquals(1, detailList.size());
        Map<String, Object> detail0 = detailList.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> detailTable = (Map<String, Object>) detail0.get("student_award_detail");
        assertNotNull(detailTable);
        assertEquals(7401L, ((Number) detailTable.get("id")).longValue());
        assertEquals("国家级证书扫描件", detailTable.get("evidence_name"));
        // 验证未显式请求的外键 award_id 已干净剔除
        assertNull(detailTable.get("award_id"));
    }

    @Test
    @DisplayName("测试子模块筛选反向约束根模块 (Semi-Join EXISTS 链式上卷)")
    void testChildModuleFilterRollupToRoot_SemiJoinExists() {
        lenient().when(metadataCacheService.getModuleComplete(101L)).thenReturn(mockModule101);
        lenient().when(metadataCacheService.getModuleComplete(103L)).thenReturn(mockModule103);
        lenient()
                .when(permissionFilterService.filterReadableFields(mockModule101))
                .thenReturn(mockModule101.getFields());
        lenient()
                .when(permissionFilterService.filterReadableFields(mockModule103))
                .thenReturn(mockModule103.getFields());

        // 子模块 103 (选课 student_course) 设置筛选条件: course_name LIKE '英语'
        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .moduleId(101L)
                        .fields(List.of(1011L, 1012L))
                        .children(
                                List.of(
                                        DynamicQueryReq.builder()
                                                .moduleId(103L)
                                                .fields(List.of(1031L, 1032L))
                                                .filters(
                                                        List.of(
                                                                DynamicFilterItem.builder()
                                                                        .fieldId(1031L)
                                                                        .operator("LIKE")
                                                                        .value("英语")
                                                                        .build()))
                                                .build()))
                        .build();

        var queryPlan = queryPlanCompiler.compile(req);
        assertNotNull(queryPlan);
        var rootPlan = queryPlan.getRootNodePlan();
        assertNotNull(rootPlan);

        // 1. 验证根节点必须包含针对 student_course 的 exists 子查询
        String rootCondSql = rootPlan.getCondition().toString().toLowerCase();
        assertTrue(
                rootCondSql.contains("exists"), "子模块 103 存在过滤条件时，根节点 Condition 必须上卷 EXISTS 半连接约束");
        assertTrue(rootCondSql.contains("student_course"), "EXISTS 子查询必须针对子表 student_course");
        assertTrue(rootCondSql.contains("student_id"), "EXISTS 子查询中必须关联从表外键 student_id");
        assertTrue(rootCondSql.contains("英语"), "EXISTS 子查询中必须包含子模块的业务筛选值 '英语'");

        // 2. 验证 rootPlan 的 effectiveFilterCondition 非空
        assertNotNull(rootPlan.getEffectiveFilterCondition());

        // 3. 验证执行查询成功
        DataPage<Map<String, Object>> result = queryPlanExecutor.execute(queryPlan);
        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
    }

    @Test
    @DisplayName("测试三级子孙模块筛选链式上卷反向约束根模块 (Nested EXISTS)")
    void testThreeLevelModuleFilterRollupToRoot_NestedSemiJoin() {
        SysModuleMetaResp mockModule106 = new SysModuleMetaResp();
        SysModuleMetaResp.ModuleInfo moduleInfo106 = new SysModuleMetaResp.ModuleInfo();
        moduleInfo106.setId(106L);
        moduleInfo106.setPrimaryTable("student_award_detail");
        moduleInfo106.setParentId(104L);
        mockModule106.setModule(moduleInfo106);
        mockModule106.setFields(
                List.of(
                        ModuleFieldDTO.builder()
                                .id(74L)
                                .moduleId(106L)
                                .tableName("student_award_detail")
                                .columnName("id")
                                .displayName("佐证ID")
                                .sortOrder(1)
                                .build(),
                        ModuleFieldDTO.builder()
                                .id(75L)
                                .moduleId(106L)
                                .tableName("student_award_detail")
                                .columnName("award_id")
                                .displayName("荣誉ID")
                                .sortOrder(2)
                                .build(),
                        ModuleFieldDTO.builder()
                                .id(76L)
                                .moduleId(106L)
                                .tableName("student_award_detail")
                                .columnName("evidence_name")
                                .displayName("材料名称")
                                .sortOrder(3)
                                .build()));
        mockModule106.setTableRelations(
                List.of(
                        TableRelationDTO.builder()
                                .mainTable("student_award")
                                .mainField("id")
                                .joinTable("student_award_detail")
                                .joinField("award_id")
                                .relationType("1:N")
                                .build()));

        SysModuleMetaResp mockModule104Real = new SysModuleMetaResp();
        SysModuleMetaResp.ModuleInfo moduleInfo104Real = new SysModuleMetaResp.ModuleInfo();
        moduleInfo104Real.setId(104L);
        moduleInfo104Real.setPrimaryTable("student_award");
        moduleInfo104Real.setParentId(101L);
        mockModule104Real.setModule(moduleInfo104Real);
        mockModule104Real.setFields(
                List.of(
                        ModuleFieldDTO.builder()
                                .id(37L)
                                .moduleId(104L)
                                .tableName("student_award")
                                .columnName("id")
                                .displayName("荣誉ID")
                                .sortOrder(1)
                                .build(),
                        ModuleFieldDTO.builder()
                                .id(38L)
                                .moduleId(104L)
                                .tableName("student_award")
                                .columnName("award_name")
                                .displayName("荣誉名称")
                                .sortOrder(2)
                                .build()));
        mockModule104Real.setTableRelations(
                List.of(
                        TableRelationDTO.builder()
                                .mainTable("student")
                                .mainField("id")
                                .joinTable("student_award")
                                .joinField("student_id")
                                .relationType("1:N")
                                .build()));

        lenient().when(metadataCacheService.getModuleComplete(101L)).thenReturn(mockModule101);
        lenient().when(metadataCacheService.getModuleComplete(104L)).thenReturn(mockModule104Real);
        lenient().when(metadataCacheService.getModuleComplete(106L)).thenReturn(mockModule106);
        lenient()
                .when(permissionFilterService.filterReadableFields(mockModule101))
                .thenReturn(mockModule101.getFields());
        lenient()
                .when(permissionFilterService.filterReadableFields(mockModule104Real))
                .thenReturn(mockModule104Real.getFields());
        lenient()
                .when(permissionFilterService.filterReadableFields(mockModule106))
                .thenReturn(mockModule106.getFields());

        // 仅在三级孙模块 106 上设置筛选条件: evidence_name LIKE '证书'
        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .moduleId(101L)
                        .fields(List.of(1011L, 1012L))
                        .children(
                                List.of(
                                        DynamicQueryReq.builder()
                                                .moduleId(104L)
                                                .fields(List.of(37L, 38L))
                                                .children(
                                                        List.of(
                                                                DynamicQueryReq.builder()
                                                                        .moduleId(106L)
                                                                        .fields(List.of(74L, 76L))
                                                                        .filters(
                                                                                List.of(
                                                                                        DynamicFilterItem
                                                                                                .builder()
                                                                                                .fieldId(
                                                                                                        76L)
                                                                                                .operator(
                                                                                                        "LIKE")
                                                                                                .value(
                                                                                                        "证书")
                                                                                                .build()))
                                                                        .build()))
                                                .build()))
                        .build();

        var queryPlan = queryPlanCompiler.compile(req);
        var rootPlan = queryPlan.getRootNodePlan();
        assertNotNull(rootPlan);

        // 1. 验证 104 子节点包含针对 106 的 exists 条件
        var child104 = rootPlan.getChildren().get(0);
        String child104Sql = child104.getCondition().toString().toLowerCase();
        assertTrue(child104Sql.contains("exists"));
        assertTrue(child104Sql.contains("student_award_detail"));
        assertTrue(child104Sql.contains("award_id"));
        assertTrue(child104Sql.contains("证书"));

        // 2. 验证根节点 101 包含链式上卷的 exists 条件 (外层查 student_award，内层查 student_award_detail)
        String rootSql = rootPlan.getCondition().toString().toLowerCase();
        assertTrue(rootSql.contains("exists"));
        assertTrue(rootSql.contains("student_award"));
        assertTrue(rootSql.contains("student_award_detail"));
        assertTrue(rootSql.contains("证书"));

        // 3. 执行查询
        DataPage<Map<String, Object>> result = queryPlanExecutor.execute(queryPlan);
        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
    }

    @Test
    @DisplayName("测试无子模块筛选时保持左外连接语义 (不生成 EXISTS 约束，杜绝误杀主记录)")
    void testNoChildFilterMaintainsLeftJoinSemantics_NoExistsRollup() {
        lenient().when(metadataCacheService.getModuleComplete(101L)).thenReturn(mockModule101);
        lenient().when(metadataCacheService.getModuleComplete(103L)).thenReturn(mockModule103);
        lenient()
                .when(permissionFilterService.filterReadableFields(mockModule101))
                .thenReturn(mockModule101.getFields());
        lenient()
                .when(permissionFilterService.filterReadableFields(mockModule103))
                .thenReturn(mockModule103.getFields());

        // 子模块 103 仅投影，没有任何 filters
        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .moduleId(101L)
                        .fields(List.of(1011L, 1012L))
                        .children(
                                List.of(
                                        DynamicQueryReq.builder()
                                                .moduleId(103L)
                                                .fields(List.of(1031L, 1032L))
                                                .build()))
                        .build();

        var queryPlan = queryPlanCompiler.compile(req);
        var rootPlan = queryPlan.getRootNodePlan();
        assertNotNull(rootPlan);

        // 根节点绝不能包含 exists
        String rootCondSql = rootPlan.getCondition().toString().toLowerCase();
        assertFalse(rootCondSql.contains("exists"), "无子模块筛选时，根节点绝不能生成 EXISTS 约束");
        assertNull(rootPlan.getEffectiveFilterCondition());

        DataPage<Map<String, Object>> result = queryPlanExecutor.execute(queryPlan);
        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
    }

    @Test
    @DisplayName("测试同物理表垂直拆分模块过滤反向约束 (直接 AND 合并，不生成 EXISTS)")
    void testSameTableModuleFilterRollup_DirectAndMerge() {
        // 模拟 105 模块与 101 模块同为主表 student
        SysModuleMetaResp mockModule105SameTable = new SysModuleMetaResp();
        SysModuleMetaResp.ModuleInfo info105 = new SysModuleMetaResp.ModuleInfo();
        info105.setId(105L);
        info105.setPrimaryTable("student");
        info105.setParentId(101L);
        mockModule105SameTable.setModule(info105);
        mockModule105SameTable.setFields(
                List.of(
                        ModuleFieldDTO.builder()
                                .id(1051L)
                                .moduleId(105L)
                                .tableName("student")
                                .columnName("hobby")
                                .displayName("爱好")
                                .sortOrder(1)
                                .build()));

        lenient().when(metadataCacheService.getModuleComplete(101L)).thenReturn(mockModule101);
        lenient()
                .when(metadataCacheService.getModuleComplete(105L))
                .thenReturn(mockModule105SameTable);
        lenient()
                .when(permissionFilterService.filterReadableFields(mockModule101))
                .thenReturn(mockModule101.getFields());
        lenient()
                .when(permissionFilterService.filterReadableFields(mockModule105SameTable))
                .thenReturn(mockModule105SameTable.getFields());

        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .moduleId(101L)
                        .fields(List.of(1011L, 1012L))
                        .children(
                                List.of(
                                        DynamicQueryReq.builder()
                                                .moduleId(105L)
                                                .fields(List.of(1051L))
                                                .filters(
                                                        List.of(
                                                                DynamicFilterItem.builder()
                                                                        .fieldId(1051L)
                                                                        .operator("LIKE")
                                                                        .value("篮球")
                                                                        .build()))
                                                .build()))
                        .build();

        var queryPlan = queryPlanCompiler.compile(req);
        var rootPlan = queryPlan.getRootNodePlan();
        assertNotNull(rootPlan);

        // 根节点应当直接 AND 合并 hobby 条件，绝不包含 EXISTS
        String rootCondSql = rootPlan.getCondition().toString().toLowerCase();
        assertFalse(rootCondSql.contains("exists"), "同表垂直拆分模块过滤条件应直接合并，不生成 EXISTS");
        assertTrue(rootCondSql.contains("hobby"), "根节点必须包含同表子模块的筛选字段 hobby");
        assertTrue(rootCondSql.contains("篮球"), "根节点必须包含同表子模块的筛选值 '篮球'");

        DataPage<Map<String, Object>> result = queryPlanExecutor.execute(queryPlan);
        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
    }

    @Test
    @DisplayName("测试 DataPage 序列化输出严格符合顶层挂在根 moduleId 下的契约")
    void testDataPageJsonSerializationMatchesRootModuleIdContract() throws Exception {
        lenient().when(metadataCacheService.getModuleComplete(101L)).thenReturn(mockModule101);
        lenient().when(metadataCacheService.getModuleComplete(103L)).thenReturn(mockModule103);
        lenient().when(metadataCacheService.getModuleComplete(104L)).thenReturn(mockModule104);
        lenient()
                .when(permissionFilterService.filterReadableFields(mockModule101))
                .thenReturn(mockModule101.getFields());
        lenient()
                .when(permissionFilterService.filterReadableFields(mockModule103))
                .thenReturn(mockModule103.getFields());
        lenient()
                .when(permissionFilterService.filterReadableFields(mockModule104))
                .thenReturn(mockModule104.getFields());

        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .moduleId(101L)
                        .pageNo(1)
                        .pageSize(10)
                        .fields(List.of(1011L, 1012L, 1013L, 1014L))
                        .children(
                                List.of(
                                        DynamicQueryReq.builder()
                                                .moduleId(103L)
                                                .fields(List.of(1031L, 1032L))
                                                .children(
                                                        List.of(
                                                                DynamicQueryReq.builder()
                                                                        .moduleId(104L)
                                                                        .fields(
                                                                                List.of(
                                                                                        1041L,
                                                                                        1042L))
                                                                        .build()))
                                                .build()))
                        .build();

        DataPage<Map<String, Object>> response = dynamicQueryService.query(req);
        assertNotNull(response);

        // 验证 header 树形契约原子下发
        assertNotNull(response.getHeader(), "DataPage 响应中必须原子携带本次查询对齐的树形 header");
        assertEquals(101L, response.getHeader().getModuleId());
        assertEquals("学生综合档案", response.getHeader().getLabel());
        assertNotNull(response.getHeader().getChildren());
        assertTrue(
                response.getHeader().getChildren().stream()
                        .anyMatch(c -> "student.name".equals(c.getDataIndex())));
        assertTrue(
                response.getHeader().getChildren().stream()
                        .anyMatch(c -> Long.valueOf(103L).equals(c.getModuleId())));

        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        String json = mapper.writeValueAsString(response);

        // 验证 records 和 header 顶层同级
        com.fasterxml.jackson.databind.JsonNode rootNode = mapper.readTree(json);
        assertTrue(rootNode.has("header"), "JSON 根节点必须包含原子 'header' 树形契约");
        assertTrue(rootNode.has("records"), "JSON 根节点必须包含与 'header' 同级的 'records' 数组");
        assertEquals(1, rootNode.get("records").size());

        // 验证三级递归自相似数据链路：records[0]["101"] 根模块包裹，行内自包含物理表与子模块数组
        com.fasterxml.jackson.databind.JsonNode rootRecord = rootNode.get("records").get(0);
        assertTrue(rootRecord.has("101"), "每条记录必须包含根模块 101 包裹对象");
        com.fasterxml.jackson.databind.JsonNode studentNode = rootRecord.get("101");
        assertEquals("S001", studentNode.get("student").get("student_no").asText());
        assertEquals("张三", studentNode.get("student").get("name").asText());
        assertEquals("高三(1)班", studentNode.get("clazz").get("class_name").asText());

        // 二级模块 103 选课 (包含在 101 内部)
        com.fasterxml.jackson.databind.JsonNode courseNode = studentNode.get("103").get(0);
        assertEquals("大学英语", courseNode.get("student_course").get("course_name").asText());

        // 三级模块 104 成绩项 (包含在 103 选课内部)
        com.fasterxml.jackson.databind.JsonNode scoreNode = courseNode.get("104").get(0);
        assertEquals("期末大作业", scoreNode.get("student_course_score_item").get("item_name").asText());
        assertEquals(95.0, scoreNode.get("student_course_score_item").get("score").asDouble());
    }

    @Test
    @DisplayName("测试同模块 1:N 物理从表装配时自动解包消除双层同名嵌套")
    void testAssembleSameModuleSubTableUnwrapped() {
        TreeResultAssembler assembler = new TreeResultAssembler();

        // 根层记录（学生记录）
        Map<String, Object> studentRow = new LinkedHashMap<>();
        studentRow.put("student", Map.of("id", 5L, "name", "张三"));

        // 选课子记录（未装配前，包含物理表和外键）
        Map<String, Object> courseRow = new LinkedHashMap<>();
        courseRow.put("student_course", Map.of("id", 8L, "student_id", 5L, "course_name", "高等数学"));
        courseRow.put("_fk_student_id", 5L);

        // 同模块子计划（成绩分项，属于同一模块 103）
        com.jdec.platform.data.biz.plan.model.QueryNodePlan scoreSubPlan =
                com.jdec.platform.data.biz.plan.model.QueryNodePlan.builder()
                        .moduleId(103L)
                        .primaryTable("student_course_score_item")
                        .parentForeignKey("student_course_id")
                        .build();

        // 选课子计划（属于模块 103，挂载成绩分项计划）
        com.jdec.platform.data.biz.plan.model.QueryNodePlan coursePlan =
                com.jdec.platform.data.biz.plan.model.QueryNodePlan.builder()
                        .moduleId(103L)
                        .primaryTable("student_course")
                        .parentForeignKey("student_id")
                        .children(List.of(scoreSubPlan))
                        .build();

        // 模拟执行器拉取的原始从表记录（已被包裹在物理表空间内）
        Map<String, Object> rawScoreRow = new LinkedHashMap<>();
        rawScoreRow.put(
                "student_course_score_item",
                Map.of("id", 28L, "student_course_id", 8L, "item_name", "期中测验", "score", 85.0));
        rawScoreRow.put("_fk_student_course_id", 8L);

        Map<com.jdec.platform.data.biz.plan.model.QueryNodePlan, List<Map<String, Object>>>
                rawData = new IdentityHashMap<>();
        rawData.put(coursePlan, List.of(courseRow));
        rawData.put(scoreSubPlan, List.of(rawScoreRow));

        // 从根层学生开始递归装配子模块 103 及其同模块从表
        assembler.assembleChildrenRecursively(
                List.of(studentRow), 101L, List.of(coursePlan), rawData);

        // 验证：studentRow 下挂载子模块 103 数组
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courseList = (List<Map<String, Object>>) studentRow.get("103");
        assertNotNull(courseList, "必须成功装配 103 模块");
        assertEquals(1, courseList.size());

        Map<String, Object> c0 = courseList.get(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scoreItems =
                (List<Map<String, Object>>) c0.get("student_course_score_item");
        assertNotNull(scoreItems, "必须成功装配同模块从表数组");
        assertEquals(1, scoreItems.size());

        // 核心断言：消除双层嵌套，项内部直接包含物理字段，绝无同名外层包裹
        Map<String, Object> scoreItem = scoreItems.get(0);
        assertFalse(
                scoreItem.containsKey("student_course_score_item"),
                "严禁在从表项内部再次嵌套同名物理表 student_course_score_item");
        assertEquals(28L, ((Number) scoreItem.get("id")).longValue());
        assertEquals("期中测验", scoreItem.get("item_name"));
        assertEquals(85.0, ((Number) scoreItem.get("score")).doubleValue());
    }
}
