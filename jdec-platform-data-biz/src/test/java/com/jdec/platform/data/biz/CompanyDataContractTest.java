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
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.data.api.dto.request.TableSpec;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.biz.plan.PlanMeta;
import com.jdec.platform.data.biz.plan.PlanMetaLoader;
import com.jdec.platform.data.biz.plan.assembler.TreeResultAssembler;
import com.jdec.platform.data.biz.plan.compiler.QueryPlanCompiler;
import com.jdec.platform.data.biz.plan.compiler.SavePlanCompiler;
import com.jdec.platform.data.biz.plan.executor.QueryPlanExecutor;
import com.jdec.platform.data.biz.plan.model.SaveNodePlan;
import com.jdec.platform.data.biz.plan.model.SavePlan;
import com.jdec.platform.data.biz.service.DataQueryService;
import com.jdec.platform.shared.datasource.JooqDataContextFactory;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * company 项目 · data 引擎重构后契约测试集（读写同构 / 自相似树）
 *
 * <p>元数据全部取自真实库 {@code config_center} 的 company 项目（24 模块 / 9 业务表 / 8 条表关联），
 * 表名与列名与生产一致，因此本测试类可直接对照真实响应。
 *
 * <p>契约基线（重构提交 {@code 763aec9b}）：
 *
 * <ul>
 *   <li>行记录<b>不再</b>被根 moduleId 包裹 —— {@code records[0]} 直接是 {@code {info:{...}}}；
 *   <li>根模块 ID 上移到 {@link DataPage#getModuleId()}；
 *   <li>物理子模块统一挂到<b>行级</b> {@code children} 数组，元素形如 {@code {moduleId, records}}；
 *   <li>虚拟模块（{@code primary_table IS NULL} / {@code ''}）<b>同样</b>挂到行级 {@code children}， 其 {@code
 *       records} 恒为 1 个"纯逻辑容器"对象 —— 该容器没有表名键，只承载下一层 {@code children}。 数字 moduleId 键的旧形态已废除。
 * </ul>
 *
 * <p>仍需注意的一处不对称：物理模块的 {@code records[i]} 形如 {@code {<table>: {...}}}， 而虚拟模块的 {@code records[0]} 只有
 * {@code children}、没有表名键 —— 这与"虚拟模块没有物理主表" 是一致的，前端按 {@code meta.primaryTable} 取值时对虚拟模块取到 {@code
 * undefined} 属预期。
 *
 * <p>测试分两层：
 *
 * <ul>
 *   <li>族 1 契约基线 —— 断言重构后的<b>实际正确行为</b>，必须全绿；
 *   <li>族 2 缺陷锚点 —— 断言<b>应有行为</b>，当前实现不满足，统一 {@code @Disabled} 并注明原因， 修复后去掉注解即为验收断言。
 * </ul>
 */
class CompanyDataContractTest {

    // ==================================================================
    // company 真实元数据 fixture（取自 config_center，勿臆造）
    // ==================================================================

    private static final Map<Long, ModuleInfo> MODS = new LinkedHashMap<>();
    private static final Map<Long, ModuleFieldDTO> FMAP = new LinkedHashMap<>();
    private static final Map<Long, Long> F2M = new LinkedHashMap<>();
    private static final Map<Long, List<Long>> P2C = new LinkedHashMap<>();
    private static ProjectTableRelationsDTO PROJ;

    static {
        initFixture();
    }

    private static ModuleFieldDTO f(long id, long mid, String tbl, String col, String dn) {
        return ModuleFieldDTO.builder()
                .id(id)
                .moduleId(mid)
                .tableName(tbl)
                .columnName(col)
                .displayName(dn)
                .sortOrder((int) (id % 1000))
                .build();
    }

    private static void add(
            long mid, String name, String pt, long parent, List<ModuleFieldDTO> fl) {
        ModuleInfo mi =
                ModuleInfo.builder()
                        .moduleId(mid)
                        .moduleName(name)
                        .primaryTable(pt)
                        .parentId(parent)
                        .fields(fl)
                        .build();
        MODS.put(mid, mi);
        for (ModuleFieldDTO x : fl) {
            FMAP.put(x.getId(), x);
            F2M.put(x.getId(), mid);
        }
    }

    private static void initFixture() {
        // 零字段系统模块（G8 命中域）
        add(1L, "系统设置", "", 0L, List.of());
        add(2L, "模块配置", "", 1L, List.of());
        add(13L, "系统监控", "", 0L, List.of());

        add(
                1001L,
                "外部对象管理",
                "info",
                0L,
                List.of(
                        f(10001L, 1001L, "info", "id", "主键ID"),
                        f(10002L, 1001L, "info", "company_name", "外部对象全称"),
                        f(10003L, 1001L, "info", "parent_id", "自关联母集团"),
                        f(10083L, 1001L, "info", "subject_id", "主题ID")));
        add(
                1002L,
                "基本信息",
                "basic_info",
                1001L,
                List.of(
                        f(10011L, 1002L, "basic_info", "id", "主键ID"),
                        f(10012L, 1002L, "basic_info", "uscc", "统一社会信用代码"),
                        f(10021L, 1002L, "basic_info", "legal_person", "法定代表人")));
        add(
                1003L,
                "人员构成-组织架构",
                "member",
                1001L,
                List.of(
                        f(10034L, 1003L, "member", "id", "主键ID"),
                        f(10035L, 1003L, "member", "type", "节点类型"),
                        f(10038L, 1003L, "member", "name", "部门名称/姓名")));
        add(
                1004L,
                "人员构成-个人通讯录",
                "member_contact",
                1003L,
                List.of(
                        f(10043L, 1004L, "member_contact", "id", "主键ID"),
                        f(10044L, 1004L, "member_contact", "member_id", "所属员工"),
                        f(10046L, 1004L, "member_contact", "contact_value", "联系号码")));
        // T4 虚拟模块：primary_table IS NULL
        add(1020L, "联系方式", "", 1001L, List.of());
        add(
                1005L,
                "联系方式-公司地址",
                "address",
                1020L,
                List.of(
                        f(10048L, 1005L, "address", "id", "主键ID"),
                        f(10049L, 1005L, "address", "comm_country", "通讯地址-国家")));
        // T5 空主表虚拟模块：primary_table = ''
        add(1007L, "财务信息", "", 1001L, List.of());
        add(
                1008L,
                "银行卡",
                "financial_account",
                1007L,
                List.of(
                        f(10062L, 1008L, "financial_account", "id", "主键ID"),
                        f(10063L, 1008L, "financial_account", "account_no", "账号")));
        add(
                1010L,
                "信用等级",
                "credit_rating",
                1001L,
                List.of(
                        f(10073L, 1010L, "credit_rating", "id", "主键ID"),
                        f(10074L, 1010L, "credit_rating", "internal_level", "内部信用等级")));

        // ---- 探针模块：school / company 真实元数据都到不了的代码路径 ----
        // 1090 故意制造 R1 违规：主表 info 上投影 1:N 从表 member 的字段。
        // 这是 QueryPlanCompiler:455-493 与 TreeResultAssembler:279-345 这两段"死代码"的唯一入口
        // （两项目 R1 违规 = 0，见 fixture-coverage-matrix.md §3.1）。
        add(
                1090L,
                "R1违规探针",
                "info",
                0L,
                List.of(
                        f(10901L, 1090L, "info", "id", "主键ID"),
                        f(10902L, 1090L, "member", "name", "同模块1:N违规字段")));
        // 1091 虚拟模块（primary_table = ''）却配了一个字段 —— B2 盲区。
        add(
                1091L,
                "虚拟模块带字段探针",
                "",
                1001L,
                List.of(f(10911L, 1091L, "info", "company_name", "外部对象全称")));

        P2C.put(1L, List.of(2L));
        P2C.put(1001L, List.of(1002L, 1003L, 1007L, 1010L, 1020L, 1091L));
        P2C.put(1003L, List.of(1004L));
        P2C.put(1007L, List.of(1008L));
        P2C.put(1020L, List.of(1005L));

        List<TableRelationDTO> rs = new ArrayList<>();
        rs.add(rel("info", "id", "basic_info", "company_id", "1:1"));
        rs.add(rel("info", "id", "member", "company_id", "1:N"));
        rs.add(rel("info", "id", "address", "company_id", "1:1"));
        rs.add(rel("info", "id", "financial_account", "company_id", "1:N"));
        rs.add(rel("info", "id", "credit_rating", "company_id", "1:1"));
        rs.add(rel("member", "id", "member_contact", "member_id", "1:N"));

        Map<String, List<TableRelationDTO>> fwd = new LinkedHashMap<>();
        for (TableRelationDTO r : rs) {
            fwd.computeIfAbsent(r.getMainTable().toLowerCase(), k -> new ArrayList<>()).add(r);
        }
        PROJ =
                ProjectTableRelationsDTO.builder()
                        .projectNo("company")
                        .allRelations(rs)
                        .forwardRelations(fwd)
                        .reverseRelations(Collections.emptyMap())
                        .build();
    }

    private static TableRelationDTO rel(String mt, String mf, String jt, String jf, String type) {
        return TableRelationDTO.builder()
                .mainTable(mt)
                .mainField(mf)
                .joinTable(jt)
                .joinField(jf)
                .relationType(type)
                .build();
    }

    /** 按请求的 root 动态建树 —— 固定树会掩盖 G8 之类"根模块无字段"的问题。 */
    private static ModuleTreeDTO treeFor(long root) {
        List<Long> sub = new ArrayList<>();
        Deque<Long> st = new ArrayDeque<>();
        st.push(root);
        while (!st.isEmpty()) {
            long x = st.pop();
            if (MODS.get(x) == null) {
                continue;
            }
            sub.add(x);
            for (Long k : P2C.getOrDefault(x, List.of())) {
                st.push(k);
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

    /** 元数据快照：测试直接由 fixture 构造，等价于线上 PlanMetaLoader 的产物。 */
    private static PlanMeta metaFor(long root) {
        return new PlanMeta(treeFor(root), PROJ);
    }

    // ==================================================================
    // 测试装置
    // ==================================================================

    /** 每个物理表返回的行数 —— 用 member/member_contact = 2 行证明 1:N 真的多行 */
    private static final Map<String, Integer> ROW_COUNTS = Map.of("member", 2, "member_contact", 2);

    private static final Pattern ALIAS = Pattern.compile("`(\\w+)`\\.`(\\w+)` as `(\\w+)`");

    private final List<String> sqls = new ArrayList<>();
    private final AtomicInteger moduleTreeLoads = new AtomicInteger();
    private DataQueryService queryService;
    private SavePlanCompiler savePlanCompiler;
    private QueryPlanCompiler queryPlanCompiler;

    @BeforeEach
    void setUp() {
        sqls.clear();
        moduleTreeLoads.set(0);

        ConfigApi configApi =
                mock(
                        ConfigApi.class,
                        invocation -> {
                            String name = invocation.getMethod().getName();
                            if ("getModuleTree".equals(name)) {
                                moduleTreeLoads.incrementAndGet();
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

        MockDataProvider provider =
                ctx -> {
                    String sql = ctx.sql().toLowerCase();
                    sqls.add(sql);
                    DSLContext c = DSL.using(SQLDialect.MYSQL);

                    if (sql.contains("count(*)")) {
                        var res = c.newResult(DSL.field("count", Long.class));
                        var rec = c.newRecord(DSL.field("count", Long.class));
                        rec.setValue(DSL.field("count", Long.class), 15L);
                        res.add(rec);
                        return new MockResult[] {new MockResult(1, res)};
                    }

                    List<String> tables = new ArrayList<>();
                    List<String> cols = new ArrayList<>();
                    List<String> aliases = new ArrayList<>();
                    Matcher m = ALIAS.matcher(sql);
                    while (m.find()) {
                        tables.add(m.group(1));
                        cols.add(m.group(2));
                        aliases.add(m.group(3));
                    }
                    if (aliases.isEmpty()) {
                        return new MockResult[] {new MockResult(0, c.newResult())};
                    }

                    Field<?>[] fs = new Field<?>[aliases.size()];
                    for (int i = 0; i < aliases.size(); i++) {
                        fs[i] = DSL.field(aliases.get(i), Object.class);
                    }
                    String fromTable = tables.get(0);
                    int n = ROW_COUNTS.getOrDefault(fromTable, 1);

                    var res = c.newResult(fs);
                    for (int row = 0; row < n; row++) {
                        var rec = c.newRecord(fs);
                        for (int i = 0; i < aliases.size(); i++) {
                            rec.setValue((Field) fs[i], valueOf(tables.get(i), cols.get(i), row));
                        }
                        res.add(rec);
                    }
                    return new MockResult[] {new MockResult(n, res)};
                };

        DSLContext dsl = DSL.using(new MockConnection(provider), SQLDialect.MYSQL);
        JooqDataContextFactory factory = new FakeFactory(dsl);
        queryPlanCompiler = new QueryPlanCompiler();
        QueryPlanExecutor executor = new QueryPlanExecutor(factory, new TreeResultAssembler());

        queryService =
                new DataQueryService(queryPlanCompiler, executor, new PlanMetaLoader(configApi));
        savePlanCompiler = new SavePlanCompiler();
    }

    /** 按 (表, 列) 合成确定性取值；断言直接引用这些常量。 */
    private static Object valueOf(String table, String col, int row) {
        String k = table + "." + col;
        switch (k) {
            case "info.id":
                return (long) (row + 1);
            case "info.company_name":
                return "盛港集团";
            case "info.subject_id":
                return 0L;
            case "basic_info.id":
            case "basic_info.company_id":
                return 1L;
            case "basic_info.uscc":
                return "9153312343631123J";
            case "basic_info.legal_person":
                return row == 0 ? "李四" : "王五";
            case "member.id":
                return (long) (row + 1);
            case "member.company_id":
                return 1L;
            case "member.type":
                return row == 0 ? "DEPT" : "EMPLOYEE";
            case "member.name":
                return row == 0 ? "张三" : "李四";
            case "member_contact.id":
                return (long) (row + 1);
            case "member_contact.member_id":
                return 1L;
            case "member_contact.contact_value":
                return row == 0 ? "13585855898" : "13585855899";
            case "address.id":
            case "address.company_id":
                return 1L;
            case "address.comm_country":
                return "中国";
            case "financial_account.id":
            case "financial_account.company_id":
                return 1L;
            case "financial_account.account_no":
                return "62220210001";
            case "credit_rating.id":
            case "credit_rating.company_id":
                return 1L;
            case "credit_rating.internal_level":
                return "AA";
            default:
                if ("id".equals(col)) {
                    return (long) (row + 1);
                }
                if (col != null && col.endsWith("_id")) {
                    return 1L;
                }
                return k;
        }
    }

    // ==================================================================
    // 族 1 · 契约基线（必须全绿）
    // ==================================================================

    @Nested
    @DisplayName("族 1 · 契约基线")
    class ContractBaseline {

        @Test
        @DisplayName("C01 根记录不再被 moduleId 包裹，moduleId 上移到 DataPage")
        void rootRecordIsUnwrappedAndModuleIdMovesToPage() {
            DataPage<DataBlock> page = queryService.query(req(1001L, List.of(10001L, 10002L)));

            assertEquals(1001L, page.getModuleId(), "DataPage 必须携带根模块 ID");
            assertEquals(15L, page.getTotal());
            assertEquals(1, page.getRecords().size());

            DataRow row = page.getRecords().get(0);
            assertNotNull(row.getTable("info"), "主表字段应直接以表名为键挂在行上");

            Map<String, Object> info = row.getTable("info");
            assertEquals(1L, ((Number) info.get("id")).longValue());
            assertEquals("盛港集团", info.get("company_name"));
        }

        @Test
        @DisplayName("C02 1:1 子模块挂到行级 children 数组，元素为 {moduleId, records}")
        void oneToOneChildMountedUnderChildrenArray() {
            DataPage<DataBlock> page =
                    queryService.query(req(1001L, List.of(10001L, 10002L, 10011L, 10012L)));
            DataRow row = page.getRecords().get(0);

            List<DataBlock> children = childrenOf(row);
            assertEquals(1, children.size(), "只涉及 1002，应恰好 1 个 children 节点");
            DataBlock node = children.get(0);
            assertEquals(1002, node.getModuleId().intValue());
            assertNotNull(node.getRecords(), "records 必须是数组");

            List<DataRow> recs = node.getRecords();
            assertEquals(1, recs.size());
            Map<String, Object> basic = recs.get(0).getTable("basic_info");
            assertEquals("9153312343631123J", basic.get("uscc"));
        }

        @Test
        @DisplayName("C03 1:N 子模块在同一 children 节点下返回多条 records")
        void oneToManyChildReturnsMultipleRecords() {
            DataPage<DataBlock> page =
                    queryService.query(req(1001L, List.of(10001L, 10034L, 10038L)));
            DataRow row = page.getRecords().get(0);

            List<DataBlock> children = childrenOf(row);
            assertEquals(1, children.size());
            DataBlock node = children.get(0);
            assertEquals(1003, node.getModuleId().intValue());

            List<DataRow> recs = node.getRecords();
            assertEquals(2, recs.size(), "member 有 2 行，1:N 必须全部返回");
            assertEquals("张三", table(recs.get(0), "member").get("name"));
            assertEquals("李四", table(recs.get(1), "member").get("name"));
        }

        @Test
        @DisplayName("C04 三层嵌套：children 递归挂在子行内部（1001 -> 1003 -> 1004）")
        void threeLevelNestingRecursesInsideChildRow() {
            DataPage<DataBlock> page =
                    queryService.query(req(1001L, List.of(10001L, 10034L, 10043L, 10046L)));
            DataRow row = page.getRecords().get(0);

            DataBlock memberNode = childrenOf(row).get(0);
            assertEquals(1003, memberNode.getModuleId().intValue());

            List<DataRow> memberRecs = memberNode.getRecords();
            DataRow firstMember = memberRecs.get(0);

            List<DataBlock> grand = childrenOf(firstMember);
            assertEquals(1, grand.size());
            assertEquals(1004, grand.get(0).getModuleId().intValue());

            List<DataRow> contactRecs = grand.get(0).getRecords();
            assertEquals(
                    "13585855898",
                    table(contactRecs.get(0), "member_contact").get("contact_value"));
        }

        @Test
        @DisplayName("C05 虚拟模块(primary_table IS NULL)同样挂行级 children，数字键形态已废除")
        void virtualModuleNullIsMountedAsChildNode() {
            DataPage<DataBlock> page =
                    queryService.query(req(1001L, List.of(10001L, 10048L, 10049L)));
            DataRow row = page.getRecords().get(0);

            DataBlock vmNode = childNode(row, 1020L);
            assertEquals(1, recordsOf(vmNode).size(), "虚拟节点 records 恒为 1 个容器对象");

            // 虚拟节点的 records[0] 是"纯逻辑容器"：没有表名键，只承载 children
            DataRow container = recordsOf(vmNode).get(0);
            assertNull(container.getTable("info"), "虚拟容器不应包含任何物理表空间");

            DataBlock addrNode = childNode(container, 1005L);
            assertEquals("中国", table(recordsOf(addrNode).get(0), "address").get("comm_country"));
        }

        @Test
        @DisplayName("C06 虚拟模块(primary_table = '')与 NULL 形态行为完全一致")
        void virtualModuleBlankBehavesLikeNull() {
            DataPage<DataBlock> page =
                    queryService.query(req(1001L, List.of(10001L, 10062L, 10063L)));
            DataRow row = page.getRecords().get(0);

            DataBlock vmNode = childNode(row, 1007L);
            DataRow container = recordsOf(vmNode).get(0);
            DataBlock bankNode = childNode(container, 1008L);
            assertEquals(
                    "62220210001",
                    table(recordsOf(bankNode).get(0), "financial_account").get("account_no"));
        }

        @Test
        @DisplayName("C07 只涉及虚拟模块时，行级 children 同样存在（形态已统一，前端可统一遍历）")
        void virtualOnlyChildStillProducesRowLevelChildrenKey() {
            DataPage<DataBlock> page = queryService.query(req(1001L, List.of(10001L, 10048L)));
            DataRow row = page.getRecords().get(0);

            assertNotNull(row.getChildren(), "虚拟模块也走行级 children");
            assertEquals(1020, childNode(row, 1020L).getModuleId().intValue());
        }

        @Test
        @DisplayName("C08 children 与表名键同级，绝不嵌在表名键内部")
        void childrenIsSiblingOfTableKey() {
            DataPage<DataBlock> page = queryService.query(req(1001L, List.of(10001L, 10011L)));
            DataRow row = page.getRecords().get(0);

            assertNotNull(row.getChildren(), "children 应在行级");
            assertNull(table(row, "info").get("children"), "物理表空间内部不得再出现 children 键");
        }

        @Test
        @DisplayName("C09 1:1 与 1:N 在响应载荷中形状完全相同（基数信息不在 data 里）")
        void oneToOneAndOneToManyAreIndistinguishableInPayload() {
            // address 是 1:1、financial_account 是 1:N，两者 mock 都只有 1 行
            DataPage<DataBlock> oneToOne = queryService.query(req(1001L, List.of(10001L, 10048L)));
            DataPage<DataBlock> oneToMany = queryService.query(req(1001L, List.of(10001L, 10062L)));

            DataRow row1 = oneToOne.getRecords().get(0);
            DataRow row2 = oneToMany.getRecords().get(0);

            DataRow container1 = recordsOf(childNode(row1, 1020L)).get(0);
            DataRow container2 = recordsOf(childNode(row2, 1007L)).get(0);

            List<DataRow> r1 = childNode(container1, 1005L).getRecords();
            List<DataRow> r2 = childNode(container2, 1008L).getRecords();

            assertNotNull(r1);
            assertNotNull(r2);
            assertEquals(1, r1.size());
            assertEquals(1, r2.size());
            // 结论：仅凭 data 无法区分 1:1 / 1:N —— 基数必须由 meta 提供（见族 2 · D04）
        }

        @Test
        @DisplayName("C10 内部技术字段 _fk_* / _row_id 不泄露到任何层级")
        void internalTechnicalFieldsAreNotLeaked() {
            DataPage<DataBlock> page =
                    queryService.query(req(1001L, List.of(10001L, 10034L, 10043L, 10046L)));
            assertNoTechnicalKey(page.getRecords().get(0), "$");
        }

        @Test
        @DisplayName("C11 getDetail 的 records[0] 与 query 的 records[0] 结构同构")
        void getDetailDataIsIsomorphicToQueryRecord() {
            List<Long> fields = List.of(10001L, 10002L, 10011L, 10012L);
            DataRow fromQuery = queryService.query(req(1001L, fields)).getRecords().get(0);

            DataBlock detail =
                    queryService.getDetail(
                            DetailQueryReq.builder()
                                    .moduleId(1001L)
                                    .id(1L)
                                    .tables(specs(1001L, fields))
                                    .build());
            assertEquals(1001L, detail.getModuleId());
            assertNotNull(detail.getRecords());
            assertFalse(detail.getRecords().isEmpty());
            DataRow fromDetail = detail.getRecords().get(0);

            assertEquals(
                    fromQuery.getTables().keySet(), fromDetail.getTables().keySet(), "key 集合必须一致");
            assertEquals(
                    childrenOf(fromQuery).get(0).getModuleId(),
                    childrenOf(fromDetail).get(0).getModuleId());
        }

        @Test
        @DisplayName("C12 读写同构：query 的 records[0] 可直接作为 DataBlock.records 编译")
        void queryRecordFeedsBackIntoDataBlock() {
            DataRow row =
                    queryService
                            .query(req(1001L, List.of(10001L, 10002L, 10011L, 10012L)))
                            .getRecords()
                            .get(0);

            DataBlock saveBlock = DataBlock.builder().moduleId(1001L).records(List.of(row)).build();
            SavePlan plan = savePlanCompiler.compile(saveBlock, metaFor(1001L));

            SaveNodePlan root = plan.getRootNodePlan();
            assertEquals("info", root.getPrimaryTable());
            assertEquals(1001L, root.getModuleId());

            // 表名包裹被展平：company_name 提升到行顶层，供 upsertSingleRow 直接写列
            Map<String, Object> cleanRow = root.getRecords().get(0);
            assertEquals("盛港集团", cleanRow.get("company_name"));
            assertEquals(1L, ((Number) cleanRow.get("id")).longValue());

            // 行级 children 被解析为子节点计划
            List<Long> childIds = new ArrayList<>();
            for (SaveNodePlan c : root.getChildren()) {
                childIds.add(c.getModuleId());
            }
            assertTrue(childIds.contains(1002L), "children 中的 1002 应被编译为子保存计划，实际=" + childIds);
        }

        @Test
        @DisplayName("C13 1:N 子表走批量 IN 拉取，绝不产生 LEFT JOIN")
        void oneToManyChildUsesBatchFetchNotLeftJoin() {
            queryService.query(req(1001L, List.of(10001L, 10034L, 10038L)));

            for (String sql : sqls) {
                assertFalse(
                        sql.contains("left outer join") || sql.contains("left join"),
                        "1:N 子表不应产生 JOIN，实际 SQL=" + sql);
            }
            assertTrue(
                    sqls.stream().anyMatch(s -> s.contains("`member`.`company_id` in (?)")),
                    "1:N 应走 company_id IN (?) 批量拉取");
        }

        @Test
        @DisplayName("C14 不传 fields 时只查根模块，行级无 children（当前契约，属设计选择）")
        void noFieldsYieldsRootOnlyWithoutChildren() {
            DataPage<DataBlock> page = queryService.query(req(1001L, List.of()));
            DataRow row = page.getRecords().get(0);

            assertNotNull(row.getTable("info"));
            assertTrue(table(row, "info").containsKey("company_name"), "不传 fields 时投影全部字段");
            assertTrue(
                    row.getChildren() == null || row.getChildren().isEmpty(),
                    "无 fields 即无子树，行级不出现 children");
        }

        @Test
        @DisplayName("C15 子表 SQL 无条件带 deleted = 0，1:N 子表亦不例外")
        void childSqlAlwaysCarriesDeletedPredicate() {
            queryService.query(req(1001L, List.of(10001L, 10034L, 10038L)));
            assertTrue(
                    sqls.stream()
                            .anyMatch(
                                    s ->
                                            s.contains("from `member`")
                                                    && s.contains("`member`.`deleted` = ?")),
                    "子表查询必须带 deleted 条件");
        }

        @Test
        @DisplayName("C16 经虚拟模块(1007)下钻的过滤上卷为 EXISTS 链，虚拟层被穿透、不产生表引用")
        void filterRollupThroughVirtualModule() {
            // 1008(financial_account) 挂在虚拟模块 1007 之下；对它过滤必须穿透 1007 上卷到根 1001。
            QueryReq req =
                    QueryReq.builder()
                            .moduleId(1001L)
                            .tables(specs(1001L, List.of(10001L, 10063L)))
                            .filters(List.of(tf(10063L, "EQ", "62220210001")))
                            .build();

            var plan = queryPlanCompiler.compile(req, metaFor(1001L));
            String cond = String.valueOf(plan.getRootNodePlan().getCondition()).toLowerCase();

            assertTrue(cond.contains("exists"), "经虚拟模块下钻的过滤必须上卷为 EXISTS 半连接，实际=" + cond);
            assertTrue(
                    cond.contains("financial_account"),
                    "EXISTS 子查询必须针对 1008 的物理表 financial_account，实际=" + cond);
            assertTrue(
                    cond.contains("company_id"),
                    "EXISTS 中必须含 info.id = financial_account.company_id 关联，实际=" + cond);
            assertEquals(
                    1,
                    cond.split("exists", -1).length - 1,
                    "虚拟模块 1007 无物理表，必须被穿透 —— EXISTS 只应有 1 层（info -> financial_account），实际=" + cond);

            DataPage<DataBlock> page = queryService.query(req);
            assertNotNull(page);
            assertEquals(1, page.getRecords().size());
        }

        @Test
        @DisplayName("C14 声明了字段的虚拟模块不得被回退成物理模块（不得二次扫描 info）")
        void virtualModuleDeclaringFieldsMustNotBecomePhysical() {
            // 1091 的 primary_table = ''（虚拟），却声明了字段 10911（info.company_name）—— 配置错误。
            // resolvePrimaryTable 只认显式 primary_table，故 1091 既非物理模块、又无物理后代，
            // 不进入投影树；绝不能被回退成对 info 表的第二次物理扫描。
            DataPage<DataBlock> page = queryService.query(req(1001L, List.of(10001L, 10911L)));
            DataRow row = page.getRecords().get(0);

            assertNotNull(row.getTable("info"), "根模块 info 自身的投影不应受影响");
            assertNull(
                    childNodeOrNull(row, 1091L),
                    "虚拟模块 1091 的 primary_table 为空，却被 resolvePrimaryTable 回退成物理表 info，"
                            + "产生了对 info 表的第二次物理扫描");
        }

        @Test
        @DisplayName("C15 一次详情查询只读取一次元数据快照（元数据已作为参数传入编译器）")
        void detailQueryLoadsMetadataSnapshotOnce() {
            // 重构前 compileDetail 自己读一次模块树、内部 compile(QueryReq) 又读一次 ⇒ 2 次，
            // 且 id 过滤条件与投影可能来自两份不同的快照。现在快照由调用方传入，全链路只读一次。
            assertEquals(0, moduleTreeLoads.get(), "前置：setUp 后不应有任何模块树读取");

            DataBlock detail =
                    queryService.getDetail(
                            DetailQueryReq.builder().moduleId(1001L).id(100L).build());

            assertNotNull(detail);
            assertEquals(1, moduleTreeLoads.get(), "一次详情查询应只读取一次模块树快照，实际=" + moduleTreeLoads.get());
        }

        @Test
        @DisplayName("C17 1:1 从表若已是模块主表，不得再作伴生表 JOIN（避免与子模块重复取数）")
        void oneToOneTargetsOwnedByModuleTreeAreNotCompanionJoined() {
            // 真实 company 元数据：info -> basic_info / address / credit_rating 都是 '1:1'，
            // 但三张从表分别就是模块 1002 / 1005 / 1010 的 primary_table ⇒ 已由模块树按子节点取数，
            // 不能再 LEFT JOIN 进根行，否则同一份数据会在报文里出现两次。
            // 反例（真·垂直拆分）见 VerticalSplitContractTest。
            PlanMeta meta = metaFor(1001L);

            var fullTree =
                    queryPlanCompiler.compile(QueryReq.builder().moduleId(1001L).build(), meta);
            assertTrue(
                    fullTree.getRootNodePlan().getAttachedJoins().isEmpty(),
                    "1:1 从表已被模块树认领时不应产生伴生 JOIN，实际="
                            + fullTree.getRootNodePlan().getAttachedJoins());

            var explicit =
                    queryPlanCompiler.compile(
                            QueryReq.builder()
                                    .moduleId(1001L)
                                    .tables(
                                            List.of(
                                                    TableSpec.of(
                                                            1001L,
                                                            "info",
                                                            List.of("company_name"))))
                                    .build(),
                            meta);
            assertTrue(
                    explicit.getRootNodePlan().getAttachedJoins().isEmpty(),
                    "显式投影只含主表列时不应 JOIN 伴生表，实际=" + explicit.getRootNodePlan().getAttachedJoins());
        }

        @Test
        @DisplayName("C18 保存树出现未知数字子模块键时快速失败，不得静默丢弃")
        void unknownNumericChildModuleFailsFast() {
            DataRow row = new DataRow();
            row.putTable("info", new LinkedHashMap<>(Map.of("company_name", "盛港集团")));
            // 999999 不是任何模块：修复前会被 log.warn 吞掉并静默丢弃整棵子树
            row.putTable("999999", new LinkedHashMap<>());
            DataBlock block = DataBlock.builder().moduleId(1001L).records(List.of(row)).build();

            IllegalArgumentException ex =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> savePlanCompiler.compile(block, metaFor(1001L)));
            assertTrue(
                    ex.getMessage().contains("999999"), "错误信息应指出出错的子模块 ID，实际=" + ex.getMessage());
        }
    }

    // ==================================================================
    // 族 2 · 缺陷锚点（断言应有行为；当前不满足，故 @Disabled）
    // ==================================================================

    @Nested
    @DisplayName("族 2 · 缺陷锚点")
    class DefectAnchors {

        @Test
        @Disabled("Q12：子节点自身已带 deleted=0 条件，执行器又追加一次，SQL 出现两个 deleted 谓词")
        @DisplayName("D01 子表 SQL 不应重复追加 deleted = 0")
        void childSqlShouldNotDuplicateDeletedPredicate() {
            queryService.query(req(1001L, List.of(10001L, 10034L, 10038L)));
            String childSql =
                    sqls.stream()
                            .filter(s -> s.contains("from `member`"))
                            .findFirst()
                            .orElseThrow();
            int first = childSql.indexOf("`member`.`deleted` = ?");
            int second = childSql.indexOf("`member`.`deleted` = ?", first + 1);
            assertEquals(-1, second, "deleted 谓词只应出现一次，实际 SQL=" + childSql);
        }

        @Test
        @Disabled("G8：虚拟模块无 id 字段 → compileDetail 抛 IllegalStateException → HTTP 500，应转为可读的 4xx")
        @DisplayName("D02 虚拟模块详情查询不应把内部 IllegalStateException 泄漏成 500")
        void detailOnVirtualModuleShouldNotLeakInternalException() {
            // 无论 G8 最终定性为「设计缺陷 → 转 4xx」还是「缺陷 → 支持虚拟模块详情」，都不应把
            // compileDetail 内部的 IllegalStateException 直接泄漏给调用方（当前 → HTTP 500）。
            try {
                queryService.getDetail(DetailQueryReq.builder().moduleId(1020L).id(1L).build());
            } catch (IllegalStateException e) {
                fail(
                        "G8 未落地：内部 IllegalStateException 泄漏给调用方（→ HTTP 500）。message="
                                + e.getMessage());
            } catch (RuntimeException e) {
                // 已转为明确的业务异常（G8 定性为设计缺陷 → 转 4xx），通过
            }
        }

        @Test
        @Disabled("R2 未落地：过滤字段未投影时未拒绝，且静默把 1003 -> 1004 整条链拉入响应")
        @DisplayName("D03 过滤字段不属于投影集合时应拒绝（R2）")
        void filterFieldOutsideProjectionShouldBeRejected() {
            QueryReq req =
                    QueryReq.builder()
                            .moduleId(1001L)
                            .tables(specs(1001L, List.of(10001L)))
                            .filters(List.of(tf(10046L, "LIKE", "1358")))
                            .build();

            DataPage<DataBlock> page = queryService.query(req);
            DataRow row = page.getRecords().get(0);
            assertTrue(
                    row.getChildren() == null || row.getChildren().isEmpty(),
                    "未投影的过滤字段不应把 1003/1004 拉入响应");
        }

        @Test
        @Disabled("契约缺口：data 中 1:1 与 1:N 形状相同，但 ModuleInfo 未提供 cardinality，前端无法判定渲染形态")
        @DisplayName("D04 ModuleInfo 应携带 cardinality 供前端判定 1:1 / 1:N")
        void moduleInfoShouldExposeCardinality() {
            ModuleInfo info = treeFor(1001L).getModule(1003L);
            assertNotNull(info);
            // 期望：info.getCardinality() == ONE_TO_MANY（相对父模块 1001）
            fail("ModuleInfo 尚无 cardinality 属性，方案 §4.1 未落地");
        }

        @Test
        @Disabled(
                "契约缺口：isMany 由 records.size() > 1 推断，isRawPayloadArray 硬编码 false → 单元素 1:N 被判为 1:1")
        @DisplayName("D05 1:N 子模块即使只有 1 条记录也应判定为 many")
        void singleElementOneToManyShouldStillBeMany() {
            // 1008(financial_account) 相对其父 1007 是 1:N，且 ROW_COUNTS 未覆盖该表 ⇒ 只会返回 1 条记录。
            // 取 1001 的整行（含 1007 -> 1008 的 children 链）直接回灌 DataBlock，验证 isMany 判定。
            DataRow row =
                    queryService.query(req(1001L, List.of(10001L, 10062L))).getRecords().get(0);

            DataBlock saveBlock = DataBlock.builder().moduleId(1001L).records(List.of(row)).build();
            SavePlan plan = savePlanCompiler.compile(saveBlock, metaFor(1001L));

            SaveNodePlan child = findSaveNode(plan.getRootNodePlan(), 1008L);
            assertNotNull(
                    child,
                    "SavePlan 树中未找到 1008。若为 null，多半是 SavePlanCompiler 未适配 children 形态，"
                            + "而非 isMany 本身的问题");
            assertTrue(
                    child.isMany(),
                    "1008 相对 1007 是 1:N，即使本次只有 1 条记录也必须 isMany=true；"
                            + "当前 isMany = records.size() > 1 且 isRawPayloadArray 硬编码 false");
        }

        @Test
        @Disabled("Q11：同模块 1:N 属配置错误，应由配置校验拒绝；当前会挂成数字键并静默返回")
        @DisplayName("D06 同模块 1:N 配置应被拒绝（配置校验），而非产生数字键挂载")
        void sameModuleOneToManyShouldBeRejectedByConfigValidation() {
            // 1090 是 R1 违规配置：主表 info 上投影了 1:N 从表 member 的字段（10902）。
            IllegalArgumentException ex =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    queryPlanCompiler.compile(
                                            req(1090L, List.of(10901L, 10902L)), metaFor(1090L)),
                            "R1 违规配置（同模块 1:N）应在编译期被拒绝，而不是静默挂成数字键返回");
            assertTrue(
                    String.valueOf(ex.getMessage()).contains("1:N")
                            || String.valueOf(ex.getMessage()).contains("同模块"),
                    "拒绝原因应指向同模块 1:N，实际=" + ex.getMessage());
        }
    }

    // ==================================================================
    // 断言辅助
    // ==================================================================

    /** 在 SavePlan 树中按模块 ID 深度优先查找节点 */
    private static SaveNodePlan findSaveNode(SaveNodePlan node, long moduleId) {
        if (node == null) {
            return null;
        }
        if (Long.valueOf(moduleId).equals(node.getModuleId())) {
            return node;
        }
        for (SaveNodePlan child : node.getChildren()) {
            SaveNodePlan hit = findSaveNode(child, moduleId);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private static QueryReq req(Long moduleId, List<Long> fields) {
        return QueryReq.builder().moduleId(moduleId).tables(specs(moduleId, fields)).build();
    }

    /**
     * 将 fieldId 列表按物理表分组为 TableSpec，语义与旧版 fields 投影完全一致。
     *
     * <p>fieldIds 为空时等价于旧版「不传 fields」：仅投影根模块自身字段，不展开子树。
     */
    private static List<TableSpec> specs(Long moduleId, List<Long> fieldIds) {
        if (fieldIds == null || fieldIds.isEmpty()) {
            ModuleInfo root = MODS.get(moduleId);
            String rootTable = root != null ? root.getPrimaryTable() : null;
            if (rootTable == null || rootTable.isBlank()) {
                return new ArrayList<>();
            }
            List<String> cols = new ArrayList<>();
            if (root.getFields() != null) {
                for (ModuleFieldDTO f : root.getFields()) {
                    if (f.getColumnName() != null && !f.getColumnName().isBlank()) {
                        cols.add(f.getColumnName());
                    }
                }
            }
            return new ArrayList<>(List.of(TableSpec.of(root.getModuleId(), rootTable, cols)));
        }
        record ModTblKey(Long moduleId, String tableName) {}
        Map<ModTblKey, LinkedHashSet<String>> grouped = new LinkedHashMap<>();
        for (Long fid : fieldIds) {
            ModuleFieldDTO f = FMAP.get(fid);
            if (f == null) {
                throw new IllegalArgumentException("元数据 fixture 中不存在字段: " + fid);
            }
            String t =
                    (f.getTableName() != null && !f.getTableName().isBlank())
                            ? f.getTableName().trim()
                            : primaryTableOf(f.getModuleId());
            grouped.computeIfAbsent(new ModTblKey(f.getModuleId(), t), k -> new LinkedHashSet<>())
                    .add(f.getColumnName());
        }
        List<TableSpec> result = new ArrayList<>();
        grouped.forEach(
                (k, cols) ->
                        result.add(
                                TableSpec.of(k.moduleId(), k.tableName(), new ArrayList<>(cols))));
        return result;
    }

    private static String primaryTableOf(Long moduleId) {
        ModuleInfo m = MODS.get(moduleId);
        return m != null ? m.getPrimaryTable() : null;
    }

    private static FilterItem tf(long fieldId, String operator, Object value) {
        ModuleFieldDTO f = FMAP.get(fieldId);
        String t =
                (f.getTableName() != null && !f.getTableName().isBlank())
                        ? f.getTableName().trim()
                        : primaryTableOf(f.getModuleId());
        return FilterItem.builder()
                .tableName(t)
                .columnName(f.getColumnName())
                .operator(operator)
                .value(value)
                .build();
    }

    private static List<DataBlock> childrenOf(DataRow row) {
        List<DataBlock> v = row.getChildren();
        assertNotNull(v, "期望存在行级 children 数组");
        return v;
    }

    /** 在 children 中按 moduleId 取节点，不存在时返回 null（用于断言节点"不应出现"）。 */
    private static DataBlock childNodeOrNull(DataRow row, long moduleId) {
        List<DataBlock> children = row.getChildren();
        if (children == null) {
            return null;
        }
        for (DataBlock n : children) {
            if (n.getModuleId() != null && n.getModuleId().longValue() == moduleId) {
                return n;
            }
        }
        return null;
    }

    /** 在 children 中按 moduleId 取节点 —— 物理模块与虚拟模块统一走这里。 */
    private static DataBlock childNode(DataRow row, long moduleId) {
        for (DataBlock n : childrenOf(row)) {
            if (n.getModuleId() != null && n.getModuleId().longValue() == moduleId) {
                return n;
            }
        }
        fail("children 中不存在 moduleId=" + moduleId + " 的节点，实际=" + childrenOf(row));
        return null;
    }

    private static List<DataRow> recordsOf(DataBlock node) {
        List<DataRow> v = node.getRecords();
        assertNotNull(v, "期望 records 数组");
        return v;
    }

    private static Map<String, Object> table(DataRow row, String tableName) {
        Map<String, Object> v = row.getTable(tableName);
        assertNotNull(v, "期望存在表空间键 " + tableName);
        return v;
    }

    private static void assertNoTechnicalKey(Object node, String path) {
        if (node instanceof DataRow r) {
            if (r.getTables() != null) {
                for (Map.Entry<String, Object> e : r.getTables().entrySet()) {
                    String k = e.getKey();
                    assertFalse(
                            k.startsWith("_fk_") || k.equals("_row_id"),
                            "技术字段泄露于 " + path + " -> " + k);
                    assertNoTechnicalKey(e.getValue(), path + "." + k);
                }
            }
            if (r.getChildren() != null) {
                assertNoTechnicalKey(r.getChildren(), path + ".children");
            }
        } else if (node instanceof DataBlock n) {
            if (n.getRecords() != null) {
                assertNoTechnicalKey(n.getRecords(), path + ".records");
            }
        } else if (node instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String k = String.valueOf(e.getKey());
                assertFalse(
                        k.startsWith("_fk_") || k.equals("_row_id"),
                        "技术字段泄露于 " + path + " -> " + k);
                assertNoTechnicalKey(e.getValue(), path + "." + k);
            }
        } else if (node instanceof List<?> l) {
            for (int i = 0; i < l.size(); i++) {
                assertNoTechnicalKey(l.get(i), path + "[" + i + "]");
            }
        }
    }

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
    }
}
