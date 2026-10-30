package com.jdec.platform.common.biz.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jdec.platform.common.api.UserMenuHeaderApi;
import com.jdec.platform.common.api.dto.request.MenuActiveToggleReq;
import com.jdec.platform.common.api.dto.response.StatusSimpleResp;
import com.jdec.platform.common.biz.controller.CommonAggregateController;
import com.jdec.platform.common.biz.service.CommonAggregateService;
import com.jdec.platform.config.api.SysStatusApi;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.response.ModuleApprovalChainConfigResp;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.config.open.api.OpenMenuApi;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.datasource.JooqDataContextFactory;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.security.model.LoginUser;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MenuActiveToggleTest {

    private OpenMenuApi openMenuApi;
    private ConfigApi configApi;
    private SysStatusApi sysStatusApi;
    private JooqDataContextFactory jooqDataContextFactory;
    private CommonAggregateServiceImpl service;
    private CommonAggregateController controller;

    private AtomicReference<String> executedSql;
    private AtomicReference<Object[]> executedBindings;

    @BeforeEach
    void setUp() {
        AppContext.setContext("company", null);
        UserContext.setLoginUser(
                LoginUser.builder().userId(88L).roleId(1L).username("admin").build());

        openMenuApi = mock(OpenMenuApi.class);
        configApi = mock(ConfigApi.class);
        sysStatusApi = mock(SysStatusApi.class);
        jooqDataContextFactory = mock(JooqDataContextFactory.class);

        executedSql = new AtomicReference<>();
        executedBindings = new AtomicReference<>();

        MockDataProvider provider =
                new MockDataProvider() {
                    @Override
                    public MockResult[] execute(MockExecuteContext ctx) {
                        executedSql.set(ctx.sql());
                        executedBindings.set(ctx.bindings());
                        return new MockResult[] {new MockResult(2, null)};
                    }
                };
        DSLContext dsl = DSL.using(new MockConnection(provider), SQLDialect.MYSQL);
        when(jooqDataContextFactory.getContext()).thenReturn(dsl);

        service =
                new CommonAggregateServiceImpl(
                        mock(UserMenuHeaderApi.class),
                        openMenuApi,
                        configApi,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        jooqDataContextFactory,
                        sysStatusApi);

        controller = new CommonAggregateController(service);
    }

    @Test
    @DisplayName("Controller 端点 toggleActiveStatusByMenu 成功委托 Service 并返回增强后的 StatusSimpleResp")
    void testControllerToggleActiveStatusByMenu() {
        CommonAggregateService mockService = mock(CommonAggregateService.class);
        CommonAggregateController testController = new CommonAggregateController(mockService);
        MenuActiveToggleReq req =
                MenuActiveToggleReq.builder()
                        .businessNos(List.of("BIZ001", "BIZ002"))
                        .targetStatus(99)
                        .build();

        StatusSimpleResp expectedResp =
                StatusSimpleResp.builder()
                        .statusValue(99)
                        .title("已启用")
                        .statusBackground("#e6f7ff")
                        .statusFontColor("#1890ff")
                        .build();

        when(mockService.toggleActiveStatusByMenu(eq(100L), eq(req))).thenReturn(expectedResp);

        ApiResponse<StatusSimpleResp> resp = testController.toggleActiveStatusByMenu(100L, req);
        assertThat(resp).isNotNull();
        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(resp.getData()).isEqualTo(expectedResp);
    }

    @Test
    @DisplayName("参数校验: menuId 为空或请求体为空时抛异常")
    void testValidateEmptyParams() {
        assertThatThrownBy(() -> service.toggleActiveStatusByMenu(null, new MenuActiveToggleReq()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("菜单 ID 不能为空");

        assertThatThrownBy(() -> service.toggleActiveStatusByMenu(100L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("请求体 req 不能为空");

        MenuActiveToggleReq emptyReq =
                MenuActiveToggleReq.builder().businessNos(Collections.emptyList()).build();
        assertThatThrownBy(() -> service.toggleActiveStatusByMenu(100L, emptyReq))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("业务编号列表 businessNos 不能为空");
    }

    @Test
    @DisplayName("参数校验: 未提供有效启用/停用目标状态或动作时抛异常")
    void testValidateInvalidTargetStatus() {
        MenuActiveToggleReq req =
                MenuActiveToggleReq.builder()
                        .businessNos(List.of("BIZ001"))
                        .targetStatus(50) // 非法状态
                        .build();

        assertThatThrownBy(() -> service.toggleActiveStatusByMenu(100L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未指定有效的启用/停用操作目标");
    }

    @Test
    @DisplayName("业务校验: 模块未开启启用停用功能 (enableActive != 1) 时抛 BusinessException")
    void testValidateModuleNotEnableActive() {
        when(openMenuApi.getModuleIdByMenuId(100L)).thenReturn(1001L);

        ModuleInfo moduleInfo =
                ModuleInfo.builder()
                        .moduleId(1001L)
                        .primaryTable("company.info")
                        .enableActive(0)
                        .build();

        ModuleTreeDTO moduleTree =
                ModuleTreeDTO.builder()
                        .rootModuleId(1001L)
                        .rootModule(moduleInfo)
                        .moduleMap(Map.of(1001L, moduleInfo))
                        .build();
        when(configApi.getModuleTree(eq("company"), any(), eq(1001L))).thenReturn(moduleTree);

        MenuActiveToggleReq req =
                MenuActiveToggleReq.builder()
                        .businessNos(List.of("BIZ001"))
                        .targetStatus(99)
                        .build();

        assertThatThrownBy(() -> service.toggleActiveStatusByMenu(100L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未开启启停功能");
    }

    @Test
    @DisplayName("执行启用: targetStatus=99，将 11 改为 99，返回增强后的 StatusSimpleResp")
    void testExecuteActivate() {
        when(openMenuApi.getModuleIdByMenuId(100L)).thenReturn(1001L);

        ModuleInfo moduleInfo =
                ModuleInfo.builder()
                        .moduleId(1001L)
                        .primaryTable("company.info")
                        .enableActive(1)
                        .build();

        ModuleTreeDTO moduleTree =
                ModuleTreeDTO.builder()
                        .rootModuleId(1001L)
                        .rootModule(moduleInfo)
                        .moduleMap(Map.of(1001L, moduleInfo))
                        .build();
        when(configApi.getModuleTree(eq("company"), any(), eq(1001L))).thenReturn(moduleTree);

        // Mock 状态元数据增强
        ModuleApprovalChainConfigResp.StatusNode statusNode =
                new ModuleApprovalChainConfigResp.StatusNode();
        statusNode.setStatusValue(99);
        statusNode.setTitle("已启用");
        statusNode.setStatusBackground("#e6f7ff");
        statusNode.setStatusFontColor("#1890ff");
        when(sysStatusApi.getStatusByValue(eq(99), eq(1001L))).thenReturn(statusNode);

        MenuActiveToggleReq req =
                MenuActiveToggleReq.builder()
                        .businessNos(List.of("BIZ001", "BIZ002"))
                        .targetStatus(99)
                        .build();

        StatusSimpleResp result = service.toggleActiveStatusByMenu(100L, req);
        assertThat(result).isNotNull();
        assertThat(result.getStatusValue()).isEqualTo(99);
        assertThat(result.getTitle()).isEqualTo("已启用");
        assertThat(result.getStatusBackground()).isEqualTo("#e6f7ff");
        assertThat(result.getStatusFontColor()).isEqualTo("#1890ff");

        // 验证生成的 SQL 和绑定参数: 将 11 改为 99
        String sql = executedSql.get();
        assertThat(sql).isNotNull();
        assertThat(sql.toLowerCase()).contains("update `company`.`info`");
        assertThat(sql.toLowerCase())
                .contains("set `business_status` = ?, `update_by` = ?, `updated_date` = ?");
        assertThat(sql.toLowerCase())
                .contains(
                        "where (`business_no` in (?, ?) and `business_status` = ? and `deleted` ="
                                + " ?)");

        Object[] bindings = executedBindings.get();
        assertThat(bindings).isNotNull();
        // targetStatus = 99
        assertThat(bindings[0]).isEqualTo(99);
        // update_by = 88L
        assertThat(bindings[1]).isEqualTo(88L);
        // originalStatus = 11 (倒数第2个参数)
        assertThat(bindings[bindings.length - 2]).isEqualTo(11);
        // deleted = 0 (倒数第1个参数)
        assertThat(bindings[bindings.length - 1]).isEqualTo((byte) 0);
    }

    @Test
    @DisplayName("执行停用: action='deactivate'，将 99 改为 11，返回增强后的 StatusSimpleResp")
    void testExecuteDeactivate() {
        when(openMenuApi.getModuleIdByMenuId(100L)).thenReturn(1001L);

        ModuleInfo moduleInfo =
                ModuleInfo.builder()
                        .moduleId(1001L)
                        .primaryTable("company.info")
                        .enableActive(1)
                        .build();

        ModuleTreeDTO moduleTree =
                ModuleTreeDTO.builder()
                        .rootModuleId(1001L)
                        .rootModule(moduleInfo)
                        .moduleMap(Map.of(1001L, moduleInfo))
                        .build();
        when(configApi.getModuleTree(eq("company"), any(), eq(1001L))).thenReturn(moduleTree);

        // Mock 状态元数据增强
        ModuleApprovalChainConfigResp.StatusNode statusNode =
                new ModuleApprovalChainConfigResp.StatusNode();
        statusNode.setStatusValue(11);
        statusNode.setTitle("已停用");
        statusNode.setStatusBackground("#fff1f0");
        statusNode.setStatusFontColor("#f5222d");
        when(sysStatusApi.getStatusByValue(eq(11), eq(1001L))).thenReturn(statusNode);

        MenuActiveToggleReq req =
                MenuActiveToggleReq.builder()
                        .businessNos(List.of("BIZ003"))
                        .action("deactivate")
                        .build();

        StatusSimpleResp result = service.toggleActiveStatusByMenu(100L, req);
        assertThat(result).isNotNull();
        assertThat(result.getStatusValue()).isEqualTo(11);
        assertThat(result.getTitle()).isEqualTo("已停用");
        assertThat(result.getStatusBackground()).isEqualTo("#fff1f0");
        assertThat(result.getStatusFontColor()).isEqualTo("#f5222d");

        // 验证生成的 SQL 和绑定参数: 将 99 改为 11
        Object[] bindings = executedBindings.get();
        assertThat(bindings).isNotNull();
        // targetStatus = 11
        assertThat(bindings[0]).isEqualTo(11);
        // originalStatus = 99
        assertThat(bindings[bindings.length - 2]).isEqualTo(99);
        // deleted = 0
        assertThat(bindings[bindings.length - 1]).isEqualTo((byte) 0);
    }
}
