package com.jdec.platform.common.biz.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jdec.platform.approval.api.IApprovalEngine;
import com.jdec.platform.common.api.UserMenuHeaderApi;
import com.jdec.platform.common.api.dto.request.FieldSortItem;
import com.jdec.platform.common.api.dto.request.MenuFlatFieldFilterItem;
import com.jdec.platform.common.api.dto.request.MenuOptionReq;
import com.jdec.platform.common.api.dto.request.MenuQueryReq;
import com.jdec.platform.common.api.dto.response.DataRecordItem;
import com.jdec.platform.common.api.dto.response.MenuDataResp;
import com.jdec.platform.common.api.dto.response.StatusSimpleResp;
import com.jdec.platform.common.api.dto.response.UserSimpleResp;
import com.jdec.platform.config.api.SysApprovalChainConfigApi;
import com.jdec.platform.config.api.SysStatusApi;
import com.jdec.platform.config.api.SysUserApi;
import com.jdec.platform.config.api.dto.common.*;
import com.jdec.platform.config.api.dto.response.*;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.config.open.api.OpenMenuApi;
import com.jdec.platform.config.open.api.OpenRolePermissionApi;
import com.jdec.platform.data.api.DataApi;
import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.common.DataRow;
import com.jdec.platform.data.api.dto.request.OptionReq;
import com.jdec.platform.data.api.dto.request.PageQueryReq;
import com.jdec.platform.data.api.dto.request.TableSpec;
import com.jdec.platform.data.api.dto.response.DataOptionResp;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.security.model.LoginUser;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 基于真实 company 项目拓扑的菜单聚合查询字段收集与筛选校验契约测试 */
class CompanyMenuQueryContractTest {

    private UserMenuHeaderApi userMenuHeaderApi;
    private OpenMenuApi openMenuApi;
    private ConfigApi configApi;
    private DataApi dataApi;
    private IApprovalEngine approvalEngine;
    private SysApprovalChainConfigApi sysApprovalChainConfigApi;
    private SysUserApi sysUserApi;
    private SysStatusApi sysStatusApi;
    private OpenRolePermissionApi openRolePermissionApi;

    private CommonAggregateServiceImpl commonAggregateService;

    private ModuleTreeDTO companyModuleTree;
    private ProjectTableRelationsDTO companyRelations;
    private MenuHeaderColumnResp companyHeader;

    @BeforeEach
    void setUp() {
        AppContext.setContext("company", null);
        UserContext.setLoginUser(
                LoginUser.builder().userId(1L).roleId(1L).username("admin").build());

        userMenuHeaderApi = mock(UserMenuHeaderApi.class);
        openMenuApi = mock(OpenMenuApi.class);
        configApi = mock(ConfigApi.class);
        dataApi = mock(DataApi.class);
        approvalEngine = mock(IApprovalEngine.class);
        sysApprovalChainConfigApi = mock(SysApprovalChainConfigApi.class);
        sysUserApi = mock(SysUserApi.class);
        sysStatusApi = mock(SysStatusApi.class);
        openRolePermissionApi = mock(OpenRolePermissionApi.class);

        commonAggregateService =
                new CommonAggregateServiceImpl(
                        userMenuHeaderApi,
                        openMenuApi,
                        configApi,
                        dataApi,
                        new CommonFilterResolver(
                                approvalEngine,
                                sysApprovalChainConfigApi,
                                configApi,
                                openRolePermissionApi,
                                dataApi),
                        sysUserApi,
                        sysStatusApi);

        initCompanyFixture();

        MenuHeaderDisplayResp displayResp =
                MenuHeaderDisplayResp.builder().header(companyHeader).build();
        when(userMenuHeaderApi.getUserMenuHeaderDisplay(
                        anyLong(), anyLong(), anyLong(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(displayResp);
        when(configApi.getModuleTree(eq("company"), any(), eq(1001L)))
                .thenReturn(companyModuleTree);
        when(configApi.getProjectTableRelations(eq("company"))).thenReturn(companyRelations);

        when(dataApi.queryPage(any(PageQueryReq.class))).thenReturn(DataPage.empty());
    }

    private void initCompanyFixture() {
        // 1. 模块元数据定义
        // 根模块 1001 (info)
        // 1:1 子模块 1002 (basic_info)
        // 1:1 子模块 1007 (address)
        // 1:N 子模块 1003 (member) -> 下挂 1:N:M 孙模块 1004 (member_contact)
        // 1:N 子模块 1010 (financial_account)
        Map<Long, ModuleInfo> moduleMap = new HashMap<>();
        Map<Long, List<Long>> p2c = new HashMap<>();
        Map<Long, ModuleFieldDTO> fieldMap = new HashMap<>();
        Map<Long, Long> fieldToModuleMap = new HashMap<>();
        Set<Long> allowedModuleIds =
                new HashSet<>(List.of(1001L, 1002L, 1007L, 1003L, 1004L, 1010L));

        moduleMap.put(
                1001L,
                ModuleInfo.builder()
                        .moduleId(1001L)
                        .parentId(0L)
                        .primaryTable("info")
                        .moduleName("公司主档")
                        .build());
        moduleMap.put(
                1002L,
                ModuleInfo.builder()
                        .moduleId(1002L)
                        .parentId(1001L)
                        .primaryTable("basic_info")
                        .moduleName("基础信息")
                        .build());
        moduleMap.put(
                1007L,
                ModuleInfo.builder()
                        .moduleId(1007L)
                        .parentId(1001L)
                        .primaryTable("address")
                        .moduleName("地址信息")
                        .build());
        moduleMap.put(
                1003L,
                ModuleInfo.builder()
                        .moduleId(1003L)
                        .parentId(1001L)
                        .primaryTable("member")
                        .moduleName("高管成员")
                        .build());
        moduleMap.put(
                1004L,
                ModuleInfo.builder()
                        .moduleId(1004L)
                        .parentId(1003L)
                        .primaryTable("member_contact")
                        .moduleName("成员联系方式")
                        .build());
        moduleMap.put(
                1010L,
                ModuleInfo.builder()
                        .moduleId(1010L)
                        .parentId(1001L)
                        .primaryTable("financial_account")
                        .moduleName("财务账户")
                        .build());
        moduleMap.put(
                1020L,
                ModuleInfo.builder()
                        .moduleId(1020L)
                        .parentId(1001L)
                        .primaryTable(null)
                        .moduleName("联系方式(虚拟)")
                        .build());
        moduleMap.put(
                1006L,
                ModuleInfo.builder()
                        .moduleId(1006L)
                        .parentId(1020L)
                        .primaryTable("company_contact")
                        .moduleName("公司联系方式")
                        .build());

        p2c.put(1001L, List.of(1002L, 1007L, 1003L, 1010L, 1020L));
        p2c.put(1003L, List.of(1004L));
        p2c.put(1020L, List.of(1006L));

        addField(moduleMap, fieldMap, fieldToModuleMap, 10001L, 1001L, "info", "id");
        addField(moduleMap, fieldMap, fieldToModuleMap, 10002L, 1001L, "info", "company_name");
        addField(moduleMap, fieldMap, fieldToModuleMap, 10003L, 1001L, "info", "created_by");
        addField(moduleMap, fieldMap, fieldToModuleMap, 10004L, 1001L, "info", "business_status");
        addField(moduleMap, fieldMap, fieldToModuleMap, 10005L, 1001L, "info", "reg_status");
        addField(moduleMap, fieldMap, fieldToModuleMap, 10006L, 1001L, "info", "cooperation_types");
        addField(moduleMap, fieldMap, fieldToModuleMap, 10021L, 1002L, "basic_info", "reg_capital");
        addField(moduleMap, fieldMap, fieldToModuleMap, 10071L, 1007L, "address", "detail_address");
        addField(moduleMap, fieldMap, fieldToModuleMap, 10031L, 1003L, "member", "member_name");
        addField(moduleMap, fieldMap, fieldToModuleMap, 10041L, 1004L, "member_contact", "phone");
        addField(
                moduleMap,
                fieldMap,
                fieldToModuleMap,
                10045L,
                1004L,
                "member_contact",
                "contact_type");
        addField(
                moduleMap,
                fieldMap,
                fieldToModuleMap,
                10058L,
                1006L,
                "company_contact",
                "contact_type");
        addField(
                moduleMap,
                fieldMap,
                fieldToModuleMap,
                10101L,
                1010L,
                "financial_account",
                "account_no");

        allowedModuleIds.addAll(List.of(1020L, 1006L));

        companyModuleTree =
                ModuleTreeDTO.builder()
                        .rootModuleId(1001L)
                        .projectNo("company")
                        .rootModule(moduleMap.get(1001L))
                        .moduleMap(moduleMap)
                        .parentToChildren(p2c)
                        .fieldMap(fieldMap)
                        .fieldToModuleMap(fieldToModuleMap)
                        .allowedModuleIds(allowedModuleIds)
                        .build();

        // 2. 表关联拓扑定义
        List<TableRelationDTO> rs = new ArrayList<>();
        rs.add(
                TableRelationDTO.builder()
                        .mainTable("info")
                        .joinTable("basic_info")
                        .mainField("id")
                        .joinField("company_id")
                        .relationType("1:1")
                        .build());
        rs.add(
                TableRelationDTO.builder()
                        .mainTable("info")
                        .joinTable("address")
                        .mainField("id")
                        .joinField("company_id")
                        .relationType("1:1")
                        .build());
        rs.add(
                TableRelationDTO.builder()
                        .mainTable("info")
                        .joinTable("member")
                        .mainField("id")
                        .joinField("company_id")
                        .relationType("1:N")
                        .build());
        rs.add(
                TableRelationDTO.builder()
                        .mainTable("member")
                        .joinTable("member_contact")
                        .mainField("id")
                        .joinField("member_id")
                        .relationType("1:N")
                        .build());
        rs.add(
                TableRelationDTO.builder()
                        .mainTable("info")
                        .joinTable("financial_account")
                        .mainField("id")
                        .joinField("company_id")
                        .relationType("1:N")
                        .build());
        rs.add(
                TableRelationDTO.builder()
                        .mainTable("info")
                        .joinTable("company_contact")
                        .mainField("id")
                        .joinField("company_id")
                        .relationType("1:N")
                        .build());

        Map<String, List<TableRelationDTO>> fwd = new LinkedHashMap<>();
        for (TableRelationDTO r : rs) {
            fwd.computeIfAbsent(r.getMainTable().toLowerCase(), k -> new ArrayList<>()).add(r);
        }
        companyRelations =
                ProjectTableRelationsDTO.builder()
                        .projectNo("company")
                        .allRelations(rs)
                        .forwardRelations(fwd)
                        .reverseRelations(Collections.emptyMap())
                        .build();

        // 3. 构建表头树结构 (headerRoot)
        companyHeader =
                MenuHeaderColumnResp.builder()
                        .moduleId(1001L)
                        .label("公司主档")
                        .children(
                                List.of(
                                        col(1001L, 10001L, "ID"),
                                        col(1001L, 10002L, "企业全称"),
                                        MenuHeaderColumnResp.builder()
                                                .moduleId(1002L)
                                                .label("基础信息(1:1)")
                                                .children(List.of(col(1002L, 10021L, "注册资本")))
                                                .build(),
                                        MenuHeaderColumnResp.builder()
                                                .moduleId(1007L)
                                                .label("地址信息(1:1)")
                                                .children(List.of(col(1007L, 10071L, "详细地址")))
                                                .build(),
                                        MenuHeaderColumnResp.builder()
                                                .moduleId(1003L)
                                                .label("高管成员(1:N)")
                                                .children(
                                                        List.of(
                                                                col(1003L, 10031L, "高管姓名"),
                                                                MenuHeaderColumnResp.builder()
                                                                        .moduleId(1004L)
                                                                        .label("成员联系方式(1:N:M)")
                                                                        .children(
                                                                                List.of(
                                                                                        col(
                                                                                                1004L,
                                                                                                10041L,
                                                                                                "联系电话")))
                                                                        .build()))
                                                .build(),
                                        MenuHeaderColumnResp.builder()
                                                .moduleId(1010L)
                                                .label("财务账户(1:N)")
                                                .children(List.of(col(1010L, 10101L, "银行账号")))
                                                .build()))
                        .build();
    }

    private static void addField(
            Map<Long, ModuleInfo> moduleMap,
            Map<Long, ModuleFieldDTO> fieldMap,
            Map<Long, Long> fieldToModuleMap,
            Long fid,
            Long mid,
            String table,
            String col) {
        ModuleFieldDTO f =
                ModuleFieldDTO.builder()
                        .id(fid)
                        .moduleId(mid)
                        .tableName(table)
                        .columnName(col)
                        .build();
        fieldMap.put(fid, f);
        fieldToModuleMap.put(fid, mid);
        ModuleInfo m = moduleMap.get(mid);
        if (m != null) {
            if (m.getFields() == null) {
                m.setFields(new ArrayList<>());
            }
            m.getFields().add(f);
        }
    }

    private static MenuHeaderColumnResp col(Long moduleId, Long fieldId, String label) {
        return MenuHeaderColumnResp.builder()
                .moduleId(moduleId)
                .fieldId(fieldId)
                .label(label)
                .sorted(1)
                .searchType(
                        MenuHeaderSearchTypeResp.builder()
                                .code("input")
                                .name("文本输入")
                                .component("Input")
                                .build())
                .build();
    }

    private static MenuHeaderColumnResp findColumnByFieldId(
            MenuHeaderColumnResp node, Long fieldId) {
        if (node == null || fieldId == null) {
            return null;
        }
        if (fieldId.equals(node.getFieldId())) {
            return node;
        }
        if (node.getChildren() != null) {
            for (MenuHeaderColumnResp child : node.getChildren()) {
                MenuHeaderColumnResp found = findColumnByFieldId(child, fieldId);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    @DisplayName("场景1：无搜索条件时，收集根模块 + 所有 1:1 模块 + 所有 1:N 模块字段")
    void testNoFilter_collectAllFields() {
        MenuQueryReq req = MenuQueryReq.builder().filters(Collections.emptyList()).build();

        commonAggregateService.queryByMenu(100L, req);

        ArgumentCaptor<PageQueryReq> captor = ArgumentCaptor.forClass(PageQueryReq.class);
        verify(dataApi).queryPage(captor.capture());

        List<String> actualFields = flattenPhysicalFields(captor.getValue());
        assertEquals(
                List.of(
                        "info.id",
                        "info.company_name",
                        "basic_info.reg_capital",
                        "address.detail_address",
                        "member.member_name",
                        "member_contact.phone",
                        "financial_account.account_no"),
                actualFields,
                "无筛选时应收集全部已勾选字段");
    }

    @Test
    @DisplayName("场景2：仅筛选 1:1 模块字段时，全量 1:N 模块均继续保留展示")
    void testFilter1To1Only_collectAllFields() {
        MenuQueryReq req =
                MenuQueryReq.builder()
                        .filters(
                                List.of(
                                        MenuFlatFieldFilterItem.builder()
                                                .fieldId(10021L) // basic_info.reg_capital (1:1)
                                                .operator("eq")
                                                .value("1000万")
                                                .build()))
                        .build();

        commonAggregateService.queryByMenu(100L, req);

        ArgumentCaptor<PageQueryReq> captor = ArgumentCaptor.forClass(PageQueryReq.class);
        verify(dataApi).queryPage(captor.capture());

        List<String> actualFields = flattenPhysicalFields(captor.getValue());
        assertEquals(
                List.of(
                        "info.id",
                        "info.company_name",
                        "basic_info.reg_capital",
                        "address.detail_address",
                        "member.member_name",
                        "member_contact.phone",
                        "financial_account.account_no"),
                actualFields,
                "仅筛选 1:1 模块字段时不应剔除任何 1:N 模块");
    }

    @Test
    @DisplayName("场景3：筛选单一 1:N 模块（财务账户 1010）时，仅收集 1:1 + 该 1:N 模块，剔除未被筛选的 1:N 模块")
    void testFilterSingle1ToN_excludeOther1ToN() {
        MenuQueryReq req =
                MenuQueryReq.builder()
                        .filters(
                                List.of(
                                        MenuFlatFieldFilterItem.builder()
                                                .fieldId(10101L) // financial_account.account_no
                                                // (1:N)
                                                .operator("eq")
                                                .value("6222020202")
                                                .build()))
                        .build();

        commonAggregateService.queryByMenu(100L, req);

        ArgumentCaptor<PageQueryReq> captor = ArgumentCaptor.forClass(PageQueryReq.class);
        verify(dataApi).queryPage(captor.capture());

        List<String> actualFields = flattenPhysicalFields(captor.getValue());
        assertEquals(
                List.of(
                        "info.id",
                        "info.company_name",
                        "basic_info.reg_capital",
                        "address.detail_address",
                        "financial_account.account_no"),
                actualFields,
                "应收集根模块 + 1:1 模块 + 命中的 1010 财务账户，剔除 1003/1004 高管成员");
        assertEquals(1, captor.getValue().getFilters().size());
        assertEquals("financial_account", captor.getValue().getFilters().get(0).getTableName());
        assertEquals("account_no", captor.getValue().getFilters().get(0).getColumnName());
    }

    @Test
    @DisplayName("场景4：同时筛选 1:N 模块（高管 1003）与 1:N:M 模块（电话 1004）时，归级为同分支，正常允许查询并收集二者")
    void testFilter1ToNAnd1ToNToM_allowAndCollectTogether() {
        MenuQueryReq req =
                MenuQueryReq.builder()
                        .filters(
                                List.of(
                                        MenuFlatFieldFilterItem.builder()
                                                .fieldId(10031L) // member.member_name (1:N)
                                                .operator("like")
                                                .value("张三")
                                                .build(),
                                        MenuFlatFieldFilterItem.builder()
                                                .fieldId(10041L) // member_contact.phone (1:N:M)
                                                .operator("eq")
                                                .value("13800000000")
                                                .build()))
                        .build();

        commonAggregateService.queryByMenu(100L, req);

        ArgumentCaptor<PageQueryReq> captor = ArgumentCaptor.forClass(PageQueryReq.class);
        verify(dataApi).queryPage(captor.capture());

        List<String> actualFields = flattenPhysicalFields(captor.getValue());
        assertEquals(
                List.of(
                        "info.id",
                        "info.company_name",
                        "basic_info.reg_capital",
                        "address.detail_address",
                        "member.member_name",
                        "member_contact.phone"),
                actualFields,
                "1:N 与 1:N:M 同属 1003 分支，应允许同时筛选并收集 1003 与 1004 字段");
    }

    @Test
    @DisplayName("场景5：跨多个独立的 1:N 模块（高管 1003 + 财务账户 1010）同时筛选时，严格抛出 400 业务异常")
    void testFilterMultiple1ToN_throwsBusinessException() {
        MenuQueryReq req =
                MenuQueryReq.builder()
                        .filters(
                                List.of(
                                        MenuFlatFieldFilterItem.builder()
                                                .fieldId(10031L) // member (1:N 分支1)
                                                .operator("eq")
                                                .value("张三")
                                                .build(),
                                        MenuFlatFieldFilterItem.builder()
                                                .fieldId(10101L) // financial_account (1:N 分支2)
                                                .operator("eq")
                                                .value("6222020202")
                                                .build()))
                        .build();

        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () -> commonAggregateService.queryByMenu(100L, req),
                        "跨多个独立 1:N 模块筛选时应抛出 BusinessException");

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("最多只允许 1 个 1:N 模块参与筛选"));
    }

    @Test
    @DisplayName("场景6：用户真实参数校验 10002(根)+10006(根)+10045(成员通讯录1:N)+10058(公司联系方式1:N) 必须被拦截抛出 400")
    void testUserExactQueryFilters_mustThrowBusinessException() {
        MenuQueryReq req =
                MenuQueryReq.builder()
                        .filters(
                                List.of(
                                        MenuFlatFieldFilterItem.builder()
                                                .fieldId(10002L) // info.company_name (根模块)
                                                .operator("IN")
                                                .value(List.of("盛港煤焦化", "盛港集团"))
                                                .build(),
                                        MenuFlatFieldFilterItem.builder()
                                                .fieldId(10006L) // info.cooperation_types (根模块)
                                                .operator("LIKE")
                                                .value("供应商")
                                                .build(),
                                        MenuFlatFieldFilterItem.builder()
                                                .fieldId(10045L) // member_contact (1:N 顶层分支 1003)
                                                .operator("IN")
                                                .value(List.of("手机"))
                                                .build(),
                                        MenuFlatFieldFilterItem.builder()
                                                .fieldId(10058L) // company_contact (1:N 顶层分支 1006)
                                                .operator("IN")
                                                .value(List.of("座机"))
                                                .build()))
                        .build();

        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () -> commonAggregateService.queryByMenu(100L, req),
                        "同时筛选 10045(成员通讯录) 与 10058(公司联系方式) 跨了 2 个独立 1:N 分支，必须被拦截抛出异常");

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("最多只允许 1 个 1:N 模块参与筛选"));
    }

    @Test
    @DisplayName("测试第一层主模块记录中的 created_by 成功转换为 UserSimpleResp 对象包含 userId、头像、名字和工号")
    void testCreatedByConversion_convertsToUserSimpleResp() {
        Map<String, Object> tableData = new HashMap<>();
        tableData.put("id", 1001L);
        tableData.put("created_by", 8888L);
        tableData.put("company_name", "测试企业");
        DataRow dataRow = DataRow.builder().tables(Map.of("info", tableData)).build();

        DataBlock block = DataBlock.builder().moduleId(1001L).records(List.of(dataRow)).build();
        DataPage<DataBlock> page = DataPage.of(block, 1, 20, 1L);
        when(dataApi.queryPage(any(PageQueryReq.class))).thenReturn(page);

        SysUserPageResp userResp = new SysUserPageResp();
        userResp.setId(99L);
        userResp.setUserId(8888L);
        userResp.setUserName("张三");
        userResp.setUserAvatar("https://avatar.com/zhangsan.png");
        userResp.setWorkNumber("EMP8888");

        when(sysUserApi.getUsersByUserIds(any())).thenReturn(List.of(userResp));

        MenuDataResp resp =
                commonAggregateService.queryByMenu(100L, MenuQueryReq.builder().build());

        assertNotNull(resp);
        assertNotNull(resp.getData());
        assertNotNull(resp.getData().getRecord());
        assertEquals(1, resp.getData().getRecord().size());

        DataRecordItem item = resp.getData().getRecord().get(0);
        Object createdByObj = item.getRow().get("info.created_by");
        assertNotNull(createdByObj);
        assertTrue(createdByObj instanceof UserSimpleResp);

        UserSimpleResp userRespObj = (UserSimpleResp) createdByObj;
        assertEquals(8888L, userRespObj.getUserId());
        assertEquals("张三", userRespObj.getUserName());
        assertEquals("https://avatar.com/zhangsan.png", userRespObj.getUserAvatar());
        assertEquals("EMP8888", userRespObj.getWorkNumber());
    }

    @Test
    @DisplayName(
            "测试第一层主模块记录中的 business_status 成功转换为 StatusSimpleResp 对象包含 title、statusValue、statusBackground、statusFontColor")
    void testBusinessStatusConversion_convertsToStatusSimpleResp() {
        Map<String, Object> tableData = new HashMap<>();
        tableData.put("id", 1001L);
        tableData.put("business_status", 1);
        tableData.put("company_name", "测试企业");
        DataRow dataRow = DataRow.builder().tables(Map.of("info", tableData)).build();

        DataBlock block = DataBlock.builder().moduleId(1001L).records(List.of(dataRow)).build();
        DataPage<DataBlock> page = DataPage.of(block, 1, 20, 1L);
        when(dataApi.queryPage(any(PageQueryReq.class))).thenReturn(page);

        ModuleApprovalChainConfigResp.StatusNode statusNode =
                new ModuleApprovalChainConfigResp.StatusNode();
        statusNode.setId(10L);
        statusNode.setTitle("已审核");
        statusNode.setStatusValue(1);
        statusNode.setStatusBackground("#e6f7ff");
        statusNode.setStatusFontColor("#1890ff");

        when(sysStatusApi.getStatusByValue(eq(1), eq(1001L))).thenReturn(statusNode);

        MenuDataResp resp =
                commonAggregateService.queryByMenu(100L, MenuQueryReq.builder().build());

        assertNotNull(resp);
        assertNotNull(resp.getData());
        assertNotNull(resp.getData().getRecord());
        assertEquals(1, resp.getData().getRecord().size());

        DataRecordItem item = resp.getData().getRecord().get(0);
        Object statusObj = item.getRow().get("info.business_status");
        assertNotNull(statusObj);
        assertTrue(statusObj instanceof StatusSimpleResp);

        StatusSimpleResp statusRespObj = (StatusSimpleResp) statusObj;
        assertEquals("已审核", statusRespObj.getTitle());
        assertEquals(1, statusRespObj.getStatusValue());
        assertEquals("#e6f7ff", statusRespObj.getStatusBackground());
        assertEquals("#1890ff", statusRespObj.getStatusFontColor());
    }

    @Test
    @DisplayName("测试待办(approvalType=1)中非审批引擎返回的自建草稿记录，btnList 成功补齐为办理(handle)")
    void testApprovalButtons_fallbackForNonEngineTodoRecords() {
        com.jdec.platform.approval.api.resp.ApprovalIdButtonListResp emptyResp =
                new com.jdec.platform.approval.api.resp.ApprovalIdButtonListResp();
        emptyResp.setToDoList(Collections.emptyList());
        emptyResp.setDoneList(Collections.emptyList());
        emptyResp.setAllList(Collections.emptyList());
        when(approvalEngine.getApprovalListButton(eq(1001L))).thenReturn(emptyResp);

        Map<String, Object> tableData = new HashMap<>();
        tableData.put("id", 999L);
        tableData.put("created_by", 1L); // 与当前登录用户 UserContext.getUserId() 一致
        tableData.put("business_status", 1);
        tableData.put("company_name", "自建草稿企业");
        DataRow dataRow = DataRow.builder().tables(Map.of("info", tableData)).build();

        DataBlock block = DataBlock.builder().moduleId(1001L).records(List.of(dataRow)).build();
        DataPage<DataBlock> page = DataPage.of(block, 1, 20, 1L);
        when(dataApi.queryPage(any(PageQueryReq.class))).thenReturn(page);

        MenuDataResp resp =
                commonAggregateService.queryByMenu(
                        100L, MenuQueryReq.builder().approvalType(1).build());

        assertNotNull(resp);
        assertNotNull(resp.getApproval());
        assertEquals(1, resp.getApproval().size());

        var approvalRow = resp.getApproval().get(0);
        assertNotNull(approvalRow.getRow());
        assertEquals(999L, approvalRow.getRow().get("id"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> btnList =
                (List<Map<String, Object>>) approvalRow.getRow().get("btnList");
        assertNotNull(btnList);
        assertEquals(1, btnList.size());
        assertEquals("handle", btnList.get(0).get("buttonKey"));
        assertEquals("办理", btnList.get(0).get("buttonName"));
    }

    @Test
    @DisplayName("测试全部(approvalType=3)中，自建草稿补齐为办理(handle)，审批引擎返回的记录保持对应按钮(办理/查阅)")
    void testApprovalButtons_allTypeCorrectButtons() {
        when(sysApprovalChainConfigApi.getApprovalStatus(any())).thenReturn(true);

        // 模拟审批引擎返回：记录 101 为办理(handle)，记录 102 为查阅(view)
        com.jdec.platform.approval.api.dto.ApprpvalIdButton btn101 =
                new com.jdec.platform.approval.api.dto.ApprpvalIdButton();
        btn101.setRecordId(101L);
        btn101.setButtonKey("handle");
        btn101.setButtonName("办理");

        com.jdec.platform.approval.api.dto.ApprpvalIdButton btn102 =
                new com.jdec.platform.approval.api.dto.ApprpvalIdButton();
        btn102.setRecordId(102L);
        btn102.setButtonKey("view");
        btn102.setButtonName("查阅");

        com.jdec.platform.approval.api.resp.ApprovalIdButtonListResp countResp =
                new com.jdec.platform.approval.api.resp.ApprovalIdButtonListResp();
        countResp.setToDoList(List.of(btn101));
        countResp.setDoneList(List.of(btn102));
        countResp.setAllList(List.of(btn101, btn102));

        when(approvalEngine.getApprovalListButton(eq(1001L))).thenReturn(countResp);

        // 模拟底层数据返回 3 条记录：101(审批中待办), 102(已办), 999(非审批引擎自建草稿)
        Map<String, Object> row101 = new HashMap<>();
        row101.put("id", 101L);
        row101.put("created_by", 2L);
        row101.put("business_status", 2);
        row101.put("company_name", "审批中待办企业");

        Map<String, Object> row102 = new HashMap<>();
        row102.put("id", 102L);
        row102.put("created_by", 2L);
        row102.put("business_status", 3);
        row102.put("company_name", "已归档企业");

        Map<String, Object> row999 = new HashMap<>();
        row999.put("id", 999L);
        row999.put("created_by", 1L); // 当前用户自建草稿
        row999.put("business_status", 1);
        row999.put("company_name", "自建草稿企业");

        DataRow dr101 = DataRow.builder().tables(Map.of("info", row101)).build();
        DataRow dr102 = DataRow.builder().tables(Map.of("info", row102)).build();
        DataRow dr999 = DataRow.builder().tables(Map.of("info", row999)).build();

        DataBlock block =
                DataBlock.builder().moduleId(1001L).records(List.of(dr101, dr102, dr999)).build();
        DataPage<DataBlock> page = DataPage.of(block, 1, 20, 3L);
        when(dataApi.queryPage(any(PageQueryReq.class))).thenReturn(page);

        MenuDataResp resp =
                commonAggregateService.queryByMenu(
                        100L, MenuQueryReq.builder().approvalType(3).build());

        assertNotNull(resp);
        assertNotNull(resp.getApproval());
        assertEquals(3, resp.getApproval().size());

        Map<Long, String> btnKeyMap = new HashMap<>();
        for (var approvalItem : resp.getApproval()) {
            Long id = ((Number) approvalItem.getRow().get("id")).longValue();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> btns =
                    (List<Map<String, Object>>) approvalItem.getRow().get("btnList");
            assertNotNull(btns);
            assertFalse(btns.isEmpty());
            btnKeyMap.put(id, (String) btns.get(0).get("buttonKey"));
        }

        assertEquals("handle", btnKeyMap.get(101L), "审批待办记录应为办理(handle)");
        assertEquals("view", btnKeyMap.get(102L), "已审批记录应为查阅(view)");
        assertEquals("handle", btnKeyMap.get(999L), "自建草稿记录应为办理(handle)");
    }

    @Test
    @DisplayName("场景7：筛选单一 1:N 模块（财务账户 1010）时，返回表头中其他 1:N 模块（高管 1003/联系电话 1004）被禁用搜索与排序")
    void testFilter1ToN_maskOther1ToNSearchAndSort() {
        MenuQueryReq req =
                MenuQueryReq.builder()
                        .filters(
                                List.of(
                                        MenuFlatFieldFilterItem.builder()
                                                .fieldId(10101L) // financial_account.account_no
                                                // (1:N)
                                                .operator("eq")
                                                .value("6222020202")
                                                .build()))
                        .build();

        MenuDataResp resp = commonAggregateService.queryByMenu(100L, req);

        assertNotNull(resp);
        assertNotNull(resp.getHeader());

        // 1. 根模块与 1:1 模块字段：保留可搜索与可排序
        MenuHeaderColumnResp idCol = findColumnByFieldId(resp.getHeader(), 10001L);
        assertNotNull(idCol);
        assertEquals(1, idCol.getSorted());
        assertNotNull(idCol.getSearchType());

        MenuHeaderColumnResp regCapitalCol = findColumnByFieldId(resp.getHeader(), 10021L);
        assertNotNull(regCapitalCol);
        assertEquals(1, regCapitalCol.getSorted());
        assertNotNull(regCapitalCol.getSearchType());

        // 2. 命中的 1010 财务账户模块字段：保留可搜索与可排序
        MenuHeaderColumnResp accountNoCol = findColumnByFieldId(resp.getHeader(), 10101L);
        assertNotNull(accountNoCol);
        assertEquals(1, accountNoCol.getSorted());
        assertNotNull(accountNoCol.getSearchType());

        // 3. 其他未命中的 1:N 模块字段（1003 高管、1004 电话）：被设置为不可搜索且不可排序
        MenuHeaderColumnResp memberNameCol = findColumnByFieldId(resp.getHeader(), 10031L);
        assertNotNull(memberNameCol);
        assertEquals(0, memberNameCol.getSorted(), "未命中的 1:N 字段应置为不可排序 (sorted=0)");
        assertNull(memberNameCol.getSearchType(), "未命中的 1:N 字段应置为不可搜索 (searchType=null)");

        MenuHeaderColumnResp phoneCol = findColumnByFieldId(resp.getHeader(), 10041L);
        assertNotNull(phoneCol);
        assertEquals(0, phoneCol.getSorted(), "未命中的 1:N:M 字段应置为不可排序 (sorted=0)");
        assertNull(phoneCol.getSearchType(), "未命中的 1:N:M 字段应置为不可搜索 (searchType=null)");
    }

    @Test
    @DisplayName("场景8：仅排序单一 1:N 模块（财务账户 1010）时，正常允许排序并禁用其他 1:N 模块搜索与排序")
    void testSort1ToN_maskOther1ToNSearchAndSort() {
        MenuQueryReq req =
                MenuQueryReq.builder()
                        .sorts(List.of(FieldSortItem.asc(10101L))) // financial_account (1:N)
                        .build();

        MenuDataResp resp = commonAggregateService.queryByMenu(100L, req);

        assertNotNull(resp);
        assertNotNull(resp.getHeader());

        // 命中的 1010 财务账户保持可搜索与排序
        MenuHeaderColumnResp accountNoCol = findColumnByFieldId(resp.getHeader(), 10101L);
        assertNotNull(accountNoCol);
        assertEquals(1, accountNoCol.getSorted());
        assertNotNull(accountNoCol.getSearchType());

        // 其他 1:N 分支字段被禁用
        MenuHeaderColumnResp memberNameCol = findColumnByFieldId(resp.getHeader(), 10031L);
        assertNotNull(memberNameCol);
        assertEquals(0, memberNameCol.getSorted());
        assertNull(memberNameCol.getSearchType());

        ArgumentCaptor<PageQueryReq> captor = ArgumentCaptor.forClass(PageQueryReq.class);
        verify(dataApi).queryPage(captor.capture());
        assertEquals(1, captor.getValue().getSorts().size());
        assertEquals("financial_account", captor.getValue().getSorts().get(0).getTableName());
        assertEquals("account_no", captor.getValue().getSorts().get(0).getColumnName());
    }

    private static List<String> flattenPhysicalFields(PageQueryReq req) {
        List<String> fields = new ArrayList<>();
        if (req != null && req.getTables() != null) {
            for (TableSpec spec : req.getTables()) {
                if (spec == null || spec.getTableName() == null || spec.getColumns() == null) {
                    continue;
                }
                for (String column : spec.getColumns()) {
                    fields.add(spec.getTableName() + "." + column);
                }
            }
        }
        return fields;
    }

    @Test
    @DisplayName("场景9：筛选 1:N 模块（高管 1003）但排序另一个 1:N 模块（财务 1010）时，严格抛出 400 业务异常")
    void testFilter1ToN_AndSortAnother1ToN_throwsBusinessException() {
        MenuQueryReq req =
                MenuQueryReq.builder()
                        .filters(
                                List.of(
                                        MenuFlatFieldFilterItem.builder()
                                                .fieldId(10031L) // member (1:N 分支1)
                                                .operator("eq")
                                                .value("张三")
                                                .build()))
                        .sorts(List.of(FieldSortItem.asc(10101L))) // financial_account (1:N 分支2)
                        .build();

        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () -> commonAggregateService.queryByMenu(100L, req),
                        "筛选分支1且排序分支2时跨了 2 个独立 1:N 分支，必须被拦截抛出异常");

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("最多只允许 1 个 1:N 模块"));
    }

    @Test
    @DisplayName("测试 getOptionsByMenu 查询 created_by 时，特殊处理将结果转换为 UserSimpleResp 对象 JSON")
    void testGetOptionsByMenu_createdBy_convertsToUserSimpleResp() {
        when(openMenuApi.getModuleIdByMenuId(eq(100L))).thenReturn(1001L);

        // 模拟底层 lowcode dataApi 返回候选项：包含 Long userId (如 8888L)
        List<DataOptionResp> mockDataOptions =
                List.of(
                        DataOptionResp.builder().label(8888L).value(8888L).build(),
                        DataOptionResp.builder().label(9999L).value(9999L).build());
        when(dataApi.getOptions(any(OptionReq.class))).thenReturn(mockDataOptions);

        SysUserPageResp u8888 = new SysUserPageResp();
        u8888.setUserId(8888L);
        u8888.setUserName("张三");
        u8888.setUserAvatar("https://avatar.com/zhangsan.png");
        u8888.setWorkNumber("EMP8888");

        when(sysUserApi.getUsersByUserIds(any())).thenReturn(List.of(u8888));

        MenuOptionReq req =
                MenuOptionReq.builder().fieldId(10003L).build(); // 10003L = info.created_by
        List<DataOptionResp> options = commonAggregateService.getOptionsByMenu(100L, req);

        assertNotNull(options);
        assertEquals(2, options.size());

        // 验证 8888 用户已正确组装为 UserSimpleResp
        DataOptionResp opt1 = options.get(0);
        assertTrue(opt1.getValue() instanceof UserSimpleResp);
        assertTrue(opt1.getLabel() instanceof UserSimpleResp);
        UserSimpleResp user1 = (UserSimpleResp) opt1.getValue();
        assertEquals(8888L, user1.getUserId());
        assertEquals("张三", user1.getUserName());
        assertEquals("https://avatar.com/zhangsan.png", user1.getUserAvatar());
        assertEquals("EMP8888", user1.getWorkNumber());

        // 验证 9999 用户降级组装为只含 userId 的 UserSimpleResp
        DataOptionResp opt2 = options.get(1);
        assertTrue(opt2.getValue() instanceof UserSimpleResp);
        UserSimpleResp user2 = (UserSimpleResp) opt2.getValue();
        assertEquals(9999L, user2.getUserId());
    }

    @Test
    @DisplayName("测试 getOptionsByMenu 查询 reg_status 时，返回组装的 JSON/Map 结构")
    void testGetOptionsByMenu_regStatus_convertsToStructuredJson() {
        when(openMenuApi.getModuleIdByMenuId(eq(100L))).thenReturn(1001L);

        // 底层低代码数据引擎对 JSON 字段自动解析去重后返回结构化 Map 对象
        Map<String, Object> map1 = Map.of("word_describe", "存续", "word_id", 455);
        Map<String, Object> map2 = Map.of("word_describe", "注销", "word_id", 456);

        List<DataOptionResp> mockDataOptions =
                List.of(
                        DataOptionResp.builder().label(map1).value(map1).build(),
                        DataOptionResp.builder().label(map2).value(map2).build());
        when(dataApi.getOptions(any(OptionReq.class))).thenReturn(mockDataOptions);

        MenuOptionReq req =
                MenuOptionReq.builder().fieldId(10005L).build(); // 10005L = info.reg_status
        List<DataOptionResp> options = commonAggregateService.getOptionsByMenu(100L, req);

        assertNotNull(options);
        assertEquals(2, options.size());

        DataOptionResp opt1 = options.get(0);
        assertTrue(opt1.getValue() instanceof Map<?, ?>);
        assertEquals("存续", ((Map<?, ?>) opt1.getValue()).get("word_describe"));

        DataOptionResp opt2 = options.get(1);
        assertTrue(opt2.getValue() instanceof Map<?, ?>);
        assertEquals("注销", ((Map<?, ?>) opt2.getValue()).get("word_describe"));
    }

    @Test
    @DisplayName("测试 getOptionsByMenu 查询 business_status 时，转换为 StatusSimpleResp 对象")
    void testGetOptionsByMenu_businessStatus_convertsToStatusSimpleResp() {
        when(openMenuApi.getModuleIdByMenuId(eq(100L))).thenReturn(1001L);

        List<DataOptionResp> mockDataOptions =
                List.of(
                        DataOptionResp.builder().label(1).value(1).build(),
                        DataOptionResp.builder().label(2).value(2).build());
        when(dataApi.getOptions(any(OptionReq.class))).thenReturn(mockDataOptions);

        ModuleApprovalChainConfigResp.StatusNode node1 =
                new ModuleApprovalChainConfigResp.StatusNode();
        node1.setTitle("已审核");
        node1.setStatusValue(1);
        node1.setStatusBackground("#e6f7ff");
        node1.setStatusFontColor("#1890ff");

        when(sysStatusApi.getStatusByValue(eq(1), eq(1001L))).thenReturn(node1);
        when(sysStatusApi.getStatusByValue(eq(2), eq(1001L))).thenReturn(null);

        MenuOptionReq req =
                MenuOptionReq.builder().fieldId(10004L).build(); // 10004L = info.business_status
        List<DataOptionResp> options = commonAggregateService.getOptionsByMenu(100L, req);

        assertNotNull(options);
        assertEquals(2, options.size());

        // 验证 1 状态已正确组装为 StatusSimpleResp
        DataOptionResp opt1 = options.get(0);
        assertTrue(opt1.getValue() instanceof StatusSimpleResp);
        assertTrue(opt1.getLabel() instanceof StatusSimpleResp);
        StatusSimpleResp status1 = (StatusSimpleResp) opt1.getValue();
        assertEquals(1, status1.getStatusValue());
        assertEquals("已审核", status1.getTitle());
        assertEquals("#e6f7ff", status1.getStatusBackground());
        assertEquals("#1890ff", status1.getStatusFontColor());

        // 验证 2 状态降级组装为只含 statusValue 的 StatusSimpleResp
        DataOptionResp opt2 = options.get(1);
        assertTrue(opt2.getValue() instanceof StatusSimpleResp);
        StatusSimpleResp status2 = (StatusSimpleResp) opt2.getValue();
        assertEquals(2, status2.getStatusValue());
    }

    @Test
    @DisplayName(
            "验证数据权限解析：subjectCheckAll=1 与 subjectCheckAll=0 且 bizNames 为空/非空场景正确转换为 PhysicalFilterItem")
    void testDataPermissionResolution() {
        DataRightConfigResp config1 =
                DataRightConfigResp.builder()
                        .tableName("info")
                        .dataList(
                                List.of(
                                        DataRightConfigResp.DataConfig.builder()
                                                .permissionCheckAll(0)
                                                .subjectId(101L)
                                                .subjectCheckAll(1)
                                                .columnName("company_name")
                                                .bizNames(Collections.emptyList())
                                                .build()))
                        .build();

        DataRightConfigResp config2 =
                DataRightConfigResp.builder()
                        .tableName("basic_info")
                        .dataList(
                                List.of(
                                        DataRightConfigResp.DataConfig.builder()
                                                .permissionCheckAll(0)
                                                .subjectId(202L)
                                                .subjectCheckAll(0)
                                                .columnName("short_name")
                                                .bizNames(List.of("盛港焦化", "襄阳焦化"))
                                                .build(),
                                        DataRightConfigResp.DataConfig.builder()
                                                .permissionCheckAll(0)
                                                .subjectId(303L)
                                                .subjectCheckAll(0)
                                                .columnName("short_name")
                                                .bizNames(Collections.emptyList())
                                                .build()))
                        .build();

        when(openRolePermissionApi.getRoleDataPermissionDetail(1L))
                .thenReturn(List.of(config1, config2));

        CommonFilterResolver resolver =
                new CommonFilterResolver(
                        approvalEngine,
                        sysApprovalChainConfigApi,
                        configApi,
                        openRolePermissionApi,
                        dataApi);

        var filterItem = resolver.resolveApprovalFilters(null, null, null);
        assertNotNull(filterItem);
        List<com.jdec.platform.data.api.dto.request.FilterItem> filters = filterItem.filters();
        assertEquals(5, filters.size());

        // 1. info: subjectCheckAll = 1 -> info.subject_id = 101
        var f1 = filters.get(0);
        assertEquals("info", f1.getTableName());
        assertEquals("subject_id", f1.getColumnName());
        assertEquals("EQ", f1.getOperator());
        assertEquals(101L, f1.getValue());

        // 2. basic_info: subjectCheckAll = 0, subjectId = 202
        var f2 = filters.get(1);
        assertEquals("basic_info", f2.getTableName());
        assertEquals("subject_id", f2.getColumnName());
        assertEquals("EQ", f2.getOperator());
        assertEquals(202L, f2.getValue());

        // 3. basic_info: short_name IN ('盛港焦化', '襄阳焦化')
        var f3 = filters.get(2);
        assertEquals("basic_info", f3.getTableName());
        assertEquals("short_name", f3.getColumnName());
        assertEquals("IN", f3.getOperator());
        assertEquals(List.of("盛港焦化", "襄阳焦化"), f3.getValue());

        // 4. basic_info: subjectId = 303
        var f4 = filters.get(3);
        assertEquals("basic_info", f4.getTableName());
        assertEquals("subject_id", f4.getColumnName());
        assertEquals(303L, f4.getValue());

        // 5. basic_info: bizNames 为空 -> short_name IN ()
        var f5 = filters.get(4);
        assertEquals("basic_info", f5.getTableName());
        assertEquals("short_name", f5.getColumnName());
        assertEquals("IN", f5.getOperator());
        assertEquals(Collections.emptyList(), f5.getValue());
    }

    @Test
    @DisplayName("测试待办(approvalType=1)场景：不考虑数据权限，仅为自建草稿与审批待办单据OR组合")
    void testApprovalType1_withoutDataPermission() {
        when(sysApprovalChainConfigApi.getApprovalStatus(any())).thenReturn(true);

        // 1. 审批引擎返回待办记录 101
        com.jdec.platform.approval.api.dto.ApprpvalIdButton btn101 =
                new com.jdec.platform.approval.api.dto.ApprpvalIdButton();
        btn101.setRecordId(101L);
        btn101.setButtonKey("handle");
        btn101.setButtonName("办理");

        com.jdec.platform.approval.api.resp.ApprovalIdButtonListResp resp =
                new com.jdec.platform.approval.api.resp.ApprovalIdButtonListResp();
        resp.setToDoList(List.of(btn101));
        when(approvalEngine.getApprovalListButton(eq(1001L))).thenReturn(resp);

        // 2. 角色数据权限配置 (待办不应调用此接口查询 ID)
        DataRightConfigResp config =
                DataRightConfigResp.builder()
                        .tableName("info")
                        .dataList(
                                List.of(
                                        DataRightConfigResp.DataConfig.builder()
                                                .permissionCheckAll(0)
                                                .subjectId(999L)
                                                .subjectCheckAll(1)
                                                .build()))
                        .build();
        when(openRolePermissionApi.getRoleDataPermissionDetail(1L)).thenReturn(List.of(config));

        CommonFilterResolver resolver =
                new CommonFilterResolver(
                        approvalEngine,
                        sysApprovalChainConfigApi,
                        configApi,
                        openRolePermissionApi,
                        dataApi);

        var filterItem = resolver.resolveApprovalFilters(1001L, 1, companyModuleTree);
        assertNotNull(filterItem);
        assertFalse(filterItem.shortCircuitEmpty());

        // 验证组合后的顶层为链式 OR 树: 纯逻辑 OR 节点 -> children: [草稿分支 (AND容器), 审批待办分支 (id IN [101])]
        List<com.jdec.platform.data.api.dto.request.FilterItem> filters = filterItem.filters();
        assertEquals(1, filters.size());
        var rootOr = filters.get(0);
        assertEquals("OR", rootOr.getLogic());
        assertNull(rootOr.getColumnName());
        assertNotNull(rootOr.getChildren());
        assertEquals(2, rootOr.getChildren().size());

        // 分支 1: 草稿分支 (AND 容器)
        var draftBranch = rootOr.getChildren().get(0);
        assertEquals("AND", draftBranch.getLogic());
        assertNull(draftBranch.getColumnName());
        assertNotNull(draftBranch.getChildren());
        assertEquals(2, draftBranch.getChildren().size());
        assertEquals("created_by", draftBranch.getChildren().get(0).getColumnName());
        assertEquals("business_status", draftBranch.getChildren().get(1).getColumnName());

        // 分支 2: 审批待办分支 (id IN [101])，不再有数据权限分支
        var approvalBranch = rootOr.getChildren().get(1);
        assertEquals("info", approvalBranch.getTableName());
        assertEquals("id", approvalBranch.getColumnName());
        assertEquals("IN", approvalBranch.getOperator());
        @SuppressWarnings("unchecked")
        List<Long> approvalIds = (List<Long>) approvalBranch.getValue();
        assertEquals(List.of(101L), approvalIds);
    }

    @Test
    @DisplayName("测试全部(approvalType=3)场景：数据权限单据ID与审批引擎单据ID取并集，并与自建草稿OR组合")
    void testApprovalType3_dataPermissionIdsUnionWithApprovalIds() {
        when(sysApprovalChainConfigApi.getApprovalStatus(any())).thenReturn(true);

        // 1. 审批引擎返回全部记录 101
        com.jdec.platform.approval.api.dto.ApprpvalIdButton btn101 =
                new com.jdec.platform.approval.api.dto.ApprpvalIdButton();
        btn101.setRecordId(101L);
        btn101.setButtonKey("handle");
        btn101.setButtonName("办理");

        com.jdec.platform.approval.api.resp.ApprovalIdButtonListResp resp =
                new com.jdec.platform.approval.api.resp.ApprovalIdButtonListResp();
        resp.setAllList(List.of(btn101));
        when(approvalEngine.getApprovalListButton(eq(1001L))).thenReturn(resp);

        // 2. 角色数据权限配置
        DataRightConfigResp config =
                DataRightConfigResp.builder()
                        .tableName("info")
                        .dataList(
                                List.of(
                                        DataRightConfigResp.DataConfig.builder()
                                                .permissionCheckAll(0)
                                                .subjectId(999L)
                                                .subjectCheckAll(1)
                                                .build()))
                        .build();
        when(openRolePermissionApi.getRoleDataPermissionDetail(1L)).thenReturn(List.of(config));

        // 3. 模拟 DataApi 单独按数据权限条件查询，返回数据权限匹配的单据 ID 202
        DataRow permRow = DataRow.builder().tables(Map.of("info", Map.of("id", 202L))).build();
        DataBlock permBlock = DataBlock.builder().moduleId(1001L).records(List.of(permRow)).build();
        when(dataApi.queryPage(
                        argThat(
                                req ->
                                        req != null
                                                && req.getTables() != null
                                                && !req.getTables().isEmpty()
                                                && List.of("id")
                                                        .equals(
                                                                req.getTables()
                                                                        .get(0)
                                                                        .getColumns()))))
                .thenReturn(DataPage.of(permBlock, 1, 10000, 1L));

        CommonFilterResolver resolver =
                new CommonFilterResolver(
                        approvalEngine,
                        sysApprovalChainConfigApi,
                        configApi,
                        openRolePermissionApi,
                        dataApi);

        var filterItem = resolver.resolveApprovalFilters(1001L, 3, companyModuleTree);
        assertNotNull(filterItem);
        assertFalse(filterItem.shortCircuitEmpty());

        // 验证组合后的顶层 OR 树: 纯逻辑 OR 节点 -> 3个子分支：[草稿分支, 审批分支, 数据权限分支]
        List<com.jdec.platform.data.api.dto.request.FilterItem> filters = filterItem.filters();
        assertEquals(1, filters.size());
        var rootOr = filters.get(0);
        assertEquals("OR", rootOr.getLogic());
        assertNull(rootOr.getColumnName());
        assertNotNull(rootOr.getChildren());
        assertEquals(3, rootOr.getChildren().size());

        // 分支 1: 草稿分支 (AND 容器)
        var draftBranch = rootOr.getChildren().get(0);
        assertEquals("AND", draftBranch.getLogic());
        assertNull(draftBranch.getColumnName());
        assertNotNull(draftBranch.getChildren());
        assertEquals(2, draftBranch.getChildren().size());
        assertEquals("created_by", draftBranch.getChildren().get(0).getColumnName());
        assertEquals("business_status", draftBranch.getChildren().get(1).getColumnName());

        // 分支 2: approvalBranch (id IN [101])
        var approvalBranch = rootOr.getChildren().get(1);
        assertEquals("info", approvalBranch.getTableName());
        assertEquals("id", approvalBranch.getColumnName());
        assertEquals("IN", approvalBranch.getOperator());
        @SuppressWarnings("unchecked")
        List<Long> approvalIds = (List<Long>) approvalBranch.getValue();
        assertEquals(List.of(101L), approvalIds);

        // 分支 3: dataPermBranch (id IN [202])
        var dataPermBranch = rootOr.getChildren().get(2);
        assertEquals("info", dataPermBranch.getTableName());
        assertEquals("id", dataPermBranch.getColumnName());
        assertEquals("IN", dataPermBranch.getOperator());
        @SuppressWarnings("unchecked")
        List<Long> dataPermIds = (List<Long>) dataPermBranch.getValue();
        assertEquals(List.of(202L), dataPermIds);
    }
}
