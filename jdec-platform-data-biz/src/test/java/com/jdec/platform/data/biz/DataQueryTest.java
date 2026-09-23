package com.jdec.platform.data.biz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
import com.jdec.platform.data.api.dto.request.FilterItem;
import com.jdec.platform.data.api.dto.request.OptionReq;
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.data.api.dto.request.SortItem;
import com.jdec.platform.data.api.dto.response.DataOption;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.biz.dsl.JooqDataContextFactory;
import com.jdec.platform.data.biz.plan.assembler.TreeResultAssembler;
import com.jdec.platform.data.biz.plan.compiler.QueryPlanCompiler;
import com.jdec.platform.data.biz.plan.executor.QueryPlanExecutor;
import com.jdec.platform.data.biz.service.DataQueryService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 数据查询引擎 (Data Query Engine) 单元测试
 *
 * <p>基于 config_center、school 和 company 真实业务元数据与关系拓扑设计：
 *
 * <p>1. School 多级深层树：学生 (101) -> 选课 (105) -> 选课考核分项 (108) 1:N:N 深度递归自相似查询
 *
 * <p>2. School 荣誉多分枝：学生 (101) -> 荣誉 (104) -> 荣誉佐证明细 (106)
 *
 * <p>3. Company 虚拟容器模块：外部对象 (1001) -> 虚拟联系方式 (1020) -> 地址 (1005 1:1) & 联系方式 (1006 1:N)
 *
 * <p>4. JSON 字段原生解析：info.cooperation_types 与 info.industry_tags 自动转换为 List
 *
 * <p>5. 动态过滤组合、排序、分页与去重候选项 (Options) 查询
 */
@ExtendWith(MockitoExtension.class)
class DataQueryTest {

    @Mock private ConfigApi configApi;
    @Mock private JooqDataContextFactory jooqDataContextFactory;

    private final TreeResultAssembler treeResultAssembler = new TreeResultAssembler();
    private QueryPlanCompiler queryPlanCompiler;
    private QueryPlanExecutor queryPlanExecutor;
    private DataQueryService dataQueryService;

    private DSLContext dslContext;
    private final Map<Long, ModuleInfo> allKnownModules = new HashMap<>();
    private final List<TableRelationDTO> allKnownRelations = new ArrayList<>();
    private final List<String> executedSqlList = new ArrayList<>();

    @BeforeEach
    void setUp() {
        executedSqlList.clear();

        MockDataProvider provider =
                ctx -> {
                    String sql = ctx.sql().toLowerCase();
                    executedSqlList.add(sql);
                    DSLContext c = DSL.using(SQLDialect.MYSQL);

                    if (sql.contains("select distinct")) {
                        if (sql.contains("course_name")) {
                            var res = c.newResult(DSL.field("course_name", String.class));
                            var r1 = c.newRecord(DSL.field("course_name", String.class));
                            r1.setValue(DSL.field("course_name", String.class), "大学英语");
                            var r2 = c.newRecord(DSL.field("course_name", String.class));
                            r2.setValue(DSL.field("course_name", String.class), "高等数学");
                            res.add(r1);
                            res.add(r2);
                            return new MockResult[] {new MockResult(2, res)};
                        }
                        var res = c.newResult(DSL.field("val", String.class));
                        var r1 = c.newRecord(DSL.field("val", String.class));
                        r1.setValue(DSL.field("val", String.class), "候选值A");
                        res.add(r1);
                        return new MockResult[] {new MockResult(1, res)};
                    }

                    if (sql.contains("count(*)")) {
                        var res = c.newResult(DSL.field("count", Long.class));
                        var rec = c.newRecord(DSL.field("count", Long.class));
                        rec.setValue(DSL.field("count", Long.class), 1L);
                        res.add(rec);
                        return new MockResult[] {new MockResult(1, res)};
                    }

                    java.util.regex.Pattern p =
                            java.util.regex.Pattern.compile("`(\\w+)`\\.`(\\w+)` as `(\\w+)`");
                    java.util.regex.Matcher m = p.matcher(ctx.sql());
                    List<String> tables = new ArrayList<>();
                    List<String> cols = new ArrayList<>();
                    List<String> aliases = new ArrayList<>();
                    while (m.find()) {
                        tables.add(m.group(1));
                        cols.add(m.group(2));
                        aliases.add(m.group(3));
                    }

                    if (aliases.isEmpty()) {
                        return new MockResult[] {new MockResult(0, c.newResult())};
                    }

                    org.jooq.Field<?>[] fs = new org.jooq.Field<?>[aliases.size()];
                    for (int i = 0; i < aliases.size(); i++) {
                        fs[i] = DSL.field(aliases.get(i), Object.class);
                    }

                    String fromTable = tables.get(0);
                    int n = "company_contact".equals(fromTable) ? 2 : 1;

                    var res = c.newResult(fs);
                    for (int row = 0; row < n; row++) {
                        var rec = c.newRecord(fs);
                        for (int i = 0; i < aliases.size(); i++) {
                            rec.setValue(
                                    (org.jooq.Field) fs[i],
                                    valueOf(tables.get(i), cols.get(i), row));
                        }
                        res.add(rec);
                    }
                    return new MockResult[] {new MockResult(n, res)};
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

        queryPlanCompiler = new QueryPlanCompiler(configApi);
        queryPlanExecutor = new QueryPlanExecutor(jooqDataContextFactory, treeResultAssembler);
        dataQueryService = new DataQueryService(queryPlanCompiler, queryPlanExecutor);
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
                                                .moduleId(101L)
                                                .tableName("student")
                                                .columnName("id")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(2L)
                                                .moduleId(101L)
                                                .tableName("student")
                                                .columnName("student_no")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(3L)
                                                .moduleId(101L)
                                                .tableName("student")
                                                .columnName("name")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(4L)
                                                .moduleId(101L)
                                                .tableName("student")
                                                .columnName("gender")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(6L)
                                                .moduleId(101L)
                                                .tableName("student")
                                                .columnName("clazz_id")
                                                .build()))
                        .build();

        ModuleInfo mockModule104 =
                ModuleInfo.builder()
                        .moduleId(104L)
                        .moduleCode("MOD-STUDENT-AWARD")
                        .primaryTable("student_award")
                        .parentId(101L)
                        .fields(
                                List.of(
                                        ModuleFieldDTO.builder()
                                                .id(37L)
                                                .moduleId(104L)
                                                .tableName("student_award")
                                                .columnName("id")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(38L)
                                                .moduleId(104L)
                                                .tableName("student_award")
                                                .columnName("student_id")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(39L)
                                                .moduleId(104L)
                                                .tableName("student_award")
                                                .columnName("award_name")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(56L)
                                                .moduleId(104L)
                                                .tableName("student_award")
                                                .columnName("level")
                                                .build()))
                        .build();

        ModuleInfo mockModule106 =
                ModuleInfo.builder()
                        .moduleId(106L)
                        .moduleCode("MOD-STUDENT-AWARD-DETAIL")
                        .primaryTable("student_award_detail")
                        .parentId(104L)
                        .fields(
                                List.of(
                                        ModuleFieldDTO.builder()
                                                .id(74L)
                                                .moduleId(106L)
                                                .tableName("student_award_detail")
                                                .columnName("id")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(75L)
                                                .moduleId(106L)
                                                .tableName("student_award_detail")
                                                .columnName("award_id")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(76L)
                                                .moduleId(106L)
                                                .tableName("student_award_detail")
                                                .columnName("evidence_name")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(77L)
                                                .moduleId(106L)
                                                .tableName("student_award_detail")
                                                .columnName("evidence_no")
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
                                                .moduleId(105L)
                                                .tableName("student_course")
                                                .columnName("id")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(29L)
                                                .moduleId(105L)
                                                .tableName("student_course")
                                                .columnName("student_id")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(32L)
                                                .moduleId(105L)
                                                .tableName("student_course")
                                                .columnName("course_name")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(33L)
                                                .moduleId(105L)
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
                                                .moduleId(108L)
                                                .tableName("student_course_score_item")
                                                .columnName("id")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(58L)
                                                .moduleId(108L)
                                                .tableName("student_course_score_item")
                                                .columnName("student_course_id")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(59L)
                                                .moduleId(108L)
                                                .tableName("student_course_score_item")
                                                .columnName("item_name")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(61L)
                                                .moduleId(108L)
                                                .tableName("student_course_score_item")
                                                .columnName("score")
                                                .build()))
                        .build();

        allKnownModules.put(101L, mockModule101);
        allKnownModules.put(104L, mockModule104);
        allKnownModules.put(106L, mockModule106);
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
        allKnownRelations.add(
                TableRelationDTO.builder()
                        .mainTable("student")
                        .mainField("id")
                        .joinTable("student_award")
                        .joinField("student_id")
                        .relationType("1:N")
                        .build());
        allKnownRelations.add(
                TableRelationDTO.builder()
                        .mainTable("student_award")
                        .mainField("id")
                        .joinTable("student_award_detail")
                        .joinField("award_id")
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
                                                .moduleId(1001L)
                                                .tableName("info")
                                                .columnName("id")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(10002L)
                                                .moduleId(1001L)
                                                .tableName("info")
                                                .columnName("company_name")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(10006L)
                                                .moduleId(1001L)
                                                .tableName("info")
                                                .columnName("cooperation_types")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(10007L)
                                                .moduleId(1001L)
                                                .tableName("info")
                                                .columnName("industry_tags")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(10010L)
                                                .moduleId(1001L)
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
                                                .moduleId(1002L)
                                                .tableName("basic_info")
                                                .columnName("id")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(10012L)
                                                .moduleId(1002L)
                                                .tableName("basic_info")
                                                .columnName("uscc")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(10013L)
                                                .moduleId(1002L)
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
                                                .moduleId(1005L)
                                                .tableName("address")
                                                .columnName("id")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(10050L)
                                                .moduleId(1005L)
                                                .tableName("address")
                                                .columnName("comm_region")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(10051L)
                                                .moduleId(1005L)
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
                                                .moduleId(1006L)
                                                .tableName("company_contact")
                                                .columnName("id")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(10054L)
                                                .moduleId(1006L)
                                                .tableName("company_contact")
                                                .columnName("contact_type")
                                                .build(),
                                        ModuleFieldDTO.builder()
                                                .id(10055L)
                                                .moduleId(1006L)
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

    private static Object valueOf(String table, String col, int row) {
        String k = table + "." + col;
        switch (k) {
                // School 模块字段
            case "student.id":
                return 1001L;
            case "student.name":
                return "张三";
            case "student.student_no":
                return "S001";
            case "student.gender":
                return "男";
            case "student.clazz_id":
                return 10L;
            case "student.deleted":
                return (byte) 0;

            case "student_course.id":
                return 301L;
            case "student_course.student_id":
                return 1001L;
            case "student_course.course_name":
                return "大学英语";
            case "student_course.score":
                return 92.0;
            case "student_course.deleted":
                return (byte) 0;

            case "student_course_score_item.id":
                return 801L;
            case "student_course_score_item.student_course_id":
                return 301L;
            case "student_course_score_item.item_name":
                return "期末大作业";
            case "student_course_score_item.score":
                return 95.0;
            case "student_course_score_item.deleted":
                return (byte) 0;

            case "student_award.id":
                return 501L;
            case "student_award.student_id":
                return 1001L;
            case "student_award.award_name":
                return "国家一等奖学金";
            case "student_award.level":
                return "国家级";
            case "student_award.deleted":
                return (byte) 0;

            case "student_award_detail.id":
                return 901L;
            case "student_award_detail.award_id":
                return 501L;
            case "student_award_detail.evidence_name":
                return "国家级奖学金证书";
            case "student_award_detail.evidence_no":
                return "CERT-2026-001";
            case "student_award_detail.deleted":
                return (byte) 0;

                // Company 模块字段
            case "info.id":
                return 139L;
            case "info.company_name":
                return "石嘴山市盛港煤焦化有限公司";
            case "info.cooperation_types":
                return "[\"客户\", \"供应商\"]";
            case "info.industry_tags":
                return "[\"焦化\", \"煤炭\"]";
            case "info.audit_status":
                return "APPROVED";
            case "info.deleted":
                return (byte) 0;

            case "basic_info.id":
                return 18L;
            case "basic_info.company_id":
                return 139L;
            case "basic_info.uscc":
                return "91640200MA75WXYZ89";
            case "basic_info.short_name":
                return "盛港焦化";
            case "basic_info.deleted":
                return (byte) 0;

            case "address.id":
                return 12L;
            case "address.company_id":
                return 139L;
            case "address.comm_region":
                return "宁夏回族自治区/石嘴山市/大武口区";
            case "address.comm_detail":
                return "高新技术产业开发区8号";
            case "address.deleted":
                return (byte) 0;

            case "company_contact.id":
                return (long) (8 + row);
            case "company_contact.company_id":
                return 139L;
            case "company_contact.contact_type":
                return row == 0 ? "PHONE" : "MOBILE";
            case "company_contact.contact_value":
                return row == 0 ? "0710-3255895" : "13585855898";
            case "company_contact.deleted":
                return (byte) 0;

            default:
                if ("id".equals(col)) {
                    return 1L;
                }
                if ("deleted".equals(col)) {
                    return (byte) 0;
                }
                return null;
        }
    }

    @Test
    @DisplayName("测试单模块查询 (School 101): 返回 DataPage 结构与顶层 moduleId 规范")
    void testQuerySingleModuleSchool() {
        QueryReq req =
                QueryReq.builder()
                        .moduleId(101L)
                        .pageNo(1)
                        .pageSize(10)
                        .fields(List.of(1L, 2L, 3L))
                        .build();

        DataPage<Map<String, Object>> page = dataQueryService.query(req);

        assertNotNull(page);
        assertEquals(101L, page.getModuleId());
        assertEquals(1, page.getPageNo());
        assertEquals(10, page.getPageSize());
        assertEquals(1L, page.getTotal());
        assertNotNull(page.getRecords());
        assertEquals(1, page.getRecords().size());

        Map<String, Object> firstRow = page.getRecords().get(0);
        assertNotNull(firstRow.get("student"));
        @SuppressWarnings("unchecked")
        Map<String, Object> studentMap = (Map<String, Object>) firstRow.get("student");
        assertEquals(1001L, studentMap.get("id"));
        assertEquals("张三", studentMap.get("name"));
        assertEquals("S001", studentMap.get("student_no"));
    }

    @Test
    @DisplayName("测试多级深层自相似树查询 (School 101 -> 105 -> 108): 学生 -> 选课 -> 成绩分项")
    void testQueryDeepCascadeTreeSchool() {
        // 请求同时包含 101(学生), 105(选课), 108(成绩分项) 的字段
        QueryReq req =
                QueryReq.builder()
                        .moduleId(101L)
                        .pageNo(1)
                        .pageSize(10)
                        .fields(List.of(1L, 3L, 28L, 32L, 57L, 59L))
                        .build();

        DataPage<Map<String, Object>> page = dataQueryService.query(req);

        assertNotNull(page);
        assertEquals(101L, page.getModuleId());

        Map<String, Object> rootRow = page.getRecords().get(0);
        assertNotNull(rootRow.get("student"));

        // 校验第一层 children 挂载 105
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rootChildren =
                (List<Map<String, Object>>) rootRow.get("children");
        assertNotNull(rootChildren);
        assertFalse(rootChildren.isEmpty());

        Map<String, Object> courseNode =
                rootChildren.stream()
                        .filter(c -> Long.valueOf(105L).equals(c.get("moduleId")))
                        .findFirst()
                        .orElse(null);
        assertNotNull(courseNode);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courseRecords =
                (List<Map<String, Object>>) courseNode.get("records");
        assertEquals(1, courseRecords.size());

        Map<String, Object> courseRecord = courseRecords.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> courseTable = (Map<String, Object>) courseRecord.get("student_course");
        assertEquals(301L, courseTable.get("id"));
        assertEquals("大学英语", courseTable.get("course_name"));

        // 校验第二层 children 递归挂载 108
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courseChildren =
                (List<Map<String, Object>>) courseRecord.get("children");
        assertNotNull(courseChildren);

        Map<String, Object> scoreNode =
                courseChildren.stream()
                        .filter(c -> Long.valueOf(108L).equals(c.get("moduleId")))
                        .findFirst()
                        .orElse(null);
        assertNotNull(scoreNode);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scoreRecords =
                (List<Map<String, Object>>) scoreNode.get("records");
        assertEquals(1, scoreRecords.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> scoreTable =
                (Map<String, Object>) scoreRecords.get(0).get("student_course_score_item");
        assertEquals(801L, scoreTable.get("id"));
        assertEquals("期末大作业", scoreTable.get("item_name"));
    }

    @Test
    @DisplayName("测试同级多分枝子树查询 (School 101 -> 104 -> 106 & 105 -> 108)")
    void testQueryMultipleBranchesSchool() {
        QueryReq req =
                QueryReq.builder()
                        .moduleId(101L)
                        .pageNo(1)
                        .pageSize(10)
                        .fields(List.of(1L, 3L, 37L, 39L, 74L, 76L, 28L, 32L, 57L, 59L))
                        .build();

        DataPage<Map<String, Object>> page = dataQueryService.query(req);
        assertNotNull(page);

        Map<String, Object> rootRow = page.getRecords().get(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) rootRow.get("children");
        assertNotNull(children);

        // 验证同级包含 104 (荣誉) 与 105 (选课)
        boolean hasAward =
                children.stream().anyMatch(c -> Long.valueOf(104L).equals(c.get("moduleId")));
        boolean hasCourse =
                children.stream().anyMatch(c -> Long.valueOf(105L).equals(c.get("moduleId")));
        assertTrue(hasAward);
        assertTrue(hasCourse);

        // 验证 104 下挂载 106
        Map<String, Object> awardNode =
                children.stream()
                        .filter(c -> Long.valueOf(104L).equals(c.get("moduleId")))
                        .findFirst()
                        .orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> awardRecords =
                (List<Map<String, Object>>) awardNode.get("records");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> awardChildren =
                (List<Map<String, Object>>) awardRecords.get(0).get("children");
        assertTrue(
                awardChildren.stream().anyMatch(c -> Long.valueOf(106L).equals(c.get("moduleId"))));
    }

    @Test
    @DisplayName("测试虚拟模块与 JSON 原生解析 (Company 1001 -> 1020 -> 1005 / 1006)")
    void testQueryVirtualModuleAndJsonParsingCompany() {
        QueryReq req =
                QueryReq.builder()
                        .moduleId(1001L)
                        .pageNo(1)
                        .pageSize(10)
                        .fields(
                                List.of(
                                        10001L, 10002L, 10006L, 10007L, 10048L, 10050L, 10053L,
                                        10055L))
                        .build();

        DataPage<Map<String, Object>> page = dataQueryService.query(req);
        assertNotNull(page);
        assertEquals(1001L, page.getModuleId());

        Map<String, Object> rootRow = page.getRecords().get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> infoTable = (Map<String, Object>) rootRow.get("info");
        assertNotNull(infoTable);
        assertEquals("石嘴山市盛港煤焦化有限公司", infoTable.get("company_name"));

        // 验证 JSON 数组被原生解析为 List
        Object coopTypes = infoTable.get("cooperation_types");
        assertInstanceOf(List.class, coopTypes);
        @SuppressWarnings("unchecked")
        List<String> coopList = (List<String>) coopTypes;
        assertEquals(List.of("客户", "供应商"), coopList);

        // 验证虚拟模块 1020 作为节点挂载在 children 下 (方案 A 规范)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rootChildren =
                (List<Map<String, Object>>) rootRow.get("children");
        assertNotNull(rootChildren);

        Map<String, Object> virtualNode =
                rootChildren.stream()
                        .filter(c -> Long.valueOf(1020L).equals(c.get("moduleId")))
                        .findFirst()
                        .orElse(null);
        assertNotNull(virtualNode);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> virtualRecords =
                (List<Map<String, Object>>) virtualNode.get("records");
        assertEquals(1, virtualRecords.size());

        Map<String, Object> virtualContainer = virtualRecords.get(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> subChildren =
                (List<Map<String, Object>>) virtualContainer.get("children");
        assertNotNull(subChildren);

        // 虚拟容器内部挂载 1005 (地址) 和 1006 (联系方式)
        boolean hasAddr =
                subChildren.stream().anyMatch(c -> Long.valueOf(1005L).equals(c.get("moduleId")));
        boolean hasContact =
                subChildren.stream().anyMatch(c -> Long.valueOf(1006L).equals(c.get("moduleId")));
        assertTrue(hasAddr);
        assertTrue(hasContact);
    }

    @Test
    @DisplayName("测试动态多维组合过滤条件 (Dynamic Filter Items)")
    void testQueryDynamicFilters() {
        List<FilterItem> filters =
                List.of(
                        FilterItem.builder()
                                .fieldId(3L) // student.name
                                .operator("LIKE")
                                .value("张%")
                                .build(),
                        FilterItem.builder()
                                .fieldId(1L) // student.id
                                .operator("IN")
                                .value(List.of(1001L, 1002L))
                                .build());

        QueryReq req =
                QueryReq.builder()
                        .moduleId(101L)
                        .pageNo(1)
                        .pageSize(10)
                        .fields(List.of(1L, 2L, 3L))
                        .filters(filters)
                        .build();

        DataPage<Map<String, Object>> page = dataQueryService.query(req);
        assertNotNull(page);

        assertTrue(
                executedSqlList.stream()
                        .anyMatch(
                                sql ->
                                        sql.toLowerCase().contains("where")
                                                && sql.toLowerCase().contains("like")
                                                && sql.toLowerCase().contains("in")));
    }

    @Test
    @DisplayName("测试排序字段解析 (Sorting Direction)")
    void testQuerySorts() {
        List<SortItem> sorts =
                List.of(
                        SortItem.builder().fieldId(1L).direction("DESC").build(),
                        SortItem.builder().fieldId(2L).direction("ASC").build());

        QueryReq req =
                QueryReq.builder()
                        .moduleId(101L)
                        .pageNo(1)
                        .pageSize(10)
                        .fields(List.of(1L, 2L))
                        .sorts(sorts)
                        .build();

        DataPage<Map<String, Object>> page = dataQueryService.query(req);
        assertNotNull(page);

        assertTrue(
                executedSqlList.stream()
                        .anyMatch(
                                sql ->
                                        sql.toLowerCase().contains("order by")
                                                && sql.toLowerCase().contains("desc")
                                                && sql.toLowerCase().contains("asc")));
    }

    @Test
    @DisplayName("测试去重下拉候选项查询 (Get Options)")
    void testGetOptions() {
        OptionReq req = OptionReq.builder().moduleId(101L).fieldId(32L).build(); // course_name

        List<DataOption> options = dataQueryService.getOptions(req);

        assertNotNull(options);
        assertEquals(2, options.size());
        assertTrue(options.stream().anyMatch(o -> "大学英语".equals(o.getLabel())));
        assertTrue(options.stream().anyMatch(o -> "高等数学".equals(o.getLabel())));
    }
}
