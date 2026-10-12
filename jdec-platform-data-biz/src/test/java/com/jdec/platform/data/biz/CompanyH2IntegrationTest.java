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
import com.jdec.platform.data.api.dto.request.ForkVersionReq;
import com.jdec.platform.data.api.dto.request.PhysicalFilterItem;
import com.jdec.platform.data.api.dto.request.TableDetailQueryReq;
import com.jdec.platform.data.api.dto.request.TableOptionReq;
import com.jdec.platform.data.api.dto.request.TablePageQueryReq;
import com.jdec.platform.data.api.dto.request.TableSpec;
import com.jdec.platform.data.api.dto.response.DataOptionResp;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.api.dto.response.DataSaveResp;
import com.jdec.platform.data.biz.plan.PlanMeta;
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
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
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
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * company 项目 · data 引擎全链路集成测试（真实元数据 + 真实表结构 + 真实种子数据 + 真库执行）
 *
 * <p>与 {@code CompanyDataContractTest}（MockDataProvider 只捕获 SQL）的分工：本类在 **H2 内存库**上真实建表、真实落库、
 * 真实查询，因此能钉住「SQL 到底跑不跑得通、写进去的列到底对不对、外键有没有真的回填」这类只有真库才能证伪的结论。
 *
 * <p>fixture 三件套全部取自生产环境，不作臆造：
 *
 * <ul>
 *   <li><b>元数据</b>：{@code config_center} 中 {@code project_no='company'} 的真实 14 个模块（1001 子树 11 个 +
 *       场地产线 3 个）、101 个字段字典项、10 条 {@code sys_table_relation}；
 *   <li><b>表结构</b>：{@code company} 库 12 张物理表的真实 DDL（列名 / 类型 / 可空性 / 默认值 / 唯一键原样保留）；
 *   <li><b>种子数据</b>：{@code company} 库 {@code info.id=100}（全库唯一 9 张子表齐全的聚合根，36 行）、{@code id=146}（仅
 *       credit_rating）、{@code id=148}（仅 basic_info）。
 * </ul>
 *
 * <p><b>H2 适配</b>（只改 DDL 语法，不改引擎行为；由生成脚本逐条处理，规则如下）：
 *
 * <ol>
 *   <li>列级 {@code COLLATE} / {@code CHARACTER SET} 删除（H2 语法不支持；表级 {@code CHARSET=} / {@code
 *       COLLATE=} 保留）；
 *   <li>表级 {@code AUTO_INCREMENT=<n>} 删除（H2 会把它解析成一个名为 AUTO_INCREMENT 的列）；
 *   <li>约束 / 索引名加表名前缀：H2 的约束名是 schema 级全局唯一，MySQL 是表级，而 {@code uk_company_id} 在 basic_info /
 *       address / credit_rating 上重复出现；
 *   <li>各 {@code CREATE TABLE} 之间补 {@code ;}：H2 支持多语句批处理，但每条语句必须以分号结尾；
 *   <li>{@code json} 列改为 {@code varchar(4000)}：H2 的 JSON 类型与 MySQL 语义不同（读回时额外包裹引号、且没有 {@code
 *       JSON_CONTAINS}），而 MySQL 的 json 列读出来就是**原始 JSON 文本**。用 varchar 承载 JSON 文本， 恰好让引擎的 {@code
 *       parseIfJson} / {@code MAPPER.writeValueAsString} 走与生产完全一致的路径；
 *   <li>{@code bigint unsigned} 降级为 {@code bigint}，避免 jOOQ 的 MySQL 方言把它映射成 {@code ULong}
 *       而干扰主键取值断言（对引擎行为无影响，主键恒为正）。
 * </ol>
 *
 * <p><b>写操作必须在真实事务中执行</b>：生产里 {@code JooqDataContextFactory} 把业务库包成 {@code
 * TransactionAwareDataSourceProxy}，且 {@code SavePlanExecutor} 的入口带
 * {@code @Transactional}，二者共同保证「insert 取自增主键」与「LAST_INSERT_ID()」落在同一条物理连接上。本类直接 new 出 {@code
 * SavePlanExecutor}（没有 Spring 代理）， 所以必须由测试用 {@link #inTx} 显式开启事务；否则 jOOQ 每条语句都从 DataSource
 * 借一条新连接，H2 的 {@code LAST_INSERT_ID()} 在新会话里恒为 0 —— 那是在测一条生产不存在的代码路径。
 *
 * <p><b>本类钉住的一处真实缺陷</b>：{@code SavePlanExecutor.upsertSingleRow} 的字段白名单原先只豁免审计列，未豁免<b>父外键列</b>。 真实
 * {@code sys_module_field} 普遍不声明从表技术外键列（{@code basic_info.company_id} / {@code member.company_id} /
 * {@code credit_rating.company_id} …），于是 {@code saveChildPlanRecursively} 回填的父主键被静默丢弃，H1 / H7 会因
 * {@code basic_info.company_id} 缺失（NOT NULL 列）而失败。修复见 {@code isEngineManagedColumn}。
 */
@DisplayName("company 项目 · data 引擎 H2 全链路集成测试（真实元数据/表结构/种子数据）")
class CompanyH2IntegrationTest {

    // ==================================================================
    // 模块 ID（config_center · project_no='company' 真实值）
    // ==================================================================

    /** 1001 外部对象管理（根模块，主表 info） */
    private static final long M_INFO = 1001L;

    /** 1002 基本信息（1:1，basic_info） */
    private static final long M_BASIC = 1002L;

    /** 1003 人员构成-组织架构（1:N，member） */
    private static final long M_MEMBER = 1003L;

    /** 1004 人员构成-个人通讯录（1:N，member_contact，挂在 1003 下） */
    private static final long M_CONTACT = 1004L;

    /** 1005 联系方式-公司地址（1:1，address，挂在虚拟模块 1020 下） */
    private static final long M_ADDRESS = 1005L;

    /** 1006 联系方式-公司联系方式（1:N，company_contact，挂在虚拟模块 1020 下） */
    private static final long M_COMPANY_CONTACT = 1006L;

    /** 1007 财务信息（虚拟模块，primary_table=''） */
    private static final long M_FINANCE = 1007L;

    /** 1008 银行卡（1:N，financial_account） */
    private static final long M_FIN_ACCOUNT = 1008L;

    /** 1009 电子账户（1:N，digital_account） */
    private static final long M_DIGITAL_ACCOUNT = 1009L;

    /** 1010 信用等级（1:1，credit_rating） */
    private static final long M_CREDIT = 1010L;

    /** 1020 联系方式（虚拟模块，primary_table IS NULL） */
    private static final long M_VIRTUAL_CONTACT = 1020L;

    /** 1021 场地产线管理（独立根模块，与 1001 子树无关 —— 用于反证「不该被碰到」） */
    private static final long M_VENUE_LINE = 1021L;

    // ==================================================================
    // 测试装置
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

        // 复刻生产装配：JooqDataContextFactory 把业务库包成 TransactionAwareDataSourceProxy，
        // SavePlanExecutor 的方法带 @Transactional。二者合起来保证「insert 取自增主键」与
        // 「LAST_INSERT_ID()」落在同一条物理连接上。缺了这层事务，jOOQ 每条语句都从 DataSource
        // 借新连接，H2 的 LAST_INSERT_ID() 在新会话里恒为 0 —— 那是在测一条生产不存在的路径。
        TransactionAwareDataSourceProxy txAware = new TransactionAwareDataSourceProxy(recording);
        txTemplate = new TransactionTemplate(new DataSourceTransactionManager(txAware));

        DefaultConfiguration jooqConfig = new DefaultConfiguration();
        jooqConfig.set(txAware);
        jooqConfig.set(SQLDialect.MYSQL);
        dslContext = DSL.using(jooqConfig);

        initSchema();
        initSeed();

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

        ConfigApi configApi = buildConfigApi();
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

        // 只关心业务 SQL，把建表 / 灌种子的语句从捕获列表里清掉
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

    /**
     * 在真实事务中执行写操作，复刻生产 {@code @Transactional} + {@code TransactionAwareDataSourceProxy} 的连接语义。
     *
     * <p>本测试直接 new 出 {@link SavePlanExecutor}（无 Spring 代理），因此 {@code @Transactional} 不会自动生效；
     * 必须由测试显式开启事务，否则 insert 与自增主键回读会落在不同连接上。
     */
    private <T> T inTx(java.util.function.Supplier<T> action) {
        return txTemplate.execute(status -> action.get());
    }

    // ==================================================================
    // H1 · 写链路：全表新建整树，外键级联回填
    // ==================================================================

    @Test
    @DisplayName("H1 保存整树：9 张物理子表全部落库，父子外键自动回填，JSON/DECIMAL/DATE 原样往返")
    void saveWholeTreePropagatesForeignKeys() {
        // 租户列由 AppContext 注入，用非 0 值证明它真的被写进每一行
        AppContext.setContext("company", 7L);

        DataSaveResp resp = inTx(() -> dataPersistenceService.save(buildNewCompanyTree()));
        Long rootId = resp.getRootId();
        assertNotNull(rootId, "保存必须返回根记录 ID");
        assertTrue(rootId > 0);

        assertEquals(1, countOf("select count(*) from info where id = " + rootId));
        assertEquals("未来科技有限公司", scalar("select company_name from info where id = " + rootId));
        assertEquals(7L, asLong(scalar("select subject_id from info where id = " + rootId)));

        // 1:1 伴生模块（模块树子节点，不是 JOIN）
        assertEquals(1, countOf("select count(*) from basic_info where company_id = " + rootId));
        // 1:N 子表
        assertEquals(2, countOf("select count(*) from member where company_id = " + rootId));
        // 虚拟模块 1007 下的两张表：必须穿透虚拟层并把 company_id 回填到根 ID
        assertEquals(
                1, countOf("select count(*) from financial_account where company_id = " + rootId));
        assertEquals(
                1, countOf("select count(*) from digital_account where company_id = " + rootId));
        // 虚拟模块 1020 下的两张表
        assertEquals(1, countOf("select count(*) from address where company_id = " + rootId));
        assertEquals(
                2, countOf("select count(*) from company_contact where company_id = " + rootId));
        // 1:1 信用等级
        assertEquals(1, countOf("select count(*) from credit_rating where company_id = " + rootId));

        // 孙表：member_contact.member_id 必须指向**刚生成的** member 主键（二级外键传播）
        Long employeeId =
                asLong(
                        scalar(
                                "select id from member where company_id = "
                                        + rootId
                                        + " and type = 'EMPLOYEE'"));
        assertNotNull(employeeId);
        assertEquals(
                2, countOf("select count(*) from member_contact where member_id = " + employeeId));

        // 租户列逐表下发
        assertEquals(7L, asLong(scalar("select subject_id from member where id = " + employeeId)));
        assertEquals(
                7L,
                asLong(
                        scalar(
                                "select subject_id from financial_account where company_id = "
                                        + rootId)));

        // JSON 列：Java List 被序列化成 JSON 文本
        String nature = String.valueOf(scalar("select nature from info where id = " + rootId));
        assertTrue(nature.startsWith("["), "nature 应落库为 JSON 数组文本，实际=" + nature);
        assertTrue(nature.contains("企业"), "nature 应保留词云描述，实际=" + nature);
        assertEquals(
                "[\"一般纳税人\"]",
                String.valueOf(
                        scalar(
                                "select taxpayer_qualification from basic_info where company_id = "
                                        + rootId)));

        // DECIMAL / DATE 往返
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

        // 写回同构：响应里的源 DataRow 被回填了各级主键
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

    // ==================================================================
    // H2 · 读链路：真实 36 行聚合根整树装配
    // ==================================================================

    @Test
    @DisplayName("H2 查询整树：真实聚合根 100 的 36 行全部装配，两层虚拟模块被穿透，孙表挂到行内")
    void queryWholeTreeAssemblesRealAggregate() {
        DataPage<DataBlock> page =
                dataQueryService.queryByTable(
                        TablePageQueryReq.builder()
                                .moduleId(M_INFO)
                                .pageNo(1)
                                .pageSize(10)
                                .build());

        assertEquals(3L, page.getTotal(), "种子共 3 个 info 根记录");
        assertEquals(3, page.getRecords().size());

        DataRow r100 = rowByTableId(page.getData(), "info", 100L);
        assertNotNull(r100, "应返回 info.id=100 的聚合根");
        assertEquals("石嘴山市盛港煤焦化有限公司1010", r100.getString("info", "company_name"));
        assertEquals(3, r100.getInteger("info", "version_no"));
        assertEquals("CR202609110137000100", r100.getString("info", "business_no"));

        // JSON 列在读出时被解析回 List（而不是留成字符串）
        assertInstanceOf(List.class, r100.getValue("info", "nature"), "nature 应被解析为 List");

        // 1:1 / 1:N 直接子模块
        assertEquals(1, child(r100, M_BASIC).size());
        assertEquals(
                "9153312343631123J",
                child(r100, M_BASIC).getFirstRecord().getString("basic_info", "uscc"));
        assertEquals(16, child(r100, M_MEMBER).size(), "member 1:N 应返回全部 16 行");
        assertEquals(1, child(r100, M_CREDIT).size());

        // 虚拟模块 1007（财务信息）：records 恒为 1 个纯逻辑容器，容器内再挂物理子模块
        DataRow financeContainer = child(r100, M_FINANCE).getFirstRecord();
        assertNull(financeContainer.getTable("info"), "虚拟容器不应携带任何物理表空间");
        assertEquals(2, child(financeContainer, M_FIN_ACCOUNT).size());
        assertEquals(9, child(financeContainer, M_DIGITAL_ACCOUNT).size());

        // 虚拟模块 1020（联系方式）
        DataRow contactContainer = child(r100, M_VIRTUAL_CONTACT).getFirstRecord();
        assertEquals(1, child(contactContainer, M_ADDRESS).size());
        assertEquals(2, child(contactContainer, M_COMPANY_CONTACT).size());

        // 孙表：member_contact 挂在具体 member 行内部
        DataRow member1001 = rowByTableId(child(r100, M_MEMBER), "member", 1001L);
        assertNotNull(member1001);
        assertEquals("经理办", member1001.getString("member", "name"));
        assertEquals(4, child(member1001, M_CONTACT).size());

        // 内部技术字段绝不外泄
        assertNoTechnicalKeys(r100, "root");

        // 另外两个根记录：最小聚合根只带自己那一两张表
        DataRow r148 = rowByTableId(page.getData(), "info", 148L);
        assertEquals(1, child(r148, M_BASIC).size());
        assertEquals(0, child(r148, M_MEMBER).size(), "148 没有 member");
        DataRow r146 = rowByTableId(page.getData(), "info", 146L);
        assertEquals(1, child(r146, M_CREDIT).size());
        assertEquals(0, child(r146, M_BASIC).size());
    }

    // ==================================================================
    // H3 · SQL 形态：分批抓取而非 N+1，且不产生笛卡尔积
    // ==================================================================

    @Test
    @DisplayName("H3 SQL 形态：每张表恰好一条 SELECT、零 JOIN、批量 IN 抓取，且完全不碰 venue 三表")
    void sqlShapeIsBatchFetchWithoutJoins() {
        dataQueryService.queryByTable(
                TablePageQueryReq.builder().moduleId(M_INFO).pageNo(1).pageSize(10).build());

        List<String> selects =
                recording.sqls.stream()
                        .map(s -> s.replace('\n', ' ').replaceAll("\\s+", " ").toLowerCase())
                        .filter(s -> s.startsWith("select"))
                        .toList();
        assertFalse(selects.isEmpty(), "应捕获到查询 SQL");

        // company 的 3 条 1:1 关系从表本身就是模块主表 ⇒ 零附加表 ⇒ 走 SELECT *，不产生 JOIN
        for (String sql : selects) {
            assertFalse(sql.contains(" join "), "company 不应产生任何 JOIN，实际=" + sql);
        }
        assertTrue(
                selects.stream().anyMatch(s -> s.contains("select * from `info`")),
                "无附加表时全字段模式应走 SELECT *，实际=" + selects);

        // 每个物理表恰好一条数据查询（除 info 多一条 COUNT），证明是分批抓取而非 N+1
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

        // 子表走 fk IN (...) 批量抓取，且无条件带 deleted 软删除谓词
        String memberSql =
                selects.stream().filter(s -> s.contains("from `member`")).findFirst().orElseThrow();
        assertTrue(memberSql.contains("`member`.`company_id` in ("), "应走批量 IN 抓取，实际=" + memberSql);
        assertTrue(memberSql.contains("`deleted`"), "子表查询必须带 deleted 谓词，实际=" + memberSql);
        assertTrue(
                selects.stream()
                        .anyMatch(s -> s.contains("from `member_contact`") && s.contains(" in (")),
                "孙表 member_contact 应以其父行主键批量抓取");

        // 反证：venue_line / venue / line 三张表与 1001 子树无关，绝不能被碰到
        for (String sql : selects) {
            assertFalse(sql.contains("`venue`"), "不应查询 venue，实际=" + sql);
            assertFalse(sql.contains("`venue_line`"), "不应查询 venue_line，实际=" + sql);
            assertFalse(sql.contains("`line`"), "不应查询 line，实际=" + sql);
        }
    }

    // ==================================================================
    // H4 · 读写同构：详情读出来能原样写回去
    // ==================================================================

    @Test
    @DisplayName("H4 详情读写同构：getDetail 的整树可直接回写，行数与父子外键均不变")
    void detailIsReadWriteIsomorphic() {
        DataBlock detail =
                dataQueryService.getDetailByTable(
                        TableDetailQueryReq.builder().moduleId(M_INFO).id(100L).build());
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
        // 全部子表外键仍然指向 100，没有被「更新」破坏
        assertEquals(
                16, countOf("select count(*) from member where company_id = 100 and deleted = 0"));
        assertEquals(
                0,
                countOf(
                        "select count(*) from financial_account where company_id <> 100 and deleted = 0"));
    }

    // ==================================================================
    // H5 · 子孙过滤上卷（Semi-Join / EXISTS）
    // ==================================================================

    @Test
    @DisplayName("H5 子孙过滤上卷：对孙表 member_contact 过滤，上卷为嵌套 EXISTS 并精准裁剪父级与子级")
    void descendantFilterRollsUpAsNestedExists() {
        TablePageQueryReq req =
                TablePageQueryReq.builder()
                        .moduleId(M_INFO)
                        .pageNo(1)
                        .pageSize(10)
                        .filters(
                                List.of(
                                        PhysicalFilterItem.builder()
                                                .moduleId(M_CONTACT)
                                                .tableName("member_contact")
                                                .columnName("contact_value")
                                                .operator("EQ")
                                                .value("13585855898")
                                                .build()))
                        .build();

        DataPage<DataBlock> page = dataQueryService.queryByTable(req);

        // 只有 100 的 member 1001 拥有这个联系方式
        assertEquals(1L, page.getTotal());
        DataRow root = page.getRecords().get(0);
        assertEquals(100L, asLong(root.getValue("info", "id")));

        // 根扫描 SQL 必须上卷为 1001 -> member -> member_contact 的两层 EXISTS
        String rootSql =
                recording.sqls.stream()
                        .map(s -> s.replace('\n', ' ').replaceAll("\\s+", " ").toLowerCase())
                        .filter(s -> s.contains("from `info`") && !s.contains("count("))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("未捕获到根扫描 SQL"));
        assertEquals(2, countOfOccurrences(rootSql, "exists"), "应恰好两层 EXISTS，实际=" + rootSql);
        assertTrue(rootSql.contains("from `member`"), "第一层 EXISTS 应针对 member，实际=" + rootSql);
        assertTrue(
                rootSql.contains("from `member_contact`"),
                "第二层 EXISTS 应针对 member_contact，实际=" + rootSql);
        assertTrue(
                rootSql.contains("`member_contact`.`member_id` = `member`.`id`"),
                "孙表 EXISTS 应通过 member_id 关联 member，实际=" + rootSql);

        // 子级被裁剪：只返回命中的那个 member 与那条联系方式
        DataBlock members = child(root, M_MEMBER);
        assertEquals(1, members.size(), "只应返回命中的 member，实际=" + members.size());
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
        TablePageQueryReq req =
                TablePageQueryReq.builder()
                        .moduleId(M_INFO)
                        .pageNo(1)
                        .pageSize(10)
                        .filters(
                                List.of(
                                        PhysicalFilterItem.builder()
                                                .moduleId(M_DIGITAL_ACCOUNT)
                                                .tableName("digital_account")
                                                .columnName("account_no")
                                                .operator("EQ")
                                                .value("ZFB001")
                                                .build()))
                        .build();

        DataPage<DataBlock> page = dataQueryService.queryByTable(req);
        assertEquals(1L, page.getTotal(), "只有 100 拥有 ZFB001");

        String rootSql =
                recording.sqls.stream()
                        .map(s -> s.replace('\n', ' ').replaceAll("\\s+", " ").toLowerCase())
                        .filter(s -> s.contains("from `info`") && !s.contains("count("))
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                1,
                countOfOccurrences(rootSql, "exists"),
                "虚拟模块 1007 无物理表，必须被穿透 —— 只应有 1 层 EXISTS，实际=" + rootSql);
        assertTrue(rootSql.contains("from `digital_account`"), "实际=" + rootSql);
        assertTrue(
                rootSql.contains("`digital_account`.`company_id` = `info`.`id`"),
                "虚拟层穿透后应直接关联 info.id = digital_account.company_id，实际=" + rootSql);
    }

    // ==================================================================
    // H6 · 1:N 孤儿差集软删除 + 孙表级联
    // ==================================================================

    @Test
    @DisplayName("H6 孤儿删除：提交的 member 数组少了 1001，该行及其 member_contact 孙表被级联软删除")
    void orphanRemovalSoftDeletesRowAndDescendants() {
        DataBlock detail =
                dataQueryService.getDetailByTable(
                        TableDetailQueryReq.builder().moduleId(M_INFO).id(100L).build());

        DataBlock members = child(detail.getFirstRecord(), M_MEMBER);
        List<DataRow> kept =
                members.getRecords().stream()
                        .filter(
                                r ->
                                        !Long.valueOf(1001L)
                                                .equals(asLong(r.getValue("member", "id"))))
                        .toList();
        assertEquals(15, kept.size());
        members.setRecords(new ArrayList<>(kept));

        inTx(() -> dataPersistenceService.save(detail));

        assertEquals(
                15,
                countOf("select count(*) from member where company_id = 100 and deleted = 0"),
                "存活 member 应只剩 15 条");
        assertEquals(
                1,
                countOf("select count(*) from member where id = 1001 and deleted = 1"),
                "被移除的 member 1001 应被软删除");
        assertEquals(
                4,
                countOf(
                        "select count(*) from member_contact where member_id = 1001 and deleted = 1"),
                "member 1001 的 4 条 member_contact 应被级联软删除");
        assertEquals(
                0,
                countOf(
                        "select count(*) from member_contact where member_id = 1001 and deleted = 0"));
    }

    // ==================================================================
    // H7 · 版本派生（fork）：整树克隆 + version_no 递增 + 基准不变
    // ==================================================================

    @Test
    @DisplayName("H7 版本派生：fork 出全新整树（version_no 3→4），基准单据与子表全部不受影响")
    void forkClonesWholeTreeAndBumpsVersion() {
        // 补丁只改根行本身，不提交任何子模块。
        //
        // 刻意不构造「只提交部分子行」的补丁：DocumentVersionUtils 的类级 Javadoc（“未在 patch 中提及且未被
        // 删除的原 base 行：原样保留”）与 mergeChildRows 的实现（patch 数组 = 编辑后全量状态，未提及的行剔除）
        // 互相矛盾，属尚未定夺的设计问题。只改根行的补丁走两边都认可的「patch 完全没动该子模块 -> 100% 继承」
        // 分支，因此本用例的断言不依赖那个悬而未决的语义。
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

        // 新版本：补丁生效 + version_no 递增
        assertEquals(
                "石嘴山市盛港煤焦化有限公司1010（V4）",
                String.valueOf(scalar("select company_name from info where id = " + newId)));
        assertEquals(4, (int) asLong(scalar("select version_no from info where id = " + newId)));
        assertEquals(
                "CR202609110137000100",
                String.valueOf(scalar("select business_no from info where id = " + newId)),
                "派生单据沿用同一 business_no");

        // 整树克隆：子表数量与基准一致，且外键全部指向新根
        assertEquals(16, countOf("select count(*) from member where company_id = " + newId));
        assertEquals(1, countOf("select count(*) from basic_info where company_id = " + newId));
        assertEquals(1, countOf("select count(*) from address where company_id = " + newId));
        assertEquals(1, countOf("select count(*) from credit_rating where company_id = " + newId));
        assertEquals(
                2, countOf("select count(*) from company_contact where company_id = " + newId));
        assertEquals(
                2, countOf("select count(*) from financial_account where company_id = " + newId));
        assertEquals(
                9, countOf("select count(*) from digital_account where company_id = " + newId));
        assertEquals(
                0,
                countOf(
                        "select count(*) from member where company_id = "
                                + newId
                                + " and id <= 2008"),
                "克隆行必须是全新主键，不得复用基准行 ID");

        // 孙表跟着新 member 走（补丁未动子表 -> 原 4 条原样继承）
        Long newMemberId =
                asLong(
                        scalar(
                                "select id from member where company_id = "
                                        + newId
                                        + " and code = 'W12234'"));
        assertNotNull(newMemberId);
        assertEquals(
                4,
                countOf("select count(*) from member_contact where member_id = " + newMemberId),
                "补丁未动 member_contact，4 条原样继承");

        // 基准单据完全不受影响
        assertEquals(
                "石嘴山市盛港煤焦化有限公司1010",
                String.valueOf(scalar("select company_name from info where id = 100")));
        assertEquals(3, (int) asLong(scalar("select version_no from info where id = 100")));
        assertEquals(16, countOf("select count(*) from member where company_id = 100"));
        assertEquals(
                4,
                countOf(
                        "select count(*) from member_contact where member_id = 1001 and deleted = 0"));
    }

    // ==================================================================
    // H8 · 分页 / 排序 / 显式投影
    // ==================================================================

    @Test
    @DisplayName("H8 分页与投影：pageSize 生效、显式投影走列名而非 SELECT *")
    void paginationAndExplicitProjection() {
        DataPage<DataBlock> page1 =
                dataQueryService.queryByTable(
                        TablePageQueryReq.builder().moduleId(M_INFO).pageNo(1).pageSize(2).build());
        assertEquals(3L, page1.getTotal());
        assertEquals(2, page1.getRecords().size(), "pageSize=2 应只返回 2 行");

        DataPage<DataBlock> page2 =
                dataQueryService.queryByTable(
                        TablePageQueryReq.builder().moduleId(M_INFO).pageNo(2).pageSize(2).build());
        assertEquals(1, page2.getRecords().size());
        // 默认按 info.id DESC 排序
        assertTrue(
                asLong(page1.getRecords().get(0).getValue("info", "id"))
                        > asLong(page2.getRecords().get(0).getValue("info", "id")));

        recording.sqls.clear();
        DataPage<DataBlock> projected =
                dataQueryService.queryByTable(
                        TablePageQueryReq.builder()
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
        // 未投影子模块 ⇒ 行上不挂 children
        DataRow row = projected.getRecords().get(0);
        assertTrue(row.getChildren() == null || row.getChildren().isEmpty(), "只投影 info 列时不应展开子树");
    }

    // ==================================================================
    // H9 · 租户隔离
    // ==================================================================

    @Test
    @DisplayName("H9 租户隔离：切换到无数据的主体后查询为空，且 SQL 带 subject_id 谓词")
    void tenantIsolationBySubjectId() {
        AppContext.setContext("company", 99L);

        DataPage<DataBlock> page =
                dataQueryService.queryByTable(
                        TablePageQueryReq.builder()
                                .moduleId(M_INFO)
                                .pageNo(1)
                                .pageSize(10)
                                .build());

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
    // H10 · 跨模块层级去重候选值查询 (Options)
    // ==================================================================

    @Test
    @DisplayName("H10 跨层级下拉选项：根模块下查孙表 member_contact.contact_type，自动沿层级 INNER JOIN 回根表且应用祖先条件")
    void optionsAcrossModuleLevelsGeneratesInnerJoins() {
        recording.sqls.clear();
        TableOptionReq req =
                TableOptionReq.builder()
                        .moduleId(M_INFO) // 1001
                        .tableName("member_contact")
                        .columnName("contact_value")
                        .filters(
                                List.of(
                                        PhysicalFilterItem.builder()
                                                .moduleId(M_INFO)
                                                .tableName("info")
                                                .columnName("id")
                                                .operator("EQ")
                                                .value(100L)
                                                .build()))
                        .build();

        List<DataOptionResp> options = dataQueryService.getOptionsByTable(req);
        assertNotNull(options);
        assertFalse(options.isEmpty(), "种子数据 100 下应存在 member_contact.contact_value");

        String sql =
                recording.sqls.stream()
                        .map(s -> s.replace('\n', ' ').replaceAll("\\s+", " ").toLowerCase())
                        .filter(s -> s.contains("select distinct"))
                        .findFirst()
                        .orElseThrow();

        // 验证 FROM member_contact，并依次 INNER JOIN member 和 info
        assertTrue(sql.contains("from `member_contact`"), "FROM 表必须是目标表 member_contact，实际=" + sql);
        assertTrue(sql.contains("join `member`"), "必须连接父表 member，实际=" + sql);
        assertTrue(sql.contains("join `info`"), "必须连接根表 info，实际=" + sql);
        assertTrue(sql.contains("`info`.`id` = ?"), "必须应用根表上的过滤条件，实际=" + sql);
    }

    @Test
    @DisplayName("H11 跨虚拟模块下拉选项：根模块下查跨虚拟模块孙表 company_contact.contact_type，正确 JOIN 根表且不产生歧义列名")
    void optionsAcrossVirtualModuleGeneratesCorrectJoin() {
        recording.sqls.clear();
        TableOptionReq req =
                TableOptionReq.builder()
                        .moduleId(M_INFO) // 1001
                        .tableName("company_contact")
                        .columnName("contact_type")
                        .filters(
                                List.of(
                                        PhysicalFilterItem.builder()
                                                .moduleId(M_INFO)
                                                .tableName("info")
                                                .columnName("id")
                                                .operator("IN")
                                                .value(List.of(100L))
                                                .build()))
                        .build();

        List<DataOptionResp> options = dataQueryService.getOptionsByTable(req);
        assertNotNull(options);

        String sql =
                recording.sqls.stream()
                        .map(s -> s.replace('\n', ' ').replaceAll("\\s+", " ").toLowerCase())
                        .filter(s -> s.contains("select distinct"))
                        .findFirst()
                        .orElseThrow();

        // 验证 FROM company_contact，并跨过虚拟模块 1020 直接 INNER JOIN info
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
        TableOptionReq req =
                TableOptionReq.builder()
                        .moduleId(M_INFO) // 1001
                        .tableName("company_contact")
                        .columnName("contact_type")
                        .filters(
                                List.of(
                                        PhysicalFilterItem.builder()
                                                .moduleId(1005L)
                                                .tableName("address")
                                                .columnName("comm_country")
                                                .operator("IN")
                                                .value(List.of("中国"))
                                                .build()))
                        .build();

        List<DataOptionResp> options = dataQueryService.getOptionsByTable(req);
        assertNotNull(options);
        assertFalse(options.isEmpty());

        String sql =
                recording.sqls.stream()
                        .map(s -> s.replace('\n', ' ').replaceAll("\\s+", " ").toLowerCase())
                        .filter(s -> s.contains("select distinct"))
                        .findFirst()
                        .orElseThrow();

        // 验证 FROM company_contact，并通过 info 节点保留了对 address 的 EXISTS 约束
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
    // fixture · 真实元数据（config_center · project_no='company'）
    // ==================================================================

    private static final Map<Long, ModuleInfo> MODS = new LinkedHashMap<>();
    private static final Map<Long, ModuleFieldDTO> FMAP = new LinkedHashMap<>();
    private static final Map<Long, Long> F2M = new LinkedHashMap<>();
    private static final Map<Long, List<Long>> P2C = new LinkedHashMap<>();
    private static final ProjectTableRelationsDTO PROJ;

    static {
        // ---- sys_module（真实 module_id / parent_id / primary_table）----
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
        add(1021L, "场地产线管理", "venue_line", 0L);
        add(1032L, "场地信息", "venue", 1021L);
        add(1033L, "产线信息", "line", 1032L);
        // ---- sys_module_field（真实 field_id / module_id / table_name / column_name）----
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

        // module 1021
        f(10086L, 1021L, "venue_line", "id");
        f(10087L, 1021L, "venue_line", "company_name");
        f(10168L, 1021L, "venue_line", "version_no");
        f(10169L, 1021L, "venue_line", "module_id");

        // module 1032
        f(10156L, 1032L, "venue", "id");
        f(10157L, 1032L, "venue", "name");
        f(10165L, 1032L, "venue", "address");
        f(10166L, 1032L, "venue", "description");
        f(10167L, 1032L, "venue", "image");

        // module 1033
        f(10158L, 1033L, "line", "id");
        f(10159L, 1033L, "line", "venue_id");
        f(10160L, 1033L, "line", "name");
        f(10162L, 1033L, "line", "person");
        f(10163L, 1033L, "line", "phone");
        f(10164L, 1033L, "line", "description");

        P2C.put(M_INFO, List.of(M_BASIC, M_MEMBER, M_FINANCE, M_CREDIT, M_VIRTUAL_CONTACT));
        P2C.put(M_MEMBER, List.of(M_CONTACT));
        P2C.put(M_FINANCE, List.of(M_FIN_ACCOUNT, M_DIGITAL_ACCOUNT));
        P2C.put(M_VIRTUAL_CONTACT, List.of(M_ADDRESS, M_COMPANY_CONTACT));
        P2C.put(M_VENUE_LINE, List.of(1032L));
        P2C.put(1032L, List.of(1033L));

        // ---- sys_table_relation（真实 main_table/main_field/join_table/join_field/relation_type）----
        List<TableRelationDTO> relations =
                new ArrayList<>(
                        List.of(
                                rel("info", "id", "basic_info", "company_id", "1:1"),
                                rel("info", "id", "member", "company_id", "1:N"),
                                rel("info", "id", "address", "company_id", "1:1"),
                                rel("info", "id", "company_contact", "company_id", "1:N"),
                                rel("info", "id", "financial_account", "company_id", "1:N"),
                                rel("info", "id", "digital_account", "company_id", "1:N"),
                                rel("member", "id", "member_contact", "member_id", "1:N"),
                                rel("info", "id", "credit_rating", "company_id", "1:1"),
                                rel("venue_line", "id", "venue", "venue_line_id", "1:N"),
                                rel("venue", "id", "line", "venue_id", "1:N")));

        Map<String, List<TableRelationDTO>> forward = new LinkedHashMap<>();
        Map<String, List<TableRelationDTO>> reverse = new LinkedHashMap<>();
        for (TableRelationDTO r : relations) {
            forward.computeIfAbsent(r.getMainTable().toLowerCase(), k -> new ArrayList<>()).add(r);
            reverse.computeIfAbsent(r.getJoinTable().toLowerCase(), k -> new ArrayList<>()).add(r);
        }
        PROJ =
                ProjectTableRelationsDTO.builder()
                        .projectNo("company")
                        .allRelations(relations)
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

    /** 按请求的根模块动态裁剪子树（固定树会掩盖「根模块之外的模块不该出现」这类问题）。 */
    private static ModuleTreeDTO treeFor(long root) {
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

    private static PlanMeta metaFor(long root) {
        return new PlanMeta(treeFor(root), PROJ);
    }

    private static ConfigApi buildConfigApi() {
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

    // ==================================================================
    // fixture · 真实 DDL（company 库 12 张物理表）
    // ==================================================================

    private void initSchema() throws SQLException {
        try (Connection conn = rawDataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(DDL);
        }
    }

    // ==================================================================
    // fixture · 真实种子数据（company 库 info.id = 100 / 146 / 148）
    // ==================================================================

    private void initSeed() throws SQLException {
        try (Connection conn = rawDataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(SEED);
        }
    }

    private static final String DDL =
            """
CREATE TABLE `info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `business_no` varchar(64) DEFAULT NULL COMMENT '业务编号',
  `version_no` int NOT NULL DEFAULT '1' COMMENT '版本号',
  `subject_id` bigint NOT NULL DEFAULT '0' COMMENT '主体ID',
  `company_name` varchar(150) NOT NULL COMMENT '外部对象全称',
  `parent_id` bigint DEFAULT NULL COMMENT '外部对象父级全称对应ID（自关联母集团，空为顶级）',
  `nature` varchar(4000) DEFAULT NULL COMMENT '对象性质（企业/个体工商户/社会团体/政府机关等）',
  `reg_status` varchar(4000) DEFAULT NULL COMMENT '登记状态（存续/在业/注销/吊销）',
  `cooperation_types` varchar(4000) DEFAULT NULL COMMENT '合作类型，JSON数组，如: ["客户", "供应商"]',
  `industry_tags` varchar(4000) DEFAULT NULL COMMENT '行业标签，JSON数组，如: ["兰炭", "焦化"]',
  `industry_types` varchar(4000) DEFAULT NULL COMMENT '行业类型，JSON数组，如: ["纺织业", "食品制造业"]',
  `logo_url` varchar(500) DEFAULT NULL COMMENT '企业LOGO图片地址',
  `gender` varchar(255) DEFAULT NULL COMMENT '性别，对象性质为个人的时候填写',
  `business_status` int DEFAULT NULL COMMENT '业务状态',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `created_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  KEY `info_idx_company_name` (`company_name`),
  KEY `info_idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部对象主档表（顶部公共头核心信息）';

CREATE TABLE `basic_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `business_no` varchar(64) DEFAULT NULL COMMENT '数据编码',
  `version_no` int NOT NULL DEFAULT '1' COMMENT '版本号',
  `subject_id` bigint NOT NULL DEFAULT '0' COMMENT '主体ID',
  `company_id` bigint NOT NULL COMMENT '所属公司ID（关联 info.id）',
  `uscc` varchar(32) DEFAULT NULL COMMENT '统一社会信用代码',
  `short_name` varchar(100) DEFAULT NULL COMMENT '外部对象简称',
  `en_short_name` varchar(100) DEFAULT NULL COMMENT '外部对象英文简称',
  `former_name` varchar(150) DEFAULT NULL COMMENT '外部对象曾用名全称',
  `former_short_name` varchar(100) DEFAULT NULL COMMENT '外部对象曾用名简称',
  `en_former_name` varchar(150) DEFAULT NULL COMMENT '外部对象英文曾用名全称',
  `en_former_short_name` varchar(100) DEFAULT NULL COMMENT '外部对象英文曾用名简称',
  `taxpayer_qualification` varchar(4000) DEFAULT NULL COMMENT '纳税人资质（如: 一般纳税人/小规模纳税人）',
  `website` varchar(255) DEFAULT NULL COMMENT '公司网址',
  `legal_person` varchar(64) DEFAULT NULL COMMENT '法定代表人/负责人',
  `economic_nature` varchar(4000) DEFAULT NULL COMMENT '经济性质（如: 国有企业/民营企业）',
  `insured_count` int DEFAULT '0' COMMENT '参保人数',
  `reg_capital` decimal(15,2) DEFAULT '0.00' COMMENT '注册资本（万元）',
  `paid_in_capital` decimal(15,2) DEFAULT '0.00' COMMENT '实缴资本（万元）',
  `company_type` varchar(4000) DEFAULT NULL COMMENT '公司类型（如: 有限责任公司）',
  `reg_date` date DEFAULT NULL COMMENT '注册登记日期',
  `business_term_start` date DEFAULT NULL COMMENT '营业期限开始日期',
  `business_term_end` date DEFAULT NULL COMMENT '营业期限截止日期',
  `is_long_term` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '是否长期（1:是, 0:否）',
  `id_card_front_url` varchar(500) DEFAULT NULL COMMENT '身份证照片-头像面',
  `id_card_back_url` varchar(500) DEFAULT NULL COMMENT '身份证照片-国徽面',
  `license_url` varchar(500) DEFAULT NULL COMMENT '主体登记证书（营业执照）图片路径',
  `business_status` int DEFAULT NULL COMMENT '数据状态',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `created_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `basic_info_uk_company_id` (`company_id`),
  KEY `basic_info_idx_uscc` (`uscc`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部对象工商与法定资质表（Tab 1 基本信息）';

CREATE TABLE `member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `business_no` varchar(64) DEFAULT NULL COMMENT '人员业务唯一标识号',
  `version_no` int NOT NULL DEFAULT '1' COMMENT '单据快照版本号',
  `subject_id` bigint NOT NULL DEFAULT '0' COMMENT '主体ID',
  `company_id` bigint DEFAULT NULL COMMENT '所属公司ID（关联 info.id）',
  `type` varchar(16) DEFAULT NULL COMMENT '节点类型（DEPT:部门, EMPLOYEE:员工）',
  `parent_id` bigint DEFAULT NULL COMMENT '父节点ID（员工指向部门，子部门指向上级部门，顶级部门为NULL）',
  `member_name` varchar(100) DEFAULT NULL COMMENT '成员名称',
  `name` varchar(100) DEFAULT NULL COMMENT '部门名称 或 员工姓名',
  `code` varchar(64) DEFAULT NULL COMMENT '外部部门号（如: W12234）或工号',
  `leader_id` bigint DEFAULT NULL COMMENT '部门负责人ID（type=DEPT时有效，关联本表员工ID）',
  `position` varchar(100) DEFAULT NULL COMMENT '职务/岗位（type=EMPLOYEE时有效）',
  `sort_order` int DEFAULT '0' COMMENT '展示排序序号',
  `status` varchar(4000) DEFAULT NULL COMMENT '状态（1:在职/启用, 0:离职/停用）',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `created_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `member_uk_member_biz_ver` (`business_no`,`version_no`,`deleted`),
  KEY `member_idx_company_parent` (`company_id`,`parent_id`),
  KEY `member_idx_company_type` (`company_id`,`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部对象组织与人员树形表（Tab 2 人员构成）';

CREATE TABLE `member_contact` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `business_no` varchar(64) DEFAULT NULL COMMENT '人员联系方式业务编号',
  `version_no` int NOT NULL DEFAULT '1' COMMENT '快照版本号',
  `subject_id` bigint NOT NULL DEFAULT '0' COMMENT '主体ID',
  `member_id` bigint DEFAULT NULL COMMENT '所属员工ID（关联 member.id，且必须为 type=EMPLOYEE）',
  `contact_type` varchar(4000) DEFAULT NULL COMMENT '联系方式类型（手机/座机/工作邮箱等）',
  `contact_value` varchar(100) DEFAULT NULL COMMENT '具体电话号码或邮箱地址',
  `is_primary` tinyint unsigned DEFAULT '0' COMMENT '是否为员工本人的默认联系方式（1:是, 0:否）',
  `sort_order` int DEFAULT '0' COMMENT '排序序号（用于 Tab 3 表格序号 1, 2...）',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `created_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  KEY `member_contact_idx_member_id` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人员联系方式表（兼具个人通讯录与公司对外联络窗口职责）';

CREATE TABLE `address` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `business_no` varchar(64) DEFAULT NULL COMMENT '业务单据编号',
  `version_no` int NOT NULL DEFAULT '1' COMMENT '快照版本号',
  `subject_id` bigint NOT NULL DEFAULT '0' COMMENT '主体ID',
  `company_id` bigint NOT NULL COMMENT '所属公司ID（关联 info.id）',
  `comm_country` varchar(64) DEFAULT '中国' COMMENT '通讯地址-国家',
  `comm_region` varchar(128) DEFAULT NULL COMMENT '通讯地址-省/市/区',
  `comm_detail` varchar(255) DEFAULT NULL COMMENT '通讯地址-详细地址',
  `comm_postcode` varchar(16) DEFAULT NULL COMMENT '通讯地址-邮政编码',
  `reg_country` varchar(64) DEFAULT '中国' COMMENT '注册地址-国家',
  `reg_region` varchar(128) DEFAULT NULL COMMENT '注册地址-省/市/区',
  `reg_detail` varchar(255) DEFAULT NULL COMMENT '注册地址-详细地址',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `created_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `address_uk_company_id` (`company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部对象地址信息表（Tab 3 地址部分）';

CREATE TABLE `company_contact` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `business_no` varchar(64) DEFAULT NULL COMMENT '业务编号',
  `version_no` int NOT NULL DEFAULT '1' COMMENT '快照版本号',
  `subject_id` bigint NOT NULL DEFAULT '0' COMMENT '主体ID',
  `company_id` bigint NOT NULL COMMENT '所属公司ID（关联 info.id）',
  `contact_name` varchar(100) DEFAULT NULL COMMENT '公司联系人',
  `contact_type` varchar(4000) DEFAULT NULL COMMENT '联系方式类型（电话/座机/邮箱/传真等）',
  `contact_value` varchar(100) DEFAULT NULL COMMENT '具体联系号码或邮箱地址',
  `sort_order` int DEFAULT '0' COMMENT '排序序号（用于前端表格行序号）',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `created_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `company_contact_uk_contact_biz_ver` (`business_no`,`version_no`,`deleted`),
  KEY `company_contact_idx_company_id` (`company_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公司联系方式表（对外联络窗口与公开渠道明细）';

CREATE TABLE `financial_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `business_no` varchar(64) DEFAULT NULL COMMENT '业务单号/统一业务编号',
  `version_no` int NOT NULL DEFAULT '1' COMMENT '版本号',
  `subject_id` bigint NOT NULL DEFAULT '0' COMMENT '主体ID',
  `company_id` bigint NOT NULL COMMENT '所属公司ID（关联 info.id）',
  `bank_head` varchar(4000) DEFAULT NULL COMMENT '行别（如: 中国建设银行）',
  `bank_branch` varchar(200) DEFAULT NULL COMMENT '开户行（支行全称）',
  `account_name` varchar(150) DEFAULT NULL COMMENT '账户名（非政府机关时前端置灰只读，并默认带出外部对象全称）',
  `account_no` varchar(100) NOT NULL COMMENT '账号/银行卡号',
  `account_status` varchar(4000) DEFAULT NULL COMMENT '账号状态（NORMAL:正常, FROZEN:冻结, CANCELLED:注销）',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '前端表格排序序号',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `created_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  KEY `financial_account_idx_company` (`company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部对象银行卡结算账户表（Tab 4-1）';

CREATE TABLE `digital_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `business_no` varchar(64) DEFAULT NULL COMMENT '业务单号/统一业务编号',
  `version_no` int NOT NULL DEFAULT '1' COMMENT '版本号',
  `subject_id` bigint NOT NULL DEFAULT '0' COMMENT '主体ID',
  `company_id` bigint NOT NULL COMMENT '所属公司ID（关联 info.id）',
  `platform` varchar(4000) DEFAULT NULL COMMENT '平台（如: 支付宝、微信）',
  `real_name` varchar(64) DEFAULT NULL COMMENT '真实姓名/实名人',
  `account_no` varchar(100) NOT NULL COMMENT '电子账号',
  `account_status` varchar(4000) DEFAULT NULL COMMENT '账号状态（NORMAL:正常, FROZEN:冻结, CANCELLED:注销）',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '前端表格排序序号',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `created_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  KEY `digital_account_idx_company` (`company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部对象电子结算账户表（Tab 4-2）';

CREATE TABLE `credit_rating` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `business_no` varchar(64) DEFAULT NULL COMMENT '评级业务唯一标识号',
  `version_no` int NOT NULL DEFAULT '1' COMMENT '单据快照版本号',
  `subject_id` bigint NOT NULL DEFAULT '0' COMMENT '主体ID',
  `company_id` bigint NOT NULL COMMENT '所属公司ID（关联 info.id）',
  `internal_level` varchar(4000) DEFAULT NULL COMMENT '内部信用等级',
  `qcc_score` varchar(32) DEFAULT NULL COMMENT '企查查评分',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `created_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `credit_rating_uk_company_id` (`company_id`),
  UNIQUE KEY `credit_rating_uk_credit_biz_ver` (`business_no`,`version_no`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部对象信用等级表（Tab 5 信用等级）';

CREATE TABLE `venue_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `company_name` varchar(4000) DEFAULT NULL COMMENT '外部对象名称',
  `subject_id` bigint NOT NULL COMMENT '主体id',
  `business_no` varchar(255) DEFAULT NULL COMMENT '业务编码',
  `business_status` int DEFAULT NULL COMMENT '业务状态',
  `version_no` int DEFAULT NULL COMMENT '版本号',
  `module_id` bigint DEFAULT NULL COMMENT '模块id',
  `created_by` bigint DEFAULT NULL COMMENT '创建人id',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人id',
  `created_date` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_date` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint DEFAULT NULL COMMENT '逻辑删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='场地产线单';

CREATE TABLE `venue` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `venue_line_id` bigint DEFAULT NULL COMMENT '场地产线登记单id',
  `name` varchar(255) DEFAULT NULL COMMENT '场地名称',
  `address` varchar(4000) DEFAULT NULL COMMENT '场地位置',
  `description` varchar(255) DEFAULT NULL COMMENT '场地描述',
  `image` varchar(4000) DEFAULT NULL COMMENT '场地图片',
  `subject_id` bigint NOT NULL COMMENT '主体id',
  `business_no` varchar(255) DEFAULT NULL COMMENT '业务编码',
  `business_status` int DEFAULT NULL COMMENT '业务状态',
  `version_no` int DEFAULT NULL COMMENT '版本号',
  `module_id` bigint DEFAULT NULL COMMENT '模块id',
  `created_by` bigint DEFAULT NULL COMMENT '创建人id',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人id',
  `created_date` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_date` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint DEFAULT NULL COMMENT '逻辑删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='场地表';

CREATE TABLE `line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `venue_id` bigint DEFAULT NULL COMMENT '场地id',
  `name` varchar(255) DEFAULT NULL COMMENT '产线名称',
  `person` varchar(4000) DEFAULT NULL COMMENT '产线负责人',
  `phone` varchar(15) DEFAULT NULL COMMENT '联系电话',
  `description` varchar(255) DEFAULT NULL COMMENT '产线描述',
  `subject_id` bigint NOT NULL COMMENT '主体id',
  `business_no` varchar(255) DEFAULT NULL COMMENT '业务编码',
  `business_status` int DEFAULT NULL COMMENT '业务状态',
  `version_no` int DEFAULT NULL COMMENT '版本号',
  `module_id` bigint DEFAULT NULL COMMENT '模块id',
  `created_by` bigint DEFAULT NULL COMMENT '创建人id',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人id',
  `created_date` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_date` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint DEFAULT NULL COMMENT '逻辑删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产线表';
""";

    private static final String SEED =
            """
-- info.id=100 (石嘴山市盛港煤焦化有限公司1010)
INSERT INTO info (id, business_no, version_no, subject_id, company_name, parent_id, nature, reg_status, cooperation_types, industry_tags, industry_types, logo_url, gender, business_status, created_by, updated_by, created_date, updated_date, deleted) VALUES (100, 'CR202609110137000100', 3, 0, '石嘴山市盛港煤焦化有限公司1010', 1, '[{"word_describe":"企业","word_id":478,"alias_value_title":"企业","paths":[{"word_describe":"企业","word_id":478,"alias_value_title":"企业"}]}]', '[{"word_describe":"法院裁定受理破产 / 破产清算、重整、和解程序中。","word_id":454,"alias_value_title":"破产","paths":[{"word_describe":"法院裁定受理破产 / 破产清算、重整、和解程序中。","word_id":454,"alias_value_title":"破产"}]}]', '[{"word_describe":"客户","word_id":487,"alias_value_title":"客户","paths":[{"word_describe":"客户","word_id":487,"alias_value_title":"客户"}]}]', '[{"word_describe":"兰炭","word_id":490,"alias_value_title":"兰炭","paths":[{"word_describe":"兰炭","word_id":490,"alias_value_title":"兰炭"}]}]', '[{"word_describe":"纺织业","word_id":494,"alias_value_title":"纺织业","paths":[{"word_describe":"制造业","word_id":492,"alias_value_title":"制造业"},{"word_describe":"纺织业","word_id":494,"alias_value_title":"纺织业"}]}]', 'https://oss.example.com/company/logo/sgmjh.png', NULL, 10, 295, 298, '2026-09-11 01:37:22', '2026-10-10 15:27:28', 0);
INSERT INTO basic_info (id, business_no, version_no, subject_id, company_id, uscc, short_name, en_short_name, former_name, former_short_name, en_former_name, en_former_short_name, taxpayer_qualification, website, legal_person, economic_nature, insured_count, reg_capital, paid_in_capital, company_type, reg_date, business_term_start, business_term_end, is_long_term, id_card_front_url, id_card_back_url, license_url, business_status, created_by, updated_by, created_date, updated_date, deleted) VALUES (1, 'CBI202609110137000001', 1, 0, 100, '9153312343631123J', '盛港焦化3', 'SG Coking', NULL, NULL, NULL, NULL, '["一般纳税人"]', 'https://www.baidu.com', '李雨桐', '[{"word_describe":"国有企业","word_id":498,"alias_value_title":"国有企业","paths":[{"word_describe":"国有企业","word_id":498,"alias_value_title":"国有企业"}]}]', 500, '5000.00', '3000.00', '["有限责任公司"]', '2014-04-28', '2014-04-28', '2024-04-28', 0, '[null,""]', 'https://oss.example.com/idcard/back.jpg', 'https://oss.example.com/license/cert.jpg', NULL, NULL, 294, '2026-09-11 01:37:22', '2026-10-10 01:40:21', 0);
INSERT INTO address (id, business_no, version_no, subject_id, company_id, comm_country, comm_region, comm_detail, comm_postcode, reg_country, reg_region, reg_detail, created_by, updated_by, created_date, updated_date, deleted) VALUES (1, 'CCA202609110137000001', 1, 0, 100, '中国', '宁夏回族自治区/石嘴山市/大武口区', '长胜南街工业园区88号', '753000', '中国', '宁夏回族自治区/石嘴山市/大武口区', '长胜南街工业园区88号办公楼301', NULL, 295, '2026-09-11 01:37:22', '2026-10-10 01:40:23', 0);
INSERT INTO credit_rating (id, business_no, version_no, subject_id, company_id, internal_level, qcc_score, created_by, updated_by, created_date, updated_date, deleted) VALUES (1, 'CC202609110137000001', 1, 0, 100, '[{"word_describe":"一般","word_id":513,"alias_value_title":"一般","paths":[{"word_describe":"一般","word_id":513,"alias_value_title":"一般"}]}]', '85分', NULL, 294, '2026-09-11 01:37:22', '2026-10-10 01:40:26', 0);
INSERT INTO company_contact (id, business_no, version_no, subject_id, company_id, contact_name, contact_type, contact_value, sort_order, created_by, updated_by, created_date, updated_date, deleted) VALUES (1, 'CCB202609110137000001', 1, 0, 100, NULL, '[{"word_describe":"","word_id":59,"alias_value_title":"手机","paths":[{"word_describe":"","word_id":59,"alias_value_title":"手机"}]}]', '13585855898', 1, NULL, 295, '2026-09-11 01:37:22', '2026-10-10 01:40:28', 0);
INSERT INTO company_contact (id, business_no, version_no, subject_id, company_id, contact_name, contact_type, contact_value, sort_order, created_by, updated_by, created_date, updated_date, deleted) VALUES (2, 'CCB202609110137000002', 1, 0, 100, NULL, '[{"word_describe":"座机","word_id":517,"alias_value_title":"座机","paths":[{"word_describe":"座机","word_id":517,"alias_value_title":"座机"}]}]', '0710-3255895', 2, NULL, 295, '2026-09-11 01:37:22', '2026-10-10 01:40:28', 0);
INSERT INTO financial_account (id, business_no, version_no, subject_id, company_id, bank_head, bank_branch, account_name, account_no, account_status, sort_order, created_by, updated_by, created_date, updated_date, deleted) VALUES (1, 'CBA202609110137000001', 1, 0, 100, '[{"word_describe":"","word_id":130,"alias_value_title":"中国建设银行","paths":[{"word_describe":"","word_id":130,"alias_value_title":"中国建设银行"}]}]', '中国工商银行股份有限公司苏州工业园区支行', '石嘴山市盛港煤焦化有限公司', '62001646341059667358', '[{"word_describe":"正常","word_id":510,"alias_value_title":"正常","paths":[{"word_describe":"正常","word_id":510,"alias_value_title":"正常"}]}]', 1, NULL, 294, '2026-09-11 01:37:22', '2026-10-10 01:40:36', 0);
INSERT INTO financial_account (id, business_no, version_no, subject_id, company_id, bank_head, bank_branch, account_name, account_no, account_status, sort_order, created_by, updated_by, created_date, updated_date, deleted) VALUES (2, 'CBA202609110137000002', 1, 0, 100, '[{"word_describe":"","word_id":128,"alias_value_title":"中国农业银行","paths":[{"word_describe":"","word_id":128,"alias_value_title":"中国农业银行"}]}]', '中国农业银行石嘴山分行营业部', '石嘴山市盛港煤焦化有限公司', '6228480402568912345', '[{"word_describe":"冻结","word_id":511,"alias_value_title":"冻结","paths":[{"word_describe":"冻结","word_id":511,"alias_value_title":"冻结"}]}]', 2, NULL, 294, '2026-09-11 01:37:22', '2026-10-10 01:40:36', 0);
INSERT INTO digital_account (id, business_no, version_no, subject_id, company_id, platform, real_name, account_no, account_status, sort_order, created_by, updated_by, created_date, updated_date, deleted) VALUES (3, 'CDA202609110137000003', 1, 0, 100, '[{"word_describe":"支付宝","word_id":515}]', '李玉涛', '13598888888', '[{"word_describe":"正常","word_id":510}]', 1, NULL, 294, '2026-09-11 01:37:22', '2026-10-10 01:40:38', 0);
INSERT INTO digital_account (id, business_no, version_no, subject_id, company_id, platform, real_name, account_no, account_status, sort_order, created_by, updated_by, created_date, updated_date, deleted) VALUES (4, 'CDA202609110137000004', 1, 0, 100, '[{"word_describe":"支付宝","word_id":515}]', '', '百果园3', '[{"word_describe":"正常","word_id":510}]', 2, NULL, 294, '2026-09-11 01:37:22', '2026-10-10 01:40:38', 0);
INSERT INTO digital_account (id, business_no, version_no, subject_id, company_id, platform, real_name, account_no, account_status, sort_order, created_by, updated_by, created_date, updated_date, deleted) VALUES (5, 'CDA202609180909000005', 1, 0, 100, '[{"word_describe":"支付宝","word_id":515}]', '李玉涛9209200', '13598888888A', '[{"word_describe":"正常","word_id":510}]', 0, NULL, 294, '2026-09-18 09:09:23', '2026-10-10 01:40:38', 0);
INSERT INTO digital_account (id, business_no, version_no, subject_id, company_id, platform, real_name, account_no, account_status, sort_order, created_by, updated_by, created_date, updated_date, deleted) VALUES (6, 'CDA202609180909000006', 1, 0, 100, '[{"word_describe":"正常","word_id":510}]', '', '百果园9209200S', '[{"word_describe":"正常","word_id":510}]', 0, NULL, 294, '2026-09-18 09:09:23', '2026-10-10 01:40:38', 0);
INSERT INTO digital_account (id, business_no, version_no, subject_id, company_id, platform, real_name, account_no, account_status, sort_order, created_by, updated_by, created_date, updated_date, deleted) VALUES (7, 'CDA202609210830000007', 1, 0, 100, '[{"word_describe":"支付宝","word_id":515}]', '李玉涛9209200', '13598888888A', '[{"word_describe":"正常","word_id":510}]', 0, NULL, 294, '2026-09-21 08:30:45', '2026-10-10 01:40:38', 0);
INSERT INTO digital_account (id, business_no, version_no, subject_id, company_id, platform, real_name, account_no, account_status, sort_order, created_by, updated_by, created_date, updated_date, deleted) VALUES (8, 'CDA202609210830000008', 1, 0, 100, '[{"word_describe":"正常","word_id":510}]', '', '百果园9209200S', '[{"word_describe":"正常","word_id":510}]', 0, NULL, 294, '2026-09-21 08:30:45', '2026-10-10 01:40:38', 0);
INSERT INTO digital_account (id, business_no, version_no, subject_id, company_id, platform, real_name, account_no, account_status, sort_order, created_by, updated_by, created_date, updated_date, deleted) VALUES (9, 'CDA202609210932000009', 1, 0, 100, '[{"word_describe":"正常","word_id":510}]', '李玉涛9209200', '13598888888A', '[{"word_describe":"正常","word_id":510}]', 0, NULL, 294, '2026-09-21 09:32:25', '2026-10-10 01:40:38', 0);
INSERT INTO digital_account (id, business_no, version_no, subject_id, company_id, platform, real_name, account_no, account_status, sort_order, created_by, updated_by, created_date, updated_date, deleted) VALUES (10, 'CDA202609210932000010', 1, 0, 100, '[{"word_describe":"正常","word_id":510}]', '', '百果园9209200S', '[{"word_describe":"正常","word_id":510}]', 0, NULL, 294, '2026-09-21 09:32:25', '2026-10-10 01:40:38', 0);
INSERT INTO digital_account (id, business_no, version_no, subject_id, company_id, platform, real_name, account_no, account_status, sort_order, created_by, updated_by, created_date, updated_date, deleted) VALUES (11, 'CDA202610081753000011', 1, 0, 100, '[{"word_describe":"支付宝","word_id":515}]', NULL, 'ZFB001', '[{"word_describe":"正常","word_id":510}]', 0, 295, 294, '2026-10-08 17:53:31', '2026-10-10 01:40:38', 0);
INSERT INTO member (id, business_no, version_no, subject_id, company_id, type, parent_id, member_name, name, code, leader_id, position, sort_order, status, created_by, updated_by, created_date, updated_date, deleted) VALUES (1001, 'CM202609110137001001', 1, 0, 100, 'DEPT', 0, '经理办', '经理办', 'W12234', 2004, NULL, 1, NULL, NULL, NULL, '2026-09-11 01:37:22', '2026-10-10 01:40:31', 0);
INSERT INTO member (id, business_no, version_no, subject_id, company_id, type, parent_id, member_name, name, code, leader_id, position, sort_order, status, created_by, updated_by, created_date, updated_date, deleted) VALUES (1002, 'CM202609110137001002', 1, 0, 100, 'DEPT', 0, '财务部', '财务部', 'W12235', NULL, NULL, 2, NULL, NULL, NULL, '2026-09-11 01:37:22', '2026-10-10 01:40:31', 0);
INSERT INTO member (id, business_no, version_no, subject_id, company_id, type, parent_id, member_name, name, code, leader_id, position, sort_order, status, created_by, updated_by, created_date, updated_date, deleted) VALUES (1003, 'CM202609110137001003', 1, 0, 100, 'DEPT', 0, '人力资源部', '人力资源部', 'W12236', NULL, NULL, 3, NULL, NULL, NULL, '2026-09-11 01:37:22', '2026-10-10 01:40:31', 0);
INSERT INTO member (id, business_no, version_no, subject_id, company_id, type, parent_id, member_name, name, code, leader_id, position, sort_order, status, created_by, updated_by, created_date, updated_date, deleted) VALUES (1004, 'CM202609110137001004', 1, 0, 100, 'DEPT', 0, '生产部', '生产部', 'W12237', 2001, NULL, 4, NULL, NULL, NULL, '2026-09-11 01:37:22', '2026-10-10 01:40:31', 0);
INSERT INTO member (id, business_no, version_no, subject_id, company_id, type, parent_id, member_name, name, code, leader_id, position, sort_order, status, created_by, updated_by, created_date, updated_date, deleted) VALUES (1005, 'CM202609110137001005', 1, 0, 100, 'DEPT', 0, '市场部', '市场部', 'W12238', NULL, NULL, 5, NULL, NULL, NULL, '2026-09-11 01:37:22', '2026-10-10 01:40:31', 0);
INSERT INTO member (id, business_no, version_no, subject_id, company_id, type, parent_id, member_name, name, code, leader_id, position, sort_order, status, created_by, updated_by, created_date, updated_date, deleted) VALUES (1006, 'CM202609110137001006', 1, 0, 100, 'DEPT', 0, '品宣部', '品宣部', 'W12239', NULL, NULL, 6, NULL, NULL, NULL, '2026-09-11 01:37:22', '2026-10-10 01:40:31', 0);
INSERT INTO member (id, business_no, version_no, subject_id, company_id, type, parent_id, member_name, name, code, leader_id, position, sort_order, status, created_by, updated_by, created_date, updated_date, deleted) VALUES (1007, 'CM202609110137001007', 1, 0, 100, 'DEPT', 1004, '生产部-一车间', '生产部-一车间', 'W1223701', 2001, NULL, 4, NULL, NULL, NULL, '2026-09-11 01:37:22', '2026-10-10 01:40:31', 0);
INSERT INTO member (id, business_no, version_no, subject_id, company_id, type, parent_id, member_name, name, code, leader_id, position, sort_order, status, created_by, updated_by, created_date, updated_date, deleted) VALUES (1008, 'CM202609110137001008', 1, 0, 100, 'DEPT', 1004, '生产部-二车间', '生产部-二车间', 'W1223702', 2001, NULL, 4, NULL, NULL, NULL, '2026-09-11 01:37:22', '2026-10-10 01:40:31', 0);
INSERT INTO member (id, business_no, version_no, subject_id, company_id, type, parent_id, member_name, name, code, leader_id, position, sort_order, status, created_by, updated_by, created_date, updated_date, deleted) VALUES (2001, 'CM202609110137002001', 1, 0, 100, 'EMPLOYEE', 1004, '李玉林', '李玉林', 'EMP001', NULL, '生产车间主任', 1, NULL, NULL, NULL, '2026-09-11 01:37:22', '2026-10-10 01:40:31', 0);
INSERT INTO member (id, business_no, version_no, subject_id, company_id, type, parent_id, member_name, name, code, leader_id, position, sort_order, status, created_by, updated_by, created_date, updated_date, deleted) VALUES (2002, 'CM202609110137002002', 1, 0, 100, 'EMPLOYEE', 1004, '张山', '张山', 'EMP002', NULL, '生产调度员', 2, NULL, NULL, NULL, '2026-09-11 01:37:22', '2026-10-10 01:40:31', 0);
INSERT INTO member (id, business_no, version_no, subject_id, company_id, type, parent_id, member_name, name, code, leader_id, position, sort_order, status, created_by, updated_by, created_date, updated_date, deleted) VALUES (2003, 'CM202609110137002003', 1, 0, 100, 'EMPLOYEE', 1004, '刘兰', '刘兰', 'EMP003', NULL, '工艺工程师', 3, NULL, NULL, NULL, '2026-09-11 01:37:22', '2026-10-10 01:40:31', 0);
INSERT INTO member (id, business_no, version_no, subject_id, company_id, type, parent_id, member_name, name, code, leader_id, position, sort_order, status, created_by, updated_by, created_date, updated_date, deleted) VALUES (2004, 'CM202609110137002004', 1, 0, 100, 'EMPLOYEE', 1001, '李雨桐', '李雨桐', 'EMP000', NULL, '总经理/法定代表人', 1, NULL, NULL, NULL, '2026-09-11 01:37:22', '2026-10-10 01:40:31', 0);
INSERT INTO member (id, business_no, version_no, subject_id, company_id, type, parent_id, member_name, name, code, leader_id, position, sort_order, status, created_by, updated_by, created_date, updated_date, deleted) VALUES (2005, 'CM202609110137002005', 1, 0, 100, 'EMPLOYEE', 1001, '周玉昆', '周玉昆', 'EMP008', NULL, '商务总监', 2, NULL, NULL, NULL, '2026-09-11 01:37:22', '2026-10-10 01:40:31', 0);
INSERT INTO member (id, business_no, version_no, subject_id, company_id, type, parent_id, member_name, name, code, leader_id, position, sort_order, status, created_by, updated_by, created_date, updated_date, deleted) VALUES (2006, 'CM202609110137002006', 1, 0, 100, 'EMPLOYEE', 1007, '李四', '李四', 'EMP009', NULL, '工长', 2, NULL, NULL, NULL, '2026-09-11 01:37:22', '2026-10-10 01:40:31', 0);
INSERT INTO member (id, business_no, version_no, subject_id, company_id, type, parent_id, member_name, name, code, leader_id, position, sort_order, status, created_by, updated_by, created_date, updated_date, deleted) VALUES (2007, 'CM202609110137002007', 1, 0, 100, 'EMPLOYEE', 1007, '王五', '王五', 'EMP010', NULL, '工人', 2, NULL, NULL, NULL, '2026-09-11 01:37:22', '2026-10-10 01:40:31', 0);
INSERT INTO member (id, business_no, version_no, subject_id, company_id, type, parent_id, member_name, name, code, leader_id, position, sort_order, status, created_by, updated_by, created_date, updated_date, deleted) VALUES (2008, 'CM202609110137002008', 1, 0, 100, 'EMPLOYEE', 1008, '赵六', '赵六', 'EMP010', NULL, '工人', 2, NULL, NULL, NULL, '2026-09-11 01:37:22', '2026-10-10 01:40:31', 0);
INSERT INTO member_contact (id, business_no, version_no, subject_id, member_id, contact_type, contact_value, is_primary, sort_order, created_by, updated_by, created_date, updated_date, deleted) VALUES (1, 'CMC202609110137000001', 1, 0, 1001, NULL, '13585855898', 1, 1, NULL, NULL, '2026-09-11 01:37:22', '2026-10-10 01:40:33', 0);
INSERT INTO member_contact (id, business_no, version_no, subject_id, member_id, contact_type, contact_value, is_primary, sort_order, created_by, updated_by, created_date, updated_date, deleted) VALUES (2, 'CMC202609110137000002', 1, 0, 1001, NULL, '0710-3255895', 0, 2, NULL, NULL, '2026-09-11 01:37:22', '2026-10-10 01:40:33', 0);
INSERT INTO member_contact (id, business_no, version_no, subject_id, member_id, contact_type, contact_value, is_primary, sort_order, created_by, updated_by, created_date, updated_date, deleted) VALUES (3, 'CMC202609110137000003', 1, 0, 1001, NULL, '13800000011', 1, 0, NULL, NULL, '2026-09-11 01:37:22', '2026-10-10 01:40:33', 0);
INSERT INTO member_contact (id, business_no, version_no, subject_id, member_id, contact_type, contact_value, is_primary, sort_order, created_by, updated_by, created_date, updated_date, deleted) VALUES (4, 'CMC202609110137000004', 1, 0, 1001, NULL, '13800000012', 1, 0, NULL, NULL, '2026-09-11 01:37:22', '2026-10-10 01:40:33', 0);

-- info.id=146 (测试)
INSERT INTO info (id, business_no, version_no, subject_id, company_name, parent_id, nature, reg_status, cooperation_types, industry_tags, industry_types, logo_url, gender, business_status, created_by, updated_by, created_date, updated_date, deleted) VALUES (146, 'CR202609302103000146', 1, 0, '测试', NULL, '[{"word_describe":"企业","word_id":478,"alias_value_title":"企业","paths":[{"word_describe":"企业","word_id":478,"alias_value_title":"企业"}]}]', '[{"word_describe":"市场监管登记状态，企业正常存续有效，法人主体存在，正常开展经营","word_id":455,"alias_value_title":"存续","paths":[{"word_describe":"市场监管登记状态，企业正常存续有效，法人主体存在，正常开展经营","word_id":455,"alias_value_title":"存续"}]}]', NULL, '[{"word_describe":"兰炭","word_id":490,"alias_value_title":"兰炭","paths":[{"word_describe":"兰炭","word_id":490,"alias_value_title":"兰炭"}]}]', '[{"word_describe":"食品制造业","word_id":493,"alias_value_title":"食品制造业","paths":[{"word_describe":"制造业","word_id":492,"alias_value_title":"制造业"},{"word_describe":"食品制造业","word_id":493,"alias_value_title":"食品制造业"}]}]', NULL, NULL, 1, 295, 295, '2026-09-30 21:03:35', '2026-10-10 01:40:17', 0);
INSERT INTO credit_rating (id, business_no, version_no, subject_id, company_id, internal_level, qcc_score, created_by, updated_by, created_date, updated_date, deleted) VALUES (5, 'CC202609302103000005', 1, 0, 146, '[]', NULL, 295, 295, '2026-09-30 21:03:36', '2026-10-10 01:40:26', 0);

-- info.id=148 (测试新增002)
INSERT INTO info (id, business_no, version_no, subject_id, company_name, parent_id, nature, reg_status, cooperation_types, industry_tags, industry_types, logo_url, gender, business_status, created_by, updated_by, created_date, updated_date, deleted) VALUES (148, 'CR202610080906000148', 1, 0, '测试新增002', NULL, '[{"word_describe":"个体工商户","word_id":479,"alias_value_title":"个体工商户","paths":[{"word_describe":"个体工商户","word_id":479,"alias_value_title":"个体工商户"}]}]', '[{"word_describe":"市场监管登记状态，企业正常存续有效，法人主体存在，正常开展经营","word_id":455,"alias_value_title":"存续","paths":[{"word_describe":"市场监管登记状态，企业正常存续有效，法人主体存在，正常开展经营","word_id":455,"alias_value_title":"存续"}]}]', NULL, '[]', '[]', NULL, NULL, 1, 295, 295, '2026-10-08 09:06:20', '2026-10-10 01:40:17', 0);
INSERT INTO basic_info (id, business_no, version_no, subject_id, company_id, uscc, short_name, en_short_name, former_name, former_short_name, en_former_name, en_former_short_name, taxpayer_qualification, website, legal_person, economic_nature, insured_count, reg_capital, paid_in_capital, company_type, reg_date, business_term_start, business_term_end, is_long_term, id_card_front_url, id_card_back_url, license_url, business_status, created_by, updated_by, created_date, updated_date, deleted) VALUES (22, 'CBI202610080906000022', 1, 0, 148, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '[]', NULL, NULL, '[{"word_describe":"国有企业","word_id":498,"alias_value_title":"国有企业","paths":[{"word_describe":"国有企业","word_id":498,"alias_value_title":"国有企业"}]}]', 0, '0.00', '0.00', '[{"word_describe":"有限责任公司","word_id":500,"alias_value_title":"有限责任公司","paths":[{"word_describe":"有限责任公司","word_id":500,"alias_value_title":"有限责任公司"}]}]', NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, 295, 295, '2026-10-08 09:06:20', '2026-10-10 01:40:21', 0);

""";

    // ==================================================================
    // 测试辅助
    // ==================================================================

    /** 按新的整树结构（覆盖全部 9 张物理子表 + 2 个虚拟模块）构造一份可落库数据。 */
    private static DataBlock buildNewCompanyTree() {
        // ---- 1002 基本信息（1:1）----
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

        // ---- 1003 人员构成（1:N）+ 1004 个人通讯录（孙表）----
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

        // ---- 1007 财务信息（虚拟模块）-> 1008 银行卡 / 1009 电子账户 ----
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

        // ---- 1010 信用等级（1:1）----
        DataRow credit = new DataRow();
        credit.putValue("credit_rating", "internal_level", List.of(Map.of("word_describe", "一般")));
        credit.putValue("credit_rating", "qcc_score", "88分");

        // ---- 1020 联系方式（虚拟模块）-> 1005 地址 / 1006 公司联系方式 ----
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

        // ---- 1001 根 ----
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

    /** 记录所有下发 SQL 的数据源（动态代理包裹真实 H2 DataSource，数据仍是真库数据）。 */
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
