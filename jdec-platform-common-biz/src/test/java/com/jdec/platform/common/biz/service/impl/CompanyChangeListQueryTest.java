package com.jdec.platform.common.biz.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jdec.platform.approval.api.IApprovalEngine;
import com.jdec.platform.common.api.UserMenuHeaderApi;
import com.jdec.platform.common.api.dto.request.MenuChangeQueryReq;
import com.jdec.platform.common.api.dto.response.MenuChangeCountResp;
import com.jdec.platform.common.api.dto.response.MenuDataResp;
import com.jdec.platform.common.biz.controller.CommonAggregateController;
import com.jdec.platform.common.biz.service.CommonAggregateService;
import com.jdec.platform.common.biz.service.QueryResultEnhanceService;
import com.jdec.platform.config.api.SysApprovalChainConfigApi;
import com.jdec.platform.config.api.SysFileApi;
import com.jdec.platform.config.api.SysStatusApi;
import com.jdec.platform.config.api.SysUserApi;
import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.response.MenuHeaderColumnResp;
import com.jdec.platform.config.api.dto.response.MenuHeaderDisplayResp;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.config.open.api.OpenMenuApi;
import com.jdec.platform.data.api.DataApi;
import com.jdec.platform.data.api.dto.request.SingleTableQueryReq;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.datasource.JooqDataContextFactory;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.security.model.LoginUser;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 变更申请单列表单表分页查询 (${primaryTable}_change) 单元测试 */
class CompanyChangeListQueryTest {

    private UserMenuHeaderApi userMenuHeaderApi;
    private OpenMenuApi openMenuApi;
    private ConfigApi configApi;
    private DataApi dataApi;
    private CommonFilterResolver commonFilterResolver;
    private SysUserApi sysUserApi;
    private SysStatusApi sysStatusApi;
    private IApprovalEngine approvalEngine;
    private SysApprovalChainConfigApi sysApprovalChainConfigApi;
    private SysFileApi sysFileApi;
    private QueryResultEnhanceService queryResultEnhanceService;
    private JooqDataContextFactory jooqDataContextFactory;
    private DSLContext dslContext;
    private List<String> executedSqls;
    private CommonAggregateServiceImpl service;
    private CommonAggregateController controller;

    @BeforeEach
    void setUp() {
        AppContext.setContext("company", null);
        UserContext.setLoginUser(
                LoginUser.builder().userId(1L).roleId(1L).username("admin").build());

        userMenuHeaderApi = mock(UserMenuHeaderApi.class);
        openMenuApi = mock(OpenMenuApi.class);
        configApi = mock(ConfigApi.class);
        dataApi = mock(DataApi.class);
        commonFilterResolver = mock(CommonFilterResolver.class);
        sysUserApi = mock(SysUserApi.class);
        sysStatusApi = mock(SysStatusApi.class);
        approvalEngine = mock(IApprovalEngine.class);
        sysApprovalChainConfigApi = mock(SysApprovalChainConfigApi.class);
        sysFileApi = mock(SysFileApi.class);
        queryResultEnhanceService = mock(QueryResultEnhanceService.class);
        jooqDataContextFactory = mock(JooqDataContextFactory.class);

        executedSqls = new java.util.ArrayList<>();
        MockDataProvider provider =
                ctx -> {
                    executedSqls.add(ctx.sql());
                    return new MockResult[] {new MockResult(1)};
                };
        MockConnection conn = new MockConnection(provider);
        dslContext = DSL.using(conn, SQLDialect.MYSQL);
        when(jooqDataContextFactory.getContext()).thenReturn(dslContext);

        CommonFilterResolver.PrimaryFilterResult dummyResult =
                CommonFilterResolver.PrimaryFilterResult.of(
                        Collections.emptyList(), Collections.emptyList());
        when(commonFilterResolver.resolve(any(), any())).thenReturn(dummyResult);
        // 默认模拟当前登录用户在主表有创建的记录 (id: 286)
        when(dataApi.querySingleTableList(any())).thenReturn(List.of(Map.of("id", 286L)));

        service =
                new CommonAggregateServiceImpl(
                        userMenuHeaderApi,
                        openMenuApi,
                        configApi,
                        dataApi,
                        commonFilterResolver,
                        approvalEngine,
                        sysApprovalChainConfigApi,
                        queryResultEnhanceService,
                        null,
                        jooqDataContextFactory,
                        sysStatusApi);

        controller = new CommonAggregateController(service);
    }

    @Test
    @DisplayName("测试 Controller 端点 queryChangeListByMenu 成功委托 Service 并返回 ApiResponse")
    void testControllerQueryChangeListByMenu() {
        CommonAggregateService mockService = mock(CommonAggregateService.class);
        CommonAggregateController testController = new CommonAggregateController(mockService);

        MenuDataResp mockResp = MenuDataResp.builder().build();
        when(mockService.queryChangeListByMenu(any(), any())).thenReturn(mockResp);

        ApiResponse<MenuDataResp> apiResponse =
                testController.queryChangeListByMenu(
                        100L, MenuChangeQueryReq.builder().pageNo(1).pageSize(10).build());

        assertNotNull(apiResponse);
        assertEquals(200, apiResponse.getStatus());
        verify(mockService).queryChangeListByMenu(any(), any());
    }

    @Test
    @DisplayName("测试 Service 变更单列表单表分页查询: 基于 dataApi.querySingleTablePage 原生分页与结果组装")
    void testQueryChangeListByMenuWithSingleTablePage() {
        Long menuId = 100L;
        Long sourceId = 286L;
        Long changeId = 1L;
        Long rootModuleId = 1001L;

        // 1. Mock 表头与模块树 (包含主表物理字段 company_name 与 business_no)
        ModuleFieldDTO fComp =
                ModuleFieldDTO.builder()
                        .id(2001L)
                        .moduleId(rootModuleId)
                        .tableName("info")
                        .columnName("company_name")
                        .build();
        ModuleFieldDTO fBiz =
                ModuleFieldDTO.builder()
                        .id(2002L)
                        .moduleId(rootModuleId)
                        .tableName("info")
                        .columnName("business_no")
                        .build();
        ModuleFieldDTO fStatus =
                ModuleFieldDTO.builder()
                        .id(2003L)
                        .moduleId(rootModuleId)
                        .tableName("info")
                        .columnName("business_status")
                        .build();
        ModuleFieldDTO fVer =
                ModuleFieldDTO.builder()
                        .id(2004L)
                        .moduleId(rootModuleId)
                        .tableName("info")
                        .columnName("version_no")
                        .build();

        MenuHeaderColumnResp colComp =
                MenuHeaderColumnResp.builder()
                        .fieldId(2001L)
                        .moduleId(rootModuleId)
                        .dataIndex("company_name")
                        .build();
        MenuHeaderColumnResp colBiz =
                MenuHeaderColumnResp.builder()
                        .fieldId(2002L)
                        .moduleId(rootModuleId)
                        .dataIndex("business_no")
                        .build();

        MenuHeaderColumnResp headerRoot =
                MenuHeaderColumnResp.builder()
                        .moduleId(rootModuleId)
                        .children(List.of(colComp, colBiz))
                        .build();
        MenuHeaderDisplayResp headerDisplay =
                MenuHeaderDisplayResp.builder().header(headerRoot).build();
        when(userMenuHeaderApi.getUserMenuHeaderDisplay(
                        anyLong(), anyLong(), anyLong(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(headerDisplay);

        ModuleInfo moduleInfo =
                ModuleInfo.builder()
                        .moduleId(rootModuleId)
                        .parentId(0L)
                        .moduleCode("COMPANY-ROOT")
                        .primaryTable("info")
                        .build();
        ModuleTreeDTO tree =
                ModuleTreeDTO.builder()
                        .rootModuleId(rootModuleId)
                        .projectNo("company")
                        .rootModule(moduleInfo)
                        .moduleMap(Map.of(rootModuleId, moduleInfo))
                        .parentToChildren(new HashMap<>())
                        .fieldMap(Map.of(2001L, fComp, 2002L, fBiz, 2003L, fStatus, 2004L, fVer))
                        .fieldToModuleMap(Map.of(2001L, rootModuleId, 2002L, rootModuleId))
                        .allowedModuleIds(java.util.Set.of(rootModuleId))
                        .build();
        when(configApi.getModuleTree(eq("company"), any(), eq(rootModuleId))).thenReturn(tree);

        // 2. Mock dataApi.querySingleTablePage
        Map<String, Object> recordData = new LinkedHashMap<>();
        recordData.put("id", changeId);
        recordData.put("change_no", "CHG202610290001");
        recordData.put("source_id", sourceId);
        recordData.put("modify_reason", "企业更名申请");
        recordData.put("business_no", "CR99999");
        recordData.put("company_name", "更名后测试01");
        recordData.put("business_status", 1);
        recordData.put("deleted", (byte) 0);
        recordData.put("version_no", 1);

        DataPage<List<Map<String, Object>>> mockPage = DataPage.of(1, 20, 1L, List.of(recordData));
        when(dataApi.querySingleTablePage(any())).thenReturn(mockPage);

        // 3. 执行调用
        MenuDataResp resp =
                service.queryChangeListByMenu(
                        menuId, MenuChangeQueryReq.builder().pageNo(1).pageSize(20).build());

        // 4. 验证 dataApi.querySingleTablePage 调用契约（主表与变更表同构，直接包含主表字段与变更专有字段）
        ArgumentCaptor<SingleTableQueryReq> captor =
                ArgumentCaptor.forClass(SingleTableQueryReq.class);
        verify(dataApi).querySingleTablePage(captor.capture());
        SingleTableQueryReq req = captor.getValue();
        assertNotNull(req);
        assertEquals("info_change", req.getTableName());
        assertEquals(1, req.getPageNo());
        assertEquals(20, req.getPageSize());
        assertTrue(req.getColumns().contains("company_name"));
        assertTrue(req.getColumns().contains("business_no"));
        assertTrue(req.getColumns().contains("business_status"));
        assertTrue(req.getColumns().contains("version_no"));
        assertTrue(req.getColumns().contains("id"));
        assertTrue(req.getColumns().contains("change_no"));
        assertTrue(req.getColumns().contains("source_id"));
        assertTrue(req.getColumns().contains("modify_reason"));
        assertTrue(req.getColumns().contains("read_status"));

        // 验证必须包含主表创建人为当前用户的 source_id IN (286L) 条件
        assertTrue(
                req.getFilters().stream()
                        .anyMatch(
                                f ->
                                        "source_id".equalsIgnoreCase(f.getColumnName())
                                                && "IN".equalsIgnoreCase(f.getOperator())
                                                && List.of(286L).equals(f.getValue())));

        // 5. 验证返回数据与纯 info 命名空间结构
        assertNotNull(resp);
        assertNotNull(resp.getHeader());
        assertNotNull(resp.getHeader().getChildren());

        // 验证变更专有字段 (id, change_no, source_id, modify_reason, read_status) 在原表头缺失时已被自动构建进去
        List<String> headerDataIndexes =
                resp.getHeader().getChildren().stream()
                        .map(MenuHeaderColumnResp::getDataIndex)
                        .toList();
        assertTrue(headerDataIndexes.contains("info.id"));
        assertTrue(headerDataIndexes.contains("info.change_no"));
        assertTrue(headerDataIndexes.contains("info.source_id"));
        assertTrue(headerDataIndexes.contains("info.modify_reason"));
        assertTrue(headerDataIndexes.contains("info.read_status"));

        assertNotNull(resp.getData());
        assertEquals(1, resp.getData().getTotal());
        assertEquals(1, resp.getData().getRecord().size());

        Map<String, Object> testResultMap = resp.getData().getRecord().get(0).getRow();
        assertNotNull(testResultMap);

        // 验证纯 info 结构：业务字段与变更固有属性
        assertEquals("CR99999", testResultMap.get("info.business_no"));
        assertEquals("更名后测试01", testResultMap.get("info.company_name"));
        assertEquals(changeId, testResultMap.get("info.id"));
        assertEquals(changeId, testResultMap.get("info.change_id"));
        assertEquals("CHG202610290001", testResultMap.get("info.change_no"));
        assertEquals(sourceId, testResultMap.get("info.source_id"));
        assertEquals("企业更名申请", testResultMap.get("info.modify_reason"));

        // 确认不包含任何 info_change 前缀
        assertNull(testResultMap.get("info_change.company_name"));
        assertNull(testResultMap.get("info_change.business_no"));
        assertNull(testResultMap.get("info_change.id"));
        assertNull(testResultMap.get("info_change.change_no"));
    }

    @Test
    @DisplayName("测试返回的表头裁剪：仅保留第一层，移除第二层及所有更深层子节点")
    void testQueryChangeListByMenu_headerPrunedToFirstLevel() {
        Long menuId = 200L;
        Long rootModuleId = 1001L;

        // 构造多级嵌套表头：
        // 根 (level 0)
        //   - col1 (level 1, 直接字段)
        //   - group1 (level 1, 包含 level 2 的子列)
        //       - col2 (level 2)
        //       - group2 (level 2)
        //           - col3 (level 3)
        MenuHeaderColumnResp col3 =
                MenuHeaderColumnResp.builder()
                        .fieldId(3003L)
                        .moduleId(1002L)
                        .label("子模块字段")
                        .build();
        MenuHeaderColumnResp subModuleMenu =
                MenuHeaderColumnResp.builder()
                        .sourceId(20L)
                        .moduleId(1002L)
                        .nodeType(0)
                        .label("基本信息(子模块)")
                        .children(List.of(col3))
                        .build();
        MenuHeaderColumnResp col1 =
                MenuHeaderColumnResp.builder()
                        .fieldId(3001L)
                        .moduleId(rootModuleId)
                        .label("一级直接列")
                        .build();

        MenuHeaderColumnResp multiLevelHeader =
                MenuHeaderColumnResp.builder()
                        .moduleId(rootModuleId)
                        .label("根菜单")
                        .children(List.of(col1, subModuleMenu))
                        .build();

        when(userMenuHeaderApi.getUserMenuHeaderDisplay(
                        anyLong(), anyLong(), anyLong(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(MenuHeaderDisplayResp.builder().header(multiLevelHeader).build());

        ModuleInfo moduleInfo =
                ModuleInfo.builder()
                        .moduleId(rootModuleId)
                        .parentId(0L)
                        .primaryTable("info")
                        .build();
        ModuleTreeDTO moduleTree =
                ModuleTreeDTO.builder()
                        .rootModuleId(rootModuleId)
                        .rootModule(moduleInfo)
                        .moduleMap(Map.of(rootModuleId, moduleInfo))
                        .fieldMap(new HashMap<>())
                        .fieldToModuleMap(new HashMap<>())
                        .build();
        when(configApi.getModuleTree(any(), any(), eq(rootModuleId))).thenReturn(moduleTree);
        when(dataApi.querySingleTablePage(any()))
                .thenReturn(DataPage.of(1, 20, 0L, Collections.emptyList()));

        MenuDataResp resp =
                service.queryChangeListByMenu(
                        menuId, MenuChangeQueryReq.builder().pageNo(1).pageSize(20).build());

        assertNotNull(resp);
        MenuHeaderColumnResp respHeader = resp.getHeader();
        assertNotNull(respHeader);
        assertEquals("根菜单", respHeader.getLabel());
        assertNotNull(respHeader.getChildren());

        // 验证仅保留根模块 moduleId=1001 的信息：5个变更固有字段 + 原单1个根模块字段(col1) = 6个
        // 子模块 moduleId=1002 (基本信息) 必须被彻底移除
        assertEquals(6, respHeader.getChildren().size());

        for (MenuHeaderColumnResp child : respHeader.getChildren()) {
            assertNotNull(child);
            assertEquals(
                    rootModuleId,
                    child.getModuleId(),
                    "返回的列必须仅属于根模块 moduleId: label=" + child.getLabel());
            assertNull(child.getChildren(), "第二层及更深层必须已被裁剪为 null: label=" + child.getLabel());
        }

        List<String> labels =
                respHeader.getChildren().stream().map(MenuHeaderColumnResp::getLabel).toList();
        assertTrue(labels.contains("一级直接列"));
        assertTrue(labels.contains("变更ID"));
        assertTrue(labels.contains("变更单号"));
        assertTrue(labels.contains("原单ID"));
        assertTrue(labels.contains("变更原因"));
        assertTrue(labels.contains("读取状态"));
        assertFalse(labels.contains("基本信息(子模块)"), "子模块信息不得保留在返回的表头中");
        assertFalse(labels.contains("子模块字段"), "子模块字段不得保留在返回的表头中");
    }

    @Test
    @DisplayName("测试变更申请列表根据 readStatus 过滤 (0-未读, 1-已读, 2-全部)")
    void testQueryChangeListByMenu_readStatusFilter() {
        Long menuId = 300L;
        Long rootModuleId = 1001L;

        MenuHeaderColumnResp headerRoot =
                MenuHeaderColumnResp.builder()
                        .moduleId(rootModuleId)
                        .children(new java.util.ArrayList<>())
                        .build();
        when(userMenuHeaderApi.getUserMenuHeaderDisplay(
                        anyLong(), anyLong(), anyLong(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(MenuHeaderDisplayResp.builder().header(headerRoot).build());

        ModuleInfo moduleInfo =
                ModuleInfo.builder().moduleId(rootModuleId).primaryTable("info").build();
        ModuleTreeDTO moduleTree =
                ModuleTreeDTO.builder()
                        .rootModuleId(rootModuleId)
                        .rootModule(moduleInfo)
                        .moduleMap(Map.of(rootModuleId, moduleInfo))
                        .fieldMap(new HashMap<>())
                        .fieldToModuleMap(new HashMap<>())
                        .build();
        when(configApi.getModuleTree(any(), any(), eq(rootModuleId))).thenReturn(moduleTree);
        when(dataApi.querySingleTablePage(any()))
                .thenReturn(DataPage.of(1, 20, 0L, Collections.emptyList()));

        // Case 1: readStatus = 0 (未读)
        service.queryChangeListByMenu(
                menuId, MenuChangeQueryReq.builder().pageNo(1).pageSize(10).readStatus(0).build());
        ArgumentCaptor<SingleTableQueryReq> captor1 =
                ArgumentCaptor.forClass(SingleTableQueryReq.class);
        verify(dataApi).querySingleTablePage(captor1.capture());
        SingleTableQueryReq req1 = captor1.getValue();
        assertNotNull(req1.getFilters());
        boolean hasRead0 =
                req1.getFilters().stream()
                        .anyMatch(
                                f ->
                                        "read_status".equalsIgnoreCase(f.getColumnName())
                                                && Integer.valueOf(0).equals(f.getValue()));
        assertTrue(hasRead0, "未读过滤必须添加 read_status=0 条件");

        // Case 2: readStatus = 1 (已读)
        service.queryChangeListByMenu(
                menuId, MenuChangeQueryReq.builder().pageNo(1).pageSize(10).readStatus(1).build());
        ArgumentCaptor<SingleTableQueryReq> captor2 =
                ArgumentCaptor.forClass(SingleTableQueryReq.class);
        verify(dataApi, org.mockito.Mockito.times(2)).querySingleTablePage(captor2.capture());
        SingleTableQueryReq req2 = captor2.getValue();
        assertNotNull(req2.getFilters());
        boolean hasRead1 =
                req2.getFilters().stream()
                        .anyMatch(
                                f ->
                                        "read_status".equalsIgnoreCase(f.getColumnName())
                                                && Integer.valueOf(1).equals(f.getValue()));
        assertTrue(hasRead1, "已读过滤必须添加 read_status=1 条件");

        // Case 3: readStatus = 2 (全部)
        service.queryChangeListByMenu(
                menuId, MenuChangeQueryReq.builder().pageNo(1).pageSize(10).readStatus(2).build());
        ArgumentCaptor<SingleTableQueryReq> captor3 =
                ArgumentCaptor.forClass(SingleTableQueryReq.class);
        verify(dataApi, org.mockito.Mockito.times(3)).querySingleTablePage(captor3.capture());
        SingleTableQueryReq req3 = captor3.getValue();
        assertNotNull(req3.getFilters());
        boolean hasReadFilter =
                req3.getFilters().stream()
                        .anyMatch(f -> "read_status".equalsIgnoreCase(f.getColumnName()));
        assertFalse(hasReadFilter, "全部状态下不得添加 read_status 条件");
    }

    @Test
    @DisplayName("测试变更启用状态与未读数量统计端点 getChangeCountByMenu")
    void testGetChangeCountByMenu() {
        Long menuId = 400L;
        Long rootModuleId = 1001L;

        when(openMenuApi.getModuleIdByMenuId(menuId)).thenReturn(rootModuleId);
        ModuleInfo moduleInfo =
                ModuleInfo.builder()
                        .moduleId(rootModuleId)
                        .primaryTable("info")
                        .enableChange(1)
                        .build();
        ModuleTreeDTO moduleTree =
                ModuleTreeDTO.builder()
                        .rootModuleId(rootModuleId)
                        .rootModule(moduleInfo)
                        .moduleMap(Map.of(rootModuleId, moduleInfo))
                        .build();
        when(configApi.getModuleTree(any(), any(), eq(rootModuleId))).thenReturn(moduleTree);
        when(dataApi.countSingleTable(any())).thenReturn(6L);

        ApiResponse<MenuChangeCountResp> apiResp = controller.getChangeCountByMenu(menuId);
        assertNotNull(apiResp);
        assertEquals(200, apiResp.getStatus());
        MenuChangeCountResp data = apiResp.getData();
        assertNotNull(data);
        assertEquals(1, data.getEnableChange());
        assertEquals(6L, data.getUnreadCount());

        ArgumentCaptor<SingleTableQueryReq> captor =
                ArgumentCaptor.forClass(SingleTableQueryReq.class);
        verify(dataApi).countSingleTable(captor.capture());
        SingleTableQueryReq countReq = captor.getValue();
        assertEquals("info_change", countReq.getTableName());
        assertTrue(
                countReq.getFilters().stream()
                        .anyMatch(
                                f ->
                                        "read_status".equalsIgnoreCase(f.getColumnName())
                                                && Integer.valueOf(0).equals(f.getValue())));
        // 验证统计未读数时同样绑定了当前用户创建的主表记录 source_id IN (286L)
        assertTrue(
                countReq.getFilters().stream()
                        .anyMatch(
                                f ->
                                        "source_id".equalsIgnoreCase(f.getColumnName())
                                                && "IN".equalsIgnoreCase(f.getOperator())
                                                && List.of(286L).equals(f.getValue())));
    }

    @Test
    @DisplayName("测试当前用户在原单主表无创建记录时，变更单列表与未读数量统计均短路返回空结果")
    void testChangeListAndCount_userHasNoPrimaryRecords_returnsEmpty() {
        Long menuId = 500L;
        Long rootModuleId = 1001L;

        // 模拟当前登录用户在主表无任何记录
        when(dataApi.querySingleTableList(any())).thenReturn(Collections.emptyList());

        when(openMenuApi.getModuleIdByMenuId(menuId)).thenReturn(rootModuleId);
        ModuleInfo moduleInfo =
                ModuleInfo.builder()
                        .moduleId(rootModuleId)
                        .primaryTable("info")
                        .enableChange(1)
                        .build();
        ModuleTreeDTO moduleTree =
                ModuleTreeDTO.builder()
                        .rootModuleId(rootModuleId)
                        .rootModule(moduleInfo)
                        .moduleMap(Map.of(rootModuleId, moduleInfo))
                        .fieldMap(new HashMap<>())
                        .fieldToModuleMap(new HashMap<>())
                        .build();
        when(configApi.getModuleTree(any(), any(), eq(rootModuleId))).thenReturn(moduleTree);

        MenuHeaderColumnResp headerRoot =
                MenuHeaderColumnResp.builder()
                        .moduleId(rootModuleId)
                        .children(new java.util.ArrayList<>())
                        .build();
        when(userMenuHeaderApi.getUserMenuHeaderDisplay(
                        anyLong(), anyLong(), anyLong(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(MenuHeaderDisplayResp.builder().header(headerRoot).build());

        // 1. 验证数量统计短路返回 0L，不调用 dataApi.countSingleTable
        ApiResponse<MenuChangeCountResp> countResp = controller.getChangeCountByMenu(menuId);
        assertNotNull(countResp);
        assertEquals(0L, countResp.getData().getUnreadCount());
        verify(dataApi, org.mockito.Mockito.never()).countSingleTable(any());

        // 2. 验证列表查询短路返回空列表，不调用 dataApi.querySingleTablePage
        ApiResponse<MenuDataResp> listResp =
                controller.queryChangeListByMenu(
                        menuId, MenuChangeQueryReq.builder().pageNo(1).pageSize(10).build());
        assertNotNull(listResp);
        assertNotNull(listResp.getData());
        assertNotNull(listResp.getData().getData());
        assertEquals(0L, listResp.getData().getData().getTotal());
        assertTrue(listResp.getData().getData().getRecord().isEmpty());
        verify(dataApi, org.mockito.Mockito.never()).querySingleTablePage(any());
    }

    @Test
    @DisplayName("测试 Controller 端点 markChangeAsRead 与 markChangeAsReadFromBody 成功委托 Service")
    void testControllerMarkChangeAsRead() {
        CommonAggregateService mockService = mock(CommonAggregateService.class);
        CommonAggregateController testController = new CommonAggregateController(mockService);

        when(mockService.markChangeAsRead(100L, 50L)).thenReturn(true);

        // 1. 路径变量调用
        ApiResponse<Boolean> resp1 = testController.markChangeAsRead(100L, 50L);
        assertNotNull(resp1);
        assertEquals(200, resp1.getStatus());
        assertTrue(resp1.getData());

        // 2. RequestBody 传参调用 (Map 携带 changeId)
        ApiResponse<Boolean> resp2 =
                testController.markChangeAsReadFromBody(100L, null, Map.of("changeId", 50L));
        assertNotNull(resp2);
        assertEquals(200, resp2.getStatus());
        assertTrue(resp2.getData());

        // 3. RequestParam 传参调用
        ApiResponse<Boolean> resp3 = testController.markChangeAsReadFromBody(100L, 50L, null);
        assertNotNull(resp3);
        assertEquals(200, resp3.getStatus());
        assertTrue(resp3.getData());
    }

    @Test
    @DisplayName("测试 Service 标记已读: 未读记录成功更新 read_status=1 并返回 true")
    void testServiceMarkChangeAsRead_unreadRecord_executesUpdate() {
        Long menuId = 200L;
        Long changeId = 15L;
        Long rootModuleId = 1001L;

        when(openMenuApi.getModuleIdByMenuId(menuId)).thenReturn(rootModuleId);
        ModuleInfo moduleInfo =
                ModuleInfo.builder()
                        .moduleId(rootModuleId)
                        .primaryTable("info")
                        .enableChange(1)
                        .build();
        ModuleTreeDTO moduleTree =
                ModuleTreeDTO.builder()
                        .rootModuleId(rootModuleId)
                        .rootModule(moduleInfo)
                        .moduleMap(Map.of(rootModuleId, moduleInfo))
                        .fieldMap(new HashMap<>())
                        .fieldToModuleMap(new HashMap<>())
                        .build();
        when(configApi.getModuleTree(any(), any(), eq(rootModuleId))).thenReturn(moduleTree);

        // 模拟当前用户的主表创建记录 (id: 286L) 与该未读变更单 (id: 15L, read_status: 0)
        when(dataApi.querySingleTableList(any()))
                .thenReturn(List.of(Map.of("id", 286L))) // fetchUserCreatedPrimaryTableIds
                .thenReturn(List.of(Map.of("id", changeId, "read_status", 0))); // 校验变更单存在且属于用户

        Boolean result = service.markChangeAsRead(menuId, changeId);
        assertTrue(result);
        assertEquals(1, executedSqls.size());
        String executedSql = executedSqls.get(0).toLowerCase();
        assertTrue(executedSql.contains("update `info_change`"));
        assertTrue(executedSql.contains("`read_status` = ?"));
    }

    @Test
    @DisplayName("测试 Service 标记已读: 变更单已为已读时直接返回 true 无需重复更新")
    void testServiceMarkChangeAsRead_alreadyRead_returnsTrueWithoutUpdate() {
        Long menuId = 200L;
        Long changeId = 16L;
        Long rootModuleId = 1001L;

        when(openMenuApi.getModuleIdByMenuId(menuId)).thenReturn(rootModuleId);
        ModuleInfo moduleInfo =
                ModuleInfo.builder()
                        .moduleId(rootModuleId)
                        .primaryTable("info")
                        .enableChange(1)
                        .build();
        ModuleTreeDTO moduleTree =
                ModuleTreeDTO.builder()
                        .rootModuleId(rootModuleId)
                        .rootModule(moduleInfo)
                        .moduleMap(Map.of(rootModuleId, moduleInfo))
                        .fieldMap(new HashMap<>())
                        .fieldToModuleMap(new HashMap<>())
                        .build();
        when(configApi.getModuleTree(any(), any(), eq(rootModuleId))).thenReturn(moduleTree);

        // 模拟当前用户主表记录与已读变更单 (read_status: 1)
        when(dataApi.querySingleTableList(any()))
                .thenReturn(List.of(Map.of("id", 286L)))
                .thenReturn(List.of(Map.of("id", changeId, "read_status", 1)));

        Boolean result = service.markChangeAsRead(menuId, changeId);
        assertTrue(result);
        assertTrue(executedSqls.isEmpty(), "已读状态不应产生更新 SQL");
    }

    @Test
    @DisplayName("测试 Service 标记已读: 变更单不存在或非当前用户主表数据时抛出 BusinessException")
    void testServiceMarkChangeAsRead_notFoundOrNoPermission_throwsException() {
        Long menuId = 200L;
        Long changeId = 999L;
        Long rootModuleId = 1001L;

        when(openMenuApi.getModuleIdByMenuId(menuId)).thenReturn(rootModuleId);
        ModuleInfo moduleInfo =
                ModuleInfo.builder()
                        .moduleId(rootModuleId)
                        .primaryTable("info")
                        .enableChange(1)
                        .build();
        ModuleTreeDTO moduleTree =
                ModuleTreeDTO.builder()
                        .rootModuleId(rootModuleId)
                        .rootModule(moduleInfo)
                        .moduleMap(Map.of(rootModuleId, moduleInfo))
                        .fieldMap(new HashMap<>())
                        .fieldToModuleMap(new HashMap<>())
                        .build();
        when(configApi.getModuleTree(any(), any(), eq(rootModuleId))).thenReturn(moduleTree);

        // 变更单不存在或不在用户主表 ID 中
        when(dataApi.querySingleTableList(any()))
                .thenReturn(List.of(Map.of("id", 286L)))
                .thenReturn(Collections.emptyList());

        assertThrows(
                BusinessException.class,
                () -> {
                    service.markChangeAsRead(menuId, changeId);
                });
    }
}
