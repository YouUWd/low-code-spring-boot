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
import com.jdec.platform.data.api.dto.request.TableDetailQueryReq;
import com.jdec.platform.data.api.dto.request.TablePageQueryReq;
import com.jdec.platform.data.api.dto.response.DataPage;
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
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;
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
 * 基于 H2 内存数据库的真实 SQL 读写、级联持久化与版本分叉集成测试
 *
 * <p>参考 company 项目的拓扑结构，精简不必要字段，保留核心物理表与关系：
 *
 * <ul>
 *   <li>1001 外部对象 (info): id, company_name, business_no, version_no, subject_id, deleted, ...
 *   <li>1002 基本信息 (basic_info, 1:1): id, company_id, uscc, legal_person, deleted, ...
 *   <li>1003 人员架构 (member, 1:N): id, company_id, name, type, deleted, ...
 *   <li>1004 人员通讯录 (member_contact, 1:N 孙表): id, member_id, phone, deleted, ...
 * </ul>
 */
@DisplayName("基于 H2 内存数据库的 Data 引擎读写与版本派生全链路集成测试")
class CompanyH2IntegrationTest {

    private static final long MOD_COMPANY = 1001L;
    private static final long MOD_BASIC_INFO = 1002L;
    private static final long MOD_MEMBER = 1003L;
    private static final long MOD_MEMBER_CONTACT = 1004L;

    private JdbcDataSource rawDataSource;
    private TransactionAwareDataSourceProxy dataSource;
    private DataSourceTransactionManager txManager;
    private TransactionTemplate txTemplate;
    private DSLContext dslContext;

    private DataPersistenceService dataPersistenceService;
    private DataQueryService dataQueryService;
    private ForkVersionService forkVersionService;

    @BeforeEach
    void setUp() throws Exception {
        AppContext.setContext("company", 10L);

        rawDataSource = new JdbcDataSource();
        // 使用 H2 的 MySQL 兼容模式
        rawDataSource.setURL(
                "jdbc:h2:mem:company_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
        rawDataSource.setUser("sa");
        rawDataSource.setPassword("");

        dataSource = new TransactionAwareDataSourceProxy(rawDataSource);
        txManager = new DataSourceTransactionManager(dataSource);
        txTemplate = new TransactionTemplate(txManager);

        DefaultConfiguration jooqConfig = new DefaultConfiguration();
        jooqConfig.set(dataSource);
        jooqConfig.set(SQLDialect.H2);
        dslContext = DSL.using(jooqConfig);

        initSchemaAndData();

        JooqDataContextFactory dataContextFactory =
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

        ConfigApi configApi = buildMockConfigApi();
        PlanMetaLoader metaLoader = new PlanMetaLoader(configApi);

        QueryPlanCompiler queryCompiler = new QueryPlanCompiler();
        TreeResultAssembler assembler = new TreeResultAssembler();
        QueryPlanExecutor queryExecutor = new QueryPlanExecutor(dataContextFactory, assembler);
        dataQueryService = new DataQueryService(queryCompiler, queryExecutor, metaLoader);

        SavePlanCompiler saveCompiler = new SavePlanCompiler();
        SavePlanExecutor saveExecutor = new SavePlanExecutor(dataContextFactory);
        dataPersistenceService = new DataPersistenceService(saveCompiler, saveExecutor, metaLoader);

        forkVersionService =
                new ForkVersionService(
                        dataQueryService, dataPersistenceService, dataContextFactory, metaLoader);
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
        }
        AppContext.remove();
    }

    private void initSchemaAndData() throws Exception {
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(
                    """
                CREATE TABLE IF NOT EXISTS info (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    company_name VARCHAR(255),
                    business_no VARCHAR(64),
                    version_no INT DEFAULT 1,
                    subject_id BIGINT DEFAULT 0,
                    created_by BIGINT,
                    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_by BIGINT,
                    updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    deleted TINYINT DEFAULT 0
                );

                CREATE TABLE IF NOT EXISTS basic_info (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    company_id BIGINT,
                    uscc VARCHAR(64),
                    legal_person VARCHAR(64),
                    subject_id BIGINT DEFAULT 0,
                    created_by BIGINT,
                    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_by BIGINT,
                    updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    deleted TINYINT DEFAULT 0
                );

                CREATE TABLE IF NOT EXISTS member (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    company_id BIGINT,
                    name VARCHAR(64),
                    type VARCHAR(32),
                    subject_id BIGINT DEFAULT 0,
                    created_by BIGINT,
                    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_by BIGINT,
                    updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    deleted TINYINT DEFAULT 0
                );

                CREATE TABLE IF NOT EXISTS member_contact (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    member_id BIGINT,
                    phone VARCHAR(64),
                    subject_id BIGINT DEFAULT 0,
                    created_by BIGINT,
                    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_by BIGINT,
                    updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    deleted TINYINT DEFAULT 0
                );
            """);
        }
    }

    @Test
    @DisplayName("H1 保存整树：主表、1:1从表、1:N子表和1:N孙表级联入库，外键正确传播")
    void testSaveFullCompanyTree() {
        // 构建 company 树数据
        DataRow infoRow = new DataRow();
        infoRow.putValue("info", "company_name", "未来科技有限公司");
        infoRow.putValue("info", "business_no", "CP-2026-0001");

        // 1:1 子模块 1002 basic_info
        DataRow basicRow = new DataRow();
        basicRow.putValue("basic_info", "uscc", "91110108MA001");
        basicRow.putValue("basic_info", "legal_person", "张三");
        DataBlock basicBlock = DataBlock.of(MOD_BASIC_INFO, basicRow);

        // 1:N 子模块 1003 member
        DataRow member1 = new DataRow();
        member1.putValue("member", "name", "研发部");
        member1.putValue("member", "type", "DEPT");

        DataRow member2 = new DataRow();
        member2.putValue("member", "name", "李四");
        member2.putValue("member", "type", "EMPLOYEE");

        // 1:N 孙模块 1004 member_contact 挂在 member2
        DataRow contact1 = new DataRow();
        contact1.putValue("member_contact", "phone", "13800138000");
        DataRow contact2 = new DataRow();
        contact2.putValue("member_contact", "phone", "010-88889999");
        member2.setChildren(List.of(DataBlock.of(MOD_MEMBER_CONTACT, List.of(contact1, contact2))));

        DataBlock memberBlock = DataBlock.of(MOD_MEMBER, List.of(member1, member2));

        infoRow.setChildren(List.of(basicBlock, memberBlock));
        DataBlock rootBlock = DataBlock.of(MOD_COMPANY, infoRow);

        // 执行保存
        var saveResp = dataPersistenceService.save(rootBlock);
        assertNotNull(saveResp);
        Long companyId = saveResp.getRootId();
        assertNotNull(companyId);
        assertTrue(companyId > 0);

        // 验证数据库真实写入
        Integer infoCount =
                dslContext
                        .selectCount()
                        .from("info")
                        .where(DSL.field("id").eq(companyId))
                        .fetchOne(0, Integer.class);
        assertEquals(1, infoCount);

        Integer basicCount =
                dslContext
                        .selectCount()
                        .from("basic_info")
                        .where(DSL.field("company_id").eq(companyId))
                        .fetchOne(0, Integer.class);
        assertEquals(1, basicCount);

        Integer memberCount =
                dslContext
                        .selectCount()
                        .from("member")
                        .where(DSL.field("company_id").eq(companyId))
                        .fetchOne(0, Integer.class);
        assertEquals(2, memberCount);

        Long member2Id =
                dslContext
                        .select(DSL.field("id", Long.class))
                        .from("member")
                        .where(
                                DSL.field("company_id")
                                        .eq(companyId)
                                        .and(DSL.field("name").eq("李四")))
                        .fetchOne(0, Long.class);
        assertNotNull(member2Id);

        Integer contactCount =
                dslContext
                        .selectCount()
                        .from("member_contact")
                        .where(DSL.field("member_id").eq(member2Id))
                        .fetchOne(0, Integer.class);
        assertEquals(2, contactCount);
    }

    @Test
    @DisplayName("H2 查询整树：真实执行 Stage 0 与 Stage 1 分批抓取，并按模块树正确缝合")
    void testQueryFullCompanyTree() {
        // 先入库一份数据
        testSaveFullCompanyTree();

        // 执行分页查询
        TablePageQueryReq req =
                TablePageQueryReq.builder().moduleId(MOD_COMPANY).pageNo(1).pageSize(10).build();
        DataPage<DataBlock> page = dataQueryService.queryByTable(req);

        assertNotNull(page);
        assertEquals(1, page.getTotal());
        DataBlock block = page.getData();
        assertNotNull(block);
        assertEquals(1, block.size());

        DataRow rootRow = block.getFirstRecord();
        assertNotNull(rootRow);
        assertEquals("未来科技有限公司", rootRow.getString("info", "company_name"));

        // 验证 1:1 basic_info 子块
        DataBlock childBasic = rootRow.getChild(MOD_BASIC_INFO);
        assertNotNull(childBasic);
        assertEquals("91110108MA001", childBasic.getFirstRecord().getString("basic_info", "uscc"));
        assertEquals("张三", childBasic.getFirstRecord().getString("basic_info", "legal_person"));

        // 验证 1:N member 子块
        DataBlock childMember = rootRow.getChild(MOD_MEMBER);
        assertNotNull(childMember);
        assertEquals(2, childMember.size());

        // 验证 1:N 孙表 member_contact
        DataRow empRow =
                childMember.getRecords().stream()
                        .filter(r -> "李四".equals(r.getString("member", "name")))
                        .findFirst()
                        .orElse(null);
        assertNotNull(empRow);
        DataBlock contactBlock = empRow.getChild(MOD_MEMBER_CONTACT);
        assertNotNull(contactBlock);
        assertEquals(2, contactBlock.size());
    }

    @Test
    @DisplayName("H3 孤儿记录自动软删除：提交子表数组时，移除的记录 deleted 会被置为 1")
    void testOrphanRemovalOnUpdate() {
        testSaveFullCompanyTree();

        // 详情读取
        Long rootId =
                dslContext.select(DSL.field("id", Long.class)).from("info").fetchOne(0, Long.class);
        DataBlock detail =
                dataQueryService.getDetailByTable(
                        TableDetailQueryReq.builder().moduleId(MOD_COMPANY).id(rootId).build());

        // 从 member 列表中移除 "研发部"，只保留 "李四"
        DataRow infoRow = detail.getFirstRecord();
        DataBlock memberBlock = infoRow.getChild(MOD_MEMBER);
        DataRow empRow =
                memberBlock.getRecords().stream()
                        .filter(r -> "李四".equals(r.getString("member", "name")))
                        .findFirst()
                        .orElseThrow();
        memberBlock.setRecords(new ArrayList<>(List.of(empRow)));

        // 执行保存更新
        dataPersistenceService.save(detail);

        // 验证研发部被软删除
        Integer liveCount =
                dslContext
                        .selectCount()
                        .from("member")
                        .where(
                                DSL.field("company_id")
                                        .eq(rootId)
                                        .and(DSL.field("deleted").eq((byte) 0)))
                        .fetchOne(0, Integer.class);
        assertEquals(1, liveCount, "存活 member 应只剩 1 条");

        Integer deletedCount =
                dslContext
                        .selectCount()
                        .from("member")
                        .where(
                                DSL.field("company_id")
                                        .eq(rootId)
                                        .and(DSL.field("name").eq("研发部"))
                                        .and(DSL.field("deleted").eq((byte) 1)))
                        .fetchOne(0, Integer.class);
        assertEquals(1, deletedCount, "被移除的研发部 deleted 字段应置为 1");
    }

    @Test
    @DisplayName("H4 版本分叉 (ForkVersion)：完整派生单据，新版本继承基准数据并重置主键与审计字段，version_no 自增")
    void testForkNewVersionWithPatch() {
        testSaveFullCompanyTree();

        Long baseId =
                dslContext.select(DSL.field("id", Long.class)).from("info").fetchOne(0, Long.class);

        // 构造局部增量 patchData：修改公司名称为新名称，修改李四电话
        DataRow patchInfo = new DataRow();
        patchInfo.putValue("info", "company_name", "未来科技有限公司 (新版本)");

        // 获取原李四的 id
        Long member2Id =
                dslContext
                        .select(DSL.field("id", Long.class))
                        .from("member")
                        .where(DSL.field("company_id").eq(baseId).and(DSL.field("name").eq("李四")))
                        .fetchOne(0, Long.class);

        DataRow patchMember = new DataRow();
        patchMember.putValue("member", "id", member2Id);

        DataRow newContact = new DataRow();
        newContact.putValue("member_contact", "phone", "13999999999");
        patchMember.setChildren(List.of(DataBlock.of(MOD_MEMBER_CONTACT, List.of(newContact))));

        patchInfo.setChildren(List.of(DataBlock.of(MOD_MEMBER, List.of(patchMember))));
        DataBlock patchData = DataBlock.of(MOD_COMPANY, patchInfo);

        ForkVersionReq forkReq =
                ForkVersionReq.builder()
                        .moduleId(MOD_COMPANY)
                        .id(baseId)
                        .patchData(patchData)
                        .build();

        Long newId = forkVersionService.forkNewVersion(forkReq);
        assertNotNull(newId);
        assertNotEquals(baseId, newId, "派生后应生成全新的主键 ID");

        // 验证原基准版本不受影响
        String oldName =
                dslContext
                        .select(DSL.field("company_name", String.class))
                        .from("info")
                        .where(DSL.field("id").eq(baseId))
                        .fetchOne(0, String.class);
        assertEquals("未来科技有限公司", oldName);
        Integer oldVer =
                dslContext
                        .select(DSL.field("version_no", Integer.class))
                        .from("info")
                        .where(DSL.field("id").eq(baseId))
                        .fetchOne(0, Integer.class);
        assertEquals(1, oldVer);

        // 验证新版本派生记录
        String newName =
                dslContext
                        .select(DSL.field("company_name", String.class))
                        .from("info")
                        .where(DSL.field("id").eq(newId))
                        .fetchOne(0, String.class);
        assertEquals("未来科技有限公司 (新版本)", newName);
        Integer newVer =
                dslContext
                        .select(DSL.field("version_no", Integer.class))
                        .from("info")
                        .where(DSL.field("id").eq(newId))
                        .fetchOne(0, Integer.class);
        assertEquals(2, newVer, "版本号应从 1 递增至 2");

        // 验证新版本的 1:1 basic_info 完全继承自基准版本
        String uscc =
                dslContext
                        .select(DSL.field("uscc", String.class))
                        .from("basic_info")
                        .where(DSL.field("company_id").eq(newId))
                        .fetchOne(0, String.class);
        assertEquals("91110108MA001", uscc, "未传入 patch 的 basic_info 属性应完全继承");
    }

    // ==================== 构造精简的 Company 模块树与关系元数据 ====================

    private static ConfigApi buildMockConfigApi() {
        ConfigApi mock = mock(ConfigApi.class);

        ModuleInfo infoMod =
                ModuleInfo.builder()
                        .moduleId(MOD_COMPANY)
                        .moduleName("外部对象管理")
                        .primaryTable("info")
                        .parentId(0L)
                        .fields(
                                List.of(
                                        f(1L, MOD_COMPANY, "info", "id"),
                                        f(2L, MOD_COMPANY, "info", "company_name"),
                                        f(3L, MOD_COMPANY, "info", "business_no"),
                                        f(4L, MOD_COMPANY, "info", "version_no"),
                                        f(5L, MOD_COMPANY, "info", "subject_id")))
                        .build();

        ModuleInfo basicMod =
                ModuleInfo.builder()
                        .moduleId(MOD_BASIC_INFO)
                        .moduleName("基本信息")
                        .primaryTable("basic_info")
                        .parentId(MOD_COMPANY)
                        .fields(
                                List.of(
                                        f(11L, MOD_BASIC_INFO, "basic_info", "id"),
                                        f(12L, MOD_BASIC_INFO, "basic_info", "company_id"),
                                        f(13L, MOD_BASIC_INFO, "basic_info", "uscc"),
                                        f(14L, MOD_BASIC_INFO, "basic_info", "legal_person")))
                        .build();

        ModuleInfo memberMod =
                ModuleInfo.builder()
                        .moduleId(MOD_MEMBER)
                        .moduleName("人员架构")
                        .primaryTable("member")
                        .parentId(MOD_COMPANY)
                        .fields(
                                List.of(
                                        f(21L, MOD_MEMBER, "member", "id"),
                                        f(22L, MOD_MEMBER, "member", "company_id"),
                                        f(23L, MOD_MEMBER, "member", "name"),
                                        f(24L, MOD_MEMBER, "member", "type")))
                        .build();

        ModuleInfo contactMod =
                ModuleInfo.builder()
                        .moduleId(MOD_MEMBER_CONTACT)
                        .moduleName("人员通讯录")
                        .primaryTable("member_contact")
                        .parentId(MOD_MEMBER)
                        .fields(
                                List.of(
                                        f(31L, MOD_MEMBER_CONTACT, "member_contact", "id"),
                                        f(32L, MOD_MEMBER_CONTACT, "member_contact", "member_id"),
                                        f(33L, MOD_MEMBER_CONTACT, "member_contact", "phone")))
                        .build();

        Map<Long, ModuleInfo> moduleMap = new LinkedHashMap<>();
        moduleMap.put(MOD_COMPANY, infoMod);
        moduleMap.put(MOD_BASIC_INFO, basicMod);
        moduleMap.put(MOD_MEMBER, memberMod);
        moduleMap.put(MOD_MEMBER_CONTACT, contactMod);

        Map<Long, List<Long>> p2c = new LinkedHashMap<>();
        p2c.put(MOD_COMPANY, List.of(MOD_BASIC_INFO, MOD_MEMBER));
        p2c.put(MOD_MEMBER, List.of(MOD_MEMBER_CONTACT));

        Map<Long, ModuleFieldDTO> fieldMap = new LinkedHashMap<>();
        Map<Long, Long> fieldToModule = new LinkedHashMap<>();
        for (ModuleInfo m : moduleMap.values()) {
            for (ModuleFieldDTO f : m.getFields()) {
                fieldMap.put(f.getId(), f);
                fieldToModule.put(f.getId(), m.getModuleId());
            }
        }

        ModuleTreeDTO tree =
                ModuleTreeDTO.builder()
                        .rootModuleId(MOD_COMPANY)
                        .projectNo("company")
                        .subjectId(10L)
                        .rootModule(infoMod)
                        .moduleMap(moduleMap)
                        .parentToChildren(p2c)
                        .fieldMap(fieldMap)
                        .fieldToModuleMap(fieldToModule)
                        .allowedModuleIds(new LinkedHashSet<>(moduleMap.keySet()))
                        .build();

        List<TableRelationDTO> relations =
                List.of(
                        TableRelationDTO.builder()
                                .mainTable("info")
                                .mainField("id")
                                .joinTable("basic_info")
                                .joinField("company_id")
                                .relationType("1:1")
                                .build(),
                        TableRelationDTO.builder()
                                .mainTable("info")
                                .mainField("id")
                                .joinTable("member")
                                .joinField("company_id")
                                .relationType("1:N")
                                .build(),
                        TableRelationDTO.builder()
                                .mainTable("member")
                                .mainField("id")
                                .joinTable("member_contact")
                                .joinField("member_id")
                                .relationType("1:N")
                                .build());

        Map<String, List<TableRelationDTO>> fwd = new LinkedHashMap<>();
        for (TableRelationDTO r : relations) {
            fwd.computeIfAbsent(r.getMainTable().toLowerCase(), k -> new ArrayList<>()).add(r);
        }

        ProjectTableRelationsDTO projRelations =
                ProjectTableRelationsDTO.builder()
                        .projectNo("company")
                        .allRelations(relations)
                        .forwardRelations(fwd)
                        .reverseRelations(Collections.emptyMap())
                        .build();

        org.mockito.Mockito.lenient()
                .when(
                        mock.getModuleTree(
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.eq(MOD_COMPANY)))
                .thenReturn(tree);
        org.mockito.Mockito.lenient()
                .when(mock.getProjectTableRelations(org.mockito.ArgumentMatchers.eq("company")))
                .thenReturn(projRelations);

        return mock;
    }

    private static ModuleFieldDTO f(long id, long mid, String tbl, String col) {
        return ModuleFieldDTO.builder()
                .id(id)
                .moduleId(mid)
                .tableName(tbl)
                .columnName(col)
                .displayName(col)
                .sortOrder((int) (id % 1000))
                .build();
    }
}
