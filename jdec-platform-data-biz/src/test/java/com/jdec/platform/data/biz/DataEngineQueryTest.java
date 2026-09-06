package com.jdec.platform.data.biz;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleNodeDTO;
import com.jdec.platform.config.api.dto.common.ModuleTableHeaderDTO;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import com.jdec.platform.config.api.dto.response.SysModuleMetaResp;
import com.jdec.platform.data.api.dto.model.EngineModuleMeta;
import com.jdec.platform.data.api.dto.request.BatchDynamicQueryReq;
import com.jdec.platform.data.api.dto.request.DynamicDetailReq;
import com.jdec.platform.data.api.dto.request.DynamicFilterItem;
import com.jdec.platform.data.api.dto.request.DynamicOptionReq;
import com.jdec.platform.data.api.dto.request.DynamicQueryReq;
import com.jdec.platform.data.api.dto.request.DynamicSortItem;
import com.jdec.platform.data.api.dto.response.BatchEngineDataResult;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.api.dto.response.DynamicOptionItem;
import com.jdec.platform.data.api.dto.response.EngineDataResult;
import com.jdec.platform.data.biz.dsl.JooqConditionBuilder;
import com.jdec.platform.data.biz.dsl.JooqContextFactory;
import com.jdec.platform.data.biz.dsl.JooqSqlBuilder;
import com.jdec.platform.data.biz.service.DynamicQueryService;
import com.jdec.platform.data.biz.service.MetadataCacheService;
import com.jdec.platform.data.biz.service.PermissionFilterService;
import java.util.*;
import org.jooq.Condition;
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

/**
 * 数据引擎 101 模块及子模块 103、104、105 全量子模块表头配置与复合检索单元测试
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
                            if (sql.contains("emergency_phone")) {
                                var res =
                                        create.newResult(
                                                DSL.field("emergency_phone", String.class));
                                var r1 =
                                        create.newRecord(
                                                DSL.field("emergency_phone", String.class));
                                r1.setValue(
                                        DSL.field("emergency_phone", String.class), "13800000006");
                                res.add(r1);
                                return new MockResult[] {new MockResult(1, res)};
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
                        if (sql.contains("from `student_course_score_item` where")) {
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
                        if (sql.contains("from `student_course` where")) {
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

                        // 4. 子模块 105 从表 student_reward 批量查询响应
                        if (sql.contains("from `student_reward` where")) {
                            var result =
                                    create.newResult(
                                            DSL.field("id", Long.class),
                                            DSL.field("student_id", Long.class),
                                            DSL.field("reward_name", String.class),
                                            DSL.field("reward_level", String.class),
                                            DSL.field("reward_date", String.class),
                                            DSL.field("deleted", Byte.class));
                            var record =
                                    create.newRecord(
                                            DSL.field("id", Long.class),
                                            DSL.field("student_id", Long.class),
                                            DSL.field("reward_name", String.class),
                                            DSL.field("reward_level", String.class),
                                            DSL.field("reward_date", String.class),
                                            DSL.field("deleted", Byte.class));
                            record.setValue(DSL.field("id", Long.class), 501L);
                            record.setValue(DSL.field("student_id", Long.class), 1001L);
                            record.setValue(DSL.field("reward_name", String.class), "国家一等奖学金");
                            record.setValue(DSL.field("reward_level", String.class), "国家级");
                            record.setValue(DSL.field("reward_date", String.class), "2026-06-15");
                            record.setValue(DSL.field("deleted", Byte.class), (byte) 0);
                            result.add(record);
                            return new MockResult[] {new MockResult(1, result)};
                        }

                        // 5. 主表与伴生平铺表查询响应 (根据当前 SELECT 语句实际投影字段动态构建 MockResult)
                        List<org.jooq.Field<?>> selectFields = new ArrayList<>();
                        selectFields.add(DSL.field("id", Long.class));
                        selectFields.add(DSL.field("student_no", String.class));
                        selectFields.add(DSL.field("student__student_no", String.class));
                        selectFields.add(DSL.field("name", String.class));
                        selectFields.add(DSL.field("student__name", String.class));

                        if (sql.contains("student`.`clazz_id`")
                                || sql.contains("student__clazz_id")) {
                            selectFields.add(DSL.field("clazz_id", Long.class));
                            selectFields.add(DSL.field("student__clazz_id", Long.class));
                        }
                        selectFields.add(DSL.field("class_name", String.class));
                        selectFields.add(DSL.field("clazz__class_name", String.class));
                        selectFields.add(DSL.field("clazz__id", Long.class));

                        var result =
                                create.newResult(selectFields.toArray(new org.jooq.Field<?>[0]));
                        var record =
                                create.newRecord(selectFields.toArray(new org.jooq.Field<?>[0]));

                        record.setValue(DSL.field("id", Long.class), 1001L);
                        record.setValue(DSL.field("student_no", String.class), "S001");
                        record.setValue(DSL.field("student__student_no", String.class), "S001");
                        record.setValue(DSL.field("name", String.class), "张三");
                        record.setValue(DSL.field("student__name", String.class), "张三");
                        if (sql.contains("student`.`clazz_id`")
                                || sql.contains("student__clazz_id")) {
                            record.setValue(DSL.field("clazz_id", Long.class), 201L);
                            record.setValue(DSL.field("student__clazz_id", Long.class), 201L);
                        }
                        record.setValue(DSL.field("class_name", String.class), "高三(1)班");
                        record.setValue(DSL.field("clazz__class_name", String.class), "高三(1)班");
                        record.setValue(DSL.field("clazz__id", Long.class), 201L);
                        result.add(record);

                        return new MockResult[] {new MockResult(1, result)};
                    }
                };

        MockConnection connection = new MockConnection(provider);
        dslContext = DSL.using(connection, SQLDialect.MYSQL);
        lenient().when(jooqContextFactory.getContext()).thenReturn(dslContext);

        // 模块 101 (学生全景档案) 标准元数据配置
        SysModuleMetaResp.ModuleInfo moduleInfo = new SysModuleMetaResp.ModuleInfo();
        moduleInfo.setId(101L);
        moduleInfo.setModuleCode("MOD-STUDENT");
        moduleInfo.setModuleName("学生综合档案");
        moduleInfo.setPrimaryTable("student");
        moduleInfo.setParentId(0L);

        mockModule101 = new SysModuleMetaResp();
        mockModule101.setModule(moduleInfo);

        // 全量子模块所有字段配置 (101, 103, 104, 105)
        mockModule101.setFields(
                List.of(
                        // 101: 主表 student & 伴生表 clazz
                        ModuleFieldDTO.builder()
                                .tableName("student")
                                .columnName("student_no")
                                .build(),
                        ModuleFieldDTO.builder().tableName("student").columnName("name").build(),
                        ModuleFieldDTO.builder()
                                .tableName("student")
                                .columnName("clazz_id")
                                .build(),
                        ModuleFieldDTO.builder()
                                .tableName("clazz")
                                .columnName("class_name")
                                .build(),
                        // 103: 子模块从表 student_course
                        ModuleFieldDTO.builder()
                                .tableName("student_course")
                                .columnName("course_name")
                                .build(),
                        ModuleFieldDTO.builder()
                                .tableName("student_course")
                                .columnName("teacher_name")
                                .build(),
                        // 104: 子模块孙表 student_course_score_item
                        ModuleFieldDTO.builder()
                                .tableName("student_course_score_item")
                                .columnName("item_name")
                                .build(),
                        ModuleFieldDTO.builder()
                                .tableName("student_course_score_item")
                                .columnName("score")
                                .build(),
                        // 105: 子模块从表 student_reward
                        ModuleFieldDTO.builder()
                                .tableName("student_reward")
                                .columnName("reward_name")
                                .build(),
                        ModuleFieldDTO.builder()
                                .tableName("student_reward")
                                .columnName("reward_level")
                                .build(),
                        ModuleFieldDTO.builder()
                                .tableName("student_reward")
                                .columnName("reward_date")
                                .build()));

        // 全局关系拓扑网 (101 -> clazz, 101 -> 103 -> 104, 101 -> 105)
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
                                .joinTable("student_profile")
                                .joinField("student_id")
                                .relationType("1:1")
                                .build(),
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
                                .build(),
                        TableRelationDTO.builder()
                                .mainTable("student")
                                .mainField("id")
                                .joinTable("student_reward")
                                .joinField("student_id")
                                .relationType("1:N")
                                .build()));

        // 【关键需求】：将子模块 103、104、105 的表和字段全部配置到 101 模块的 sys_module_header 中
        mockModule101.setModuleHeaders(
                List.of(
                        // 101 主模块表头
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
                                .build(),
                        ModuleTableHeaderDTO.builder()
                                .modulePath(List.of(101L))
                                .table("clazz")
                                .field("class_name")
                                .name("班级名称")
                                .searchType("singleFuzzySelect")
                                .build(),
                        // 103 子模块表头
                        ModuleTableHeaderDTO.builder()
                                .modulePath(List.of(101L, 103L))
                                .table("student_course")
                                .field("course_name")
                                .name("选课名称")
                                .searchType("singleFuzzySelect")
                                .build(),
                        ModuleTableHeaderDTO.builder()
                                .modulePath(List.of(101L, 103L))
                                .table("student_course")
                                .field("teacher_name")
                                .name("任课教师")
                                .searchType("singleFuzzySelect")
                                .build(),
                        // 104 子模块 (孙表) 表头
                        ModuleTableHeaderDTO.builder()
                                .modulePath(List.of(101L, 103L, 104L))
                                .table("student_course_score_item")
                                .field("item_name")
                                .name("考核分项")
                                .searchType("singleFuzzySelect")
                                .build(),
                        ModuleTableHeaderDTO.builder()
                                .modulePath(List.of(101L, 103L, 104L))
                                .table("student_course_score_item")
                                .field("score")
                                .name("考核得分")
                                .searchType("between")
                                .build(),
                        // 105 子模块表头
                        ModuleTableHeaderDTO.builder()
                                .modulePath(List.of(101L, 105L))
                                .table("student_reward")
                                .field("reward_name")
                                .name("奖项名称")
                                .searchType("singleFuzzySelect")
                                .build(),
                        ModuleTableHeaderDTO.builder()
                                .modulePath(List.of(101L, 105L))
                                .table("student_reward")
                                .field("reward_level")
                                .name("奖项等级")
                                .searchType("singleFuzzySelect")
                                .build(),
                        ModuleTableHeaderDTO.builder()
                                .modulePath(List.of(101L, 105L))
                                .table("student_reward")
                                .field("reward_date")
                                .name("获奖日期")
                                .searchType("dateRange")
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

        lenient().when(metadataCacheService.getModuleComplete(101L)).thenReturn(mockModule101);
        lenient()
                .when(permissionFilterService.filterReadableHeaders(mockModule101))
                .thenReturn(mockModule101.getModuleHeaders());
    }

    @Test
    @DisplayName("测试 101 表头配置 103、104、105 全量子模块字段时，列表模式自动级联装配所有多分支从表与孙表")
    void testStandardListQueryWithAllSubModulesHeaders() {
        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .moduleId(101L)
                        .viewMode("LIST")
                        .pageNo(1)
                        .pageSize(10)
                        .sorts(
                                List.of(
                                        DynamicSortItem.builder()
                                                .moduleId(101L)
                                                .tableName("student")
                                                .columnName("id")
                                                .direction("DESC")
                                                .build()))
                        .build();

        EngineDataResult<DataPage<Map<String, Object>>> response = dynamicQueryService.query(req);

        // 1. 验证元数据与表头总数 (包含 101, 103, 104, 105 共 10 个表头)
        assertNotNull(response);
        EngineModuleMeta meta = response.getMeta();
        assertNotNull(meta);
        assertEquals(101L, meta.getModuleId());
        assertEquals(10, meta.getHeaders().size(), "表头必须完整包含子模块 103、104、105 的 10 个字段");
        assertNotNull(meta.getModuleNodes(), "meta 中必须包含 moduleNodes 子模块节点");
        assertEquals(3, meta.getModuleNodes().size(), "必须仅包含 101 的 3 个子模块节点 (103, 104, 105)");
        assertEquals(103L, meta.getModuleNodes().get(0).getId());
        assertEquals(104L, meta.getModuleNodes().get(1).getId());
        assertEquals(105L, meta.getModuleNodes().get(2).getId());

        // 2. 验证分页数据
        DataPage<Map<String, Object>> page = response.getData();
        assertEquals(1, page.getRecords().size());
        Map<String, Object> record = page.getRecords().get(0);

        // 3. 验证纯对象多模块作用域 (101, 103, 105) 完整装配与物理隔离
        assertTrue(record.containsKey("101"), "必须包含 101 模块对象空间");
        assertTrue(record.containsKey("103"), "必须包含 103 选课子模块对象空间");
        assertTrue(record.containsKey("105"), "必须包含 105 荣誉子模块对象空间");

        @SuppressWarnings("unchecked")
        Map<String, Object> mod101 = (Map<String, Object>) record.get("101");
        assertTrue(mod101.containsKey("student"), "101 模块中必须包含主表 student");
        assertTrue(mod101.containsKey("clazz"), "101 模块中必须包含伴生表 clazz");

        @SuppressWarnings("unchecked")
        Map<String, Object> mod103 = (Map<String, Object>) record.get("103");
        assertTrue(mod103.containsKey("student_course"), "103 模块中必须包含选课从表");

        @SuppressWarnings("unchecked")
        Map<String, Object> mod105 = (Map<String, Object>) record.get("105");
        assertTrue(mod105.containsKey("student_reward"), "105 模块中必须包含奖惩从表");

        // 验证 103 选课从表及孙表考核项
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courseList =
                (List<Map<String, Object>>) mod103.get("student_course");
        assertEquals(1, courseList.size());
        Map<String, Object> courseMap = courseList.get(0);
        assertEquals("大学英语", courseMap.get("course_name"));
        assertEquals("李老师", courseMap.get("teacher_name"));

        assertTrue(courseMap.containsKey("student_course_score_item"), "选课内部必须内嵌孙表考核项");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scoreList =
                (List<Map<String, Object>>) courseMap.get("student_course_score_item");
        assertEquals(1, scoreList.size());
        assertEquals("期末大作业", scoreList.get(0).get("item_name"));
        assertEquals(95.0, scoreList.get(0).get("score"));

        // 验证 105 奖惩从表
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rewardList =
                (List<Map<String, Object>>) mod105.get("student_reward");
        assertEquals(1, rewardList.size());
        Map<String, Object> rewardMap = rewardList.get(0);
        assertEquals(501L, rewardMap.get("id"));
        assertEquals("国家一等奖学金", rewardMap.get("reward_name"));
        assertEquals("国家级", rewardMap.get("reward_level"));
        assertEquals("2026-06-15", rewardMap.get("reward_date"));
    }

    @Test
    @DisplayName("测试跨 101、103、104、105 全量子模块字段的表头复合搜索 (由 searchType 自动推导，生成多分支 EXISTS)")
    void testAllSubModulesHeaderSearchWithoutOperator() {
        // 前端通过表头搜索框，同时过滤 101主表、101伴生表、103选课、104分项成绩、105奖惩
        List<DynamicFilterItem> filterList =
                List.of(
                        DynamicFilterItem.builder()
                                .moduleId(101L)
                                .tableName("student")
                                .columnName("name")
                                .value("张三")
                                .build(),
                        DynamicFilterItem.builder()
                                .moduleId(101L)
                                .tableName("clazz")
                                .columnName("class_name")
                                .value("高三")
                                .build(),
                        DynamicFilterItem.builder()
                                .moduleId(101L)
                                .tableName("student_course")
                                .columnName("teacher_name")
                                .value("李老师")
                                .build(),
                        DynamicFilterItem.builder()
                                .moduleId(101L)
                                .tableName("student_course_score_item")
                                .columnName("score")
                                .value(List.of(90.0, 100.0))
                                .build(),
                        DynamicFilterItem.builder()
                                .moduleId(101L)
                                .tableName("student_reward")
                                .columnName("reward_name")
                                .value("国家一等奖")
                                .build(),
                        DynamicFilterItem.builder()
                                .moduleId(101L)
                                .tableName("student_reward")
                                .columnName("reward_date")
                                .value(List.of("2026-01-01", "2026-12-31"))
                                .build());

        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .moduleId(101L)
                        .viewMode("LIST")
                        .pageNo(1)
                        .pageSize(10)
                        .filters(filterList)
                        .build();

        var condition =
                jooqConditionBuilder.buildConditions(
                        "student", req, mockModule101.getModuleHeaders(), 1L, mockModule101);

        assertNotNull(condition);
        String sql = condition.toString().toLowerCase();
        System.out.println("=== TEST GENERATED SQL CONDITION ===");
        System.out.println(sql);
        System.out.println("=====================================");

        // 1. 断言主表与伴生表平铺条件
        assertTrue(sql.contains("name"), "必须包含学生姓名过滤");
        assertTrue(sql.contains("class_name"), "必须包含班级过滤");

        // 2. 断言 103 + 104 链路的融合 EXISTS 条件
        assertTrue(sql.contains("student_course"), "EXISTS 必须包含 student_course");
        assertTrue(
                sql.contains("student_course_score_item"), "EXISTS 必须包含 student_course_score_item");
        assertTrue(sql.contains("between"), "104 score 表头为 between，自动生成 between 谓词");

        // 3. 断言 105 奖惩独立分支的 EXISTS 条件
        assertTrue(sql.contains("student_reward"), "必须为 105 奖惩表生成独立的 EXISTS 子查询");
        assertTrue(sql.contains("reward_name"), "必须包含奖项名称过滤");
    }

    @Test
    @DisplayName("测试按需裁剪: 若仅配置主表表头，则 103、104、105 子表 0 次 DB 查询且不返回")
    void testPrunedListQueryWhenOnlyMainTableHeadersConfigured() {
        List<ModuleTableHeaderDTO> mainOnlyHeaders =
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
                                .build());

        lenient()
                .when(permissionFilterService.filterReadableHeaders(mockModule101))
                .thenReturn(mainOnlyHeaders);

        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .moduleId(101L)
                        .viewMode("LIST")
                        .pageNo(1)
                        .pageSize(10)
                        .build();

        EngineDataResult<DataPage<Map<String, Object>>> response = dynamicQueryService.query(req);

        assertNotNull(response);
        DataPage<Map<String, Object>> page = response.getData();
        assertEquals(1, page.getRecords().size());
        Map<String, Object> record = page.getRecords().get(0);

        assertTrue(record.containsKey("101"), "必须包含 101 模块对象");
        @SuppressWarnings("unchecked")
        Map<String, Object> mod101 = (Map<String, Object>) record.get("101");
        assertTrue(mod101.containsKey("student"));
        assertTrue(mod101.containsKey("clazz"));
        assertFalse(mod101.containsKey("student_course"), "表头未选 103 时严禁返回");
        assertFalse(mod101.containsKey("student_reward"), "表头未选 105 时严禁返回");
    }

    @Test
    @DisplayName("测试标准请求 viewMode=DETAIL: 深度验证多分支 1:N 与 1:N:N 完整响应契约")
    void testStandardDetailQueryResponseContract() {
        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .moduleId(101L)
                        .viewMode("DETAIL")
                        .pageNo(1)
                        .pageSize(10)
                        .build();

        EngineDataResult<DataPage<Map<String, Object>>> response = dynamicQueryService.query(req);

        assertNotNull(response);
        EngineModuleMeta meta = response.getMeta();
        assertNotNull(meta);
        assertNotNull(meta.getFields());
        assertEquals(11, meta.getFields().size(), "DETAIL 模式包含全部 11 个物理字段");

        DataPage<Map<String, Object>> page = response.getData();
        assertEquals(1, page.getRecords().size());
        Map<String, Object> record = page.getRecords().get(0);

        assertTrue(record.containsKey("101"));
        assertTrue(record.containsKey("103"));
        assertTrue(record.containsKey("105"));

        @SuppressWarnings("unchecked")
        Map<String, Object> mod101 = (Map<String, Object>) record.get("101");
        assertTrue(mod101.containsKey("student"));
        assertTrue(mod101.containsKey("clazz"));

        @SuppressWarnings("unchecked")
        Map<String, Object> mod103 = (Map<String, Object>) record.get("103");
        assertTrue(mod103.containsKey("student_course"));

        @SuppressWarnings("unchecked")
        Map<String, Object> mod105 = (Map<String, Object>) record.get("105");
        assertTrue(mod105.containsKey("student_reward"));
    }

    @Test
    @DisplayName("测试标准请求 getDetail 门面方法")
    void testStandardGetDetailFacadeContract() {
        DynamicDetailReq req = DynamicDetailReq.builder().moduleId(101L).id(1001L).build();

        EngineDataResult<Map<String, Object>> response = dynamicQueryService.getDetail(req);

        assertNotNull(response);
        assertNotNull(response.getMeta());
        Map<String, Object> detailData = response.getData();
        assertNotNull(detailData);
        assertTrue(detailData.containsKey("101"));
        assertTrue(detailData.containsKey("103"));
        assertTrue(detailData.containsKey("105"));

        @SuppressWarnings("unchecked")
        Map<String, Object> mod101 = (Map<String, Object>) detailData.get("101");
        assertTrue(mod101.containsKey("student"));

        @SuppressWarnings("unchecked")
        Map<String, Object> mod103 = (Map<String, Object>) detailData.get("103");
        assertTrue(mod103.containsKey("student_course"));

        @SuppressWarnings("unchecked")
        Map<String, Object> mod105 = (Map<String, Object>) detailData.get("105");
        assertTrue(mod105.containsKey("student_reward"));
    }

    @Test
    @DisplayName("测试标准多模块 batchQuery 并发查询契约")
    void testStandardBatchQueryContract() {
        Map<String, DynamicQueryReq> queries = new HashMap<>();
        queries.put(
                "mainStudent",
                DynamicQueryReq.builder()
                        .moduleId(101L)
                        .viewMode("LIST")
                        .pageNo(1)
                        .pageSize(10)
                        .build());

        BatchDynamicQueryReq batchReq = BatchDynamicQueryReq.builder().queries(queries).build();

        BatchEngineDataResult batchResult = dynamicQueryService.batchQuery(batchReq);

        assertNotNull(batchResult);
        assertNotNull(batchResult.getResults());
        assertTrue(batchResult.getResults().containsKey("mainStudent"));
        EngineDataResult<DataPage<Map<String, Object>>> singleRes =
                batchResult.getResults().get("mainStudent");
        assertNotNull(singleRes);
        assertEquals(101L, singleRes.getMeta().getModuleId());
        assertEquals(1L, singleRes.getData().getTotal());
    }

    @Test
    @DisplayName("测试虚拟空白聚合模块 (sys_module_field 为空，仅靠表头引用子模块字段) 稳健执行契约")
    void testVirtualBlankCompositeModuleWithoutFields() {
        // 构造一个虚拟空白看板模块：fields 为空，仅配置表头
        SysModuleMetaResp.ModuleInfo moduleInfo = new SysModuleMetaResp.ModuleInfo();
        moduleInfo.setId(999L);
        moduleInfo.setModuleCode("MOD-VIRTUAL-DASHBOARD");
        moduleInfo.setModuleName("学生全景综合看板(虚拟模块)");

        SysModuleMetaResp virtualModule = new SysModuleMetaResp();
        virtualModule.setModule(moduleInfo);
        virtualModule.setFields(Collections.emptyList()); // 物理字段完全为空！
        virtualModule.setModuleHeaders(
                List.of(
                        ModuleTableHeaderDTO.builder()
                                .name("学号")
                                .table("student")
                                .field("student_no")
                                .build(),
                        ModuleTableHeaderDTO.builder()
                                .name("班级")
                                .table("clazz")
                                .field("class_name")
                                .build(),
                        ModuleTableHeaderDTO.builder()
                                .name("课程")
                                .table("student_course")
                                .field("course_name")
                                .build()));
        virtualModule.setTableRelations(mockModule101.getTableRelations());

        when(metadataCacheService.getModuleComplete(999L)).thenReturn(virtualModule);
        when(permissionFilterService.filterReadableHeaders(virtualModule))
                .thenReturn(virtualModule.getModuleHeaders());

        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .moduleId(999L)
                        .viewMode("LIST")
                        .pageNo(1)
                        .pageSize(10)
                        .build();

        EngineDataResult<DataPage<Map<String, Object>>> result = dynamicQueryService.query(req);

        assertNotNull(result);
        assertNotNull(result.getMeta());
        assertEquals(999L, result.getMeta().getModuleId());
        assertEquals("student", result.getMeta().getPrimaryTable());
        assertNotNull(result.getData());
        assertEquals(1L, result.getData().getTotal());
        assertFalse(result.getData().getRecords().isEmpty());

        Map<String, Object> firstRow = result.getData().getRecords().get(0);
        assertTrue(firstRow.containsKey("999"));
        @SuppressWarnings("unchecked")
        Map<String, Object> mod999 = (Map<String, Object>) firstRow.get("999");
        assertTrue(mod999.containsKey("student"));
        assertTrue(mod999.containsKey("clazz"));
    }

    @Test
    @DisplayName("测试彻底未配置模块 (fields 与 headers 均为空) 安全返回空分页不抛异常")
    void testCompletelyUnconfiguredModuleSafeEmpty() {
        SysModuleMetaResp.ModuleInfo moduleInfo = new SysModuleMetaResp.ModuleInfo();
        moduleInfo.setId(888L);
        moduleInfo.setModuleCode("MOD-EMPTY");
        moduleInfo.setModuleName("空白模块");

        SysModuleMetaResp emptyModule = new SysModuleMetaResp();
        emptyModule.setModule(moduleInfo);
        emptyModule.setFields(Collections.emptyList());
        emptyModule.setModuleHeaders(Collections.emptyList());

        when(metadataCacheService.getModuleComplete(888L)).thenReturn(emptyModule);
        when(permissionFilterService.filterReadableHeaders(emptyModule))
                .thenReturn(Collections.emptyList());

        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .moduleId(888L)
                        .viewMode("LIST")
                        .pageNo(1)
                        .pageSize(10)
                        .build();

        EngineDataResult<DataPage<Map<String, Object>>> result = dynamicQueryService.query(req);

        assertNotNull(result);
        assertNotNull(result.getData());
        assertEquals(0L, result.getData().getTotal());
        assertTrue(result.getData().getRecords().isEmpty());
    }

    @Test
    @DisplayName("测试表头乱序场景 (clazz 排第 1 列，student 排末尾) 拓扑权重分析依然精准识别主表 student 并正确组装 SQL")
    void testDisorderedHeadersTopologyStability() {
        // 构造一个表头严重倒序的虚拟空白聚合模块：
        // 第 1 列是 clazz (N:1 伴生维表)
        // 第 2 列是 student_course (1:N 级联从表)
        // 第 3 列才是 student (主实体表)
        SysModuleMetaResp.ModuleInfo moduleInfo = new SysModuleMetaResp.ModuleInfo();
        moduleInfo.setId(777L);
        moduleInfo.setModuleCode("MOD-DISORDER-HEADERS");
        moduleInfo.setModuleName("表头乱序模块");
        // 故意不显式指定 primaryTable，考察拓扑权重打分算法的自主推导能力！
        moduleInfo.setPrimaryTable(null);

        SysModuleMetaResp disorderModule = new SysModuleMetaResp();
        disorderModule.setModule(moduleInfo);
        disorderModule.setFields(Collections.emptyList());
        disorderModule.setModuleHeaders(
                List.of(
                        ModuleTableHeaderDTO.builder()
                                .name("班级名称(故意排第一列)")
                                .table("clazz")
                                .field("class_name")
                                .build(),
                        ModuleTableHeaderDTO.builder()
                                .name("选修课程(故意排第二列)")
                                .table("student_course")
                                .field("course_name")
                                .build(),
                        ModuleTableHeaderDTO.builder()
                                .name("学号(主表字段故意排最后)")
                                .table("student")
                                .field("student_no")
                                .build()));
        disorderModule.setTableRelations(mockModule101.getTableRelations());

        when(metadataCacheService.getModuleComplete(777L)).thenReturn(disorderModule);
        when(permissionFilterService.filterReadableHeaders(disorderModule))
                .thenReturn(disorderModule.getModuleHeaders());

        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .moduleId(777L)
                        .viewMode("LIST")
                        .pageNo(1)
                        .pageSize(10)
                        .build();

        EngineDataResult<DataPage<Map<String, Object>>> result = dynamicQueryService.query(req);

        assertNotNull(result);
        assertNotNull(result.getMeta());
        assertEquals(777L, result.getMeta().getModuleId());
        // 核心断言：无论表头如何颠倒，推导出的物理主表必须依然是 student！
        assertEquals("student", result.getMeta().getPrimaryTable());
        assertNotNull(result.getData());
        assertEquals(1L, result.getData().getTotal());
        assertFalse(result.getData().getRecords().isEmpty());

        Map<String, Object> row = result.getData().getRecords().get(0);
        assertTrue(row.containsKey("777"));
        @SuppressWarnings("unchecked")
        Map<String, Object> mod777 = (Map<String, Object>) row.get("777");
        assertTrue(mod777.containsKey("student"));
        assertTrue(mod777.containsKey("clazz"));
    }

    @Test
    @DisplayName("测试多层嵌套极端场景：表头仅勾选主表与孙表字段(中间从表未配任何列)，系统基于模块树闭包自动连通并装配孙表数据")
    void testMultiLevelSkippingIntermediateTableClosure() {
        // 构造模块 666：表头仅有 student (主表) 与 student_course_score_item (孙表)
        SysModuleMetaResp.ModuleInfo moduleInfo = new SysModuleMetaResp.ModuleInfo();
        moduleInfo.setId(666L);
        moduleInfo.setModuleCode("MOD-SKIP-INTERMEDIATE");
        moduleInfo.setModuleName("跳过中间表测试模块");
        moduleInfo.setPrimaryTable("student");

        SysModuleMetaResp skipModule = new SysModuleMetaResp();
        skipModule.setModule(moduleInfo);
        skipModule.setFields(Collections.emptyList());
        skipModule.setModuleHeaders(
                List.of(
                        ModuleTableHeaderDTO.builder()
                                .modulePath(List.of(666L))
                                .name("学生姓名")
                                .table("student")
                                .field("name")
                                .build(),
                        ModuleTableHeaderDTO.builder()
                                .modulePath(List.of(666L, 103L))
                                .name("考核分项(孙表，中间表未配任何字段)")
                                .table("student_course_score_item")
                                .field("item_name")
                                .build()));

        // 全局关系中具备：student -> student_course 以及 student_course -> student_course_score_item
        skipModule.setTableRelations(mockModule101.getTableRelations());

        when(metadataCacheService.getModuleComplete(666L)).thenReturn(skipModule);
        when(permissionFilterService.filterReadableHeaders(skipModule))
                .thenReturn(skipModule.getModuleHeaders());

        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .moduleId(666L)
                        .viewMode("LIST")
                        .pageNo(1)
                        .pageSize(10)
                        .build();

        EngineDataResult<DataPage<Map<String, Object>>> result = dynamicQueryService.query(req);

        assertNotNull(result);
        assertNotNull(result.getData());
        assertEquals(1, result.getData().getRecords().size());

        Map<String, Object> record = result.getData().getRecords().get(0);
        assertTrue(record.containsKey("666"), "必须包含 666 根模块空间");
        assertTrue(record.containsKey("103"), "必须通过模块闭包包含 103 选课/孙表空间");

        @SuppressWarnings("unchecked")
        Map<String, Object> mod103 = (Map<String, Object>) record.get("103");
        assertTrue(mod103.containsKey("student_course"), "必须自动作为中间桥梁拉取 student_course");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courseList =
                (List<Map<String, Object>>) mod103.get("student_course");
        assertFalse(courseList.isEmpty());
        Map<String, Object> course = courseList.get(0);
        assertTrue(course.containsKey("student_course_score_item"), "孙表数据必须成功内嵌装配");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scoreList =
                (List<Map<String, Object>>) course.get("student_course_score_item");
        assertEquals(1, scoreList.size());
        assertEquals("期末大作业", scoreList.get(0).get("item_name"));
    }

    @Test
    @DisplayName("测试跨模块同表同字段语义消歧搜索：基于 modulePath 精准区分不同模块的检索条件")
    void testCrossModuleMultiContextFilterWithModulePath() {
        when(metadataCacheService.getModuleComplete(101L)).thenReturn(mockModule101);
        when(permissionFilterService.filterReadableHeaders(mockModule101))
                .thenReturn(mockModule101.getModuleHeaders());

        // 构造包含 modulePath 的复合查询：
        // 1. 选课模块 [101, 103] 下的课程名称检索
        // 2. 考核分项 [101, 104] 下的分项名称检索
        // 3. 奖惩模块 [101, 105] 下的荣誉名称检索
        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .moduleId(101L)
                        .viewMode("LIST")
                        .pageNo(1)
                        .pageSize(10)
                        .filters(
                                List.of(
                                        DynamicFilterItem.builder()
                                                .modulePath(List.of(101L, 103L))
                                                .tableName("student_course")
                                                .columnName("course_name")
                                                .value("高等数学")
                                                .operator("EQ")
                                                .build(),
                                        DynamicFilterItem.builder()
                                                .modulePath(List.of(101L, 104L))
                                                .tableName("student_course_score_item")
                                                .columnName("item_name")
                                                .value("期末大作业")
                                                .operator("LIKE")
                                                .build(),
                                        DynamicFilterItem.builder()
                                                .modulePath(List.of(101L, 105L))
                                                .tableName("student_reward")
                                                .columnName("reward_name")
                                                .value("优秀干部")
                                                .operator("LIKE")
                                                .build()))
                        .build();

        EngineDataResult<DataPage<Map<String, Object>>> result = dynamicQueryService.query(req);

        assertNotNull(result);
        assertNotNull(result.getData());
        assertEquals(1, result.getData().getRecords().size());
    }

    @Test
    @DisplayName("测试多字段复合排序: 支持主表与伴生表多列组合优先级排序")
    void testMultiFieldCompositeSorts() {
        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .moduleId(101L)
                        .viewMode("LIST")
                        .pageNo(1)
                        .pageSize(10)
                        .sorts(
                                List.of(
                                        DynamicSortItem.builder()
                                                .moduleId(101L)
                                                .tableName("clazz")
                                                .columnName("class_name")
                                                .direction("ASC")
                                                .build(),
                                        DynamicSortItem.builder()
                                                .moduleId(101L)
                                                .tableName("student")
                                                .columnName("id")
                                                .direction("DESC")
                                                .build()))
                        .build();

        EngineDataResult<DataPage<Map<String, Object>>> result = dynamicQueryService.query(req);

        assertNotNull(result);
        assertNotNull(result.getData());
        assertEquals(1, result.getData().getRecords().size());
    }

    @Test
    @DisplayName("测试 DynamicQueryReq filters 属性支持原生结构化 Array JSON 反序列化")
    void testStructuredFiltersJsonDeserialization() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();

        String jsonArray =
                "{\"moduleId\": 101, \"filters\": [{\"moduleId\": 101, \"tableName\": \"student\", \"columnName\": \"name\", \"value\": \"李四\", \"operator\": \"LIKE\"}]}";
        DynamicQueryReq reqFromArray = mapper.readValue(jsonArray, DynamicQueryReq.class);
        assertNotNull(reqFromArray.getFilters());
        assertEquals(1, reqFromArray.getFilters().size());
        assertEquals(101L, reqFromArray.getFilters().get(0).getModuleId());
        assertEquals("student", reqFromArray.getFilters().get(0).getTableName());
        assertEquals("name", reqFromArray.getFilters().get(0).getColumnName());
        assertEquals("李四", reqFromArray.getFilters().get(0).getValue());
    }

    @Test
    @DisplayName("测试伴生表 (1:1 student_profile) 过滤条件生成精确物理表名，无未定义的别名前缀")
    void testCompanionTableFilterAlias() {
        DynamicFilterItem filterItem =
                DynamicFilterItem.builder()
                        .moduleId(101L)
                        .tableName("student_profile")
                        .columnName("emergency_phone")
                        .value("13800138000")
                        .operator("LIKE")
                        .build();

        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .moduleId(101L)
                        .viewMode("LIST")
                        .pageNo(1)
                        .pageSize(10)
                        .filters(List.of(filterItem))
                        .build();

        var condition =
                jooqConditionBuilder.buildConditions(
                        "student", req, mockModule101.getModuleHeaders(), 1L, mockModule101);

        assertNotNull(condition);
        String sql = condition.toString().toLowerCase();
        assertTrue(
                sql.contains("student_profile") && sql.contains("emergency_phone"),
                "伴生表条件必须直接使用 student_profile 物理表名");
        assertFalse(sql.contains("101_student_profile"), "外层直连伴生表严禁生成 101_ 别名前缀");
    }

    @Test
    @DisplayName("测试第二阶段子模块从表拉取时精准下推过滤条件 (如 student_course 仅返回命中课程)")
    void testSubTableCascadeFiltering() {
        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .moduleId(101L)
                        .viewMode("LIST")
                        .pageNo(1)
                        .pageSize(10)
                        .filters(
                                List.of(
                                        DynamicFilterItem.builder()
                                                .moduleId(101L)
                                                .tableName("student_profile")
                                                .columnName("emergency_phone")
                                                .value("13800000006")
                                                .operator("LIKE")
                                                .build(),
                                        DynamicFilterItem.builder()
                                                .moduleId(103L)
                                                .tableName("student_course")
                                                .columnName("course_name")
                                                .value("新选课程1")
                                                .operator("LIKE")
                                                .build()))
                        .build();

        Condition subTableCondition =
                jooqConditionBuilder.buildSubTableConditions(
                        "student_course", req, mockModule101.getModuleHeaders(), mockModule101);

        assertNotNull(subTableCondition);
        String sql = subTableCondition.toString().toLowerCase();
        assertTrue(sql.contains("course_name"), "必须包含课程名称过滤");
        assertTrue(sql.contains("新选课程1"), "必须包含过滤值新选课程1");
    }

    @Test
    @DisplayName("测试通用字段搜索下拉候选项查询 (getOptions 成功返回 label-value 列表)")
    void testGetOptionsSuccess() {
        when(metadataCacheService.getModuleComplete(101L)).thenReturn(mockModule101);

        DynamicOptionReq req =
                DynamicOptionReq.builder()
                        .moduleId(101L)
                        .tableName("student_course")
                        .columnName("course_name")
                        .build();

        List<DynamicOptionItem> options = dynamicQueryService.getOptions(req);

        assertNotNull(options, "候选项列表不能为 null");
        assertEquals(2, options.size(), "返回候选项条数应为 2");
        assertEquals("大学英语", options.get(0).getLabel());
        assertEquals("大学英语", options.get(0).getValue());
        assertEquals("高等数学", options.get(1).getLabel());
        assertEquals("高等数学", options.get(1).getValue());
    }

    @Test
    @DisplayName("测试带 keyword 的下拉候选项查询")
    void testGetOptionsWithKeyword() {
        when(metadataCacheService.getModuleComplete(101L)).thenReturn(mockModule101);

        DynamicOptionReq req =
                DynamicOptionReq.builder()
                        .moduleId(101L)
                        .tableName("student_profile")
                        .columnName("emergency_phone")
                        .keyword("138")
                        .build();

        List<DynamicOptionItem> options = dynamicQueryService.getOptions(req);

        assertNotNull(options);
        assertEquals(1, options.size());
        assertEquals("13800000006", options.get(0).getLabel());
        assertEquals("13800000006", options.get(0).getValue());
    }

    @Test
    @DisplayName("测试下拉候选项参数非法性校验 (moduleId/tableName/columnName 缺失或非法)")
    void testGetOptionsValidation() {
        assertThrows(
                IllegalArgumentException.class,
                () -> dynamicQueryService.getOptions(null),
                "null 请求必须抛出异常");

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        dynamicQueryService.getOptions(
                                DynamicOptionReq.builder()
                                        .tableName("student")
                                        .columnName("name")
                                        .build()),
                "缺少 moduleId 必须抛出异常");

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        dynamicQueryService.getOptions(
                                DynamicOptionReq.builder()
                                        .moduleId(101L)
                                        .tableName("student; drop table student;")
                                        .columnName("name")
                                        .build()),
                "非法字符必须抛出异常");
    }

    @Test
    @DisplayName("测试子模块从表字段排序 (如 student_course.semester) 不会污染外层主表 SQL 导致 Unknown column 报错")
    void testSubTableSortDoesNotBreakMainQuery() {
        when(metadataCacheService.getModuleComplete(101L)).thenReturn(mockModule101);
        when(permissionFilterService.filterReadableHeaders(mockModule101))
                .thenReturn(mockModule101.getModuleHeaders());

        DynamicSortItem subTableSort =
                DynamicSortItem.builder()
                        .moduleId(103L)
                        .tableName("student_course")
                        .columnName("semester")
                        .direction("ASC")
                        .build();

        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .moduleId(101L)
                        .viewMode("LIST")
                        .pageNo(1)
                        .pageSize(10)
                        .sorts(List.of(subTableSort))
                        .build();

        EngineDataResult<DataPage<Map<String, Object>>> result = dynamicQueryService.query(req);

        assertNotNull(result);
        assertNotNull(result.getData());
        assertNotNull(result.getData().getRecords());
        assertFalse(result.getData().getRecords().isEmpty());
    }

    @Test
    @DisplayName("测试同时支持多个排序条件 (主表多列 + 伴生表 + 子表多列复合排序)")
    void testMultipleCompositeSortsOnMainAndSubTables() {
        when(metadataCacheService.getModuleComplete(101L)).thenReturn(mockModule101);
        when(permissionFilterService.filterReadableHeaders(mockModule101))
                .thenReturn(mockModule101.getModuleHeaders());

        DynamicQueryReq req =
                DynamicQueryReq.builder()
                        .moduleId(101L)
                        .viewMode("LIST")
                        .pageNo(1)
                        .pageSize(10)
                        .sorts(
                                List.of(
                                        // 1. 伴生表字段升序
                                        DynamicSortItem.builder()
                                                .moduleId(101L)
                                                .tableName("clazz")
                                                .columnName("grade")
                                                .direction("ASC")
                                                .build(),
                                        // 2. 主表字段降序
                                        DynamicSortItem.builder()
                                                .moduleId(101L)
                                                .tableName("student")
                                                .columnName("student_no")
                                                .direction("DESC")
                                                .build(),
                                        // 3. 子表多列排序: 先学期降序、再课程名称升序
                                        DynamicSortItem.builder()
                                                .moduleId(103L)
                                                .tableName("student_course")
                                                .columnName("semester")
                                                .direction("DESC")
                                                .build(),
                                        DynamicSortItem.builder()
                                                .moduleId(103L)
                                                .tableName("student_course")
                                                .columnName("course_name")
                                                .direction("ASC")
                                                .build()))
                        .build();

        EngineDataResult<DataPage<Map<String, Object>>> result = dynamicQueryService.query(req);

        assertNotNull(result);
        assertNotNull(result.getData());
        assertNotNull(result.getData().getRecords());
        assertFalse(result.getData().getRecords().isEmpty());
    }
}
