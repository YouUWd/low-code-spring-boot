package com.jdec.platform.data.biz;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.common.ProjectTableRelationsDTO;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.common.DataRow;
import com.jdec.platform.data.api.dto.request.DetailQueryReq;
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.data.api.dto.response.DataSaveResp;
import com.jdec.platform.data.biz.plan.PlanMeta;
import com.jdec.platform.data.biz.plan.PlanMetaLoader;
import com.jdec.platform.data.biz.plan.assembler.TreeResultAssembler;
import com.jdec.platform.data.biz.plan.compiler.QueryPlanCompiler;
import com.jdec.platform.data.biz.plan.compiler.SavePlanCompiler;
import com.jdec.platform.data.biz.plan.executor.QueryPlanExecutor;
import com.jdec.platform.data.biz.plan.executor.SavePlanExecutor;
import com.jdec.platform.data.biz.plan.model.AttachedTableSpec;
import com.jdec.platform.data.biz.plan.model.QueryNodePlan;
import com.jdec.platform.data.biz.plan.model.SaveNodePlan;
import com.jdec.platform.data.biz.service.DataPersistenceService;
import com.jdec.platform.data.biz.service.DataQueryService;
import com.jdec.platform.shared.datasource.JooqDataContextFactory;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.h2.jdbcx.JdbcDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 垂直拆分（伴生表）与上游表 JOIN 的契约测试 · 真实 school 元数据
 *
 * <p>元数据表 {@code sys_table_relation} 的 {@code relation_type} 只有 {@code '1:1'} 与 {@code '1:N'} 两种取值
 * （真实库 20 行实测），所以「要不要 JOIN」不能仅凭关系类型识别，必须叠加「目标表未被模块树认领」这条判据。 按关系方向分成两类，判据对称：
 *
 * <ul>
 *   <li><b>伴生表（沿关系向下，1:1 垂直拆分）</b>：{@code course -> course_syllabus}。从表不是任何模块的 {@code
 *       primary_table}，只出现在所属模块（102）的字段字典里 ⇒ 必须 LEFT JOIN 进同一行，否则这些列被静默丢弃。 ON
 *       条件由**本表出主键、目标表出外键**（{@code course.id = course_syllabus.course_id}）。
 *   <li><b>上游表（沿关系向上，N:1 多对一）</b>：{@code teacher -> course} 表示 {@code course.teacher_id =
 *       teacher.id}， 即 teacher 是 course 的父。从当前表视角每个课程最多对应一位教师 ⇒ LEFT JOIN 不放大行数，安全。 ON
 *       条件由**本表出外键、目标表出主键**，与伴生表恰好相反。
 *   <li><b>反例</b>：company 的 {@code info -> basic_info / address / credit_rating} 同为 1:1，但三张从表各自都是
 *       模块主表（1002 / 1005 / 1010），由模块树按子节点取数 ⇒ 不得再 JOIN（见 {@code CompanyDataContractTest#C17}）。
 * </ul>
 *
 * <p>另一条通用约束：目标表必须出现在**本模块的字段字典**里，否则 JOIN 纯属浪费（见 V5）。
 */
class VerticalSplitContractTest {

    /** school 模块 102「课程排课中心」，主表 course，字段横跨 course / course_syllabus / teacher 三张物理表。 */
    private static final long COURSE_MODULE_ID = 102L;

    /** school 模块 101「学生列表」，主表同样是 student，但字段字典只声明 student.*（不含 clazz.*）。 */
    private static final long STUDENT_LIST_MODULE_ID = 101L;

    private static final String COURSE = "course";
    private static final String SYLLABUS = "course_syllabus";
    private static final String TEACHER = "teacher";
    private static final String STUDENT = "student";
    private static final String CLAZZ = "clazz";

    private final QueryPlanCompiler compiler = new QueryPlanCompiler();

    // ==================== 编译期：JOIN 规格 ====================

    @Test
    @DisplayName("V1 全字段模式：伴生表(1:1)与上游表(N:1)各生成一个 LEFT JOIN，且 ON 键方向相反")
    void fullTreeModeJoinsVerticalSplitAndUpstreamTables() {
        QueryNodePlan root =
                compiler.compile(
                                QueryReq.builder().view(String.valueOf(COURSE_MODULE_ID)).build(),
                                courseMeta())
                        .getRootNodePlan();

        List<AttachedTableSpec> joins = root.getAttachedJoins();
        assertEquals(2, joins.size(), "应恰好产生 2 个 LEFT JOIN（伴生 + 上游），实际=" + joins);

        AttachedTableSpec companion = byTarget(joins, SYLLABUS);
        assertEquals(COURSE, companion.getSourceTable(), "JOIN 应挂在主表 course 上");
        assertEquals("id", companion.getSourceField(), "伴生表由本表出主键");
        assertEquals("course_id", companion.getTargetField(), "从表外键应为 course_syllabus.course_id");
        assertEquals("1:1", companion.getJoinKind());

        AttachedTableSpec upstream = byTarget(joins, TEACHER);
        assertEquals(COURSE, upstream.getSourceTable(), "JOIN 应挂在主表 course 上");
        assertEquals("teacher_id", upstream.getSourceField(), "上游表由本表出外键");
        assertEquals("id", upstream.getTargetField(), "上游表出主键");
        assertEquals("N:1", upstream.getJoinKind());
    }

    @Test
    @DisplayName("V2 显式投影只含主表列：伴生表与上游表都不 JOIN（无谓开销收敛）")
    void explicitPrimaryOnlyProjectionSkipsAllJoins() {
        QueryNodePlan root =
                compileCourse(
                        QueryReq.builder()
                                .view(String.valueOf(COURSE_MODULE_ID))
                                .columns(List.of(COURSE + ".course_name"))
                                .build());

        assertTrue(
                root.getAttachedJoins().isEmpty(),
                "只投影 course.course_name 时不应 JOIN 任何附加表，实际=" + root.getAttachedJoins());
    }

    @Test
    @DisplayName("V3 显式投影含垂直拆分表列：保留伴生 JOIN 且投影命中该列")
    void explicitCompanionProjectionKeepsVerticalSplitJoin() {
        QueryNodePlan root =
                compileCourse(
                        QueryReq.builder()
                                .view(String.valueOf(COURSE_MODULE_ID))
                                .columns(
                                        List.of(
                                                COURSE + ".course_name",
                                                SYLLABUS + ".prerequisite"))
                                .build());

        List<AttachedTableSpec> joins = root.getAttachedJoins();
        assertEquals(1, joins.size(), "只投影 course_syllabus 列时只应 JOIN 它，实际=" + joins);
        assertEquals(SYLLABUS, joins.get(0).getTargetTable());

        List<String> projected = projectedKeys(root);
        assertTrue(
                projected.contains(SYLLABUS + ".prerequisite"),
                "投影应包含 course_syllabus.prerequisite，实际=" + projected);
        assertTrue(
                projected.contains(COURSE + ".course_name"),
                "投影应包含 course.course_name，实际=" + projected);
    }

    @Test
    @DisplayName("V4 显式投影含上游表列：生成 N:1 LEFT JOIN，且不牵出其它附加表")
    void explicitUpstreamProjectionKeepsUpstreamJoin() {
        QueryNodePlan root =
                compileCourse(
                        QueryReq.builder()
                                .view(String.valueOf(COURSE_MODULE_ID))
                                .columns(
                                        List.of(COURSE + ".course_name", TEACHER + ".teacher_name"))
                                .build());

        List<AttachedTableSpec> joins = root.getAttachedJoins();
        assertEquals(1, joins.size(), "只投影 teacher 列时只应 JOIN teacher，实际=" + joins);

        AttachedTableSpec upstream = joins.get(0);
        assertEquals(TEACHER, upstream.getTargetTable());
        assertEquals("N:1", upstream.getJoinKind());
        assertEquals("teacher_id", upstream.getSourceField());
        assertEquals("id", upstream.getTargetField());

        assertTrue(
                projectedKeys(root).contains(TEACHER + ".teacher_name"),
                "投影应包含 teacher.teacher_name，实际=" + projectedKeys(root));
    }

    @Test
    @DisplayName("V5 上游表未出现在字段字典时不 JOIN（避免拉进无用父表）")
    void upstreamTableNotDeclaredIsNotJoined() {
        // 模块 101 与 102 的 reverse 关系里都有 clazz -> student，但模块 101 的字段字典只声明 student.*，
        // 不声明 clazz.* ⇒ 不应为了 clazz 而 JOIN。
        QueryNodePlan root =
                compiler.compile(
                                QueryReq.builder()
                                        .view(String.valueOf(STUDENT_LIST_MODULE_ID))
                                        .columns(List.of(STUDENT + ".name"))
                                        .build(),
                                studentListMeta())
                        .getRootNodePlan();

        assertTrue(
                root.getAttachedJoins().isEmpty(),
                "字段字典未声明 clazz 时不应 JOIN clazz，实际=" + root.getAttachedJoins());
    }

    // ==================== 执行期：真的下发 LEFT JOIN ====================

    @Test
    @DisplayName("V6 执行侧真的下发 LEFT JOIN：SQL 同时含 course_syllabus 与 teacher 的 JOIN、ON 键及伴生表主键投影")
    void executorEmitsLeftJoinsForBothDirections() {
        List<String> sqls = new ArrayList<>();
        DataQueryService service = serviceCapturing(sqls);

        service.query(QueryReq.builder().view(String.valueOf(COURSE_MODULE_ID)).build());

        String sql = dataSql(sqls);
        assertTrue(
                sql.contains("join `course_syllabus`"),
                "SQL 应 LEFT JOIN course_syllabus，实际=" + sql);
        assertTrue(
                sql.contains("`course`.`id` = `course_syllabus`.`course_id`"),
                "伴生表 ON 键应为 course.id = course_syllabus.course_id，实际=" + sql);
        assertTrue(sql.contains("join `teacher`"), "SQL 应 LEFT JOIN teacher，实际=" + sql);
        assertTrue(
                sql.contains("`course`.`teacher_id` = `teacher`.`id`"),
                "上游表 ON 键应为 course.teacher_id = teacher.id，实际=" + sql);
        assertTrue(
                sql.contains("`course_syllabus`.`id` as `course_syllabus_id`"),
                "1:1 伴生表必须自动投影主键 course_syllabus_id 以保障读写同构更新，实际=" + sql);
        assertTrue(
                sql.contains("`course_syllabus`.`prerequisite` as `course_syllabus_prerequisite`"),
                "附加表列必须带 tbl_col 别名，否则会被塞进主表空间，实际=" + sql);
        assertFalse(sql.contains("select *"), "存在附加表时不得使用 SELECT *（列名无表前缀、无法归位），实际=" + sql);
    }

    @Test
    @DisplayName("V8 全字段模式 + 有附加表：投影显式列出各表字段（避免附加表列混进主表空间）")
    void fullTreeModeWithAttachedTablesUsesExplicitProjection() {
        QueryNodePlan root =
                compiler.compile(
                                QueryReq.builder().view(String.valueOf(COURSE_MODULE_ID)).build(),
                                courseMeta())
                        .getRootNodePlan();

        List<String> projected = projectedKeys(root);
        assertFalse(projected.isEmpty(), "存在附加表时全字段模式也必须显式投影，实际=[]");
        assertTrue(projected.contains(COURSE + ".course_name"), "实际=" + projected);
        assertTrue(projected.contains(SYLLABUS + ".prerequisite"), "实际=" + projected);
        assertTrue(projected.contains(TEACHER + ".teacher_name"), "实际=" + projected);
    }

    @Test
    @DisplayName("V9 全字段模式 + 无附加表：仍走 SELECT *，不改变既有行为")
    void fullTreeModeWithoutAttachedTablesKeepsSelectAll() {
        QueryNodePlan root =
                compiler.compile(
                                QueryReq.builder()
                                        .view(String.valueOf(STUDENT_LIST_MODULE_ID))
                                        .build(),
                                studentListMeta())
                        .getRootNodePlan();

        assertTrue(
                root.getProjectedFields().isEmpty(),
                "无附加表时全字段模式应保持 SELECT *（投影为空），实际=" + projectedKeys(root));
    }

    @Test
    @DisplayName("V7 执行侧：只投影主表列时 SQL 里没有任何附加表 JOIN")
    void executorEmitsNoJoinForPrimaryOnlyProjection() {
        List<String> sqls = new ArrayList<>();
        DataQueryService service = serviceCapturing(sqls);

        service.query(
                QueryReq.builder()
                        .view(String.valueOf(COURSE_MODULE_ID))
                        .columns(List.of(COURSE + ".course_name"))
                        .build());

        String sql = dataSql(sqls);
        assertFalse(sql.contains("course_syllabus"), "SQL 不应出现 course_syllabus，实际=" + sql);
        assertFalse(sql.contains("`teacher`"), "SQL 不应出现 teacher，实际=" + sql);
    }

    @Test
    @DisplayName("V10 保存侧：1:1 伴生表编译为 companionPlans，且自动剥离 N:1 上游只读表")
    void savePlanCompilerExtractsCompanionPlans() {
        SavePlanCompiler saveCompiler = new SavePlanCompiler();
        DataRow row = new DataRow();
        row.putValue(COURSE, "course_name", "高级软件架构");
        row.putValue(SYLLABUS, "prerequisite", "Java编程基础");
        row.putValue(TEACHER, "teacher_name", "王教授");

        DataBlock block =
                DataBlock.builder()
                        .view(String.valueOf(COURSE_MODULE_ID))
                        .records(List.of(row))
                        .build();

        SaveNodePlan plan = saveCompiler.compile(block, courseMeta()).getRootNodePlan();

        assertNotNull(plan.getCompanionPlans(), "伴生表计划列表不应为空");
        assertEquals(1, plan.getCompanionPlans().size(), "应有1行伴生表计划");
        List<SaveNodePlan> rowCompanions = plan.getCompanionPlans().get(0);
        assertEquals(1, rowCompanions.size(), "第一行应包含1个伴生表计划");
        SaveNodePlan compPlan = rowCompanions.get(0);
        assertEquals(SYLLABUS, compPlan.getPrimaryTable(), "伴生表名应为 course_syllabus");
        assertEquals("course_id", compPlan.getParentForeignKey(), "伴生表外键字段应为 course_id");
        assertEquals(1, compPlan.getRecords().size());
        assertEquals("Java编程基础", compPlan.getRecords().get(0).get("prerequisite"));
        assertFalse(plan.getRecords().get(0).containsKey(TEACHER), "上游只读表 teacher 必须从主表落库字段中剥离");
    }

    @Test
    @DisplayName("V11 H2 全链路读写同构：新建主表+伴生表 → getDetail 读取（自带伴生表 id） → 修改后原样 save 更新，不触发唯一键冲突")
    void companionTableFullReadWriteIsomorphicUpdateInH2() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL(
                "jdbc:h2:mem:vertical_split_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        try (Connection conn = ds.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
            stmt.execute(
                    """
                    CREATE TABLE `teacher` (
                      `id` bigint NOT NULL AUTO_INCREMENT,
                      `subject_id` bigint NOT NULL DEFAULT 0,
                      `teacher_name` varchar(100) NOT NULL,
                      `title` varchar(64) DEFAULT NULL,
                      `deleted` tinyint NOT NULL DEFAULT 0,
                      PRIMARY KEY (`id`)
                    );
                    CREATE TABLE `course` (
                      `id` bigint NOT NULL AUTO_INCREMENT,
                      `subject_id` bigint NOT NULL DEFAULT 0,
                      `course_code` varchar(64) DEFAULT NULL,
                      `course_name` varchar(150) NOT NULL,
                      `credit` int DEFAULT 0,
                      `teacher_id` bigint DEFAULT NULL,
                      `status` varchar(32) DEFAULT 'NORMAL',
                      `created_by` bigint DEFAULT NULL,
                      `created_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      `updated_by` bigint DEFAULT NULL,
                      `updated_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      `deleted` tinyint NOT NULL DEFAULT 0,
                      PRIMARY KEY (`id`)
                    );
                    CREATE TABLE `course_syllabus` (
                      `id` bigint NOT NULL AUTO_INCREMENT,
                      `subject_id` bigint NOT NULL DEFAULT 0,
                      `course_id` bigint NOT NULL,
                      `prerequisite` varchar(255) DEFAULT NULL,
                      `syllabus_content` varchar(1000) DEFAULT NULL,
                      `created_by` bigint DEFAULT NULL,
                      `created_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      `updated_by` bigint DEFAULT NULL,
                      `updated_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      `deleted` tinyint NOT NULL DEFAULT 0,
                      PRIMARY KEY (`id`),
                      UNIQUE KEY `uk_course_syllabus_course_id` (`course_id`)
                    );
                    INSERT INTO `teacher` (`id`, `teacher_name`, `title`, `deleted`) VALUES (10, '周教授', '博导', 0);
                    """);

            TransactionAwareDataSourceProxy txAware = new TransactionAwareDataSourceProxy(ds);
            TransactionTemplate txTemplate =
                    new TransactionTemplate(new DataSourceTransactionManager(txAware));

            DefaultConfiguration jooqConfig = new DefaultConfiguration();
            jooqConfig.set(txAware);
            jooqConfig.set(SQLDialect.MYSQL);
            DSLContext dsl = DSL.using(jooqConfig);

            FakeFactory factory = new FakeFactory(dsl);
            PlanMetaLoader metaLoader =
                    new PlanMetaLoader(configApiFor(courseTree(), courseRelations()));
            DataQueryService queryService =
                    new DataQueryService(
                            new QueryPlanCompiler(),
                            new QueryPlanExecutor(factory, new TreeResultAssembler()),
                            metaLoader);
            DataPersistenceService persistenceService =
                    new DataPersistenceService(
                            new SavePlanCompiler(), new SavePlanExecutor(factory), metaLoader);

            // 1. 首次新建保存：主表 course + 伴生表 course_syllabus
            DataRow newRow = new DataRow();
            newRow.putValue(COURSE, "course_code", "CS-101");
            newRow.putValue(COURSE, "course_name", "分布式系统架构");
            newRow.putValue(COURSE, "credit", 4);
            newRow.putValue(COURSE, "teacher_id", 10L);
            newRow.putValue(SYLLABUS, "prerequisite", "操作系统与计算机网络");
            newRow.putValue(SYLLABUS, "syllabus_content", "第一章：一致性协议");

            DataSaveResp createdResp =
                    txTemplate.execute(
                            status ->
                                    persistenceService.save(
                                            DataBlock.of(
                                                    String.valueOf(COURSE_MODULE_ID), newRow)));
            Long courseId = createdResp.getRootId();
            assertNotNull(courseId);
            Long syllabusId = createdResp.getFirstRecord().getLong(SYLLABUS, "id");
            assertNotNull(syllabusId, "新建保存后伴生表主键 id 必须回填到 DataRow");

            // 2. getDetail 查询：验证伴生表空间自动携带 id，且上游表 teacher 一并返回
            DataBlock detail =
                    queryService.getDetail(
                            DetailQueryReq.builder()
                                    .view(String.valueOf(COURSE_MODULE_ID))
                                    .id(courseId)
                                    .build());
            DataRow loadedRow = detail.getFirstRecord();
            assertNotNull(loadedRow);
            assertEquals(courseId, loadedRow.getLong(COURSE, "id"));
            assertEquals(
                    syllabusId,
                    loadedRow.getLong(SYLLABUS, "id"),
                    "getDetail 返回的伴生表空间必须包含伴生表主键 id");
            assertEquals("操作系统与计算机网络", loadedRow.getString(SYLLABUS, "prerequisite"));
            assertEquals("周教授", loadedRow.getString(TEACHER, "teacher_name"));

            // 3. 场景 A：基于 getDetail 返回的 DataBlock 修改主表与伴生表后原样回写 save（100% 读写同构）
            loadedRow.putValue(COURSE, "course_name", "分布式系统架构（进阶版）");
            loadedRow.putValue(SYLLABUS, "prerequisite", "操作系统、网络与并发编程");
            DataSaveResp updatedResp =
                    txTemplate.execute(status -> persistenceService.save(detail));
            assertEquals(courseId, updatedResp.getRootId());

            assertEquals(
                    1L,
                    dsl.fetchCount(DSL.table(DSL.name(SYLLABUS))),
                    "伴生表必须执行 UPDATE 而非重复 INSERT");
            assertEquals(
                    "分布式系统架构（进阶版）",
                    dsl.select(DSL.field(DSL.name("course_name"), String.class))
                            .from(DSL.table(DSL.name(COURSE)))
                            .where(DSL.field(DSL.name("id")).eq(courseId))
                            .fetchOne(0, String.class));
            assertEquals(
                    "操作系统、网络与并发编程",
                    dsl.select(DSL.field(DSL.name("prerequisite"), String.class))
                            .from(DSL.table(DSL.name(SYLLABUS)))
                            .where(DSL.field(DSL.name("id")).eq(syllabusId))
                            .fetchOne(0, String.class));

            // 4. 场景 B：前端仅传主表 id 与伴生表局部修改列（未传伴生表自身 id），自动按 course_id 反查主键并执行 UPDATE
            DataRow partialPatchRow = new DataRow();
            partialPatchRow.putValue(COURSE, "id", courseId);
            partialPatchRow.putValue(COURSE, "course_name", "分布式系统架构（终极版）");
            partialPatchRow.putValue(SYLLABUS, "syllabus_content", "第二章：Raft 与 Paxos 实战");

            DataSaveResp partialResp =
                    txTemplate.execute(
                            status ->
                                    persistenceService.save(
                                            DataBlock.of(
                                                    String.valueOf(COURSE_MODULE_ID),
                                                    partialPatchRow)));
            assertEquals(courseId, partialResp.getRootId());
            assertEquals(
                    syllabusId,
                    partialResp.getFirstRecord().getLong(SYLLABUS, "id"),
                    "未传伴生表 id 时应按外键反查已有主键并回填");
            assertEquals(
                    1L,
                    dsl.fetchCount(DSL.table(DSL.name(SYLLABUS))),
                    "局部更新未传伴生表 id 时同样不得新增重复伴生表记录");
            assertEquals(
                    "操作系统、网络与并发编程",
                    dsl.select(DSL.field(DSL.name("prerequisite"), String.class))
                            .from(DSL.table(DSL.name(SYLLABUS)))
                            .where(DSL.field(DSL.name("id")).eq(syllabusId))
                            .fetchOne(0, String.class),
                    "未在局部更新中提交的伴生表字段应保持原值");
            assertEquals(
                    "第二章：Raft 与 Paxos 实战",
                    dsl.select(DSL.field(DSL.name("syllabus_content"), String.class))
                            .from(DSL.table(DSL.name(SYLLABUS)))
                            .where(DSL.field(DSL.name("id")).eq(syllabusId))
                            .fetchOne(0, String.class),
                    "局部更新提交的伴生表字段应成功落库");
        } finally {
            try (Connection conn = ds.getConnection();
                    Statement stmt = conn.createStatement()) {
                stmt.execute("DROP ALL OBJECTS");
            }
        }
    }

    // ==================== 断言辅助 ====================

    private QueryNodePlan compileCourse(QueryReq req) {
        return compiler.compile(req, courseMeta()).getRootNodePlan();
    }

    private static List<String> projectedKeys(QueryNodePlan root) {
        return root.getProjectedFields().stream()
                .map(f -> f.getTableName() + "." + f.getColumnName())
                .toList();
    }

    private static AttachedTableSpec byTarget(List<AttachedTableSpec> joins, String targetTable) {
        return joins.stream()
                .filter(j -> targetTable.equalsIgnoreCase(j.getTargetTable()))
                .findFirst()
                .orElseThrow(
                        () -> new AssertionError("未找到目标表 " + targetTable + " 的 JOIN，实际=" + joins));
    }

    /** 取捕获到的「数据查询」SQL（排除 COUNT 查询），并把换行压平便于断言。 */
    private static String dataSql(List<String> sqls) {
        return sqls.stream()
                .filter(s -> !s.contains("count("))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未捕获到数据查询 SQL，实际=" + sqls))
                .replace('\n', ' ')
                .replaceAll("\\s+", " ");
    }

    /**
     * 用 MockDataProvider 捕获 SQL 的真实执行链（0 行结果，只关心下发的 SQL）。
     *
     * <p>编译期只产出 {@link AttachedTableSpec}；JOIN 到底有没有拼进 SQL 是执行侧的职责，必须单独钉住。
     */
    private static DataQueryService serviceCapturing(List<String> sqls) {
        MockDataProvider provider =
                ctx -> {
                    String sql = ctx.sql().toLowerCase();
                    sqls.add(sql);
                    DSLContext c = DSL.using(SQLDialect.MYSQL);
                    if (sql.contains("count(")) {
                        var res = c.newResult(DSL.field("count", Long.class));
                        var rec = c.newRecord(DSL.field("count", Long.class));
                        rec.setValue(DSL.field("count", Long.class), 0L);
                        res.add(rec);
                        return new MockResult[] {new MockResult(1, res)};
                    }
                    return new MockResult[] {new MockResult(0, c.newResult())};
                };

        DSLContext dsl = DSL.using(new MockConnection(provider), SQLDialect.MYSQL);
        QueryPlanExecutor executor =
                new QueryPlanExecutor(new FakeFactory(dsl), new TreeResultAssembler());
        return new DataQueryService(
                new QueryPlanCompiler(),
                executor,
                new PlanMetaLoader(configApiFor(courseTree(), courseRelations())));
    }

    private static ConfigApi configApiFor(ModuleTreeDTO tree, ProjectTableRelationsDTO relations) {
        return mock(
                ConfigApi.class,
                invocation -> {
                    String name = invocation.getMethod().getName();
                    if ("getModuleTree".equals(name)) {
                        return tree;
                    }
                    if ("getProjectModuleTree".equals(name)) {
                        return List.of(tree);
                    }
                    if ("getProjectTableRelations".equals(name)) {
                        return relations;
                    }
                    Class<?> rt = invocation.getMethod().getReturnType();
                    if (rt == boolean.class) {
                        return false;
                    }
                    if (rt == int.class) {
                        return 0;
                    }
                    if (rt == long.class) {
                        return 0L;
                    }
                    if (rt.isPrimitive()) {
                        return null;
                    }
                    if (List.class.isAssignableFrom(rt)) {
                        return Collections.emptyList();
                    }
                    if (Map.class.isAssignableFrom(rt)) {
                        return Collections.emptyMap();
                    }
                    return null;
                });
    }

    /** 让执行器拿到 MockConnection 背后的 DSLContext。 */
    static class FakeFactory extends JooqDataContextFactory {
        private final DSLContext dsl;

        FakeFactory(DSLContext dsl) {
            super(null);
            this.dsl = dsl;
        }

        @Override
        public DSLContext getContext() {
            return dsl;
        }

        @Override
        public DSLContext getContext(String projectNo) {
            return dsl;
        }
    }

    // ==================================================================
    // 最小 fixture：字段 ID / 表名 / 列名均取自 config_center 真实元数据
    // ==================================================================

    private static PlanMeta courseMeta() {
        return new PlanMeta(courseTree(), courseRelations());
    }

    private static PlanMeta studentListMeta() {
        List<ModuleFieldDTO> fields =
                List.of(
                        f(1L, STUDENT_LIST_MODULE_ID, STUDENT, "id"),
                        f(2L, STUDENT_LIST_MODULE_ID, STUDENT, "student_no"),
                        f(3L, STUDENT_LIST_MODULE_ID, STUDENT, "name"));
        return new PlanMeta(
                treeOf(module(STUDENT_LIST_MODULE_ID, "学生列表", STUDENT, fields), fields),
                courseRelations());
    }

    private static ModuleTreeDTO courseTree() {
        List<ModuleFieldDTO> fields =
                List.of(
                        f(15L, COURSE_MODULE_ID, COURSE, "id"),
                        f(16L, COURSE_MODULE_ID, COURSE, "course_code"),
                        f(17L, COURSE_MODULE_ID, COURSE, "course_name"),
                        f(18L, COURSE_MODULE_ID, COURSE, "credit"),
                        f(19L, COURSE_MODULE_ID, COURSE, "teacher_id"),
                        f(20L, COURSE_MODULE_ID, COURSE, "status"),
                        f(23L, COURSE_MODULE_ID, SYLLABUS, "prerequisite"),
                        f(24L, COURSE_MODULE_ID, SYLLABUS, "syllabus_content"),
                        f(21L, COURSE_MODULE_ID, TEACHER, "teacher_name"),
                        f(22L, COURSE_MODULE_ID, TEACHER, "title"));
        return treeOf(module(COURSE_MODULE_ID, "课程排课中心", COURSE, fields), fields);
    }

    /** 真实 school 关系（三条都与本测试的两个模块相关）。 */
    private static ProjectTableRelationsDTO courseRelations() {
        List<TableRelationDTO> relations =
                List.of(
                        // 垂直拆分：course.id = course_syllabus.course_id，真·1:1
                        rel(COURSE, "id", SYLLABUS, "course_id", "1:1"),
                        // 上游：teacher.id = course.teacher_id，单向记为 teacher -> course
                        rel(TEACHER, "id", COURSE, "teacher_id", "1:N"),
                        // 上游：clazz.id = student.clazz_id
                        rel(CLAZZ, "id", STUDENT, "clazz_id", "1:N"));

        Map<String, List<TableRelationDTO>> forward = new LinkedHashMap<>();
        Map<String, List<TableRelationDTO>> reverse = new LinkedHashMap<>();
        for (TableRelationDTO relation : relations) {
            forward.computeIfAbsent(relation.getMainTable().toLowerCase(), k -> new ArrayList<>())
                    .add(relation);
            reverse.computeIfAbsent(relation.getJoinTable().toLowerCase(), k -> new ArrayList<>())
                    .add(relation);
        }

        return ProjectTableRelationsDTO.builder()
                .projectNo("school")
                .allRelations(relations)
                .forwardRelations(forward)
                .reverseRelations(reverse)
                .build();
    }

    private static ModuleTreeDTO treeOf(ModuleInfo root, List<ModuleFieldDTO> fields) {
        Map<Long, ModuleInfo> moduleMap = new LinkedHashMap<>();
        moduleMap.put(root.getModuleId(), root);

        Map<Long, ModuleFieldDTO> fieldMap = new LinkedHashMap<>();
        Map<Long, Long> fieldToModule = new LinkedHashMap<>();
        for (ModuleFieldDTO field : fields) {
            fieldMap.put(field.getId(), field);
            fieldToModule.put(field.getId(), root.getModuleId());
        }

        return ModuleTreeDTO.builder()
                .rootModuleId(root.getModuleId())
                .projectNo("school")
                .subjectId(1L)
                .rootModule(root)
                .moduleMap(moduleMap)
                .parentToChildren(new LinkedHashMap<>())
                .fieldMap(fieldMap)
                .fieldToModuleMap(fieldToModule)
                .allowedModuleIds(new LinkedHashSet<>(List.of(root.getModuleId())))
                .build();
    }

    private static ModuleInfo module(
            long moduleId, String name, String primaryTable, List<ModuleFieldDTO> fields) {
        return ModuleInfo.builder()
                .moduleId(moduleId)
                .moduleCode(primaryTable != null ? primaryTable : String.valueOf(moduleId))
                .moduleName(name)
                .primaryTable(primaryTable)
                .parentId(0L)
                .fields(fields)
                .build();
    }

    private static TableRelationDTO rel(
            String mainTable, String mainField, String joinTable, String joinField, String type) {
        return TableRelationDTO.builder()
                .mainTable(mainTable)
                .mainField(mainField)
                .joinTable(joinTable)
                .joinField(joinField)
                .relationType(type)
                .build();
    }

    private static ModuleFieldDTO f(long id, long moduleId, String table, String column) {
        return ModuleFieldDTO.builder()
                .id(id)
                .moduleId(moduleId)
                .tableName(table)
                .columnName(column)
                .sortOrder((int) (id % 1000))
                .build();
    }
}
