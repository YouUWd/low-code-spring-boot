package com.jdec.platform.common.biz.interaction;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.common.api.dto.response.ActionButtonDTO;
import com.jdec.platform.common.api.dto.response.ApprovalRowItem;
import com.jdec.platform.config.api.dto.response.RoleInteractionPermissionByModuleResp;
import com.jdec.platform.data.api.dto.common.DataRow;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 互动权限操作分发与模块定制逻辑单元测试 */
class InteractionActionServiceTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("测试默认Provider机制：不区分apply/edit等，默认全部互动权限项均为全局按钮输出")
    void testDefaultProviderAllGlobalByDefault() throws Exception {
        // 1. 模拟当前角色拥有的模块交互权限：包含 1 个申请权限与 2 个编辑操作权限
        RoleInteractionPermissionByModuleResp permResp = buildTestPermissions();

        InteractionActionContext ctx =
                InteractionActionContext.builder()
                        .projectNo("hr")
                        .rootModuleId(2001L)
                        .moduleCode("HR-ROOT")
                        .primaryTable("hr_emp")
                        .userId(1L)
                        .roleId(10L)
                        .hasEditPermission(true)
                        .modulePermissionResp(permResp)
                        .assignedPermissionCodes(
                                Set.of(
                                        "company_apply",
                                        "update_company_info",
                                        "edit_company_reg_status"))
                        .build();

        DataRow row1 = createDataRow(301L, "CR20261027001");
        DataRow row2 = createDataRow(302L, "CR20261027002");

        // 仅注册通用的 DefaultInteractionButtonProvider
        InteractionActionService service =
                new InteractionActionService(List.of(new DefaultInteractionButtonProvider()));

        List<ApprovalRowItem> actions = service.buildInteractionActions(List.of(row1, row2), ctx);

        assertNotNull(actions);
        // 默认全部为全局项：因此仅有 1 个全局 row (无 id 与 businessNo)，包含全部 3 个互动按钮
        assertEquals(1, actions.size());

        ApprovalRowItem globalAction = actions.get(0);
        assertNotNull(globalAction.getRow());
        assertNull(globalAction.getRow().get("id"));
        assertNull(globalAction.getRow().get("businessNo"));

        @SuppressWarnings("unchecked")
        List<ActionButtonDTO> btns = (List<ActionButtonDTO>) globalAction.getRow().get("btnList");
        assertEquals(3, btns.size());

        String json = objectMapper.writeValueAsString(actions);
        assertTrue(json.contains("\"buttonKey\":\"company_apply\""));
        assertTrue(json.contains("\"buttonKey\":\"update_company_info\""));
        assertTrue(json.contains("\"buttonKey\":\"edit_company_reg_status\""));
    }

    @Test
    @DisplayName("测试业务定制继承机制：重写isRowLevelPermission将特定权限修改为非全局(行级)")
    void testCustomSubclassOverrideToRowLevel() throws Exception {
        RoleInteractionPermissionByModuleResp permResp = buildTestPermissions();

        InteractionActionContext ctx =
                InteractionActionContext.builder()
                        .projectNo("company")
                        .rootModuleId(1001L)
                        .moduleCode("COMPANY-ROOT")
                        .primaryTable("info")
                        .userId(1L)
                        .roleId(10L)
                        .hasEditPermission(true)
                        .modulePermissionResp(permResp)
                        .assignedPermissionCodes(
                                Set.of(
                                        "company_apply",
                                        "update_company_info",
                                        "edit_company_reg_status"))
                        .build();

        DataRow row1 = createDataRow(301L, "CR20261027001");
        DataRow row2 = createDataRow(302L, "CR20261027002");

        // 模拟特定模块业务定制 Provider (继承 DefaultInteractionButtonProvider)
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
                        // 业务定制：将修改登记状态定制为非全局 (行级)
                        if ("edit_company_reg_status".equals(perm.getPermissionCode())) {
                            return true;
                        }
                        return super.isRowLevelPermission(perm, ctx);
                    }
                };

        // 注册通用默认 Provider 和业务专属定制 Provider
        InteractionActionService service =
                new InteractionActionService(
                        List.of(new DefaultInteractionButtonProvider(), customCompanyProvider));

        List<ApprovalRowItem> actions = service.buildInteractionActions(List.of(row1, row2), ctx);

        assertNotNull(actions);
        // 预期共 3 项：第 1 项为全局操作 (2个按钮)；第 2 项为 301L 行绑定操作；第 3 项为 302L 行绑定操作
        assertEquals(3, actions.size());

        // 1. 全局项：应包含申请和修改信息 (不含被移至行级的修改登记状态)
        ApprovalRowItem globalAction = actions.get(0);
        assertNull(globalAction.getRow().get("id"));
        @SuppressWarnings("unchecked")
        List<ActionButtonDTO> globalBtns =
                (List<ActionButtonDTO>) globalAction.getRow().get("btnList");
        assertEquals(2, globalBtns.size());

        // 2. 行级项：行 301L 与 302L 应分别输出行级互动按钮 (修改登记状态)
        ApprovalRowItem rowAction1 = actions.get(1);
        assertEquals(301L, rowAction1.getRow().get("id"));
        assertEquals("CR20261027001", rowAction1.getRow().get("businessNo"));
        @SuppressWarnings("unchecked")
        List<ActionButtonDTO> rowBtns1 = (List<ActionButtonDTO>) rowAction1.getRow().get("btnList");
        assertEquals(1, rowBtns1.size());
        assertEquals("edit_company_reg_status", rowBtns1.get(0).getButtonKey());

        ApprovalRowItem rowAction2 = actions.get(2);
        assertEquals(302L, rowAction2.getRow().get("id"));
        assertEquals("CR20261027002", rowAction2.getRow().get("businessNo"));
    }

    @Test
    @DisplayName("测试1001模块真实定制组件：edit_company_reg_status仅在启用(99)与取消(-99)状态下按行展示")
    void testCompanyModule1001CustomInteractionButtons() {
        RoleInteractionPermissionByModuleResp permResp = buildTestPermissions();

        InteractionActionContext ctx =
                InteractionActionContext.builder()
                        .projectNo("company")
                        .rootModuleId(1001L) // 目标模块 ID = 1001
                        .moduleCode("COMPANY-ROOT")
                        .primaryTable("info")
                        .userId(1L)
                        .roleId(10L)
                        .hasEditPermission(true)
                        .modulePermissionResp(permResp)
                        .assignedPermissionCodes(
                                Set.of(
                                        "company_apply",
                                        "update_company_info",
                                        "edit_company_reg_status"))
                        .build();

        // 构造4行不同业务状态的数据行
        DataRow rowActive = createDataRow(101L, "CR-ACTIVE", 99); // 启用状态 (99) -> 应该展示
        DataRow rowCancel = createDataRow(102L, "CR-CANCEL", -99); // 取消状态 (-99) -> 应该展示
        DataRow rowDeactive = createDataRow(103L, "CR-DEACTIVE", 11); // 停用状态 (11) -> 不应该展示
        DataRow rowDraft = createDataRow(104L, "CR-DRAFT", 1); // 待审核/草稿状态 (1) -> 不应该展示

        // 注册通用默认 Provider 与真实定制的 CompanyInteractionButtonProvider
        InteractionActionService service =
                new InteractionActionService(
                        List.of(
                                new DefaultInteractionButtonProvider(),
                                new CompanyInteractionButtonProvider()));

        List<ApprovalRowItem> actions =
                service.buildInteractionActions(
                        List.of(rowActive, rowCancel, rowDeactive, rowDraft), ctx);

        assertNotNull(actions);
        // 预期共 3 项：
        // 1. 全局项 (包含申请和修改信息)
        // 2. 行 101L (启用状态 99，包含 edit_company_reg_status)
        // 3. 行 102L (取消状态 -99，包含 edit_company_reg_status)
        // 行 103L 与 104L 不满足状态条件，不产生行级操作项
        assertEquals(3, actions.size());

        // 1. 验证全局项
        ApprovalRowItem globalAction = actions.get(0);
        assertNull(globalAction.getRow().get("id"));
        @SuppressWarnings("unchecked")
        List<ActionButtonDTO> globalBtns =
                (List<ActionButtonDTO>) globalAction.getRow().get("btnList");
        assertEquals(2, globalBtns.size());
        assertTrue(globalBtns.stream().anyMatch(b -> "company_apply".equals(b.getButtonKey())));
        assertTrue(
                globalBtns.stream().anyMatch(b -> "update_company_info".equals(b.getButtonKey())));
        assertFalse(
                globalBtns.stream()
                        .anyMatch(b -> "edit_company_reg_status".equals(b.getButtonKey())));

        // 2. 验证启用状态行 (101L)
        ApprovalRowItem activeAction = actions.get(1);
        assertEquals(101L, activeAction.getRow().get("id"));
        assertEquals("CR-ACTIVE", activeAction.getRow().get("businessNo"));
        @SuppressWarnings("unchecked")
        List<ActionButtonDTO> activeBtns =
                (List<ActionButtonDTO>) activeAction.getRow().get("btnList");
        assertEquals(1, activeBtns.size());
        assertEquals("edit_company_reg_status", activeBtns.get(0).getButtonKey());

        // 3. 验证取消状态行 (102L)
        ApprovalRowItem cancelAction = actions.get(2);
        assertEquals(102L, cancelAction.getRow().get("id"));
        assertEquals("CR-CANCEL", cancelAction.getRow().get("businessNo"));
        @SuppressWarnings("unchecked")
        List<ActionButtonDTO> cancelBtns =
                (List<ActionButtonDTO>) cancelAction.getRow().get("btnList");
        assertEquals(1, cancelBtns.size());
        assertEquals("edit_company_reg_status", cancelBtns.get(0).getButtonKey());
    }

    private RoleInteractionPermissionByModuleResp buildTestPermissions() {
        RoleInteractionPermissionByModuleResp.InteractionPermissionItem applyItem =
                new RoleInteractionPermissionByModuleResp.InteractionPermissionItem();
        applyItem.setPermissionCode("company_apply");
        applyItem.setPermissionName("外部对象申请");
        applyItem.setType(1);
        applyItem.setSort(1);

        RoleInteractionPermissionByModuleResp.InteractionPermissionItem updateItem =
                new RoleInteractionPermissionByModuleResp.InteractionPermissionItem();
        updateItem.setPermissionCode("update_company_info");
        updateItem.setPermissionName("外部对象修改信息");
        updateItem.setType(2);

        RoleInteractionPermissionByModuleResp.InteractionPermissionItem regStatusItem =
                new RoleInteractionPermissionByModuleResp.InteractionPermissionItem();
        regStatusItem.setPermissionCode("edit_company_reg_status");
        regStatusItem.setPermissionName("外部对象修改登记状态");
        regStatusItem.setType(2);

        RoleInteractionPermissionByModuleResp permResp =
                new RoleInteractionPermissionByModuleResp();
        permResp.setApplyPermissions(List.of(applyItem));
        permResp.setEditPermissions(List.of(updateItem, regStatusItem));
        permResp.setViewPermissions(Collections.emptyList());
        return permResp;
    }

    private DataRow createDataRow(Long id, String businessNo) {
        return createDataRow(id, businessNo, null);
    }

    private DataRow createDataRow(Long id, String businessNo, Integer businessStatus) {
        DataRow row = new DataRow();
        Map<String, Object> table = new HashMap<>();
        table.put("id", id);
        table.put("business_no", businessNo);
        if (businessStatus != null) {
            table.put("business_status", businessStatus);
        }
        row.setTables(Map.of("info", table));
        return row;
    }
}
