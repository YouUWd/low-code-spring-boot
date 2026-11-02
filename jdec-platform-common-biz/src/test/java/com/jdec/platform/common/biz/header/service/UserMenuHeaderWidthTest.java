package com.jdec.platform.common.biz.header.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.common.api.dto.request.SaveUserMenuHeaderWidthReq;
import com.jdec.platform.config.api.dto.response.SysMenuHeaderResp;
import com.jdec.platform.config.open.api.OpenMenuHeaderApi;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.datasource.JooqDataContextFactory;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.security.model.LoginUser;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 用户个性化表头「拖动列宽」单元测试（jOOQ MockConnection 断言真实落库语句） */
class UserMenuHeaderWidthTest {

    private static final Long USER_ID = 7L;
    private static final Long ROLE_ID = 9L;
    private static final Long LIST_MENU_ID = 100L;
    private static final Long HEADER_MENU_ID = 200L;
    private static final Long FIELD_ID = 3001L;
    private static final Long OTHER_FIELD_ID = 3002L;
    private static final Integer WIDTH = 237;
    private static final Integer OLD_WIDTH = 120;

    /** 结果列名与类型，与 {@code UserMenuHeaderService.USER_NODE_COLUMNS} 的别名一一对应（fetchInto 按名映射） */
    private static final Map<String, Class<?>> NODE_COLUMNS = nodeColumns();

    /** 已执行的 SQL（含绑定值），供断言语句类型与取值 */
    private final List<String> sqls = new ArrayList<>();

    /** {@code user_menu_header} 的模拟存量记录，按用例填充 */
    private final List<Map<String, Object>> storedRows = new ArrayList<>();

    private OpenMenuHeaderApi openMenuHeaderApi;
    private UserMenuHeaderService service;

    @BeforeEach
    void setUp() {
        AppContext.setContext("company", 1L);
        UserContext.setLoginUser(LoginUser.builder().userId(USER_ID).roleId(ROLE_ID).build());
        sqls.clear();
        storedRows.clear();

        openMenuHeaderApi = mock(OpenMenuHeaderApi.class);
        when(openMenuHeaderApi.resolveHeaderMenuId(LIST_MENU_ID)).thenReturn(HEADER_MENU_ID);
        when(openMenuHeaderApi.getMenuHeaderTree(LIST_MENU_ID, ROLE_ID)).thenReturn(defaultTree());

        DSLContext dsl = DSL.using(new MockConnection(provider()), SQLDialect.MYSQL);
        JooqDataContextFactory factory =
                new JooqDataContextFactory(null) {
                    @Override
                    public DSLContext getContext() {
                        return dsl;
                    }

                    @Override
                    public DSLContext getContext(String projectNo) {
                        return dsl;
                    }
                };
        service = new UserMenuHeaderService(factory, openMenuHeaderApi, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        AppContext.remove();
        UserContext.remove();
    }

    @Test
    @DisplayName("无任何个性化记录：整棵用户表头树落库，目标列写入宽度")
    void initWholeTreeWithWidth() {
        service.saveUserMenuHeaderWidth(widthReq(1, FIELD_ID, "info.company_name", WIDTH));

        assertEquals(3, countStatements("insert into"), "根菜单 + 两个字段节点全部落库");
        assertEquals(0, countStatements("update"), "首次个性化只做新增");
        assertTrue(anyStatement("insert into", "Field_3001", "237"), "目标列落库时带上拖动的宽度");
        assertTrue(noStatement("insert into", "Field_3002", "237"), "其余节点宽度不受本次影响");
    }

    @Test
    @DisplayName("已有该列记录：只更新宽度，不再重建表头树")
    void updateExistingRecordOnly() {
        storedRows.add(
                storedRow(11L, "Field_3001", 1, FIELD_ID, "info.company_name", 1, OLD_WIDTH));

        service.saveUserMenuHeaderWidth(widthReq(1, FIELD_ID, "info.company_name", WIDTH));

        assertEquals(1, countStatements("update"), "只更新命中的那一条记录");
        assertEquals(0, countStatements("insert into"));
        assertTrue(anyStatement("update", "237"), "更新语句写入新宽度");
        verify(openMenuHeaderApi, never()).getMenuHeaderTree(any(), any());
    }

    @Test
    @DisplayName("该列未勾选：拒绝设置宽度且不落库")
    void rejectUncheckedColumn() {
        storedRows.add(
                storedRow(11L, "Field_3001", 1, FIELD_ID, "info.company_name", 0, OLD_WIDTH));

        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.saveUserMenuHeaderWidth(
                                        widthReq(1, FIELD_ID, "info.company_name", WIDTH)));

        assertTrue(ex.getMessage().contains("未勾选"), ex.getMessage());
        assertEquals(0, countStatements("update"));
    }

    @Test
    @DisplayName("菜单节点：不支持设置列宽")
    void rejectMenuNode() {
        storedRows.add(storedRow(12L, "MENU_200", 0, HEADER_MENU_ID, null, 1, OLD_WIDTH));

        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.saveUserMenuHeaderWidth(
                                        widthReq(0, HEADER_MENU_ID, null, WIDTH)));

        assertTrue(ex.getMessage().contains("仅字段列"), ex.getMessage());
        assertEquals(0, countStatements("update"));
    }

    @Test
    @DisplayName("已有其它列记录、目标列无记录：只补插该列并写入宽度")
    void insertMissingColumnOnly() {
        storedRows.add(storedRow(13L, "Field_3002", 1, OTHER_FIELD_ID, "info.uscc", 1, OLD_WIDTH));

        service.saveUserMenuHeaderWidth(widthReq(1, FIELD_ID, "info.company_name", WIDTH));

        assertEquals(1, countStatements("insert into"), "只补插目标列一条记录");
        assertEquals(0, countStatements("update"), "其余个性化记录保持不动");
        assertTrue(anyStatement("insert into", "Field_3001", "237"));
    }

    @Test
    @DisplayName("目标列在表头树中不存在：提示节点不存在")
    void rejectUnknownColumn() {
        storedRows.add(storedRow(13L, "Field_3002", 1, OTHER_FIELD_ID, "info.uscc", 1, OLD_WIDTH));

        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () -> service.saveUserMenuHeaderWidth(widthReq(1, 9999L, null, WIDTH)));

        assertTrue(ex.getMessage().contains("不存在"), ex.getMessage());
        assertEquals(0, countStatements("insert into"));
    }

    @Test
    @DisplayName("virId 未命中时按字段索引兜底定位已保存记录")
    void matchByDataIndexWhenVirIdMisses() {
        storedRows.add(storedRow(14L, "Field_5555", 1, 5555L, "info.company_name", 1, OLD_WIDTH));

        service.saveUserMenuHeaderWidth(widthReq(1, FIELD_ID, "info.company_name", WIDTH));

        assertEquals(1, countStatements("update"), "按 dataIndex 兜底命中后只更新宽度");
        assertEquals(0, countStatements("insert into"));
    }

    @Test
    @DisplayName("参数校验：菜单ID 与列宽非法时直接拒绝")
    void validateParams() {
        SaveUserMenuHeaderWidthReq noMenu = widthReq(1, FIELD_ID, null, WIDTH);
        noMenu.setMenuId(null);
        assertTrue(
                assertThrows(BusinessException.class, () -> service.saveUserMenuHeaderWidth(noMenu))
                        .getMessage()
                        .contains("菜单ID"));

        assertTrue(
                assertThrows(
                                BusinessException.class,
                                () ->
                                        service.saveUserMenuHeaderWidth(
                                                widthReq(1, FIELD_ID, null, 0)))
                        .getMessage()
                        .contains("列宽"));
    }

    // ==================== 测试装置 ====================

    private static SaveUserMenuHeaderWidthReq widthReq(
            Integer nodeType, Long sourceId, String dataIndex, Integer width) {
        SaveUserMenuHeaderWidthReq req = new SaveUserMenuHeaderWidthReq();
        req.setMenuId(LIST_MENU_ID);
        req.setNodeType(nodeType);
        req.setSourceId(sourceId);
        req.setDataIndex(dataIndex);
        req.setWidth(width);
        return req;
    }

    /** 默认表头树：根菜单 + 已勾选字段(3001) + 未勾选字段(3002)，与 config 侧生成结构同形 */
    private static SysMenuHeaderResp defaultTree() {
        SysMenuHeaderResp root = new SysMenuHeaderResp();
        root.setVirId("MENU_" + HEADER_MENU_ID);
        root.setMenuId(HEADER_MENU_ID);
        root.setNodeType(0);
        root.setSourceId(HEADER_MENU_ID);
        root.setChecked(0);
        root.setSorted(1);
        root.setSortValue(1);
        root.setChildren(
                List.of(
                        fieldNode(FIELD_ID, "info.company_name", 1, 1),
                        fieldNode(OTHER_FIELD_ID, "info.uscc", 0, 2)));
        return root;
    }

    private static SysMenuHeaderResp fieldNode(
            Long fieldId, String dataIndex, Integer checked, Integer sortValue) {
        SysMenuHeaderResp node = new SysMenuHeaderResp();
        node.setVirId("Field_" + fieldId);
        node.setMenuId(HEADER_MENU_ID);
        node.setParentId("MENU_" + HEADER_MENU_ID);
        node.setNodeType(1);
        node.setSourceId(fieldId);
        node.setDataIndex(dataIndex);
        node.setChecked(checked);
        node.setSorted(1);
        node.setEllipsis(0);
        node.setSortValue(sortValue);
        return node;
    }

    /** {@code user_menu_header} 的一行模拟记录（列名与查询别名一致，未给出的列为 null） */
    private static Map<String, Object> storedRow(
            Long id,
            String virId,
            Integer nodeType,
            Long sourceId,
            String dataIndex,
            Integer checked,
            Integer width) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("userId", USER_ID);
        row.put("virId", virId);
        row.put("menuId", HEADER_MENU_ID);
        row.put("nodeType", nodeType);
        row.put("sourceId", sourceId);
        row.put("dataIndex", dataIndex);
        row.put("checked", checked);
        row.put("width", width);
        row.put("sorted", 1);
        row.put("sortValue", 1);
        return row;
    }

    private MockDataProvider provider() {
        return ctx -> {
            // 绑定值不体现在 ctx.sql() 里，拼在后面供断言取值
            sqls.add(ctx.sql() + " " + Arrays.toString(ctx.bindings()));
            if (!ctx.sql().trim().toLowerCase().startsWith("select")) {
                return new MockResult[] {new MockResult(1)};
            }
            DSLContext dsl = DSL.using(SQLDialect.MYSQL);
            List<Field<?>> columns = new ArrayList<>();
            NODE_COLUMNS.forEach((name, type) -> columns.add(DSL.field(name, type)));
            Field<?>[] fields = columns.toArray(new Field<?>[0]);
            Result<Record> result = dsl.newResult(fields);
            for (Map<String, Object> row : storedRows) {
                Record record = dsl.newRecord(fields);
                for (Field<?> field : fields) {
                    record.set(raw(field), row.get(field.getName()));
                }
                result.add(record);
            }
            return new MockResult[] {new MockResult(result.size(), result)};
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Field<Object> raw(Field<?> field) {
        return (Field<Object>) field;
    }

    /** 是否存在以 {@code prefix} 开头、且同时包含全部片段的语句 */
    private boolean anyStatement(String prefix, String... fragments) {
        return sqls.stream()
                .filter(sql -> startsWith(sql, prefix))
                .anyMatch(sql -> Stream.of(fragments).allMatch(sql::contains));
    }

    private boolean noStatement(String prefix, String... fragments) {
        return !anyStatement(prefix, fragments);
    }

    private long countStatements(String prefix) {
        return sqls.stream().filter(sql -> startsWith(sql, prefix)).count();
    }

    private static boolean startsWith(String sql, String prefix) {
        return sql.trim().toLowerCase().startsWith(prefix);
    }

    private static Map<String, Class<?>> nodeColumns() {
        Map<String, Class<?>> columns = new LinkedHashMap<>();
        columns.put("id", Long.class);
        columns.put("userId", Long.class);
        columns.put("virId", String.class);
        columns.put("menuId", Long.class);
        columns.put("nodeType", Integer.class);
        columns.put("sourceId", Long.class);
        columns.put("colors", String.class);
        columns.put("width", Integer.class);
        columns.put("dataIndex", String.class);
        columns.put("sorted", Integer.class);
        columns.put("searchType", String.class);
        columns.put("ellipsis", Integer.class);
        columns.put("fixed", String.class);
        columns.put("checked", Integer.class);
        columns.put("sortValue", Integer.class);
        return columns;
    }
}
