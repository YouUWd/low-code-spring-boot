package com.jdec.platform.common.biz.postprocess.action.interaction;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.common.api.dto.response.ActionButtonDTO;
import com.jdec.platform.common.api.dto.response.ActionRowItem;
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

        List<ActionButtonDTO> globalActions = service.buildGlobalActions(ctx);
        assertNotNull(globalActions);
        // 默认全部为全局项：3 个互动按钮直接以 ActionButtonDTO 形式输出给全局工具栏
        assertEquals(3, globalActions.size());
        assertTrue(globalActions.stream().anyMatch(b -> "company_apply".equals(b.getButtonKey())));
        assertTrue(
                globalActions.stream()
                        .anyMatch(b -> "update_company_info".equals(b.getButtonKey())));
        assertTrue(
                globalActions.stream()
                        .anyMatch(b -> "edit_company_reg_status".equals(b.getButtonKey())));

        // 默认无行级项：返回 null
        List<ActionRowItem> rowActions = service.buildRowActions(List.of(row1, row2), ctx);
        assertNull(rowActions);
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

        // 1. 全局项：应包含申请和修改信息 (不含被移至行级的修改登记状态)
        List<ActionButtonDTO> globalActions = service.buildGlobalActions(ctx);
        assertNotNull(globalActions);
        assertEquals(2, globalActions.size());
        assertTrue(globalActions.stream().anyMatch(b -> "company_apply".equals(b.getButtonKey())));
        assertTrue(
                globalActions.stream()
                        .anyMatch(b -> "update_company_info".equals(b.getButtonKey())));
        assertFalse(
                globalActions.stream()
                        .anyMatch(b -> "edit_company_reg_status".equals(b.getButtonKey())));

        // 2. 纯行级项：行 301L 与 302L 应分别输出行级互动按钮 (修改登记状态)
        List<ActionRowItem> rowActions = service.buildRowActions(List.of(row1, row2), ctx);
        assertNotNull(rowActions);
        assertEquals(2, rowActions.size());

        ActionRowItem rowAction1 = rowActions.get(0);
        assertEquals(301L, rowAction1.getRow().getId());
        assertEquals("CR20261027001", rowAction1.getRow().getBusinessNo());
        List<ActionButtonDTO> rowBtns1 = rowAction1.getRow().getBtnList();
        assertEquals(1, rowBtns1.size());
        assertEquals("edit_company_reg_status", rowBtns1.get(0).getButtonKey());

        ActionRowItem rowAction2 = rowActions.get(1);
        assertEquals(302L, rowAction2.getRow().getId());
        assertEquals("CR20261027002", rowAction2.getRow().getBusinessNo());
    }

    @Test
    @DisplayName("测试1001模块真实定制组件：edit_company_reg_status仅在启用(99)与停用(-2)状态下按行展示")
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
        DataRow rowInactive = createDataRow(102L, "CR-INACTIVE", -2); // 停用状态 (-2) -> 应该展示
        DataRow rowCancel = createDataRow(103L, "CR-CANCEL", -99); // 取消状态 (-99) -> 不应该展示
        DataRow rowDraft = createDataRow(104L, "CR-DRAFT", 1); // 待审核/草稿状态 (1) -> 不应该展示

        // 注册通用默认 Provider 与真实定制的 CompanyInteractionButtonProvider
        InteractionActionService service =
                new InteractionActionService(
                        List.of(
                                new DefaultInteractionButtonProvider(),
                                new CompanyInteractionButtonProvider()));

        // 1. 验证全局项 (包含申请和修改信息，不含行级登记状态)
        List<ActionButtonDTO> globalActions = service.buildGlobalActions(ctx);
        assertNotNull(globalActions);
        assertEquals(2, globalActions.size());
        assertTrue(globalActions.stream().anyMatch(b -> "company_apply".equals(b.getButtonKey())));
        assertTrue(
                globalActions.stream()
                        .anyMatch(b -> "update_company_info".equals(b.getButtonKey())));
        assertFalse(
                globalActions.stream()
                        .anyMatch(b -> "edit_company_reg_status".equals(b.getButtonKey())));

        // 2. 验证纯行级项 (仅 101L 与 102L 满足状态条件，103L 与 104L 不产生操作)
        List<ActionRowItem> rowActions =
                service.buildRowActions(List.of(rowActive, rowInactive, rowCancel, rowDraft), ctx);
        assertNotNull(rowActions);
        assertEquals(2, rowActions.size());

        // 2.1 验证启用状态行 (101L)
        ActionRowItem activeAction = rowActions.get(0);
        assertEquals(101L, activeAction.getRow().getId());
        assertEquals("CR-ACTIVE", activeAction.getRow().getBusinessNo());
        List<ActionButtonDTO> activeBtns = activeAction.getRow().getBtnList();
        assertEquals(1, activeBtns.size());
        assertEquals("edit_company_reg_status", activeBtns.get(0).getButtonKey());

        // 2.2 验证停用状态行 (102L)
        ActionRowItem inactiveAction = rowActions.get(1);
        assertEquals(102L, inactiveAction.getRow().getId());
        assertEquals("CR-INACTIVE", inactiveAction.getRow().getBusinessNo());
        List<ActionButtonDTO> inactiveBtns = inactiveAction.getRow().getBtnList();
        assertEquals(1, inactiveBtns.size());
        assertEquals("edit_company_reg_status", inactiveBtns.get(0).getButtonKey());
    }

    @Test
    void testCompanyInteractionButtonProvider_individualNatureExcluded() {
        InteractionActionContext ctx =
                InteractionActionContext.builder()
                        .projectNo("company")
                        .moduleCode("COMPANY-ROOT")
                        .primaryTable("info")
                        .modulePermissionResp(buildTestPermissions())
                        .assignedPermissionCodes(
                                Set.of(
                                        "company_apply",
                                        "update_company_info",
                                        "edit_company_reg_status"))
                        .build();

        // 1. 企业性质 (99启用，应该展示)
        DataRow rowEnterprise = createDataRow(201L, "CR-ENT", 99);
        rowEnterprise.putValue("info", "nature", "[{\"alias_value_title\":\"企业\"}]");

        // 2. 个人性质词云 JSON (99启用，是个人，不应该展示)
        DataRow rowIndividualJson = createDataRow(202L, "CR-IND", 99);
        rowIndividualJson.putValue("info", "nature", "[{\"alias_value_title\":\"个人\"}]");

        // 3. 个人性质纯文本 (-99取消，是个人，不应该展示)
        DataRow rowIndividualText = createDataRow(203L, "CR-IND-TXT", -99);
        rowIndividualText.putValue("info", "nature", "个人");

        InteractionActionService service =
                new InteractionActionService(
                        List.of(
                                new DefaultInteractionButtonProvider(),
                                new CompanyInteractionButtonProvider()));

        List<ActionRowItem> rowActions =
                service.buildRowActions(
                        List.of(rowEnterprise, rowIndividualJson, rowIndividualText), ctx);
        assertNotNull(rowActions);
        assertEquals(1, rowActions.size());
        assertEquals(201L, rowActions.get(0).getRow().getId());
        assertEquals(
                "edit_company_reg_status",
                rowActions.get(0).getRow().getBtnList().get(0).getButtonKey());
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
