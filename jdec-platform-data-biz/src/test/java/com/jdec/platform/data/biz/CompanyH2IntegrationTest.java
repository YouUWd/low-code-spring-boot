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
import com.jdec.platform.data.api.dto.request.FilterItem;
import com.jdec.platform.data.api.dto.request.ForkVersionReq;
import com.jdec.platform.data.api.dto.request.OptionReq;
import com.jdec.platform.data.api.dto.request.PageQueryReq;
import com.jdec.platform.data.api.dto.request.TableSpec;
import com.jdec.platform.data.api.dto.response.DataOptionResp;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.api.dto.response.DataSaveResp;
import com.jdec.platform.data.biz.plan.PlanMetaLoader;
import com.jdec.platform.data.biz.plan.assembler.TreeResultAssembler;
import com.jdec.platform.data.biz.plan.compiler.QueryPlanCompiler;
import com.jdec.platform.data.biz.plan.compiler.SavePlanCompiler;
import com.jdec.platform.data.biz.plan.executor.QueryPlanExecutor;
import com.jdec.platform.data.biz.plan.executor.SavePlanExecutor;
import com.jdec.platform.data.biz.service.DataPersistenceService;
import com.jdec.platform.data.biz.service.DataQueryService;
import com.jdec.platform.data.biz.service.ForkVersionService;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.JooqDataContextFactory;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * company 项目 · data 引擎 H2 全链路集成测试套件（真实元数据 + 真实表结构 + 真实种子数据 + 真库执行）
 *
 * <p>本类将测试基础设施、装置自检与全链路测试合一维护，涵盖：
 *
 * <ul>
 *   <li><b>阶段 0</b>：装置与元数据自检（F1~F4：SQL 资源加载、9 张物理表建表、100/101/102 种子基线、元数据闭包）；
 *   <li><b>阶段 1</b>：读链路整树组装与 SQL 批抓取（H2/H2b：完整/稀疏/极简根装配、H3：零 JOIN 与防 N+1、H8：分页与投影）；
 *   <li><b>阶段 2</b>：多层级过滤与下拉候选路由（H5/H5b：嵌套 EXISTS 上卷与虚拟层穿透、H10~H12：跨层级/跨虚拟模块/跨兄弟分支下拉选项）；
 *   <li><b>阶段 3</b>：写链路、同构性与局部更新（H1：全量新建与外键级联、H4：详情读写同构、H13：局部 Patch 更新、H14：边界值往返）；
 *   <li><b>阶段 4</b>：1:N 差集与级联删除（H6：孤儿父行与孙表级联软删除、H6b：1:N 增删改混合保存）；
 *   <li><b>阶段 5</b>：单据版本派生与多租户隔离（H7：Fork 独立整树克隆、H7b：Fork 复杂 Patch 覆盖 1:1/1:N/1:N:N
 *       增删改与继承、H9：subject_id 租户隔离）。
 * </ul>
 */
@DisplayName("company 项目 · data 引擎 H2 全链路集成测试套件")
class CompanyH2IntegrationTest {

    // ==================================================================
    // 模块 ID（config_center · project_no='company' 真实值）
    // ==================================================================

    /** 1001 外部对象管理（根模块，主表 info） */
    public static final long M_INFO = 1001L;

    /** 1002 基本信息（1:1，basic_info） */
    public static final long M_BASIC = 1002L;

    /** 1003 人员构成-组织架构（1:N，member） */
    public static final long M_MEMBER = 1003L;

    /** 1004 人员构成-个人通讯录（1:N，member_contact，挂在 1003 下） */
    public static final long M_CONTACT = 1004L;

    /** 1005 联系方式-公司地址（1:1，address，挂在虚拟模块 1020 下） */
    public static final long M_ADDRESS = 1005L;

    /** 1006 联系方式-公司联系方式（1:N，company_contact，挂在虚拟模块 1020 下） */
    public static final long M_COMPANY_CONTACT = 1006L;

    /** 1007 财务信息（虚拟模块，primary_table=''） */
    public static final long M_FINANCE = 1007L;

    /** 1008 银行卡（1:N，financial_account） */
    public static final long M_FIN_ACCOUNT = 1008L;

    /** 1009 电子账户（1:N，digital_account） */
    public static final long M_DIGITAL_ACCOUNT = 1009L;

    /** 1010 信用等级（1:1，credit_rating） */
    public static final long M_CREDIT = 1010L;

    /** 1020 联系方式（虚拟模块，primary_table IS NULL） */
    public static final long M_VIRTUAL_CONTACT = 1020L;

    // ==================================================================
    // SQL 资源路径
    // ==================================================================

    static final String SCHEMA_RESOURCE = "/h2/company-schema.sql";
    static final String SEED_RESOURCE = "/h2/company-seed.sql";

    // ==================================================================
    // 测试装置与上下文环境
    // ==================================================================

    private JdbcDataSource rawDataSource;
    private RecordingDataSource recording;
    private TransactionTemplate txTemplate;
    private DSLContext dslContext;

    private DataQueryService dataQueryService;
    private DataPersistenceService dataPersistenceService;
    private ForkVersionService forkVersionService;

    @BeforeEach
    void setUp() throws Exception {
        AppContext.setContext("company", 0L);

        rawDataSource = new JdbcDataSource();
        rawDataSource.setURL(
                "jdbc:h2:mem:company_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
        rawDataSource.setUser("sa");
        rawDataSource.setPassword("");

        recording = new RecordingDataSource(rawDataSource);

        TransactionAwareDataSourceProxy txAware = new TransactionAwareDataSourceProxy(recording);
        txTemplate = new TransactionTemplate(new DataSourceTransactionManager(txAware));

        DefaultConfiguration jooqConfig = new DefaultConfiguration();
        jooqConfig.set(txAware);
        jooqConfig.set(SQLDialect.MYSQL);
        dslContext = DSL.using(jooqConfig);

        initSchema(rawDataSource);
        initSeed(rawDataSource);

        JooqDataContextFactory factory =
                new JooqDataContextFactory(null) {
                    @Override
                    public DSLContext getContext() {
                        return dslContext;
                    }

                    @Override
                    public DSLContext getContext(String projectNo) {
                        return dslContext;
                    }
                };

        ConfigApi configApi = configApi();
        PlanMetaLoader metaLoader = new PlanMetaLoader(configApi);

        dataQueryService =
                new DataQueryService(
                        new QueryPlanCompiler(),
                        new QueryPlanExecutor(factory, new TreeResultAssembler()),
                        metaLoader);
        dataPersistenceService =
                new DataPersistenceService(
                        new SavePlanCompiler(), new SavePlanExecutor(factory), metaLoader);
        forkVersionService =
                new ForkVersionService(
                        dataQueryService, dataPersistenceService, factory, metaLoader);

        recording.sqls.clear();
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = rawDataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
        }
        AppContext.remove();
    }

    private <T> T inTx(java.util.function.Supplier<T> action) {
        return txTemplate.execute(status -> action.get());
    }

    // ==================================================================
    // 阶段 0 · 基础设施与元数据自检 (F1 ~ F4)
    // ==================================================================

    @Nested
    @DisplayName("阶段 0 · 基础设施与元数据自检")
    class FixtureSelfCheck {

        @Test
        @DisplayName("F1 SQL 资源可加载、非空，且中文种子按 UTF-8 正确解码")
        void sqlResourcesAreLoadable() {
            String schema = load(SCHEMA_RESOURCE);
            String seed = load(SEED_RESOURCE);
            assertFalse(schema.isBlank(), "company-schema.sql 不应为空");
            assertFalse(seed.isBlank(), "company-seed.sql 不应为空");
            assertTrue(schema.contains("CREATE TABLE"), "schema.sql 应含建表语句");
            assertTrue(seed.contains("石嘴山市盛港煤焦化有限公司1010"), "种子中文必须以 UTF-8 正确解码");
        }

        @Test
        @DisplayName("F2 9 张物理表全部建出（info 聚合根范围）")
        void allPhysicalTablesCreated() throws SQLException {
            long tables =
                    countRows(
                            rawDataSource,
                            "select count(*) from information_schema.tables where lower(table_name) in"
                                    + " ('info','basic_info','member','member_contact','address','company_contact',"
                                    + "'financial_account','digital_account','credit_rating')");
            assertEquals(9L, tables, "应恰好建出 info 聚合根的 9 张物理表");
        }

        @Test
        @DisplayName("F3 种子行数与分布基线：id=100/101/102 异构形态正确，且每张表 2~5 行")
        void seedRowCountsMatchFixture() throws SQLException {
            assertEquals(
                    3L, countRows(rawDataSource, "select count(*) from info"), "种子共 3 个 info 根记录");

            // id=100：完整聚合根，9 张子表齐全（共 17 行）
            assertEquals(
                    1L,
                    countRows(
                            rawDataSource,
                            "select count(*) from basic_info where company_id = 100 and deleted = 0"));
            assertEquals(
                    3L,
                    countRows(
                            rawDataSource,
                            "select count(*) from member where company_id = 100 and deleted = 0"));
            assertEquals(
                    2L,
                    countRows(
                            rawDataSource,
                            "select count(*) from member_contact where member_id = 1001 and deleted = 0"));
            assertEquals(
                    2L,
                    countRows(
                            rawDataSource,
                            "select count(*) from member_contact where member_id = 2001 and deleted = 0"));
            assertEquals(
                    1L,
                    countRows(
                            rawDataSource,
                            "select count(*) from address where company_id = 100 and deleted = 0"));
            assertEquals(
                    2L,
                    countRows(
                            rawDataSource,
                            "select count(*) from company_contact where company_id = 100 and deleted = 0"));
            assertEquals(
                    2L,
                    countRows(
                            rawDataSource,
                            "select count(*) from financial_account where company_id = 100 and deleted = 0"));
            assertEquals(
                    3L,
                    countRows(
                            rawDataSource,
                            "select count(*) from digital_account where company_id = 100 and deleted = 0"));
            assertEquals(
                    1L,
                    countRows(
                            rawDataSource,
                            "select count(*) from credit_rating where company_id = 100 and deleted = 0"));

            long aggregateRows =
                    countRows(
                                    rawDataSource,
                                    "select count(*) from basic_info where company_id = 100 and deleted = 0")
                            + countRows(
                                    rawDataSource,
                                    "select count(*) from member where company_id = 100 and deleted = 0")
                            + countRows(
                                    rawDataSource,
                                    "select count(*) from member_contact mc join member m on m.id = mc.member_id"
                                            + " where m.company_id = 100 and mc.deleted = 0 and m.deleted = 0")
                            + countRows(
                                    rawDataSource,
                                    "select count(*) from address where company_id = 100 and deleted = 0")
                            + countRows(
                                    rawDataSource,
                                    "select count(*) from company_contact where company_id = 100 and deleted = 0")
                            + countRows(
                                    rawDataSource,
                                    "select count(*) from financial_account where company_id = 100 and deleted = 0")
                            + countRows(
                                    rawDataSource,
                                    "select count(*) from digital_account where company_id = 100 and deleted = 0")
                            + countRows(
                                    rawDataSource,
                                    "select count(*) from credit_rating where company_id = 100 and deleted = 0");
            assertEquals(17L, aggregateRows, "id=100 聚合根应共 17 行子表数据");

            // 每张物理表总行数落在 2~5 行之间
            for (String t :
                    List.of(
                            "info",
                            "basic_info",
                            "member",
                            "member_contact",
                            "address",
                            "company_contact",
                            "financial_account",
                            "digital_account",
                            "credit_rating")) {
                long n = countRows(rawDataSource, "select count(*) from " + t);
                assertTrue(n >= 2 && n <= 5, "表 " + t + " 行数应在 2~5 之间，实际=" + n);
            }

            // 101 = credit_rating + address；102 = 仅 basic_info（最小根）
            assertEquals(
                    1L,
                    countRows(
                            rawDataSource,
                            "select count(*) from credit_rating where company_id = 101 and deleted = 0"));
            assertEquals(
                    1L,
                    countRows(
                            rawDataSource,
                            "select count(*) from address where company_id = 101 and deleted = 0"));
            assertEquals(
                    0L,
                    countRows(
                            rawDataSource,
                            "select count(*) from basic_info where company_id = 101 and deleted = 0"));
            assertEquals(
                    1L,
                    countRows(
                            rawDataSource,
                            "select count(*) from basic_info where company_id = 102 and deleted = 0"));
            assertEquals(
                    0L,
                    countRows(
                            rawDataSource,
                            "select count(*) from credit_rating where company_id = 102 and deleted = 0"));

            // 依赖上限断言
            assertEquals(
                    2001L,
                    countRows(rawDataSource, "select max(id) from member where company_id = 100"),
                    "H7 依赖 member 最大 id = 2001");
        }

        @Test
        @DisplayName("F4 元数据快照：11 模块 / 86 字段 / 8 关系，且 1001 树恰好覆盖自身聚合根")
        void metadataSnapshotMatchesRealConfigCenter() {
            assertEquals(11, MODS.size(), "info 聚合根真实 11 个模块");
            assertEquals(86, FMAP.size(), "真实 86 个字段字典项");
            assertEquals(8, RELATIONS.size(), "真实 8 条 sys_table_relation");

            ModuleTreeDTO tree = treeFor(1001L);
            assertEquals(11, tree.getModuleMap().size(), "1001 子树应含 11 个模块");
            assertEquals(
                    Set.of(
                            1001L, 1002L, 1003L, 1004L, 1005L, 1006L, 1007L, 1008L, 1009L, 1010L,
                            1020L),
                    tree.getAllowedModuleIds());
        }
    }

    // ==================================================================
    // 阶段 1 · 读链路全形态装配与 SQL 执行形态 (H2, H2b, H3, H8)
    // ==================================================================

    @Test
    @DisplayName("H2 查询整树：聚合根 100 的 17 行全部装配，两层虚拟模块被穿透，孙表按父行分组")
    void queryWholeTreeAssemblesRealAggregate() {
        DataPage<DataBlock> page =
                dataQueryService.queryPage(
                        PageQueryReq.builder().moduleId(M_INFO).pageNo(1).pageSize(10).build());

        long dbInfoTotal = countOf("select count(*) from info");
        assertEquals(dbInfoTotal, page.getTotal(), "查询总数必须与数据库 info 表行数一致");
        assertEquals((int) dbInfoTotal, page.getRecords().size());

        DataRow r100 = rowByTableId(page.getData(), "info", 100L);
        assertNotNull(r100, "应返回 info.id=100 的聚合根");
        // 字段值直接与数据库原始值比对
        assertEquals(
                String.valueOf(scalar("select company_name from info where id = 100")),
                r100.getString("info", "company_name"));
        assertEquals(
                (int) asLong(scalar("select version_no from info where id = 100")),
                r100.getInteger("info", "version_no"));
        assertEquals(
                String.valueOf(scalar("select business_no from info where id = 100")),
                r100.getString("info", "business_no"));

        // JSON 列在读出时被解析回 List
        assertInstanceOf(List.class, r100.getValue("info", "nature"), "nature 应被解析为 List");

        // 1:1 / 1:N 直接子模块 — 行数与数据库实际行数对齐
        long dbBasicCount =
                countOf("select count(*) from basic_info where company_id = 100 and deleted = 0");
        assertEquals((int) dbBasicCount, child(r100, M_BASIC).size());
        assertEquals(
                String.valueOf(scalar("select uscc from basic_info where company_id = 100")),
                child(r100, M_BASIC).getFirstRecord().getString("basic_info", "uscc"));
        long dbMemberCount =
                countOf("select count(*) from member where company_id = 100 and deleted = 0");
        assertEquals((int) dbMemberCount, child(r100, M_MEMBER).size(), "member 1:N 应返回全部行");
        long dbCreditCount =
                countOf(
                        "select count(*) from credit_rating where company_id = 100 and deleted = 0");
        assertEquals((int) dbCreditCount, child(r100, M_CREDIT).size());

        // 虚拟模块 1007（财务信息）
        DataRow financeContainer = child(r100, M_FINANCE).getFirstRecord();
        assertNull(financeContainer.getTable("info"), "虚拟容器不应携带任何物理表空间");
        long dbFinAcctCount =
                countOf(
                        "select count(*) from financial_account where company_id = 100 and deleted = 0");
        assertEquals((int) dbFinAcctCount, child(financeContainer, M_FIN_ACCOUNT).size());
        long dbDigAcctCount =
                countOf(
                        "select count(*) from digital_account where company_id = 100 and deleted = 0");
        assertEquals((int) dbDigAcctCount, child(financeContainer, M_DIGITAL_ACCOUNT).size());

        // 虚拟模块 1020（联系方式）
        DataRow contactContainer = child(r100, M_VIRTUAL_CONTACT).getFirstRecord();
        long dbAddrCount =
                countOf("select count(*) from address where company_id = 100 and deleted = 0");
        assertEquals((int) dbAddrCount, child(contactContainer, M_ADDRESS).size());
        long dbContactCount =
                countOf(
                        "select count(*) from company_contact where company_id = 100 and deleted = 0");
        assertEquals((int) dbContactCount, child(contactContainer, M_COMPANY_CONTACT).size());

        // 孙表按父行分组 — member 字段值与名字均来自数据库
        DataBlock memberRows = child(r100, M_MEMBER);
        DataRow member1001 = rowByTableId(memberRows, "member", 1001L);
        assertNotNull(member1001);
        assertEquals(
                String.valueOf(scalar("select name from member where id = 1001")),
                member1001.getString("member", "name"));
        long db1001ContactCount =
                countOf(
                        "select count(*) from member_contact where member_id = 1001 and deleted = 0");
        assertEquals((int) db1001ContactCount, child(member1001, M_CONTACT).size());

        DataRow member2001 = rowByTableId(memberRows, "member", 2001L);
        assertNotNull(member2001);
        long db2001ContactCount =
                countOf(
                        "select count(*) from member_contact where member_id = 2001 and deleted = 0");
        assertEquals(
                (int) db2001ContactCount,
                child(member2001, M_CONTACT).size(),
                "员工 2001 的联系方式不应挂到别的 member 上");

        DataRow member1002 = rowByTableId(memberRows, "member", 1002L);
        assertNotNull(member1002);
        DataBlock noContact = member1002.getChild(M_CONTACT);
        assertTrue(
                noContact == null || noContact.size() == 0,
                "无联系方式的 member 不应挂 member_contact 子区块，实际=" + noContact);

        // 内部技术字段绝不外泄
        assertNoTechnicalKeys(r100, "root");

        // 稀疏根与极简根 — 行数均来自数据库实际行数
        DataRow r102 = rowByTableId(page.getData(), "info", 102L);
        long db102BasicCount =
                countOf("select count(*) from basic_info where company_id = 102 and deleted = 0");
        assertEquals((int) db102BasicCount, child(r102, M_BASIC).size());
        long db102MemberCount =
                countOf("select count(*) from member where company_id = 102 and deleted = 0");
        assertEquals((int) db102MemberCount, child(r102, M_MEMBER).size(), "102 没有 member");

        DataRow r101 = rowByTableId(page.getData(), "info", 101L);
        long db101CreditCount =
                countOf(
                        "select count(*) from credit_rating where company_id = 101 and deleted = 0");
        assertEquals((int) db101CreditCount, child(r101, M_CREDIT).size());
        long db101BasicCount =
                countOf("select count(*) from basic_info where company_id = 101 and deleted = 0");
        assertEquals((int) db101BasicCount, child(r101, M_BASIC).size());

        DataRow r101Contact = child(r101, M_VIRTUAL_CONTACT).getFirstRecord();
        long db101AddrCount =
                countOf("select count(*) from address where company_id = 101 and deleted = 0");
        assertEquals((int) db101AddrCount, child(r101Contact, M_ADDRESS).size());
    }

    @Test
    @DisplayName("H2b 稀疏根与极简根装配边界：缺失子表安全返回空集合或不挂载，不发生 NPE")
    void querySparseAndMinimalAggregateRootsSafely() {
        DataBlock detail101 =
                dataQueryService.getDetail(
                        DetailQueryReq.builder().moduleId(M_INFO).id(101L).build());
        DataRow root101 = detail101.getFirstRecord();
        assertEquals(101L, asLong(root101.getValue("info", "id")));
        long db101BasicCnt =
                countOf("select count(*) from basic_info where company_id = 101 and deleted = 0");
        assertEquals(
                (int) db101BasicCnt,
                child(root101, M_BASIC).size(),
                "稀疏根 101 basic_info 行数必须与 DB 一致");
        long db101MemberCnt =
                countOf("select count(*) from member where company_id = 101 and deleted = 0");
        assertEquals((int) db101MemberCnt, child(root101, M_MEMBER).size(), "稀疏根 101 没有 member");
        long db101CreditCnt =
                countOf(
                        "select count(*) from credit_rating where company_id = 101 and deleted = 0");
        assertEquals(
                (int) db101CreditCnt, child(root101, M_CREDIT).size(), "稀疏根 101 有 credit_rating");

        DataBlock detail102 =
                dataQueryService.getDetail(
                        DetailQueryReq.builder().moduleId(M_INFO).id(102L).build());
        DataRow root102 = detail102.getFirstRecord();
        assertEquals(102L, asLong(root102.getValue("info", "id")));
        long db102BasicCnt =
                countOf("select count(*) from basic_info where company_id = 102 and deleted = 0");
        assertEquals(
                (int) db102BasicCnt,
                child(root102, M_BASIC).size(),
                "极简根 102 basic_info 行数必须与 DB 一致");
        long db102CreditCnt =
                countOf(
                        "select count(*) from credit_rating where company_id = 102 and deleted = 0");
        assertEquals(
                (int) db102CreditCnt, child(root102, M_CREDIT).size(), "极简根 102 没有 credit_rating");
        DataRow contact102 = child(root102, M_VIRTUAL_CONTACT).getFirstRecord();
        long db102AddrCnt =
                countOf("select count(*) from address where company_id = 102 and deleted = 0");
        assertEquals((int) db102AddrCnt, child(contact102, M_ADDRESS).size(), "极简根 102 没有 address");
    }

    @Test
    @DisplayName("H3 SQL 形态：每张表恰好一条 SELECT、零 JOIN、批量 IN 抓取，且只触达 9 张表")
    void sqlShapeIsBatchFetchWithoutJoins() {
        dataQueryService.queryPage(
                PageQueryReq.builder().moduleId(M_INFO).pageNo(1).pageSize(10).build());

        List<String> selects =
                recording.sqls.stream()
                        .map(s -> s.replace('\n', ' ').replaceAll("\\s+", " ").toLowerCase())
                        .filter(s -> s.startsWith("select"))
                        .toList();
        assertFalse(selects.isEmpty(), "应捕获到查询 SQL");

        for (String sql : selects) {
            assertFalse(sql.contains(" join "), "company 不应产生任何 JOIN，实际=" + sql);
        }
        assertTrue(
                selects.stream().anyMatch(s -> s.contains("select * from `info`")),
                "无附加表时全字段模式应走 SELECT *，实际=" + selects);

        Map<String, Long> byTable = new LinkedHashMap<>();
        for (String sql : selects) {
            String t = tableOf(sql);
            if (t != null) {
                byTable.merge(t, 1L, Long::sum);
            }
        }
        assertEquals(2L, byTable.get("info"), "info 应有 1 条 COUNT + 1 条分页查询，实际=" + byTable);
        for (String t :
                List.of(
                        "basic_info",
                        "member",
                        "member_contact",
                        "address",
                        "company_contact",
                        "financial_account",
                        "digital_account",
                        "credit_rating")) {
            assertEquals(1L, byTable.get(t), "物理表 " + t + " 应恰好查询一次，实际=" + byTable);
        }

        String memberSql =
                selects.stream().filter(s -> s.contains("from `member`")).findFirst().orElseThrow();
        assertTrue(memberSql.contains("`member`.`company_id` in ("), "应走批量 IN 抓取，实际=" + memberSql);
        assertTrue(memberSql.contains("`deleted`"), "子表查询必须带 deleted 谓词，实际=" + memberSql);
        assertTrue(
                selects.stream()
                        .anyMatch(s -> s.contains("from `member_contact`") && s.contains(" in (")),
                "孙表 member_contact 应以其父行主键批量抓取");

        assertEquals(
                Set.of(
                        "info",
                        "basic_info",
                        "member",
                        "member_contact",
                        "address",
                        "company_contact",
                        "financial_account",
                        "digital_account",
                        "credit_rating"),
                byTable.keySet(),
                "1001 树只应触达 info 聚合根的 9 张表，实际=" + byTable);
    }

    @Test
    @DisplayName("H8 分页与投影：pageSize 生效、显式投影走列名而非 SELECT *")
    void paginationAndExplicitProjection() {
        DataPage<DataBlock> page1 =
                dataQueryService.queryPage(
                        PageQueryReq.builder().moduleId(M_INFO).pageNo(1).pageSize(2).build());
        assertEquals(3L, page1.getTotal());
        assertEquals(2, page1.getRecords().size(), "pageSize=2 应只返回 2 行");

        DataPage<DataBlock> page2 =
                dataQueryService.queryPage(
                        PageQueryReq.builder().moduleId(M_INFO).pageNo(2).pageSize(2).build());
        assertEquals(1, page2.getRecords().size());
        assertTrue(
                asLong(page1.getRecords().get(0).getValue("info", "id"))
                        > asLong(page2.getRecords().get(0).getValue("info", "id")));

        recording.sqls.clear();
        DataPage<DataBlock> projected =
                dataQueryService.queryPage(
                        PageQueryReq.builder()
                                .moduleId(M_INFO)
                                .pageNo(1)
                                .pageSize(10)
                                .tables(
                                        List.of(
                                                TableSpec.of(
                                                        M_INFO, "info", List.of("company_name"))))
                                .build());
        assertNotNull(projected);
        String sql =
                recording.sqls.stream()
                        .map(s -> s.replace('\n', ' ').replaceAll("\\s+", " ").toLowerCase())
                        .filter(s -> s.contains("from `info`") && !s.contains("count("))
                        .findFirst()
                        .orElseThrow();
        assertTrue(sql.contains("`company_name`"), "显式投影应包含列名，实际=" + sql);
        assertFalse(sql.contains("select *"), "显式投影不应使用 SELECT *，实际=" + sql);

        DataRow row = projected.getRecords().get(0);
        assertTrue(row.getChildren() == null || row.getChildren().isEmpty(), "只投影 info 列时不应展开子树");
    }

    // ==================================================================
    // 阶段 2 · 复杂过滤与候选值查询 (H5, H5b, H10 ~ H12)
    // ==================================================================

    @Test
    @DisplayName("H5 子孙过滤上卷：对孙表 member_contact 过滤，上卷为嵌套 EXISTS 并精准裁剪父级与子级")
    void descendantFilterRollsUpAsNestedExists() {
        PageQueryReq req =
                PageQueryReq.builder()
                        .moduleId(M_INFO)
                        .pageNo(1)
                        .pageSize(10)
                        .filters(
                                List.of(
                                        FilterItem.builder()
                                                .moduleId(M_CONTACT)
                                                .tableName("member_contact")
                                                .columnName("contact_value")
                                                .operator("EQ")
                                                .value("13585855898")
                                                .build()))
                        .build();

        DataPage<DataBlock> page = dataQueryService.queryPage(req);
        assertEquals(1L, page.getTotal());
        DataRow root = page.getRecords().get(0);
        assertEquals(100L, asLong(root.getValue("info", "id")));

        String rootSql =
                recording.sqls.stream()
                        .map(s -> s.replace('\n', ' ').replaceAll("\\s+", " ").toLowerCase())
                        .filter(s -> s.contains("from `info`") && !s.contains("count("))
                        .findFirst()
                        .orElseThrow();
        assertEquals(2, countOfOccurrences(rootSql, "exists"), "应恰好两层 EXISTS，实际=" + rootSql);
        assertTrue(rootSql.contains("from `member`"), "第一层 EXISTS 应针对 member，实际=" + rootSql);
        assertTrue(
                rootSql.contains("from `member_contact`"),
                "第二层 EXISTS 应针对 member_contact，实际=" + rootSql);
        assertTrue(
                rootSql.contains("`member_contact`.`member_id` = `member`.`id`"),
                "孙表 EXISTS 应通过 member_id 关联 member，实际=" + rootSql);

        DataBlock members = child(root, M_MEMBER);
        assertEquals(1, members.size(), "只应返回命中的 member");
        DataRow member = members.getFirstRecord();
        assertEquals(1001L, asLong(member.getValue("member", "id")));
        DataBlock contacts = child(member, M_CONTACT);
        assertEquals(1, contacts.size());
        assertEquals(
                "13585855898",
                contacts.getFirstRecord().getString("member_contact", "contact_value"));
    }

    @Test
    @DisplayName("H5b 虚拟模块下子表过滤：对 1009 过滤，EXISTS 穿透虚拟层直接挂到根表")
    void filterOnVirtualModuleChildRollsUpThroughVirtualLayer() {
        PageQueryReq req =
                PageQueryReq.builder()
                        .moduleId(M_INFO)
                        .pageNo(1)
                        .pageSize(10)
                        .filters(
                                List.of(
                                        FilterItem.builder()
                                                .moduleId(M_DIGITAL_ACCOUNT)
                                                .tableName("digital_account")
                                                .columnName("account_no")
                                                .operator("EQ")
                                                .value("ZFB001")
                                                .build()))
                        .build();

        DataPage<DataBlock> page = dataQueryService.queryPage(req);
        assertEquals(1L, page.getTotal(), "只有 100 拥有 ZFB001");

        String rootSql =
                recording.sqls.stream()
                        .map(s -> s.replace('\n', ' ').replaceAll("\\s+", " ").toLowerCase())
                        .filter(s -> s.contains("from `info`") && !s.contains("count("))
                        .findFirst()
                        .orElseThrow();
        assertEquals(1, countOfOccurrences(rootSql, "exists"), "虚拟模块 1007 必须被穿透，实际=" + rootSql);
        assertTrue(rootSql.contains("from `digital_account`"), "实际=" + rootSql);
        assertTrue(
                rootSql.contains("`digital_account`.`company_id` = `info`.`id`"),
                "虚拟层穿透后应直接关联 info.id = digital_account.company_id，实际=" + rootSql);
    }

    @Test
    @DisplayName("H10 跨层级下拉选项：根模块下查孙表 member_contact.contact_type，自动沿层级 INNER JOIN 回根表且应用祖先条件")
    void optionsAcrossModuleLevelsGeneratesInnerJoins() {
        recording.sqls.clear();
        OptionReq req =
                OptionReq.builder()
                        .moduleId(M_INFO)
                        .tableName("member_contact")
                        .columnName("contact_value")
                        .filters(
                                List.of(
                                        FilterItem.builder()
                                                .moduleId(M_INFO)
                                                .tableName("info")
                                                .columnName("id")
                                                .operator("EQ")
                                                .value(100L)
                                                .build()))
                        .build();

        List<DataOptionResp> options = dataQueryService.getOptions(req);
        assertNotNull(options);
        assertFalse(options.isEmpty(), "种子数据 100 下应存在 member_contact.contact_value");

        String sql =
                recording.sqls.stream()
                        .map(s -> s.replace('\n', ' ').replaceAll("\\s+", " ").toLowerCase())
                        .filter(s -> s.contains("select distinct"))
                        .findFirst()
                        .orElseThrow();

        assertTrue(sql.contains("from `member_contact`"), "FROM 表必须是目标表 member_contact，实际=" + sql);
        assertTrue(sql.contains("join `member`"), "必须连接父表 member，实际=" + sql);
        assertTrue(sql.contains("join `info`"), "必须连接根表 info，实际=" + sql);
        assertTrue(sql.contains("`info`.`id` = ?"), "必须应用根表上的过滤条件，实际=" + sql);
    }

    @Test
    @DisplayName("H11 跨虚拟模块下拉选项：根模块下查跨虚拟模块孙表 company_contact.contact_type，正确 JOIN 根表且不产生歧义列名")
    void optionsAcrossVirtualModuleGeneratesCorrectJoin() {
        recording.sqls.clear();
        OptionReq req =
                OptionReq.builder()
                        .moduleId(M_INFO)
                        .tableName("company_contact")
                        .columnName("contact_type")
                        .filters(
                                List.of(
                                        FilterItem.builder()
                                                .moduleId(M_INFO)
                                                .tableName("info")
                                                .columnName("id")
                                                .operator("IN")
                                                .value(List.of(100L))
                                                .build()))
                        .build();

        List<DataOptionResp> options = dataQueryService.getOptions(req);
        assertNotNull(options);

        String sql =
                recording.sqls.stream()
                        .map(s -> s.replace('\n', ' ').replaceAll("\\s+", " ").toLowerCase())
                        .filter(s -> s.contains("select distinct"))
                        .findFirst()
                        .orElseThrow();

        assertTrue(
                sql.contains("from `company_contact`"), "FROM 表必须是目标表 company_contact，实际=" + sql);
        assertTrue(
                sql.contains("join `info` on (`company_contact`.`company_id` = `info`.`id`"),
                "必须使用物理父表与带表前缀的外键，实际=" + sql);
        assertFalse(
                sql.contains("join `info` on (`id` = `info`.`id`"),
                "ON 子句不能出现无表前缀的歧义列名 id，实际=" + sql);
    }

    @Test
    @DisplayName(
            "H12 跨分支兄弟模块过滤：查 company_contact.contact_type 时过滤 address.comm_country，正确保留 address 模块的 EXISTS 谓词")
    void optionsWithSiblingModuleFilterRetainsExistsPredicate() {
        recording.sqls.clear();
        OptionReq req =
                OptionReq.builder()
                        .moduleId(M_INFO)
                        .tableName("company_contact")
                        .columnName("contact_type")
                        .filters(
                                List.of(
                                        FilterItem.builder()
                                                .moduleId(1005L)
                                                .tableName("address")
                                                .columnName("comm_country")
                                                .operator("IN")
                                                .value(List.of("中国"))
                                                .build()))
                        .build();

        List<DataOptionResp> options = dataQueryService.getOptions(req);
        assertNotNull(options);
        assertFalse(options.isEmpty());

        String sql =
                recording.sqls.stream()
                        .map(s -> s.replace('\n', ' ').replaceAll("\\s+", " ").toLowerCase())
                        .filter(s -> s.contains("select distinct"))
                        .findFirst()
                        .orElseThrow();

        assertTrue(
                sql.contains("from `company_contact`"), "FROM 表必须是目标表 company_contact，实际=" + sql);
        assertTrue(
                sql.contains("exists (select 1 as `one` from `address`"),
                "必须包含针对兄弟模块 address 的 EXISTS 过滤条件，实际=" + sql);
        assertTrue(
                sql.contains("`address`.`comm_country` in (?)"),
                "EXISTS 必须包含 address.comm_country 过滤条件，实际=" + sql);
    }

    // ==================================================================
    // 阶段 3 · 写链路、同构性与局部更新 (H1, H4, H13, H14)
    // ==================================================================

    @Test
    @DisplayName("H1 保存整树：9 张物理子表全部落库，父子外键自动回填，JSON/DECIMAL/DATE 原样往返")
    void saveWholeTreePropagatesForeignKeys() {
        AppContext.setContext("company", 7L);

        DataSaveResp resp = inTx(() -> dataPersistenceService.save(buildNewCompanyTree()));
        Long rootId = resp.getRootId();
        assertNotNull(rootId, "保存必须返回根记录 ID");
        assertTrue(rootId > 0);

        assertEquals(1, countOf("select count(*) from info where id = " + rootId));
        assertEquals("未来科技有限公司", scalar("select company_name from info where id = " + rootId));
        assertEquals(7L, asLong(scalar("select subject_id from info where id = " + rootId)));

        assertEquals(1, countOf("select count(*) from basic_info where company_id = " + rootId));
        assertEquals(2, countOf("select count(*) from member where company_id = " + rootId));
        assertEquals(
                1, countOf("select count(*) from financial_account where company_id = " + rootId));
        assertEquals(
                1, countOf("select count(*) from digital_account where company_id = " + rootId));
        assertEquals(1, countOf("select count(*) from address where company_id = " + rootId));
        assertEquals(
                2, countOf("select count(*) from company_contact where company_id = " + rootId));
        assertEquals(1, countOf("select count(*) from credit_rating where company_id = " + rootId));

        Long employeeId =
                asLong(
                        scalar(
                                "select id from member where company_id = "
                                        + rootId
                                        + " and type = 'EMPLOYEE'"));
        assertNotNull(employeeId);
        assertEquals(
                2, countOf("select count(*) from member_contact where member_id = " + employeeId));

        assertEquals(7L, asLong(scalar("select subject_id from member where id = " + employeeId)));
        assertEquals(
                7L,
                asLong(
                        scalar(
                                "select subject_id from financial_account where company_id = "
                                        + rootId)));

        String nature = String.valueOf(scalar("select nature from info where id = " + rootId));
        assertTrue(nature.startsWith("["), "nature 应落库为 JSON 数组文本，实际=" + nature);
        assertTrue(nature.contains("企业"), "nature 应保留词云描述，实际=" + nature);
        assertEquals(
                "[\"一般纳税人\"]",
                String.valueOf(
                        scalar(
                                "select taxpayer_qualification from basic_info where company_id = "
                                        + rootId)));

        assertEquals(
                0,
                new BigDecimal("5000.00")
                        .compareTo(
                                (BigDecimal)
                                        scalar(
                                                "select reg_capital from basic_info where company_id = "
                                                        + rootId)));
        assertTrue(
                String.valueOf(
                                scalar(
                                        "select reg_date from basic_info where company_id = "
                                                + rootId))
                        .startsWith("2014-04-28"));

        DataRow savedRoot = resp.getData().getFirstRecord();
        assertEquals(rootId, asLong(savedRoot.getValue("info", "id")));
        assertNotNull(
                child(savedRoot, M_FINANCE)
                        .getFirstRecord()
                        .getChild(M_FIN_ACCOUNT)
                        .getFirstRecord()
                        .getValue("financial_account", "id"),
                "虚拟模块下的物理子表也必须回填主键");
    }

    @Test
    @DisplayName("H4 详情读写同构：getDetail 的整树可直接回写，行数与父子外键均不变")
    void detailIsReadWriteIsomorphic() {
        DataBlock detail =
                dataQueryService.getDetail(
                        DetailQueryReq.builder().moduleId(M_INFO).id(100L).build());
        assertEquals(1, detail.size());
        assertEquals(100L, asLong(detail.getFirstRecord().getValue("info", "id")));

        long memberBefore = countOf("select count(*) from member where company_id = 100");
        long contactBefore = countOf("select count(*) from member_contact");
        String nameBefore = String.valueOf(scalar("select company_name from info where id = 100"));

        inTx(() -> dataPersistenceService.save(detail));

        assertEquals(memberBefore, countOf("select count(*) from member where company_id = 100"));
        assertEquals(contactBefore, countOf("select count(*) from member_contact"));
        assertEquals(
                nameBefore, String.valueOf(scalar("select company_name from info where id = 100")));
        // deleted=0 活跃行数应与保存前实际 DB 中的 deleted=0 行数一致
        long memberActiveBefore =
                countOf("select count(*) from member where company_id = 100 and deleted = 0");
        assertEquals(
                memberActiveBefore,
                countOf("select count(*) from member where company_id = 100 and deleted = 0"),
                "回写后存活 member 行数应不变");
        assertEquals(
                0,
                countOf(
                        "select count(*) from financial_account where company_id <> 100 and deleted = 0"));
    }

    @Test
    @DisplayName("H13 局部更新 Patch 回写：仅更新部分字段，其余子模块原样保留且外键保持稳定")
    void partialUpdateRetainsOtherModules() {
        DataBlock detail =
                dataQueryService.getDetail(
                        DetailQueryReq.builder().moduleId(M_INFO).id(100L).build());
        DataRow root = detail.getFirstRecord();

        // 保存前先快照各子表实际行数，用于后续验证“未变”
        long memberCountBefore =
                countOf("select count(*) from member where company_id = 100 and deleted = 0");
        long addrCountBefore =
                countOf("select count(*) from address where company_id = 100 and deleted = 0");
        long creditCountBefore =
                countOf(
                        "select count(*) from credit_rating where company_id = 100 and deleted = 0");
        long compContactCountBefore =
                countOf(
                        "select count(*) from company_contact where company_id = 100 and deleted = 0");
        long finAcctCountBefore =
                countOf(
                        "select count(*) from financial_account where company_id = 100 and deleted = 0");
        long digAcctCountBefore =
                countOf(
                        "select count(*) from digital_account where company_id = 100 and deleted = 0");

        // 仅修改根表名称和 basic_info 注册资本
        root.putValue("info", "company_name", "石嘴山市盛港煤焦化有限公司1010-Updated");
        DataRow basic = child(root, M_BASIC).getFirstRecord();
        basic.putValue("basic_info", "reg_capital", new BigDecimal("8888.00"));

        inTx(() -> dataPersistenceService.save(detail));

        // 验证修改生效
        assertEquals(
                "石嘴山市盛港煤焦化有限公司1010-Updated",
                scalar("select company_name from info where id = 100"));
        assertEquals(
                0,
                new BigDecimal("8888.00")
                        .compareTo(
                                (BigDecimal)
                                        scalar(
                                                "select reg_capital from basic_info where company_id = 100")));

        // 验证其余 8 张物理子表数据完整无损（均与保存前快照对齐）
        assertEquals(
                memberCountBefore,
                countOf("select count(*) from member where company_id = 100 and deleted = 0"));
        assertEquals(
                addrCountBefore,
                countOf("select count(*) from address where company_id = 100 and deleted = 0"));
        assertEquals(
                creditCountBefore,
                countOf(
                        "select count(*) from credit_rating where company_id = 100 and deleted = 0"));
        assertEquals(
                compContactCountBefore,
                countOf(
                        "select count(*) from company_contact where company_id = 100 and deleted = 0"));
        assertEquals(
                finAcctCountBefore,
                countOf(
                        "select count(*) from financial_account where company_id = 100 and deleted = 0"));
        assertEquals(
                digAcctCountBefore,
                countOf(
                        "select count(*) from digital_account where company_id = 100 and deleted = 0"));
    }

    @Test
    @DisplayName("H14 数据类型与边界值测试：空数组 []、空字符串与 NULL 值的保存与解析边界")
    void boundaryDataTypesAndNullValues() {
        DataRow root = new DataRow();
        root.putValue("info", "company_name", "边界值测试企业");
        root.putValue("info", "business_no", "CP-BOUND-001");
        root.putValue("info", "version_no", 1);
        root.putValue("info", "nature", List.of()); // 空数组
        root.putValue("info", "cooperation_types", null); // NULL

        DataRow basic = new DataRow();
        basic.putValue("basic_info", "uscc", "91330100MA000000X1");
        basic.putValue("basic_info", "reg_capital", new BigDecimal("0.00")); // 零值
        basic.putValue("basic_info", "taxpayer_qualification", List.of());

        root.setChildren(List.of(DataBlock.of(M_BASIC, basic)));

        DataSaveResp resp = inTx(() -> dataPersistenceService.save(DataBlock.of(M_INFO, root)));
        Long savedId = resp.getRootId();
        assertNotNull(savedId);

        // 验证落库值
        String nature = String.valueOf(scalar("select nature from info where id = " + savedId));
        assertEquals("[]", nature, "空 List 应落库为 []");
        assertNull(scalar("select cooperation_types from info where id = " + savedId));

        // 验证读出值装配
        DataBlock reloaded =
                dataQueryService.getDetail(
                        DetailQueryReq.builder().moduleId(M_INFO).id(savedId).build());
        DataRow reloadedRow = reloaded.getFirstRecord();
        Object reloadedNature = reloadedRow.getValue("info", "nature");
        assertInstanceOf(List.class, reloadedNature);
        assertTrue(((List<?>) reloadedNature).isEmpty(), "[] 应解析为空 List");
        assertNull(reloadedRow.getValue("info", "cooperation_types"));
    }

    // ==================================================================
    // 阶段 4 · 1:N 差集与级联删除 (H6, H6b)
    // ==================================================================

    @Test
    @DisplayName("H6 孤儿删除：提交的 member 数组少了 1001，该行及其 member_contact 孙表被级联软删除")
    void orphanRemovalSoftDeletesRowAndDescendants() {
        // 删除前先快照 member 1001 的实际行数与其子表行数
        long memberActiveBeforeDelete =
                countOf("select count(*) from member where company_id = 100 and deleted = 0");
        long contact1001Before =
                countOf(
                        "select count(*) from member_contact where member_id = 1001 and deleted = 0");

        DataBlock detail =
                dataQueryService.getDetail(
                        DetailQueryReq.builder().moduleId(M_INFO).id(100L).build());

        DataBlock members = child(detail.getFirstRecord(), M_MEMBER);
        List<DataRow> kept =
                members.getRecords().stream()
                        .filter(
                                r ->
                                        !Long.valueOf(1001L)
                                                .equals(asLong(r.getValue("member", "id"))))
                        .toList();
        assertEquals(memberActiveBeforeDelete - 1, kept.size(), "移除 1001 后保留行数应比保存前少 1 条");
        members.setRecords(new ArrayList<>(kept));

        inTx(() -> dataPersistenceService.save(detail));

        assertEquals(
                memberActiveBeforeDelete - 1,
                countOf("select count(*) from member where company_id = 100 and deleted = 0"),
                "存活 member 应比删除前少 1 条");
        assertEquals(
                1,
                countOf("select count(*) from member where id = 1001 and deleted = 1"),
                "被移除的 member 1001 应被软删除");
        assertEquals(
                contact1001Before,
                countOf(
                        "select count(*) from member_contact where member_id = 1001 and deleted = 1"),
                "member 1001 的 member_contact 全部应被级联软删除");
        assertEquals(
                0,
                countOf(
                        "select count(*) from member_contact where member_id = 1001 and deleted = 0"));
    }

    @Test
    @DisplayName("H6b 1:N 混合保存：在 1:N 列表中同时执行保留旧行、修改现有行与新增新行")
    void mixedSaveInOneToManyRelationship() {
        DataBlock detail =
                dataQueryService.getDetail(
                        DetailQueryReq.builder().moduleId(M_INFO).id(100L).build());
        DataBlock members = child(detail.getFirstRecord(), M_MEMBER);

        // 保存前快照 member 活跃行数
        long memberCountBefore =
                countOf("select count(*) from member where company_id = 100 and deleted = 0");

        // 修改现有行 1002 名称
        DataRow member1002 = rowByTableId(members, "member", 1002L);
        assertNotNull(member1002);
        member1002.putValue("member", "name", "财务部-重命名");

        // 新增一行 member（不传 id）
        DataRow newMember = new DataRow();
        newMember.putValue("member", "type", "DEPT");
        newMember.putValue("member", "name", "技术创新部");
        newMember.putValue("member", "parent_id", 0);
        newMember.putValue("member", "sort_order", 9);
        members.getRecords().add(newMember);

        inTx(() -> dataPersistenceService.save(detail));

        // 验证修改与新增均生效
        assertEquals(
                memberCountBefore + 1,
                countOf("select count(*) from member where company_id = 100 and deleted = 0"),
                "新增 1 行后，存活 member 行数应比保存前多 1");
        assertEquals("财务部-重命名", scalar("select name from member where id = 1002"));
        assertEquals(
                1,
                countOf("select count(*) from member where company_id = 100 and name = '技术创新部'"));
    }

    // ==================================================================
    // 阶段 5 · 单据版本派生与多租户隔离 (H7, H9)
    // ==================================================================

    @Test
    @DisplayName("H7 版本派生：fork 出全新整树（version_no 3→4），基准单据与子表全部不受影响")
    void forkClonesWholeTreeAndBumpsVersion() {
        DataRow patchInfo = new DataRow();
        patchInfo.putValue("info", "company_name", "石嘴山市盛港煤焦化有限公司1010（V4）");

        Long newId =
                inTx(
                        () ->
                                forkVersionService.forkNewVersion(
                                        ForkVersionReq.builder()
                                                .moduleId(M_INFO)
                                                .id(100L)
                                                .patchData(DataBlock.of(M_INFO, patchInfo))
                                                .build()));

        assertNotNull(newId);
        assertNotEquals(100L, newId, "派生必须生成全新主键");

        assertEquals(
                "石嘴山市盛港煤焦化有限公司1010（V4）",
                String.valueOf(scalar("select company_name from info where id = " + newId)));
        // 校验版本号基于基准单据 MAX(version_no) + 1
        int baseVer = (int) asLong(scalar("select version_no from info where id = 100"));
        assertEquals(
                baseVer + 1,
                (int) asLong(scalar("select version_no from info where id = " + newId)));
        assertEquals(
                scalar("select business_no from info where id = 100"),
                scalar("select business_no from info where id = " + newId),
                "派生单据沿用同一 business_no");

        // 校验各子表克隆行数严格与基准单据在数据库中的实际行数一致
        assertEquals(
                countOf("select count(*) from member where company_id = 100 and deleted = 0"),
                countOf("select count(*) from member where company_id = " + newId));
        assertEquals(
                countOf("select count(*) from basic_info where company_id = 100 and deleted = 0"),
                countOf("select count(*) from basic_info where company_id = " + newId));
        assertEquals(
                countOf("select count(*) from address where company_id = 100 and deleted = 0"),
                countOf("select count(*) from address where company_id = " + newId));
        assertEquals(
                countOf(
                        "select count(*) from credit_rating where company_id = 100 and deleted = 0"),
                countOf("select count(*) from credit_rating where company_id = " + newId));
        assertEquals(
                countOf(
                        "select count(*) from company_contact where company_id = 100 and deleted = 0"),
                countOf("select count(*) from company_contact where company_id = " + newId));
        assertEquals(
                countOf(
                        "select count(*) from financial_account where company_id = 100 and deleted = 0"),
                countOf("select count(*) from financial_account where company_id = " + newId));
        assertEquals(
                countOf(
                        "select count(*) from digital_account where company_id = 100 and deleted = 0"),
                countOf("select count(*) from digital_account where company_id = " + newId));

        Long maxBaseMemberId = asLong(scalar("select max(id) from member where company_id = 100"));
        assertEquals(
                0,
                countOf(
                        "select count(*) from member where company_id = "
                                + newId
                                + " and id <= "
                                + maxBaseMemberId),
                "克隆行必须是全新主键，不得复用基准行 ID");

        String sampleMemberCode =
                String.valueOf(
                        scalar(
                                "select code from member where company_id = 100 and parent_id = 0 limit 1"));
        Long baseSampleMemberId =
                asLong(
                        scalar(
                                "select id from member where company_id = 100 and code = '"
                                        + sampleMemberCode
                                        + "'"));
        Long newMemberId =
                asLong(
                        scalar(
                                "select id from member where company_id = "
                                        + newId
                                        + " and code = '"
                                        + sampleMemberCode
                                        + "'"));
        assertNotNull(newMemberId);
        assertEquals(
                countOf(
                        "select count(*) from member_contact where member_id = "
                                + baseSampleMemberId
                                + " and deleted = 0"),
                countOf("select count(*) from member_contact where member_id = " + newMemberId),
                "补丁未动 member_contact，原样继承数据库中的实际条数");

        // 基准单据完全不受影响（基于数据库现有实际值与状态，而非硬编码）
        assertEquals(
                baseVer,
                (int) asLong(scalar("select version_no from info where id = 100")),
                "基准单据版本号不变");
        assertEquals(
                countOf("select count(*) from member where company_id = 100 and deleted = 0"),
                countOf("select count(*) from member where company_id = 100"),
                "基准单据明细未被软删除");
        assertEquals(
                countOf(
                        "select count(*) from member_contact where member_id = "
                                + baseSampleMemberId
                                + " and deleted = 0"),
                countOf(
                        "select count(*) from member_contact where member_id = "
                                + baseSampleMemberId),
                "基准单据孙表明细未被软删除");
    }

    @Test
    @DisplayName("H7b 版本派生（复杂 Patch）：完整覆盖 1:1 伴生表、1:N 明细行与 1:N:N 孙表（增/删/改/继承），生成全新整树且基准单据不受影响")
    void forkWithComplexPatchCoveringAllRelationshipLevels() {
        // ------------------------------------------------------------------
        // 1. 构造包含 1:1、1:N、1:N:N 的局部增量补丁 (Patch)
        // ------------------------------------------------------------------
        DataRow patchRoot = new DataRow();
        // (1) 根表：修改公司名称
        patchRoot.putValue("info", "company_name", "石嘴山市盛港煤焦化有限公司（Fork-复杂补丁V4）");

        // (2) 1:1 伴生表 basic_info (模块 1002)：修改已有行 id=1 的法人、增资，其余列继承基准
        DataRow patchBasic = new DataRow();
        patchBasic.putValue("basic_info", "id", 1L);
        patchBasic.putValue("basic_info", "reg_capital", new BigDecimal("9999.00"));
        patchBasic.putValue("basic_info", "legal_person", "王五");
        patchRoot.setChildren(new ArrayList<>());
        patchRoot.getChildren().add(DataBlock.of(M_BASIC, patchBasic));

        // (3) 1:N 明细表 member (模块 1003) & 1:N:N 孙表 member_contact (模块 1004)：
        //     - 原基准单据 member 有 3 行：1001(DEPT-经理办)、1002(DEPT-财务部)、2001(EMPLOYEE-李玉林)
        //     - 业务 Patch 变更定义：
        //       a. 修改已有行 1001：名称重命名为 "总裁办"
        //          其下的孙表 member_contact (原 2 条：id=1 手机, id=2 座机)：
        //          * 改：保留并修改 id=1 的手机号为 "13999999999"
        //          * 增：新增一条联系方式 (无 id, "010-66668888")
        //          * 删：不提交原 id=2 的座机行（通过快照比对自然剔除）
        //       b. 纯新增 member 行 (不带 id)：新员工 "赵六"，下挂 1 条新孙表联系方式 ("13888888888")
        //       c. 剔除原行 1002、2001：Patch 中不传该两行，验证被删除的行及其孙表不会进入新版本
        DataRow modifiedDept = new DataRow();
        modifiedDept.putValue("member", "id", 1001L);
        modifiedDept.putValue("member", "name", "总裁办");

        DataRow modContact1 = new DataRow();
        modContact1.putValue("member_contact", "id", 1L);
        modContact1.putValue("member_contact", "contact_value", "13999999999");

        DataRow newContactForDept = new DataRow();
        newContactForDept.putValue("member_contact", "contact_value", "010-66668888");
        newContactForDept.putValue("member_contact", "is_primary", 0);

        modifiedDept.setChildren(
                List.of(DataBlock.of(M_CONTACT, List.of(modContact1, newContactForDept))));

        DataRow newEmployee = new DataRow();
        newEmployee.putValue("member", "type", "EMPLOYEE");
        newEmployee.putValue("member", "name", "赵六");
        newEmployee.putValue("member", "parent_id", 0);
        newEmployee.putValue("member", "position", "总监");
        newEmployee.putValue("member", "sort_order", 2);

        DataRow newContactForEmp = new DataRow();
        newContactForEmp.putValue("member_contact", "contact_value", "13888888888");
        newContactForEmp.putValue("member_contact", "is_primary", 1);
        newEmployee.setChildren(List.of(DataBlock.of(M_CONTACT, List.of(newContactForEmp))));

        patchRoot.getChildren().add(DataBlock.of(M_MEMBER, List.of(modifiedDept, newEmployee)));

        // ------------------------------------------------------------------
        // 2. Fork 前先快照基准单据关键数据，用于尾部冻结验证（避免封常硬编码）
        // ------------------------------------------------------------------
        String snapshotCompanyName =
                String.valueOf(scalar("select company_name from info where id = 100"));
        int snapshotVersionNo = (int) asLong(scalar("select version_no from info where id = 100"));
        String snapshotLegalPerson =
                String.valueOf(
                        scalar("select legal_person from basic_info where company_id = 100"));
        long snapshotMemberCount = countOf("select count(*) from member where company_id = 100");
        long snapshotContact1001 =
                countOf(
                        "select count(*) from member_contact where member_id = 1001 and deleted = 0");
        long snapshotContact2001 =
                countOf(
                        "select count(*) from member_contact where member_id = 2001 and deleted = 0");

        // ------------------------------------------------------------------
        // 3. 执行版本派生 Fork
        // ------------------------------------------------------------------
        Long newId =
                inTx(
                        () ->
                                forkVersionService.forkNewVersion(
                                        ForkVersionReq.builder()
                                                .moduleId(M_INFO)
                                                .id(100L)
                                                .patchData(DataBlock.of(M_INFO, patchRoot))
                                                .build()));

        assertNotNull(newId);
        assertNotEquals(100L, newId, "派生单据必须生成新主键");

        // ------------------------------------------------------------------
        // 3. 完整整树比对：调用 getDetailByTable 读取 base(100) 与 derived(newId)
        //    进行逐模块、逐行、逐字段的深度一致性与差异性比对
        // ------------------------------------------------------------------
        DataBlock baseDetail =
                dataQueryService.getDetail(
                        DetailQueryReq.builder().moduleId(M_INFO).id(100L).build());
        DataBlock derivedDetail =
                dataQueryService.getDetail(
                        DetailQueryReq.builder().moduleId(M_INFO).id(newId).build());

        DataRow baseRoot = baseDetail.getFirstRecord();
        DataRow derivedRoot = derivedDetail.getFirstRecord();

        // (A) 根表 info 比对：
        //     - 业务编号一致
        //     - 版本号自动递增 3 -> 4
        //     - 主键互不相同且生成新主键
        //     - 修改字段生效，未修改字段（nature, cooperation_types 等）完整继承
        assertEquals(
                baseRoot.getString("info", "business_no"),
                derivedRoot.getString("info", "business_no"),
                "派生版本 business_no 必须与基准版本完全一致");
        // version_no: 基准单据不应封常为 3，应与数据库实际快照一致
        int baseVersionInDb = (int) asLong(scalar("select version_no from info where id = 100"));
        assertEquals(
                baseVersionInDb,
                baseRoot.getInteger("info", "version_no"),
                "基准单据的 version_no 必须与数据库实际快照字段完全对齐");
        assertEquals(
                baseVersionInDb + 1, derivedRoot.getInteger("info", "version_no"), "派生版本版本号必须递增");
        assertNotEquals(
                baseRoot.getValue("info", "id"),
                derivedRoot.getValue("info", "id"),
                "派生版本必须生成全新根主键 ID");
        assertEquals(
                "石嘴山市盛港煤焦化有限公司（Fork-复杂补丁V4）",
                derivedRoot.getString("info", "company_name"),
                "补丁中修改的公司名称必须生效");
        assertEquals(
                baseRoot.getValue("info", "nature"),
                derivedRoot.getValue("info", "nature"),
                "未修改的 JSON 数组列（nature）必须 100% 完整继承");
        assertEquals(
                baseRoot.getValue("info", "reg_status"),
                derivedRoot.getValue("info", "reg_status"),
                "未修改的 reg_status 必须完整继承");
        assertEquals(
                baseRoot.getValue("info", "industry_tags"),
                derivedRoot.getValue("info", "industry_tags"),
                "未修改的 industry_tags 必须完整继承");
        assertEquals(
                baseRoot.getValue("info", "industry_types"),
                derivedRoot.getValue("info", "industry_types"),
                "未修改的 industry_types 必须完整继承");
        assertEquals(
                baseRoot.getValue("info", "logo_url"),
                derivedRoot.getValue("info", "logo_url"),
                "未修改的 logo_url 必须完整继承");

        // (B) 1:1 伴生表 basic_info 比对：
        //     - 行数一致（均为 1 行）
        //     - 外键 company_id 自动指向对应版本的根主键 ID
        //     - 修改字段（reg_capital, legal_person）生效
        //     - 其余所有列（uscc, short_name, en_short_name, reg_date, website 等）完整继承
        DataRow baseBasic = child(baseRoot, M_BASIC).getFirstRecord();
        DataRow derivedBasic = child(derivedRoot, M_BASIC).getFirstRecord();
        assertNotEquals(
                baseBasic.getValue("basic_info", "id"),
                derivedBasic.getValue("basic_info", "id"),
                "1:1 伴生表必须生成全新主键，不可沿用旧 id");
        assertEquals(100L, asLong(baseBasic.getValue("basic_info", "company_id")));
        assertEquals(newId, asLong(derivedBasic.getValue("basic_info", "company_id")));
        // legal_person: Patch 指定 "王五"，验证派生已生效；基准应反映 DB 实际存储值（不硬编码）
        assertEquals("王五", derivedBasic.getString("basic_info", "legal_person"));
        String baseDbLegalPerson =
                String.valueOf(
                        scalar("select legal_person from basic_info where company_id = 100"));
        assertEquals(
                baseDbLegalPerson,
                baseBasic.getString("basic_info", "legal_person"),
                "基准 basic_info 的 legal_person 必须与数据库实际存储的字段一致");
        assertNotEquals(
                derivedBasic.getString("basic_info", "legal_person"),
                baseBasic.getString("basic_info", "legal_person"),
                "Patch 指定了修改 legal_person，派生应与基准不同");
        assertEquals(
                0,
                new BigDecimal("9999.00")
                        .compareTo(
                                new BigDecimal(
                                        String.valueOf(
                                                derivedBasic.getValue(
                                                        "basic_info", "reg_capital")))));
        // 逐字段对比继承项
        assertEquals(
                baseBasic.getString("basic_info", "uscc"),
                derivedBasic.getString("basic_info", "uscc"),
                "1:1 表未提及字段 uscc 必须完全一致");
        assertEquals(
                baseBasic.getString("basic_info", "short_name"),
                derivedBasic.getString("basic_info", "short_name"),
                "1:1 表未提及字段 short_name 必须完全一致");
        assertEquals(
                baseBasic.getString("basic_info", "en_short_name"),
                derivedBasic.getString("basic_info", "en_short_name"),
                "1:1 表未提及字段 en_short_name 必须完全一致");
        assertEquals(
                baseBasic.getString("basic_info", "website"),
                derivedBasic.getString("basic_info", "website"),
                "1:1 表未提及字段 website 必须完全一致");
        assertEquals(
                baseBasic.getValue("basic_info", "taxpayer_qualification"),
                derivedBasic.getValue("basic_info", "taxpayer_qualification"),
                "1:1 表未提及字段 taxpayer_qualification 必须完全一致");
        assertEquals(
                baseBasic.getValue("basic_info", "economic_nature"),
                derivedBasic.getValue("basic_info", "economic_nature"),
                "1:1 表未提及字段 economic_nature 必须完全一致");
        assertEquals(
                baseBasic.getValue("basic_info", "insured_count"),
                derivedBasic.getValue("basic_info", "insured_count"),
                "1:1 表未提及字段 insured_count 必须完全一致");

        // (C) 1:N 明细表 member 比对：
        //     - 原基准 3 行（1001, 1002, 2001），派生后 2 行（1001 修改行 + 赵六 新增行；1002/2001 剔除）
        DataBlock baseMembers = child(baseRoot, M_MEMBER);
        DataBlock derivedMembers = child(derivedRoot, M_MEMBER);
        long dbBaseMemberCount =
                countOf("select count(*) from member where company_id = 100 and deleted = 0");
        assertEquals((int) dbBaseMemberCount, baseMembers.size(), "基准 member 行数必须与 DB 实际一致");
        // Patch 年中仅提交了 2 行（1001 修改 + 赵六 新增），1002/2001 被剥除
        assertEquals(2, derivedMembers.size(), "派生 member 经增删改后应为 2 行");

        // 验证修改行：原部门 1001 改名为“总裁办”
        DataRow baseDept = rowByTableId(baseMembers, "member", 1001L);
        assertNotNull(baseDept);
        DataRow derivedDept = null;
        for (DataRow r : derivedMembers.getRecords()) {
            if ("DEPT".equals(r.getString("member", "type"))) {
                derivedDept = r;
                break;
            }
        }
        assertNotNull(derivedDept, "派生版本中必须存在修改后的 DEPT 记录");
        assertNotEquals(
                baseDept.getValue("member", "id"),
                derivedDept.getValue("member", "id"),
                "修改行的 member 主键必须是全新生成，不得复用原 1001");
        assertEquals(newId, asLong(derivedDept.getValue("member", "company_id")));
        assertEquals("总裁办", derivedDept.getString("member", "name"), "修改的部门名称生效");
        // 基准单据的部门名称应与数据库实际存储一致（不硬编码）
        String baseDbDeptName = String.valueOf(scalar("select name from member where id = 1001"));
        assertEquals(baseDbDeptName, baseDept.getString("member", "name"), "基准单据部门名称未受污染");
        assertNotEquals(
                derivedDept.getString("member", "name"),
                baseDept.getString("member", "name"),
                "Patch 修改了部门名，派生应与基准不同");
        // 继承字段比对
        assertEquals(
                baseDept.getString("member", "code"),
                derivedDept.getString("member", "code"),
                "member 修改行未涉及的 code 必须 100% 继承");
        assertEquals(
                baseDept.getValue("member", "sort_order"),
                derivedDept.getValue("member", "sort_order"),
                "member 修改行未涉及的 sort_order 必须 100% 继承");
        assertEquals(
                baseDept.getValue("member", "parent_id"),
                derivedDept.getValue("member", "parent_id"),
                "member 修改行未涉及的 parent_id 必须 100% 继承");

        // 验证新增行：新增员工“赵六”
        DataRow derivedEmp = null;
        for (DataRow r : derivedMembers.getRecords()) {
            if ("EMPLOYEE".equals(r.getString("member", "type"))) {
                derivedEmp = r;
                break;
            }
        }
        assertNotNull(derivedEmp, "派生版本中必须存在新增的 EMPLOYEE 记录");
        assertEquals("赵六", derivedEmp.getString("member", "name"));
        assertEquals("总监", derivedEmp.getString("member", "position"));
        assertEquals(newId, asLong(derivedEmp.getValue("member", "company_id")));
        assertNotNull(derivedEmp.getValue("member", "id"), "新增行必须生成有效新主键");

        // 验证剔除行：原 1002(财务部)、2001(员工李玉林) 不在新版本中
        for (DataRow r : derivedMembers.getRecords()) {
            assertNotEquals("财务部", r.getString("member", "name"), "未提交的财务部已被自然剔除");
            assertNotEquals("李玉林", r.getString("member", "name"), "未提交的原员工已被自然剔除");
        }

        // (D) 1:N:N 孙表 member_contact 比对：
        //     - 新部门 derivedDept 下挂 2 条（1 改 + 1 增；原座机剔除）
        //     - 新员工 derivedEmp 下挂 1 条（新增手机号）
        DataBlock baseDeptContacts = child(baseDept, M_CONTACT);
        DataBlock derivedDeptContacts = child(derivedDept, M_CONTACT);
        // 基准部门联系方式行数必须与 DB 实际行数一致
        long dbBaseDeptContactCount =
                countOf(
                        "select count(*) from member_contact where member_id = 1001 and deleted = 0");
        assertEquals((int) dbBaseDeptContactCount, baseDeptContacts.size(), "基准部门联系方式行数必须与 DB 一致");
        assertEquals(2, derivedDeptContacts.size(), "派生部门下经改/增/删后仍为 2 条联系方式");

        Long newDeptPk = asLong(derivedDept.getValue("member", "id"));
        for (DataRow dc : derivedDeptContacts.getRecords()) {
            assertEquals(
                    newDeptPk,
                    asLong(dc.getValue("member_contact", "member_id")),
                    "孙表的外键必须正确级联回填为新部门的主键");
            assertNotNull(dc.getValue("member_contact", "id"), "孙表必须生成全新主键");
            assertNotEquals(1L, asLong(dc.getValue("member_contact", "id")));
            assertNotEquals(2L, asLong(dc.getValue("member_contact", "id")));
        }
        // 验证修改字段生效与新增字段落库
        List<String> derivedContactValues =
                derivedDeptContacts.getRecords().stream()
                        .map(r -> r.getString("member_contact", "contact_value"))
                        .toList();
        assertTrue(
                derivedContactValues.contains("13999999999"),
                "孙表已修改的联系方式 13999999999 必须存在，实际=" + derivedContactValues);
        assertTrue(
                derivedContactValues.contains("010-66668888"),
                "孙表新增的联系方式 010-66668888 必须存在，实际=" + derivedContactValues);
        assertFalse(
                derivedContactValues.contains("0710-3255895"),
                "未在 Patch 中提交的原座机 0710-3255895 必须被剔除");

        // 验证新增员工的孙表
        DataBlock derivedEmpContacts = child(derivedEmp, M_CONTACT);
        assertEquals(1, derivedEmpContacts.size(), "新增员工下应恰好有 1 条联系方式");
        DataRow empContact = derivedEmpContacts.getFirstRecord();
        assertEquals("13888888888", empContact.getString("member_contact", "contact_value"));
        assertEquals(
                asLong(derivedEmp.getValue("member", "id")),
                asLong(empContact.getValue("member_contact", "member_id")),
                "新增员工的孙表外键必须回填为新增员工主键");

        // (E) 100% 完整继承的其余子模块整树完整比对 (address, credit_rating, company_contact, finance):
        //     - 数据行数完全一致
        //     - 每个模块的所有业务属性值 1:1 镜像相等
        //     - 外键 company_id 统一重定向到 newId，主键全部生成新主键

        // 1. address (挂在 1020 下)
        DataRow baseAddr =
                child(child(baseRoot, M_VIRTUAL_CONTACT).getFirstRecord(), M_ADDRESS)
                        .getFirstRecord();
        DataRow derivedAddr =
                child(child(derivedRoot, M_VIRTUAL_CONTACT).getFirstRecord(), M_ADDRESS)
                        .getFirstRecord();
        assertNotEquals(baseAddr.getValue("address", "id"), derivedAddr.getValue("address", "id"));
        assertEquals(newId, asLong(derivedAddr.getValue("address", "company_id")));
        assertEquals(
                baseAddr.getString("address", "comm_region"),
                derivedAddr.getString("address", "comm_region"));
        assertEquals(
                baseAddr.getString("address", "comm_detail"),
                derivedAddr.getString("address", "comm_detail"));
        assertEquals(
                baseAddr.getString("address", "reg_detail"),
                derivedAddr.getString("address", "reg_detail"));

        // 2. company_contact (挂在 1020 下，2 条)
        DataBlock baseCc =
                child(child(baseRoot, M_VIRTUAL_CONTACT).getFirstRecord(), M_COMPANY_CONTACT);
        DataBlock derivedCc =
                child(child(derivedRoot, M_VIRTUAL_CONTACT).getFirstRecord(), M_COMPANY_CONTACT);
        assertEquals(baseCc.size(), derivedCc.size(), "company_contact 行数必须一致");
        List<DataRow> sortedBaseCc =
                baseCc.getRecords().stream()
                        .sorted(
                                Comparator.comparing(
                                        r -> r.getString("company_contact", "contact_value")))
                        .toList();
        List<DataRow> sortedDerivedCc =
                derivedCc.getRecords().stream()
                        .sorted(
                                Comparator.comparing(
                                        r -> r.getString("company_contact", "contact_value")))
                        .toList();
        for (int i = 0; i < sortedBaseCc.size(); i++) {
            DataRow bc = sortedBaseCc.get(i);
            DataRow dc = sortedDerivedCc.get(i);
            assertNotEquals(
                    bc.getValue("company_contact", "id"), dc.getValue("company_contact", "id"));
            assertEquals(newId, asLong(dc.getValue("company_contact", "company_id")));
            assertEquals(
                    bc.getString("company_contact", "contact_value"),
                    dc.getString("company_contact", "contact_value"));
            assertEquals(
                    bc.getValue("company_contact", "contact_type"),
                    dc.getValue("company_contact", "contact_type"));
        }

        // 3. credit_rating (1:1，1 条)
        DataRow baseCredit = child(baseRoot, M_CREDIT).getFirstRecord();
        DataRow derivedCredit = child(derivedRoot, M_CREDIT).getFirstRecord();
        assertNotEquals(
                baseCredit.getValue("credit_rating", "id"),
                derivedCredit.getValue("credit_rating", "id"));
        assertEquals(newId, asLong(derivedCredit.getValue("credit_rating", "company_id")));
        assertEquals(
                baseCredit.getValue("credit_rating", "internal_level"),
                derivedCredit.getValue("credit_rating", "internal_level"));
        assertEquals(
                baseCredit.getString("credit_rating", "qcc_score"),
                derivedCredit.getString("credit_rating", "qcc_score"));

        // 4. financial_account (虚拟模块 1007 下，2 条)
        DataBlock baseFin = child(child(baseRoot, M_FINANCE).getFirstRecord(), M_FIN_ACCOUNT);
        DataBlock derivedFin = child(child(derivedRoot, M_FINANCE).getFirstRecord(), M_FIN_ACCOUNT);
        assertEquals(baseFin.size(), derivedFin.size(), "financial_account 行数必须一致");
        List<DataRow> sortedBaseFin =
                baseFin.getRecords().stream()
                        .sorted(
                                Comparator.comparing(
                                        r -> r.getString("financial_account", "account_no")))
                        .toList();
        List<DataRow> sortedDerivedFin =
                derivedFin.getRecords().stream()
                        .sorted(
                                Comparator.comparing(
                                        r -> r.getString("financial_account", "account_no")))
                        .toList();
        for (int i = 0; i < sortedBaseFin.size(); i++) {
            DataRow bf = sortedBaseFin.get(i);
            DataRow df = sortedDerivedFin.get(i);
            assertNotEquals(
                    bf.getValue("financial_account", "id"), df.getValue("financial_account", "id"));
            assertEquals(newId, asLong(df.getValue("financial_account", "company_id")));
            assertEquals(
                    bf.getString("financial_account", "account_no"),
                    df.getString("financial_account", "account_no"));
            assertEquals(
                    bf.getString("financial_account", "account_name"),
                    df.getString("financial_account", "account_name"));
            assertEquals(
                    bf.getString("financial_account", "bank_branch"),
                    df.getString("financial_account", "bank_branch"));
            assertEquals(
                    bf.getValue("financial_account", "bank_head"),
                    df.getValue("financial_account", "bank_head"));
            assertEquals(
                    bf.getValue("financial_account", "account_status"),
                    df.getValue("financial_account", "account_status"));
        }

        // 5. digital_account (虚拟模块 1007 下，3 条)
        DataBlock baseDig = child(child(baseRoot, M_FINANCE).getFirstRecord(), M_DIGITAL_ACCOUNT);
        DataBlock derivedDig =
                child(child(derivedRoot, M_FINANCE).getFirstRecord(), M_DIGITAL_ACCOUNT);
        assertEquals(baseDig.size(), derivedDig.size(), "digital_account 行数必须一致");
        List<DataRow> sortedBaseDig =
                baseDig.getRecords().stream()
                        .sorted(
                                Comparator.comparing(
                                        r -> r.getString("digital_account", "account_no")))
                        .toList();
        List<DataRow> sortedDerivedDig =
                derivedDig.getRecords().stream()
                        .sorted(
                                Comparator.comparing(
                                        r -> r.getString("digital_account", "account_no")))
                        .toList();
        for (int i = 0; i < sortedBaseDig.size(); i++) {
            DataRow bd = sortedBaseDig.get(i);
            DataRow dd = sortedDerivedDig.get(i);
            assertNotEquals(
                    bd.getValue("digital_account", "id"), dd.getValue("digital_account", "id"));
            assertEquals(newId, asLong(dd.getValue("digital_account", "company_id")));
            assertEquals(
                    bd.getString("digital_account", "account_no"),
                    dd.getString("digital_account", "account_no"));
            assertEquals(
                    bd.getString("digital_account", "real_name"),
                    dd.getString("digital_account", "real_name"));
            assertEquals(
                    bd.getValue("digital_account", "platform"),
                    dd.getValue("digital_account", "platform"));
            assertEquals(
                    bd.getValue("digital_account", "account_status"),
                    dd.getValue("digital_account", "account_status"));
        }

        // ------------------------------------------------------------------
        // 4. 验证基准单据 (id=100) 状态完全冻结，未受任何污染
        //    所有断言均与 fork 前的快照对齐，不使用封常字面量
        // ------------------------------------------------------------------
        assertEquals(
                snapshotCompanyName,
                scalar("select company_name from info where id = 100"),
                "基准单据的 company_name 应与 fork 前快照完全一致");
        assertEquals(
                snapshotVersionNo,
                (int) asLong(scalar("select version_no from info where id = 100")),
                "基准单据的 version_no 应与 fork 前快照完全一致");
        assertEquals(
                snapshotLegalPerson,
                scalar("select legal_person from basic_info where company_id = 100"),
                "基准 basic_info 的 legal_person 应与 fork 前快照完全一致");
        assertEquals(
                snapshotMemberCount,
                countOf("select count(*) from member where company_id = 100"),
                "基准单据的 member 总行数应与 fork 前快照完全一致");
        assertEquals(
                snapshotContact1001,
                countOf(
                        "select count(*) from member_contact where member_id = 1001 and deleted = 0"),
                "member 1001 的联系方式行数应与 fork 前快照完全一致");
        assertEquals(
                snapshotContact2001,
                countOf(
                        "select count(*) from member_contact where member_id = 2001 and deleted = 0"),
                "member 2001 的联系方式行数应与 fork 前快照完全一致");
    }

    @Test
    @DisplayName("H9 租户隔离：切换到无数据的主体后查询为空，且 SQL 带 subject_id 谓词")
    void tenantIsolationBySubjectId() {
        AppContext.setContext("company", 99L);

        DataPage<DataBlock> page =
                dataQueryService.queryPage(
                        PageQueryReq.builder().moduleId(M_INFO).pageNo(1).pageSize(10).build());

        assertEquals(0L, page.getTotal(), "主体 99 下不应看到任何 company 数据");
        assertTrue(page.getRecords().isEmpty());

        String sql =
                recording.sqls.stream()
                        .map(s -> s.replace('\n', ' ').replaceAll("\\s+", " ").toLowerCase())
                        .filter(s -> s.contains("from `info`") && !s.contains("count("))
                        .findFirst()
                        .orElseThrow();
        assertTrue(sql.contains("`subject_id`"), "根模块声明了 subject_id，查询必须带该谓词，实际=" + sql);
    }

    // ==================================================================
    // 辅助工具方法
    // ==================================================================

    private static DataBlock buildNewCompanyTree() {
        DataRow basic = new DataRow();
        basic.putValue("basic_info", "uscc", "91110108MA001");
        basic.putValue("basic_info", "short_name", "未来科技");
        basic.putValue("basic_info", "legal_person", "张三");
        basic.putValue("basic_info", "taxpayer_qualification", List.of("一般纳税人"));
        basic.putValue("basic_info", "economic_nature", List.of(Map.of("word_describe", "民营企业")));
        basic.putValue("basic_info", "insured_count", 120);
        basic.putValue("basic_info", "reg_capital", new BigDecimal("5000.00"));
        basic.putValue("basic_info", "paid_in_capital", new BigDecimal("3000.00"));
        basic.putValue("basic_info", "reg_date", java.sql.Date.valueOf("2014-04-28"));
        basic.putValue("basic_info", "is_long_term", 0);

        DataRow dept = new DataRow();
        dept.putValue("member", "type", "DEPT");
        dept.putValue("member", "name", "研发部");
        dept.putValue("member", "parent_id", 0);
        dept.putValue("member", "sort_order", 1);

        DataRow employee = new DataRow();
        employee.putValue("member", "type", "EMPLOYEE");
        employee.putValue("member", "name", "李四");
        employee.putValue("member", "parent_id", 0);
        employee.putValue("member", "position", "架构师");
        employee.putValue("member", "sort_order", 2);
        DataRow c1 = new DataRow();
        c1.putValue("member_contact", "contact_value", "13800138000");
        c1.putValue("member_contact", "is_primary", 1);
        DataRow c2 = new DataRow();
        c2.putValue("member_contact", "contact_value", "010-88889999");
        c2.putValue("member_contact", "is_primary", 0);
        employee.setChildren(List.of(DataBlock.of(M_CONTACT, List.of(c1, c2))));

        DataRow bank = new DataRow();
        bank.putValue("financial_account", "account_no", "62001646341059667358");
        bank.putValue("financial_account", "account_name", "未来科技有限公司");
        bank.putValue("financial_account", "bank_head", List.of(Map.of("word_describe", "中国建设银行")));
        bank.putValue("financial_account", "bank_branch", "中国建设银行北京分行");
        bank.putValue(
                "financial_account", "account_status", List.of(Map.of("word_describe", "正常")));

        DataRow digital = new DataRow();
        digital.putValue("digital_account", "platform", List.of(Map.of("word_describe", "支付宝")));
        digital.putValue("digital_account", "account_no", "ZFB-2026-001");
        digital.putValue("digital_account", "real_name", "张三");
        digital.putValue(
                "digital_account", "account_status", List.of(Map.of("word_describe", "正常")));

        DataRow financeContainer = new DataRow();
        financeContainer.setChildren(
                List.of(
                        DataBlock.of(M_FIN_ACCOUNT, bank),
                        DataBlock.of(M_DIGITAL_ACCOUNT, digital)));

        DataRow credit = new DataRow();
        credit.putValue("credit_rating", "internal_level", List.of(Map.of("word_describe", "一般")));
        credit.putValue("credit_rating", "qcc_score", "88分");

        DataRow address = new DataRow();
        address.putValue("address", "comm_country", "中国");
        address.putValue("address", "comm_region", "北京市/北京市/海淀区");
        address.putValue("address", "comm_detail", "中关村大街 1 号");
        address.putValue("address", "comm_postcode", "100080");
        address.putValue("address", "reg_country", "中国");
        address.putValue("address", "reg_region", "北京市/北京市/海淀区");
        address.putValue("address", "reg_detail", "中关村大街 1 号 101 室");

        DataRow phone = new DataRow();
        phone.putValue("company_contact", "contact_name", "张三");
        phone.putValue("company_contact", "contact_type", List.of(Map.of("word_describe", "手机")));
        phone.putValue("company_contact", "contact_value", "13585855898");
        phone.putValue("company_contact", "sort_order", 1);
        DataRow landline = new DataRow();
        landline.putValue(
                "company_contact", "contact_type", List.of(Map.of("word_describe", "座机")));
        landline.putValue("company_contact", "contact_value", "010-88886666");
        landline.putValue("company_contact", "sort_order", 2);

        DataRow contactContainer = new DataRow();
        contactContainer.setChildren(
                List.of(
                        DataBlock.of(M_ADDRESS, address),
                        DataBlock.of(M_COMPANY_CONTACT, List.of(phone, landline))));

        DataRow root = new DataRow();
        root.putValue("info", "company_name", "未来科技有限公司");
        root.putValue("info", "business_no", "CP-2026-0001");
        root.putValue("info", "version_no", 1);
        root.putValue("info", "nature", List.of(Map.of("word_describe", "企业")));
        root.putValue("info", "reg_status", List.of(Map.of("word_describe", "存续")));
        root.putValue("info", "cooperation_types", List.of(Map.of("word_describe", "客户")));
        root.putValue("info", "industry_tags", List.of(Map.of("word_describe", "兰炭")));
        root.putValue("info", "business_status", 10);
        root.setChildren(
                List.of(
                        DataBlock.of(M_BASIC, basic),
                        DataBlock.of(M_MEMBER, List.of(dept, employee)),
                        DataBlock.of(M_FINANCE, financeContainer),
                        DataBlock.of(M_CREDIT, credit),
                        DataBlock.of(M_VIRTUAL_CONTACT, contactContainer)));
        return DataBlock.of(M_INFO, root);
    }

    private Object scalar(String sql) {
        var record = dslContext.fetchOne(sql);
        return record == null ? null : record.get(0);
    }

    private long countOf(String sql) {
        Object v = scalar(sql);
        return v == null ? 0L : ((Number) v).longValue();
    }

    private static long asLong(Object v) {
        assertNotNull(v, "期望非空的数值");
        return ((Number) v).longValue();
    }

    private static DataBlock child(DataRow row, long moduleId) {
        DataBlock block = row.getChild(moduleId);
        assertNotNull(block, "行上缺少 moduleId=" + moduleId + " 的子区块，实际=" + row.getTables().keySet());
        return block;
    }

    private static DataRow rowByTableId(DataBlock block, String table, long id) {
        for (DataRow r : block.getRecords()) {
            Object v = r.getValue(table, "id");
            if (v instanceof Number n && n.longValue() == id) {
                return r;
            }
        }
        return null;
    }

    private static String tableOf(String sql) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("from `(\\w+)`").matcher(sql);
        return m.find() ? m.group(1) : null;
    }

    private static int countOfOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = haystack.indexOf(needle);
        while (idx >= 0) {
            count++;
            idx = haystack.indexOf(needle, idx + needle.length());
        }
        return count;
    }

    private static void assertNoTechnicalKeys(Object node, String path) {
        if (node instanceof DataRow r) {
            if (r.getTables() != null) {
                for (Map.Entry<String, Object> e : r.getTables().entrySet()) {
                    assertFalse(
                            e.getKey().startsWith("_fk_") || e.getKey().equals("_row_id"),
                            "内部技术字段泄露于 " + path + " -> " + e.getKey());
                    assertNoTechnicalKeys(e.getValue(), path + "." + e.getKey());
                }
            }
            assertNoTechnicalKeys(r.getChildren(), path + ".children");
        } else if (node instanceof DataBlock b) {
            assertNoTechnicalKeys(b.getRecords(), path + ".records");
        } else if (node instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                assertFalse(
                        String.valueOf(e.getKey()).startsWith("_fk_")
                                || "_row_id".equals(String.valueOf(e.getKey())),
                        "内部技术字段泄露于 " + path + " -> " + e.getKey());
                assertNoTechnicalKeys(e.getValue(), path + "." + e.getKey());
            }
        } else if (node instanceof List<?> l) {
            for (int i = 0; i < l.size(); i++) {
                assertNoTechnicalKeys(l.get(i), path + "[" + i + "]");
            }
        }
    }

    // ==================================================================
    // 装置与元数据定义（内置于本测试类中）
    // ==================================================================

    static String load(String resource) {
        try (InputStream in = CompanyH2IntegrationTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("classpath 上找不到 SQL 资源: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取 SQL 资源失败: " + resource, e);
        }
    }

    static void initSchema(DataSource ds) throws SQLException {
        executeSql(ds, load(SCHEMA_RESOURCE));
    }

    static void initSeed(DataSource ds) throws SQLException {
        executeSql(ds, load(SEED_RESOURCE));
    }

    private static void executeSql(DataSource ds, String sql) throws SQLException {
        try (Connection conn = ds.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    static long countRows(DataSource ds, String sql) throws SQLException {
        try (Connection conn = ds.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static final Map<Long, ModuleInfo> MODS = new LinkedHashMap<>();
    private static final Map<Long, ModuleFieldDTO> FMAP = new LinkedHashMap<>();
    private static final Map<Long, Long> F2M = new LinkedHashMap<>();
    private static final Map<Long, List<Long>> P2C = new LinkedHashMap<>();
    private static final List<TableRelationDTO> RELATIONS = new ArrayList<>();
    private static final ProjectTableRelationsDTO PROJ;

    static {
        add(1001L, "外部对象管理", "info", 0L);
        add(1002L, "基本信息", "basic_info", 1001L);
        add(1003L, "人员构成-组织架构", "member", 1001L);
        add(1004L, "人员构成-个人通讯录", "member_contact", 1003L);
        add(1005L, "联系方式-公司地址", "address", 1020L);
        add(1006L, "联系方式-公司联系方式", "company_contact", 1020L);
        add(1007L, "财务信息", "", 1001L);
        add(1008L, "银行卡", "financial_account", 1007L);
        add(1009L, "电子账户", "digital_account", 1007L);
        add(1010L, "信用等级", "credit_rating", 1001L);
        add(1020L, "联系方式", "", 1001L);

        // module 1001
        f(10001L, 1001L, "info", "id");
        f(10002L, 1001L, "info", "company_name");
        f(10003L, 1001L, "info", "parent_id");
        f(10004L, 1001L, "info", "nature");
        f(10005L, 1001L, "info", "reg_status");
        f(10006L, 1001L, "info", "cooperation_types");
        f(10007L, 1001L, "info", "industry_tags");
        f(10008L, 1001L, "info", "industry_types");
        f(10009L, 1001L, "info", "logo_url");
        f(10077L, 1001L, "info", "business_no");
        f(10078L, 1001L, "info", "business_status");
        f(10079L, 1001L, "info", "version_no");
        f(10081L, 1001L, "info", "created_by");
        f(10082L, 1001L, "info", "updated_by");
        f(10083L, 1001L, "info", "subject_id");
        f(10084L, 1001L, "info", "created_date");
        f(10085L, 1001L, "info", "updated_date");
        f(10171L, 1001L, "info", "gender");

        // module 1002
        f(10011L, 1002L, "basic_info", "id");
        f(10012L, 1002L, "basic_info", "uscc");
        f(10013L, 1002L, "basic_info", "short_name");
        f(10014L, 1002L, "basic_info", "en_short_name");
        f(10015L, 1002L, "basic_info", "former_name");
        f(10016L, 1002L, "basic_info", "former_short_name");
        f(10017L, 1002L, "basic_info", "en_former_name");
        f(10018L, 1002L, "basic_info", "en_former_short_name");
        f(10019L, 1002L, "basic_info", "taxpayer_qualification");
        f(10020L, 1002L, "basic_info", "website");
        f(10021L, 1002L, "basic_info", "legal_person");
        f(10022L, 1002L, "basic_info", "economic_nature");
        f(10023L, 1002L, "basic_info", "insured_count");
        f(10024L, 1002L, "basic_info", "reg_capital");
        f(10025L, 1002L, "basic_info", "paid_in_capital");
        f(10026L, 1002L, "basic_info", "company_type");
        f(10027L, 1002L, "basic_info", "reg_date");
        f(10028L, 1002L, "basic_info", "business_term_start");
        f(10029L, 1002L, "basic_info", "business_term_end");
        f(10030L, 1002L, "basic_info", "is_long_term");
        f(10031L, 1002L, "basic_info", "id_card_front_url");
        f(10032L, 1002L, "basic_info", "id_card_back_url");
        f(10033L, 1002L, "basic_info", "license_url");
        f(10155L, 1002L, "basic_info", "version_no");

        // module 1003
        f(10034L, 1003L, "member", "id");
        f(10035L, 1003L, "member", "type");
        f(10036L, 1003L, "member", "parent_id");
        f(10037L, 1003L, "member", "code");
        f(10038L, 1003L, "member", "name");
        f(10039L, 1003L, "member", "leader_id");
        f(10040L, 1003L, "member", "position");
        f(10041L, 1003L, "member", "sort_order");
        f(10042L, 1003L, "member", "status");
        f(10161L, 1003L, "member", "member_name");

        // module 1004
        f(10043L, 1004L, "member_contact", "id");
        f(10044L, 1004L, "member_contact", "member_id");
        f(10045L, 1004L, "member_contact", "contact_type");
        f(10046L, 1004L, "member_contact", "contact_value");
        f(10047L, 1004L, "member_contact", "is_primary");

        // module 1005
        f(10048L, 1005L, "address", "id");
        f(10049L, 1005L, "address", "comm_country");
        f(10050L, 1005L, "address", "comm_region");
        f(10051L, 1005L, "address", "comm_detail");
        f(10052L, 1005L, "address", "comm_postcode");
        f(10053L, 1005L, "address", "reg_country");
        f(10054L, 1005L, "address", "reg_region");
        f(10055L, 1005L, "address", "reg_detail");

        // module 1006
        f(10056L, 1006L, "company_contact", "id");
        f(10057L, 1006L, "company_contact", "company_id");
        f(10058L, 1006L, "company_contact", "contact_type");
        f(10059L, 1006L, "company_contact", "contact_value");
        f(10061L, 1006L, "company_contact", "sort_order");
        f(10170L, 1006L, "company_contact", "contact_name");

        // module 1008
        f(10062L, 1008L, "financial_account", "id");
        f(10063L, 1008L, "financial_account", "account_no");
        f(10064L, 1008L, "financial_account", "account_name");
        f(10065L, 1008L, "financial_account", "bank_head");
        f(10066L, 1008L, "financial_account", "bank_branch");
        f(10067L, 1008L, "financial_account", "account_status");

        // module 1009
        f(10068L, 1009L, "digital_account", "id");
        f(10069L, 1009L, "digital_account", "platform");
        f(10070L, 1009L, "digital_account", "account_no");
        f(10071L, 1009L, "digital_account", "real_name");
        f(10072L, 1009L, "digital_account", "account_status");

        // module 1010
        f(10073L, 1010L, "credit_rating", "id");
        f(10074L, 1010L, "credit_rating", "internal_level");
        f(10075L, 1010L, "credit_rating", "qcc_score");
        f(10076L, 1010L, "credit_rating", "updated_by");

        P2C.put(M_INFO, List.of(M_BASIC, M_MEMBER, M_FINANCE, M_CREDIT, M_VIRTUAL_CONTACT));
        P2C.put(M_MEMBER, List.of(M_CONTACT));
        P2C.put(M_FINANCE, List.of(M_FIN_ACCOUNT, M_DIGITAL_ACCOUNT));
        P2C.put(M_VIRTUAL_CONTACT, List.of(M_ADDRESS, M_COMPANY_CONTACT));

        RELATIONS.addAll(
                List.of(
                        rel("info", "id", "basic_info", "company_id", "1:1"),
                        rel("info", "id", "member", "company_id", "1:N"),
                        rel("info", "id", "address", "company_id", "1:1"),
                        rel("info", "id", "company_contact", "company_id", "1:N"),
                        rel("info", "id", "financial_account", "company_id", "1:N"),
                        rel("info", "id", "digital_account", "company_id", "1:N"),
                        rel("member", "id", "member_contact", "member_id", "1:N"),
                        rel("info", "id", "credit_rating", "company_id", "1:1")));

        Map<String, List<TableRelationDTO>> forward = new LinkedHashMap<>();
        Map<String, List<TableRelationDTO>> reverse = new LinkedHashMap<>();
        for (TableRelationDTO r : RELATIONS) {
            forward.computeIfAbsent(r.getMainTable().toLowerCase(), k -> new ArrayList<>()).add(r);
            reverse.computeIfAbsent(r.getJoinTable().toLowerCase(), k -> new ArrayList<>()).add(r);
        }
        PROJ =
                ProjectTableRelationsDTO.builder()
                        .projectNo("company")
                        .allRelations(RELATIONS)
                        .forwardRelations(forward)
                        .reverseRelations(reverse)
                        .build();
    }

    private static void add(long mid, String name, String primaryTable, long parent) {
        ModuleInfo mi =
                ModuleInfo.builder()
                        .moduleId(mid)
                        .moduleName(name)
                        .primaryTable(primaryTable)
                        .parentId(parent)
                        .fields(new ArrayList<>())
                        .build();
        MODS.put(mid, mi);
    }

    private static ModuleFieldDTO f(long id, long mid, String table, String column) {
        ModuleFieldDTO dto =
                ModuleFieldDTO.builder()
                        .id(id)
                        .moduleId(mid)
                        .tableName(table)
                        .columnName(column)
                        .displayName(column)
                        .sortOrder((int) (id % 1000))
                        .build();
        FMAP.put(id, dto);
        F2M.put(id, mid);
        ModuleInfo owner = MODS.get(mid);
        if (owner != null) {
            owner.getFields().add(dto);
        }
        return dto;
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

    static ModuleTreeDTO treeFor(long root) {
        List<Long> sub = new ArrayList<>();
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            long x = stack.pop();
            if (MODS.get(x) == null || sub.contains(x)) {
                continue;
            }
            sub.add(x);
            for (Long k : P2C.getOrDefault(x, List.of())) {
                stack.push(k);
            }
        }
        Map<Long, ModuleInfo> mm = new LinkedHashMap<>();
        Map<Long, List<Long>> p2c = new LinkedHashMap<>();
        for (Long x : sub) {
            mm.put(x, MODS.get(x));
            List<Long> kids = new ArrayList<>();
            for (Long k : P2C.getOrDefault(x, List.of())) {
                if (sub.contains(k)) {
                    kids.add(k);
                }
            }
            if (!kids.isEmpty()) {
                p2c.put(x, kids);
            }
        }
        return ModuleTreeDTO.builder()
                .rootModuleId(root)
                .projectNo("company")
                .subjectId(0L)
                .rootModule(MODS.get(root))
                .moduleMap(mm)
                .parentToChildren(p2c)
                .fieldMap(FMAP)
                .fieldToModuleMap(F2M)
                .allowedModuleIds(new LinkedHashSet<>(sub))
                .build();
    }

    static ConfigApi configApi() {
        return mock(
                ConfigApi.class,
                invocation -> {
                    String name = invocation.getMethod().getName();
                    if ("getModuleTree".equals(name)) {
                        return treeFor(invocation.getArgument(2));
                    }
                    if ("getProjectTableRelations".equals(name)) {
                        return PROJ;
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

    static final class RecordingDataSource implements DataSource {
        private final DataSource delegate;
        final List<String> sqls = Collections.synchronizedList(new ArrayList<>());

        RecordingDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection real = delegate.getConnection();
            return (Connection)
                    Proxy.newProxyInstance(
                            RecordingDataSource.class.getClassLoader(),
                            new Class<?>[] {Connection.class},
                            (proxy, method, args) -> {
                                Object result = method.invoke(real, args);
                                if (result instanceof java.sql.PreparedStatement
                                        && args != null
                                        && args.length > 0
                                        && args[0] instanceof String sql) {
                                    sqls.add(sql);
                                }
                                return result;
                            });
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        @Override
        public java.io.PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getLogger("company-it");
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }
    }
}
