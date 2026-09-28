package com.jdec.platform.common.biz.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jdec.platform.approval.api.IApprovalEngine;
import com.jdec.platform.common.api.UserMenuHeaderApi;
import com.jdec.platform.common.biz.dto.request.FlatFilterItem;
import com.jdec.platform.common.biz.dto.request.MenuQueryReq;
import com.jdec.platform.config.api.SysApprovalChainConfigApi;
import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.common.ProjectTableRelationsDTO;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import com.jdec.platform.config.api.dto.response.MenuHeaderColumnResp;
import com.jdec.platform.config.api.dto.response.MenuHeaderDisplayResp;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.config.open.api.OpenMenuApi;
import com.jdec.platform.data.api.DataApi;
import com.jdec.platform.data.api.dto.request.PageQueryReq;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.exception.BusinessException;
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

    private CommonAggregateServiceImpl commonAggregateService;

    private ModuleTreeDTO companyModuleTree;
    private ProjectTableRelationsDTO companyRelations;
    private MenuHeaderColumnResp companyHeader;

    @BeforeEach
    void setUp() {
        AppContext.setContext("company", null);
        UserContext.setLoginUser(
                com.jdec.platform.shared.security.model.LoginUser.builder()
                        .userId(1L)
                        .roleId(1L)
                        .username("admin")
                        .build());

        userMenuHeaderApi = mock(UserMenuHeaderApi.class);
        openMenuApi = mock(OpenMenuApi.class);
        configApi = mock(ConfigApi.class);
        dataApi = mock(DataApi.class);
        approvalEngine = mock(IApprovalEngine.class);
        sysApprovalChainConfigApi = mock(SysApprovalChainConfigApi.class);

        commonAggregateService =
                new CommonAggregateServiceImpl(
                        userMenuHeaderApi,
                        openMenuApi,
                        configApi,
                        dataApi,
                        approvalEngine,
                        sysApprovalChainConfigApi);

        initCompanyFixture();

        MenuHeaderDisplayResp displayResp =
                MenuHeaderDisplayResp.builder().header(companyHeader).build();
        when(userMenuHeaderApi.getUserMenuHeaderDisplay(anyLong(), anyLong(), anyLong()))
                .thenReturn(displayResp);
        when(configApi.getModuleTree(eq("company"), any(), eq(1001L)))
                .thenReturn(companyModuleTree);
        when(configApi.getProjectTableRelations(eq("company"))).thenReturn(companyRelations);

        when(dataApi.query(any(PageQueryReq.class))).thenReturn(DataPage.empty());
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

        p2c.put(1001L, List.of(1002L, 1007L, 1003L, 1010L));
        p2c.put(1003L, List.of(1004L));

        addField(fieldMap, fieldToModuleMap, 10001L, 1001L, "info", "id");
        addField(fieldMap, fieldToModuleMap, 10002L, 1001L, "info", "company_name");
        addField(fieldMap, fieldToModuleMap, 10021L, 1002L, "basic_info", "reg_capital");
        addField(fieldMap, fieldToModuleMap, 10071L, 1007L, "address", "detail_address");
        addField(fieldMap, fieldToModuleMap, 10031L, 1003L, "member", "member_name");
        addField(fieldMap, fieldToModuleMap, 10041L, 1004L, "member_contact", "phone");
        addField(fieldMap, fieldToModuleMap, 10101L, 1010L, "financial_account", "account_no");

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
    }

    private static MenuHeaderColumnResp col(Long moduleId, Long fieldId, String label) {
        return MenuHeaderColumnResp.builder()
                .moduleId(moduleId)
                .fieldId(fieldId)
                .label(label)
                .build();
    }

    @Test
    @DisplayName("场景1：无搜索条件时，收集根模块 + 所有 1:1 模块 + 所有 1:N 模块字段")
    void testNoFilter_collectAllFields() {
        MenuQueryReq req = MenuQueryReq.builder().filters(Collections.emptyList()).build();

        commonAggregateService.queryByMenu(100L, req);

        ArgumentCaptor<PageQueryReq> captor = ArgumentCaptor.forClass(PageQueryReq.class);
        verify(dataApi).query(captor.capture());

        List<Long> actualFields = captor.getValue().getFields();
        assertEquals(
                List.of(10001L, 10002L, 10021L, 10071L, 10031L, 10041L, 10101L),
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
                                        FlatFilterItem.builder()
                                                .fieldId(10021L) // basic_info.reg_capital (1:1)
                                                .operator("eq")
                                                .value("1000万")
                                                .build()))
                        .build();

        commonAggregateService.queryByMenu(100L, req);

        ArgumentCaptor<PageQueryReq> captor = ArgumentCaptor.forClass(PageQueryReq.class);
        verify(dataApi).query(captor.capture());

        List<Long> actualFields = captor.getValue().getFields();
        assertEquals(
                List.of(10001L, 10002L, 10021L, 10071L, 10031L, 10041L, 10101L),
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
                                        FlatFilterItem.builder()
                                                .fieldId(10101L) // financial_account.account_no
                                                // (1:N)
                                                .operator("eq")
                                                .value("6222020202")
                                                .build()))
                        .build();

        commonAggregateService.queryByMenu(100L, req);

        ArgumentCaptor<PageQueryReq> captor = ArgumentCaptor.forClass(PageQueryReq.class);
        verify(dataApi).query(captor.capture());

        List<Long> actualFields = captor.getValue().getFields();
        assertEquals(
                List.of(10001L, 10002L, 10021L, 10071L, 10101L),
                actualFields,
                "应收集根模块 + 1:1 模块 + 命中的 1010 财务账户，剔除 1003/1004 高管成员");
    }

    @Test
    @DisplayName("场景4：同时筛选 1:N 模块（高管 1003）与 1:N:M 模块（电话 1004）时，归级为同分支，正常允许查询并收集二者")
    void testFilter1ToNAnd1ToNToM_allowAndCollectTogether() {
        MenuQueryReq req =
                MenuQueryReq.builder()
                        .filters(
                                List.of(
                                        FlatFilterItem.builder()
                                                .fieldId(10031L) // member.member_name (1:N)
                                                .operator("like")
                                                .value("张三")
                                                .build(),
                                        FlatFilterItem.builder()
                                                .fieldId(10041L) // member_contact.phone (1:N:M)
                                                .operator("eq")
                                                .value("13800000000")
                                                .build()))
                        .build();

        commonAggregateService.queryByMenu(100L, req);

        ArgumentCaptor<PageQueryReq> captor = ArgumentCaptor.forClass(PageQueryReq.class);
        verify(dataApi).query(captor.capture());

        List<Long> actualFields = captor.getValue().getFields();
        assertEquals(
                List.of(10001L, 10002L, 10021L, 10071L, 10031L, 10041L),
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
                                        FlatFilterItem.builder()
                                                .fieldId(10031L) // member (1:N 分支1)
                                                .operator("eq")
                                                .value("张三")
                                                .build(),
                                        FlatFilterItem.builder()
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
}
