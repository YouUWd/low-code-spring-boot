package com.jdec.platform.common.biz.action;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jdec.platform.common.api.dto.response.ActionButtonDTO;
import com.jdec.platform.common.api.dto.response.ActionRowItem;
import com.jdec.platform.common.biz.custom.ActiveToggleButtonProvider;
import com.jdec.platform.common.biz.custom.RowCustomActionService;
import com.jdec.platform.common.biz.interaction.DefaultInteractionButtonProvider;
import com.jdec.platform.common.biz.interaction.InteractionActionContext;
import com.jdec.platform.common.biz.interaction.InteractionActionService;
import com.jdec.platform.config.api.SysRolePermissionApi;
import com.jdec.platform.config.api.dto.response.RoleInteractionPermissionByModuleResp;
import com.jdec.platform.data.api.dto.common.DataRow;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 菜单按钮集中中枢 (MenuActionButtonService) 单元测试：集中处理调度，数据独立输出 */
class MenuActionButtonServiceTest {

    private MenuActionButtonService menuActionButtonService;
    private SysRolePermissionApi sysRolePermissionApi;

    @BeforeEach
    void setUp() {
        // 1. 定制操作服务 (如启停)
        RowCustomActionService rowCustomActionService =
                new RowCustomActionService(List.of(new ActiveToggleButtonProvider()));

        // 2. 角色交互权限服务 (注册通用默认 Provider 与业务定制子类 Provider)
        DefaultInteractionButtonProvider customCompanyProvider =
                new DefaultInteractionButtonProvider() {
                    @Override
                    public String target() {
                        return "company:*";
                    }

                    @Override
                    protected boolean isRowLevelPermission(
                            RoleInteractionPermissionByModuleResp.InteractionPermissionItem perm,
                            InteractionActionContext ctx) {
                        return "edit_company_reg_status".equals(perm.getPermissionCode());
                    }
                };

        InteractionActionService interactionActionService =
                new InteractionActionService(
                        List.of(new DefaultInteractionButtonProvider(), customCompanyProvider));

        // 3. Mock 权限配置中心
        sysRolePermissionApi = mock(SysRolePermissionApi.class);

        menuActionButtonService =
                new MenuActionButtonService(
                        rowCustomActionService, interactionActionService, sysRolePermissionApi);
    }

    @Test
    @DisplayName("测试集中处理调度：customActions(启停) 与 interactionActions(互动权限) 均独立正确输出")
    void testBuildActionsCentralizedAndIndependent() {
        // 模拟权限配置中心返回 1 个申请权限 (全局) 和 1 个业务操作权限 (行级)
        RoleInteractionPermissionByModuleResp.InteractionPermissionItem applyItem =
                new RoleInteractionPermissionByModuleResp.InteractionPermissionItem();
        applyItem.setPermissionCode("company_apply");
        applyItem.setPermissionName("外部对象申请");
        applyItem.setType(1);

        RoleInteractionPermissionByModuleResp.InteractionPermissionItem regStatusItem =
                new RoleInteractionPermissionByModuleResp.InteractionPermissionItem();
        regStatusItem.setPermissionCode("edit_company_reg_status");
        regStatusItem.setPermissionName("外部对象修改登记状态");
        regStatusItem.setType(2);

        RoleInteractionPermissionByModuleResp permResp =
                new RoleInteractionPermissionByModuleResp();
        permResp.setApplyPermissions(List.of(applyItem));
        permResp.setEditPermissions(List.of(regStatusItem));
        permResp.setViewPermissions(Collections.emptyList());

        when(sysRolePermissionApi.getRoleInteractionPermissionByModule(10L, 1001L))
                .thenReturn(permResp);

        MenuActionContext ctx =
                MenuActionContext.builder()
                        .projectNo("company")
                        .rootModuleId(1001L)
                        .moduleCode("COMPANY-ROOT")
                        .primaryTable("info")
                        .enableActive(1) // 开启启停
                        .userId(1L)
                        .roleId(10L)
                        .hasEditPermission(true)
                        .build();

        // 模拟 1 行数据：处于启用状态 (99)
        DataRow row = new DataRow();
        Map<String, Object> table = new HashMap<>();
        table.put("id", 888L);
        table.put("business_no", "CR8888");
        table.put("business_status", 99);
        row.setTables(Map.of("info", table));

        // 执行集中处理
        MenuActionBundle bundle = menuActionButtonService.buildActions(List.of(row), ctx);

        assertNotNull(bundle);

        // 1. 验证 customActions (独立输出启停操作：99状态输出停用按钮)
        List<ActionRowItem> customActions = bundle.getCustomActions();
        assertNotNull(customActions);
        assertEquals(1, customActions.size());
        assertEquals(888L, customActions.get(0).getId());
        assertEquals("CR8888", customActions.get(0).getBusinessNo());
        List<ActionButtonDTO> customBtns = customActions.get(0).getBtnList();
        assertNotNull(customBtns);
        assertEquals("deactivate", customBtns.get(0).getButtonKey());
        assertEquals("停用", customBtns.get(0).getButtonName());

        // 2. 验证 interactionActions (独立输出互动权限：1项全局申请 + 1项行级修改登记状态)
        List<ActionRowItem> interactionActions = bundle.getInteractionActions();
        assertNotNull(interactionActions);
        assertEquals(2, interactionActions.size());

        // 第 1 项：全局申请操作 (无 id/businessNo)
        assertNull(interactionActions.get(0).getId());
        assertNull(interactionActions.get(0).getBusinessNo());

        // 第 2 项：行级登记状态修改操作 (带 id/businessNo)
        assertEquals(888L, interactionActions.get(1).getId());
        assertEquals("CR8888", interactionActions.get(1).getBusinessNo());
    }

    @Test
    @DisplayName("测试短路查询时集中输出空包")
    void testBuildEmptyActions() {
        MenuActionContext ctx =
                MenuActionContext.builder()
                        .projectNo("company")
                        .rootModuleId(1001L)
                        .moduleCode("COMPANY-ROOT")
                        .primaryTable("info")
                        .enableActive(1)
                        .userId(1L)
                        .roleId(10L)
                        .hasEditPermission(true)
                        .build();

        MenuActionBundle bundle = menuActionButtonService.buildEmptyActions(ctx);
        assertNotNull(bundle);
        assertNotNull(bundle.getCustomActions());
        assertTrue(bundle.getCustomActions().isEmpty());
    }
}
