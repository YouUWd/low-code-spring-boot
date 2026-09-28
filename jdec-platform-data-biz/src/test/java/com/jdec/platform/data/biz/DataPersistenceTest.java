package com.jdec.platform.data.biz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;

import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.common.ProjectTableRelationsDTO;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.common.DataRow;
import com.jdec.platform.data.api.dto.request.SaveReq;
import com.jdec.platform.data.biz.plan.compiler.SavePlanCompiler;
import com.jdec.platform.data.biz.plan.executor.SavePlanExecutor;
import com.jdec.platform.data.biz.service.DataPersistenceService;
import com.jdec.platform.shared.datasource.JooqDataContextFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
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
 * 数据持久化引擎 (Save Engine) 单元测试
 *
 * <p>基于 config_center、school 和 company 真实业务元数据与关系拓扑设计：
 *
 * <p>1. School 多级深层树：学生档案 (101) -> 选课 (105) -> 选课考核分项 (108) 1:N:N 级联持久化与外键自动传播
 *
 * <p>2. Company 外部对象树：外部对象 (1001) -> 基本信息 (1002 1:1) / 人员 (1003 1:N)
 *
 * <p>3. 虚拟容器模块穿透：外部对象 (1001) -> 虚拟联系方式 (1020) -> 地址 (1005 1:1) & 联系方式 (1006 1:N)
 */
@ExtendWith(MockitoExtension.class)
class DataPersistenceTest {

    @Mock private ConfigApi configApi;
    @Mock private JooqDataContextFactory jooqDataContextFactory;

    private SavePlanCompiler savePlanCompiler;
    private SavePlanExecutor savePlanExecutor;
    private DataPersistenceService dataPersistenceService;

    private DSLContext dslContext;
    private final Map<Long, ModuleInfo> allKnownModules = new HashMap<>();
    private final List<TableRelationDTO> allKnownRelations = new ArrayList<>();
    private final List<String> executedSqlList = new ArrayList<>();
    private final AtomicLong idGenerator = new AtomicLong(2000L);

    @BeforeEach
    void setUp() {
        executedSqlList.clear();
        idGenerator.set(2000L);

        MockDataProvider provider =
                new MockDataProvider() {
                    @Override
                    public MockResult[] execute(MockExecuteContext ctx) {
                        String sql = ctx.sql();
                        executedSqlList.add(sql);
                        DSLContext create = DSL.using(SQLDialect.MYSQL);

                        if (sql.toLowerCase().contains("returning")
                                || sql.toLowerCase().contains("insert into")
                                || sql.toLowerCase().contains("last_insert_id")) {
                            long newId = idGenerator.incrementAndGet();
                            var result = create.newResult(DSL.field("id", Long.class));
                            var record = create.newRecord(DSL.field("id", Long.class));
                            record.setValue(DSL.field("id", Long.class), newId);
                            result.add(record);
                            return new MockResult[] {new MockResult(1, result)};
                        }

                        return new MockResult[] {new MockResult(1, create.newResult())};
                    }
                };

        MockConnection connection = new MockConnection(provider);
        dslContext = DSL.using(connection, SQLDialect.MYSQL);
        lenient().when(jooqDataContextFactory.getContext()).thenReturn(dslContext);

        initMockMetadata();

        lenient()
                .when(configApi.getModuleTree(any(), any(), anyLong()))
                .thenAnswer(
                        invocation -> {
                            Long rootId = invocation.getArgument(2);
                            ModuleInfo rootInfo = allKnownModules.get(rootId);
                            if (rootInfo == null) {
                                return null;
                            }

                            String projectNo = rootId < 1000L ? "school" : "company";
                            Set<Long> allowedIds = new LinkedHashSet<>();
                            Map<Long, ModuleInfo> modMap = new LinkedHashMap<>();
                            Map<Long, List<Long>> p2c = new LinkedHashMap<>();
                            Map<Long, ModuleFieldDTO> fMap = new LinkedHashMap<>();
                            Map<Long, Long> f2m = new LinkedHashMap<>();

                            List<Long> queue = new ArrayList<>();
                            queue.add(rootId);
                            allowedIds.add(rootId);
                            modMap.put(rootId, rootInfo);

                            int idx = 0;
                            while (idx < queue.size()) {
                                Long cur = queue.get(idx++);
                                for (ModuleInfo m : allKnownModules.values()) {
                                    if (cur.equals(m.getParentId())) {
                                        allowedIds.add(m.getModuleId());
                                        modMap.put(m.getModuleId(), m);
                                        p2c.computeIfAbsent(cur, k -> new ArrayList<>())
                                                .add(m.getModuleId());
                                        queue.add(m.getModuleId());
                                    }
                                }
                            }

                            for (ModuleInfo m : modMap.values()) {
                                if (m.getFields() != null) {
                                    for (ModuleFieldDTO f : m.getFields()) {
                                        fMap.put(f.getId(), f);
                                        f2m.put(f.getId(), m.getModuleId());
                                    }
                                }
                            }

                            return ModuleTreeDTO.builder()
                                    .rootModuleId(rootId)
                                    .projectNo(projectNo)
                                    .subjectId(1L)
                                    .rootModule(rootInfo)
                                    .moduleMap(modMap)
                                    .parentToChildren(p2c)
                                    .fieldMap(fMap)
                                    .fieldToModuleMap(f2m)
                                    .allowedModuleIds(allowedIds)
                                    .build();
                        });

        Map<String, List<TableRelationDTO>> forwardMap = new HashMap<>();
        Map<String, List<TableRelationDTO>> reverseMap = new HashMap<>();
        for (TableRelationDTO r : allKnownRelations) {
            forwardMap
                    .computeIfAbsent(
                            r.getMainTable().toLowerCase(), k -> new java.util.ArrayList<>())
                    .add(r);
            reverseMap
                    .computeIfAbsent(
                            r.getJoinTable().toLowerCase(), k -> new java.util.ArrayList<>())
                    .add(r);
        }
        ProjectTableRelationsDTO mockProjectRelations =
                ProjectTableRelationsDTO.builder()
                        .projectNo("school")
                        .allRelations(allKnownRelations)
                        .forwardRelations(forwardMap)
                        .reverseRelations(reverseMap)
                        .build();

        lenient()
                .when(configApi.getProjectTableRelations(nullable(String.class)))
                .thenReturn(mockProjectRelations);

        savePlanCompiler = new SavePlanCompiler(configApi);
        savePlanExecutor = new SavePlanExecutor(jooqDataContextFactory);
        dataPersistenceService = new DataPersistenceService(savePlanCompiler, savePlanExecutor);
    }

    private void initMockMetadata() {
        allKnownModules.clear();
        allKnownRelations.clear();

        // ------------------------- School 模块元数据 -------------------------
        ModuleInfo mockModule101 =
                ModuleInfo.builder()
                        .moduleId(101L)
                        .moduleCode("MOD-SCHOOL-STUDENT")
                        .primaryTable("student")
                        .parentId(0L)
                        .fields(
                                List.of(
                                        ModuleFieldDTO.builder()
                                                .id(1L)
                                                .tableName("student")
                                                .columnName("id")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(2L)
                                                .tableName("student")
                                                .columnName("student_no")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(3L)
                                                .tableName("student")
                                                .columnName("name")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(4L)
                                                .tableName("student")
                                                .columnName("gender")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(6L)
                                                .tableName("student")
                                                .columnName("clazz_id")
                                                .build()))
                        .build();

        ModuleInfo mockModule105 =
                ModuleInfo.builder()
                        .moduleId(105L)
                        .moduleCode("MOD-STUDENT-COURSE")
                        .primaryTable("student_course")
                        .parentId(101L)
                        .fields(
                                List.of(
                                        ModuleFieldDTO.builder()
                                                .id(28L)
                                                .tableName("student_course")
                                                .columnName("id")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(29L)
                                                .tableName("student_course")
                                                .columnName("student_id")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(32L)
                                                .tableName("student_course")
                                                .columnName("course_name")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(33L)
                                                .tableName("student_course")
                                                .columnName("score")
                                                .build()))
                        .build();

        ModuleInfo mockModule108 =
                ModuleInfo.builder()
                        .moduleId(108L)
                        .moduleCode("MOD-STUDENT-COURSE-SCORE")
                        .primaryTable("student_course_score_item")
                        .parentId(105L)
                        .fields(
                                List.of(
                                        ModuleFieldDTO.builder()
                                                .id(57L)
                                                .tableName("student_course_score_item")
                                                .columnName("id")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(58L)
                                                .tableName("student_course_score_item")
                                                .columnName("student_course_id")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(59L)
                                                .tableName("student_course_score_item")
                                                .columnName("item_name")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(61L)
                                                .tableName("student_course_score_item")
                                                .columnName("score")
                                                .build()))
                        .build();

        allKnownModules.put(101L, mockModule101);
        allKnownModules.put(105L, mockModule105);
        allKnownModules.put(108L, mockModule108);

        allKnownRelations.add(
                TableRelationDTO.builder()
                        .mainTable("student")
                        .mainField("id")
                        .joinTable("student_course")
                        .joinField("student_id")
                        .relationType("1:N")
                        .build());
        allKnownRelations.add(
                TableRelationDTO.builder()
                        .mainTable("student_course")
                        .mainField("id")
                        .joinTable("student_course_score_item")
                        .joinField("student_course_id")
                        .relationType("1:N")
                        .build());

        // ------------------------- Company 模块元数据 -------------------------
        ModuleInfo mockModule1001 =
                ModuleInfo.builder()
                        .moduleId(1001L)
                        .moduleCode("COMPANY-ROOT")
                        .primaryTable("info")
                        .parentId(0L)
                        .fields(
                                List.of(
                                        ModuleFieldDTO.builder()
                                                .id(10001L)
                                                .tableName("info")
                                                .columnName("id")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(10002L)
                                                .tableName("info")
                                                .columnName("company_name")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(10006L)
                                                .tableName("info")
                                                .columnName("cooperation_types")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(10010L)
                                                .tableName("info")
                                                .columnName("audit_status")
                                                .build()))
                        .build();

        ModuleInfo mockModule1002 =
                ModuleInfo.builder()
                        .moduleId(1002L)
                        .moduleCode("COMPANY-BASIC-INFO")
                        .primaryTable("basic_info")
                        .parentId(1001L)
                        .fields(
                                List.of(
                                        ModuleFieldDTO.builder()
                                                .id(10011L)
                                                .tableName("basic_info")
                                                .columnName("id")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(10012L)
                                                .tableName("basic_info")
                                                .columnName("uscc")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(10013L)
                                                .tableName("basic_info")
                                                .columnName("short_name")
                                                .build()))
                        .build();

        ModuleInfo mockModule1020 =
                ModuleInfo.builder()
                        .moduleId(1020L)
                        .moduleCode("COMPANY-MEMBER-V")
                        .primaryTable(null) // 虚拟模块
                        .parentId(1001L)
                        .fields(Collections.emptyList())
                        .build();

        ModuleInfo mockModule1005 =
                ModuleInfo.builder()
                        .moduleId(1005L)
                        .moduleCode("COMPANY-CONTACT-ADDR")
                        .primaryTable("address")
                        .parentId(1020L)
                        .fields(
                                List.of(
                                        ModuleFieldDTO.builder()
                                                .id(10048L)
                                                .tableName("address")
                                                .columnName("id")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(10050L)
                                                .tableName("address")
                                                .columnName("comm_region")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(10051L)
                                                .tableName("address")
                                                .columnName("comm_detail")
                                                .build()))
                        .build();

        ModuleInfo mockModule1006 =
                ModuleInfo.builder()
                        .moduleId(1006L)
                        .moduleCode("COMPANY-CONTACT-BIZ")
                        .primaryTable("company_contact")
                        .parentId(1020L)
                        .fields(
                                List.of(
                                        ModuleFieldDTO.builder()
                                                .id(10053L)
                                                .tableName("company_contact")
                                                .columnName("id")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(10054L)
                                                .tableName("company_contact")
                                                .columnName("contact_type")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(10055L)
                                                .tableName("company_contact")
                                                .columnName("contact_value")
                                                .build()))
                        .build();

        allKnownModules.put(1001L, mockModule1001);
        allKnownModules.put(1002L, mockModule1002);
        allKnownModules.put(1020L, mockModule1020);
        allKnownModules.put(1005L, mockModule1005);
        allKnownModules.put(1006L, mockModule1006);

        allKnownRelations.add(
                TableRelationDTO.builder()
                        .mainTable("info")
                        .mainField("id")
                        .joinTable("basic_info")
                        .joinField("company_id")
                        .relationType("1:1")
                        .build());
        allKnownRelations.add(
                TableRelationDTO.builder()
                        .mainTable("info")
                        .mainField("id")
                        .joinTable("address")
                        .joinField("company_id")
                        .relationType("1:1")
                        .build());
        allKnownRelations.add(
                TableRelationDTO.builder()
                        .mainTable("info")
                        .mainField("id")
                        .joinTable("company_contact")
                        .joinField("company_id")
                        .relationType("1:N")
                        .build());
    }

    @Test
    @DisplayName("测试单模块同构保存 (School): student 表单条记录插入")
    void testSaveSingleModuleSchool() {
        Map<String, Object> studentRow = new HashMap<>();
        studentRow.put("name", "张三");
        studentRow.put("student_no", "S001");
        studentRow.put("gender", "男");

        DataRow row = new DataRow();
        row.putTable("student", studentRow);

        SaveReq saveReq = SaveReq.builder().moduleId(101L).records(List.of(row)).build();

        Long savedId = dataPersistenceService.save(saveReq);

        assertNotNull(savedId);
        assertTrue(savedId > 0);
        assertTrue(
                executedSqlList.stream()
                        .anyMatch(sql -> sql.toLowerCase().contains("insert into `student`")));
    }

    @Test
    @DisplayName("测试多级深层树级联保存 (School 101 -> 105 -> 108): 学生 -> 选课 -> 成绩分项")
    void testSaveCascadeTreeSchool() {
        // 孙记录: 成绩分项 108
        Map<String, Object> scoreItem = new HashMap<>();
        scoreItem.put("item_name", "期末大作业");
        scoreItem.put("score", 95.0);

        DataRow scoreItemRow = new DataRow();
        scoreItemRow.putTable("student_course_score_item", scoreItem);

        // 子记录: 选课 105 行对象 (包含 student_course 与 children)
        Map<String, Object> courseRow = new HashMap<>();
        courseRow.put("course_name", "大学英语");
        courseRow.put("score", 90.0);

        DataRow courseRecord = new DataRow();
        courseRecord.putTable("student_course", courseRow);
        courseRecord.setChildren(
                List.of(DataBlock.builder().moduleId(108L).records(List.of(scoreItemRow)).build()));

        // 根记录: 学生 101 行对象 (包含 student 与 children)
        Map<String, Object> studentRow = new HashMap<>();
        studentRow.put("name", "李四");
        studentRow.put("student_no", "S002");

        DataRow rootRecord = new DataRow();
        rootRecord.putTable("student", studentRow);
        rootRecord.setChildren(
                List.of(DataBlock.builder().moduleId(105L).records(List.of(courseRecord)).build()));

        SaveReq saveReq = SaveReq.builder().moduleId(101L).records(List.of(rootRecord)).build();

        Long savedId = dataPersistenceService.save(saveReq);
        assertNotNull(savedId);

        // 校验 3 张表的 INSERT 均被顺序触发
        assertTrue(
                executedSqlList.stream()
                        .anyMatch(sql -> sql.toLowerCase().contains("insert into `student`")));
        assertTrue(
                executedSqlList.stream()
                        .anyMatch(
                                sql -> sql.toLowerCase().contains("insert into `student_course`")));
        assertTrue(
                executedSqlList.stream()
                        .anyMatch(
                                sql ->
                                        sql.toLowerCase()
                                                .contains(
                                                        "insert into `student_course_score_item`")));
    }

    @Test
    @DisplayName("测试虚拟模块穿透级联保存 (Company 1001 -> 1020 -> 1005 / 1006)")
    void testSaveVirtualModuleCascadeCompany() {
        // 地址 1005
        Map<String, Object> addrRow = new HashMap<>();
        addrRow.put("comm_region", "宁夏回族自治区/石嘴山市");
        addrRow.put("comm_detail", "大武口区高新技术产业园8号");

        DataRow addrDataRow = new DataRow();
        addrDataRow.putTable("address", addrRow);

        // 联系方式 1006 (多条)
        Map<String, Object> contact1 = new HashMap<>();
        contact1.put("contact_type", "PHONE");
        contact1.put("contact_value", "0710-3255895");
        DataRow contactRow1 = new DataRow();
        contactRow1.putTable("company_contact", contact1);

        Map<String, Object> contact2 = new HashMap<>();
        contact2.put("contact_type", "MOBILE");
        contact2.put("contact_value", "13585855898");
        DataRow contactRow2 = new DataRow();
        contactRow2.putTable("company_contact", contact2);

        // 虚拟容器 1020 行对象
        DataRow virtualContainer = new DataRow();
        virtualContainer.setChildren(
                List.of(
                        DataBlock.builder().moduleId(1005L).records(List.of(addrDataRow)).build(),
                        DataBlock.builder()
                                .moduleId(1006L)
                                .records(List.of(contactRow1, contactRow2))
                                .build()));

        // 根记录 1001 行对象
        Map<String, Object> infoRow = new HashMap<>();
        infoRow.put("company_name", "盛港焦化有限公司");
        infoRow.put("cooperation_types", "[\"供应商\"]");

        DataRow rootRecord = new DataRow();
        rootRecord.putTable("info", infoRow);
        rootRecord.setChildren(
                List.of(
                        DataBlock.builder()
                                .moduleId(1020L)
                                .records(List.of(virtualContainer))
                                .build()));

        SaveReq saveReq = SaveReq.builder().moduleId(1001L).records(List.of(rootRecord)).build();

        Long savedId = dataPersistenceService.save(saveReq);
        assertNotNull(savedId);

        // 校验根表与穿透子表的 INSERT
        assertTrue(
                executedSqlList.stream()
                        .anyMatch(sql -> sql.toLowerCase().contains("insert into `info`")));
        assertTrue(
                executedSqlList.stream()
                        .anyMatch(sql -> sql.toLowerCase().contains("insert into `address`")));
        assertTrue(
                executedSqlList.stream()
                        .anyMatch(
                                sql ->
                                        sql.toLowerCase()
                                                .contains("insert into `company_contact`")));
    }

    @Test
    @DisplayName("测试更新模式 (Update Mode): 携带主键 id 触发精准 update 语句")
    void testUpdateExistingRecord() {
        Map<String, Object> studentRow = new HashMap<>();
        studentRow.put("id", 1001L);
        studentRow.put("name", "张三(已更新)");
        studentRow.put("student_no", "S001");

        DataRow row = new DataRow();
        row.putTable("student", studentRow);

        SaveReq saveReq = SaveReq.builder().moduleId(101L).records(List.of(row)).build();

        Long savedId = dataPersistenceService.save(saveReq);
        assertEquals(1001L, savedId);

        assertTrue(
                executedSqlList.stream()
                        .anyMatch(
                                sql ->
                                        sql.toLowerCase().contains("update `student`")
                                                && sql.toLowerCase().contains("where")
                                                && sql.toLowerCase().contains("id")));
    }
}
