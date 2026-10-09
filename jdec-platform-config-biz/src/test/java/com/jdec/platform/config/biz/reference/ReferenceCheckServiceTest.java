package com.jdec.platform.config.biz.reference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.jdec.platform.config.biz.entity.SysConfigCategory;
import com.jdec.platform.config.biz.entity.SysConfigItem;
import com.jdec.platform.config.biz.entity.SysMenu;
import com.jdec.platform.config.biz.entity.SysModule;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

/** 验证 {@link ReferenceCheckService} 的引用查询与提示文案。 */
@ExtendWith(MockitoExtension.class)
class ReferenceCheckServiceTest {

    @Mock private ReferenceRegistry referenceRegistry;
    @Mock private JdbcTemplate jdbcTemplate;
    @InjectMocks private ReferenceCheckService referenceCheckService;

    private static final ReferenceMetadata MENU_TO_MODULE =
            new ReferenceMetadata(
                    SysMenu.class,
                    "sys_menu",
                    "module_id",
                    "id",
                    "title",
                    "菜单",
                    SysModule.class,
                    "sys_module",
                    "id");

    @Test
    void check_noReferences_returnsEmpty() {
        when(referenceRegistry.findByTarget(SysModule.class)).thenReturn(List.of());
        assertTrue(referenceCheckService.check(SysModule.class, List.of(1L)).isEmpty());
    }

    @Test
    void check_emptyIds_returnsEmpty() {
        assertTrue(referenceCheckService.check(SysModule.class, List.of()).isEmpty());
        verifyNoInteractions(referenceRegistry);
    }

    @Test
    void check_foundReferences_returnsViolationWithDetails() throws Exception {
        when(referenceRegistry.findByTarget(SysModule.class)).thenReturn(List.of(MENU_TO_MODULE));
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("src_val")).thenReturn(100L);
        when(rs.getObject("src_pk")).thenReturn(1001L);
        when(rs.getObject("src_display")).thenReturn("信息管理");

        doAnswer(
                        invocation -> {
                            RowCallbackHandler handler = invocation.getArgument(1);
                            handler.processRow(rs);
                            return null;
                        })
                .when(jdbcTemplate)
                .query(anyString(), any(RowCallbackHandler.class), any(Object[].class));

        List<ReferenceViolation> violations =
                referenceCheckService.check(SysModule.class, List.of(100L, 200L));

        assertEquals(1, violations.size());
        ReferenceViolation violation = violations.get(0);
        assertEquals(100L, violation.getTargetId());
        assertEquals("sys_menu", violation.getSourceTable());
        assertEquals("module_id", violation.getSourceField());
        assertEquals("菜单", violation.getSourceName());
        assertEquals(List.of(1001L), violation.getSourceIds());
        assertEquals(List.of("信息管理"), violation.getDisplayValues());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate)
                .query(sqlCaptor.capture(), any(RowCallbackHandler.class), any(Object[].class));
        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("FROM sys_menu"), sql);
        assertTrue(sql.contains("module_id IN (?,?)"), sql);
    }

    @Test
    void check_byStringKey_groupsByAlias() throws Exception {
        ReferenceMetadata itemToCategory =
                new ReferenceMetadata(
                        SysConfigItem.class,
                        "sys_config_item",
                        "category_alias",
                        "id",
                        "label",
                        "配置项",
                        SysConfigCategory.class,
                        "sys_config_category",
                        "categoryAlias");
        when(referenceRegistry.findByTarget(SysConfigCategory.class, "categoryAlias"))
                .thenReturn(List.of(itemToCategory));
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("src_val")).thenReturn("employee_status");
        when(rs.getObject("src_pk")).thenReturn(7L);
        when(rs.getObject("src_display")).thenReturn("员工状态");

        doAnswer(
                        invocation -> {
                            RowCallbackHandler handler = invocation.getArgument(1);
                            handler.processRow(rs);
                            return null;
                        })
                .when(jdbcTemplate)
                .query(anyString(), any(RowCallbackHandler.class), any(Object[].class));

        List<ReferenceViolation> violations =
                referenceCheckService.check(
                        SysConfigCategory.class, "categoryAlias", List.of("employee_status"));

        assertEquals(1, violations.size());
        ReferenceViolation violation = violations.get(0);
        assertEquals("employee_status", violation.getTargetId());
        assertEquals("sys_config_item", violation.getSourceTable());
        assertEquals("category_alias", violation.getSourceField());
        assertEquals("配置项", violation.getSourceName());
        assertEquals(List.of(7L), violation.getSourceIds());
        assertEquals(List.of("员工状态"), violation.getDisplayValues());
    }

    @Test
    void buildMessage_formatsViolations() {
        ReferenceViolation violation =
                ReferenceViolation.builder()
                        .targetId(100L)
                        .sourceTable("sys_menu")
                        .sourceField("module_id")
                        .sourceName("菜单")
                        .sourceIds(List.of(1001L, 1005L))
                        .displayValues(List.of("信息管理", "用户管理"))
                        .build();
        String message = referenceCheckService.buildMessage("信息模块", List.of(violation));
        assertTrue(message.contains("无法删除【信息模块】"), message);
        assertTrue(message.contains("菜单(sys_menu.module_id)：2 条 [1001, 1005]"), message);
        assertTrue(message.contains("名称：[信息管理, 用户管理]"), message);
    }
}
