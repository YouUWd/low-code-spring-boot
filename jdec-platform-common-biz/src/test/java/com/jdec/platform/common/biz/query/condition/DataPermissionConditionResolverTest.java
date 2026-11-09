package com.jdec.platform.common.biz.query.condition;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.response.DataRightConfigResp;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.config.open.api.OpenRolePermissionApi;
import com.jdec.platform.data.api.DataApi;
import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.common.DataRow;
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.data.api.model.predicate.ComparisonPredicate;
import com.jdec.platform.data.api.model.predicate.CompoundPredicate;
import com.jdec.platform.data.api.model.predicate.Predicate;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.security.model.LoginUser;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DataPermissionConditionResolverTest {

    private OpenRolePermissionApi openRolePermissionApi;
    private DataApi dataApi;
    private ConfigApi configApi;
    private DataPermissionConditionResolver resolver;
    private ModuleTreeDTO moduleTree;

    @BeforeEach
    void setUp() {
        UserContext.setLoginUser(
                LoginUser.builder().userId(1L).roleId(1L).username("admin").build());
        openRolePermissionApi = mock(OpenRolePermissionApi.class);
        dataApi = mock(DataApi.class);
        configApi = mock(ConfigApi.class);
        resolver = new DataPermissionConditionResolver(openRolePermissionApi, dataApi, configApi);

        ModuleInfo rootModule =
                ModuleInfo.builder()
                        .moduleId(1001L)
                        .moduleCode("ORDER")
                        .primaryTable("t_order")
                        .build();

        moduleTree =
                ModuleTreeDTO.builder()
                        .rootModuleId(1001L)
                        .rootModule(rootModule)
                        .moduleMap(Map.of(1001L, rootModule))
                        .build();
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    @Test
    @DisplayName("多主体数据权限场景：同一表下不同主体之间应使用 OR 连接，主体内部使用 AND")
    void testMultipleSubjectsConnectedWithOr() {
        DataRightConfigResp config =
                DataRightConfigResp.builder()
                        .tableName("t_order")
                        .dataList(
                                List.of(
                                        DataRightConfigResp.DataConfig.builder()
                                                .permissionCheckAll(0)
                                                .subjectId(101L)
                                                .subjectCheckAll(0)
                                                .columnName("dept_id")
                                                .bizNames(List.of("D1"))
                                                .build(),
                                        DataRightConfigResp.DataConfig.builder()
                                                .permissionCheckAll(0)
                                                .subjectId(202L)
                                                .subjectCheckAll(0)
                                                .columnName("dept_id")
                                                .bizNames(List.of("D2"))
                                                .build()))
                        .build();

        when(openRolePermissionApi.getRoleDataPermissionDetail(1L)).thenReturn(List.of(config));

        ArgumentCaptor<QueryReq> captor = ArgumentCaptor.forClass(QueryReq.class);
        DataRow row = new DataRow();
        row.putTable("t_order", Map.of("id", 1001L));
        when(dataApi.queryList(captor.capture()))
                .thenReturn(DataBlock.builder().view("ORDER").records(List.of(row)).build());

        Optional<Condition> condOpt = resolver.resolve(moduleTree, null);
        assertTrue(condOpt.isPresent());

        QueryReq capturedReq = captor.getValue();
        assertNotNull(capturedReq);
        Predicate filter = capturedReq.getFilter();
        assertNotNull(filter);

        // 验证顶层为 OR 复合谓词（连接主体 101 和主体 202）
        assertTrue(filter instanceof CompoundPredicate, "Filter 应为复合谓词");
        CompoundPredicate orPred = (CompoundPredicate) filter;
        assertEquals("OR", orPred.getOperator(), "多主体之间必须使用 OR 连接");
        assertEquals(2, orPred.getPredicates().size());

        // 验证每个主体内部为 AND（subject_id = xxx AND dept_id IN (...)）
        Predicate sub1 = orPred.getPredicates().get(0);
        assertTrue(sub1 instanceof CompoundPredicate);
        assertEquals("AND", ((CompoundPredicate) sub1).getOperator());

        Predicate sub2 = orPred.getPredicates().get(1);
        assertTrue(sub2 instanceof CompoundPredicate);
        assertEquals("AND", ((CompoundPredicate) sub2).getOperator());
    }

    @Test
    @DisplayName("节点全选场景：字段A节点全选不应绕过字段B的限制（修复 anyMatch 越权漏洞）")
    void testNodeCheckAllDoesNotBypassOtherRestrictedNodes() {
        DataRightConfigResp config =
                DataRightConfigResp.builder()
                        .tableName("t_order")
                        .dataList(
                                List.of(
                                        // 字段 A 全选放行
                                        DataRightConfigResp.DataConfig.builder()
                                                .columnName("dept_id")
                                                .permissionCheckAll(1)
                                                .subjectId(null)
                                                .subjectCheckAll(0)
                                                .bizNames(Collections.emptyList())
                                                .build(),
                                        // 字段 B 受限
                                        DataRightConfigResp.DataConfig.builder()
                                                .columnName("order_type")
                                                .permissionCheckAll(0)
                                                .subjectId(101L)
                                                .subjectCheckAll(0)
                                                .bizNames(List.of("NORMAL"))
                                                .build()))
                        .build();

        when(openRolePermissionApi.getRoleDataPermissionDetail(1L)).thenReturn(List.of(config));

        ArgumentCaptor<QueryReq> captor = ArgumentCaptor.forClass(QueryReq.class);
        DataRow row = new DataRow();
        row.putTable("t_order", Map.of("id", 999L));
        when(dataApi.queryList(captor.capture()))
                .thenReturn(DataBlock.builder().view("ORDER").records(List.of(row)).build());

        Optional<Condition> condOpt = resolver.resolve(moduleTree, null);
        assertTrue(condOpt.isPresent());

        // 验证 dataApi.queryList 确实被调用，字段 B 没有被整表跳过
        verify(dataApi, times(1)).queryList(any());

        QueryReq capturedReq = captor.getValue();
        assertNotNull(capturedReq.getFilter());

        // 验证过滤条件中包含 order_type 限制，且不包含 dept_id 空 IN 条件
        String filterStr = capturedReq.getFilter().toString();
        assertTrue(filterStr.contains("order_type"));
        assertFalse(filterStr.contains("dept_id"));
    }

    @Test
    @DisplayName("整表所有节点全选放行：返回 DSL.trueCondition() 且无需调用底层查询求解")
    void testAllNodesCheckAllReturnsTrueCondition() {
        DataRightConfigResp config =
                DataRightConfigResp.builder()
                        .tableName("t_order")
                        .dataList(
                                List.of(
                                        DataRightConfigResp.DataConfig.builder()
                                                .columnName("dept_id")
                                                .permissionCheckAll(1)
                                                .build(),
                                        DataRightConfigResp.DataConfig.builder()
                                                .columnName("order_type")
                                                .permissionCheckAll(1)
                                                .build()))
                        .build();

        when(openRolePermissionApi.getRoleDataPermissionDetail(1L)).thenReturn(List.of(config));

        Optional<Condition> condOpt = resolver.resolve(moduleTree, null);
        assertTrue(condOpt.isPresent());
        assertEquals(DSL.trueCondition(), condOpt.get());

        // 全选放行时无需查询底层引擎
        verify(dataApi, never()).queryList(any());
    }

    @Test
    @DisplayName("数据权限过滤后无匹配记录：返回 DSL.falseCondition() 阻断访问")
    void testEmptyMatchedIdsReturnsFalseCondition() {
        DataRightConfigResp config =
                DataRightConfigResp.builder()
                        .tableName("t_order")
                        .dataList(
                                List.of(
                                        DataRightConfigResp.DataConfig.builder()
                                                .permissionCheckAll(0)
                                                .subjectId(101L)
                                                .subjectCheckAll(1)
                                                .build()))
                        .build();

        when(openRolePermissionApi.getRoleDataPermissionDetail(1L)).thenReturn(List.of(config));
        when(dataApi.queryList(any()))
                .thenReturn(
                        DataBlock.builder().view("ORDER").records(Collections.emptyList()).build());

        Optional<Condition> condOpt = resolver.resolve(moduleTree, null);
        assertTrue(condOpt.isPresent());
        assertEquals(DSL.falseCondition(), condOpt.get());
    }

    @Test
    @DisplayName("受限配置但无有效谓词生成：必须返回 DSL.falseCondition() 默认拒绝，防止查出全量数据越权")
    void testRestrictedConfigWithNoValidPredicateReturnsFalseCondition() {
        DataRightConfigResp config =
                DataRightConfigResp.builder()
                        .tableName("t_order")
                        .dataList(
                                List.of(
                                        // 未配置主体且主体全选放行，且未指定列
                                        DataRightConfigResp.DataConfig.builder()
                                                .permissionCheckAll(0)
                                                .subjectId(null)
                                                .subjectCheckAll(1)
                                                .columnName(null)
                                                .build()))
                        .build();

        when(openRolePermissionApi.getRoleDataPermissionDetail(1L)).thenReturn(List.of(config));

        Optional<Condition> condOpt = resolver.resolve(moduleTree, null);
        assertTrue(condOpt.isPresent());
        assertEquals(DSL.falseCondition(), condOpt.get());

        // 验证未调用底层引擎全量扫描
        verify(dataApi, never()).queryList(any());
    }

    @Test
    @DisplayName("底层引擎求解抛出异常：必须返回 DSL.falseCondition() 默认拒绝（Fail-Closed），不能放行")
    void testQueryListExceptionReturnsFalseCondition() {
        DataRightConfigResp config =
                DataRightConfigResp.builder()
                        .tableName("t_order")
                        .dataList(
                                List.of(
                                        DataRightConfigResp.DataConfig.builder()
                                                .permissionCheckAll(0)
                                                .subjectId(101L)
                                                .subjectCheckAll(0)
                                                .columnName("dept_id")
                                                .bizNames(List.of("D1"))
                                                .build()))
                        .build();

        when(openRolePermissionApi.getRoleDataPermissionDetail(1L)).thenReturn(List.of(config));
        when(dataApi.queryList(any())).thenThrow(new RuntimeException("DB Connection Timeout"));

        Optional<Condition> condOpt = resolver.resolve(moduleTree, null);
        assertTrue(condOpt.isPresent());
        assertEquals(DSL.falseCondition(), condOpt.get());
    }

    @Test
    @DisplayName("JSON类型字段权限过滤：应使用 JSON_CONTAINS 谓词操作符而非 IN")
    void testJsonFieldUsesJsonContainsOperator() {
        String jsonVal =
                "{\"alias_value_no\":\"W260825142929\",\"alias_value_title\":\"客户\",\"uuid\":\"r3xEQ0BmWS\"}";
        DataRightConfigResp config =
                DataRightConfigResp.builder()
                        .tableName("t_order")
                        .dataList(
                                List.of(
                                        DataRightConfigResp.DataConfig.builder()
                                                .permissionCheckAll(0)
                                                .subjectId(0L)
                                                .subjectCheckAll(0)
                                                .columnName("cooperation_types")
                                                .bizNames(List.of(jsonVal))
                                                .build()))
                        .build();

        when(openRolePermissionApi.getRoleDataPermissionDetail(1L)).thenReturn(List.of(config));

        ArgumentCaptor<QueryReq> captor = ArgumentCaptor.forClass(QueryReq.class);
        DataRow row = new DataRow();
        row.putTable("t_order", Map.of("id", 385L));
        when(dataApi.queryList(captor.capture()))
                .thenReturn(DataBlock.builder().view("ORDER").records(List.of(row)).build());

        Optional<Condition> condOpt = resolver.resolve(moduleTree, null);
        assertTrue(condOpt.isPresent());

        QueryReq capturedReq = captor.getValue();
        assertNotNull(capturedReq);
        Predicate filter = capturedReq.getFilter();
        assertNotNull(filter);

        assertTrue(filter instanceof CompoundPredicate);
        CompoundPredicate compound = (CompoundPredicate) filter;
        boolean foundJsonContains =
                compound.getPredicates().stream()
                        .anyMatch(
                                p ->
                                        p instanceof ComparisonPredicate cp
                                                && "cooperation_types".equals(cp.getColumnName())
                                                && "JSON_CONTAINS".equals(cp.getOperator()));
        assertTrue(foundJsonContains, "cooperation_types 必须使用 JSON_CONTAINS 操作符");
    }

    @Test
    @DisplayName("无主体限制的JSON结构字段：基于取值特征识别并使用 JSON_CONTAINS 操作符")
    void testNonSubjectJsonFieldRecognizedByValue() {
        Map<String, Object> jsonMap = Map.of("alias_value_no", "W123", "alias_value_title", "供应商");
        DataRightConfigResp config =
                DataRightConfigResp.builder()
                        .tableName("t_order")
                        .dataList(
                                List.of(
                                        DataRightConfigResp.DataConfig.builder()
                                                .permissionCheckAll(0)
                                                .subjectId(null)
                                                .subjectCheckAll(0)
                                                .columnName("custom_json_col")
                                                .bizNames(List.of(jsonMap))
                                                .build()))
                        .build();

        when(openRolePermissionApi.getRoleDataPermissionDetail(1L)).thenReturn(List.of(config));

        ArgumentCaptor<QueryReq> captor = ArgumentCaptor.forClass(QueryReq.class);
        DataRow row = new DataRow();
        row.putTable("t_order", Map.of("id", 385L));
        when(dataApi.queryList(captor.capture()))
                .thenReturn(DataBlock.builder().view("ORDER").records(List.of(row)).build());

        Optional<Condition> condOpt = resolver.resolve(moduleTree, null);
        assertTrue(condOpt.isPresent());

        QueryReq capturedReq = captor.getValue();
        assertNotNull(capturedReq);
        Predicate filter = capturedReq.getFilter();
        assertNotNull(filter);

        assertTrue(filter instanceof ComparisonPredicate);
        ComparisonPredicate cp = (ComparisonPredicate) filter;
        assertEquals("custom_json_col", cp.getColumnName());
        assertEquals("JSON_CONTAINS", cp.getOperator());
    }

    @Test
    @DisplayName("通过 INFORMATION_SCHEMA 元数据识别 JSON 列：生成 JSON_CONTAINS 谓词操作符")
    void testJsonFieldRecognizedFromInformationSchema() {
        DataRightConfigResp config =
                DataRightConfigResp.builder()
                        .tableName("t_order")
                        .dataList(
                                List.of(
                                        DataRightConfigResp.DataConfig.builder()
                                                .permissionCheckAll(0)
                                                .subjectId(101L)
                                                .subjectCheckAll(0)
                                                .columnName("dynamic_tag_col")
                                                .bizNames(List.of("VIP", "PARTNER"))
                                                .build()))
                        .build();

        when(openRolePermissionApi.getRoleDataPermissionDetail(1L)).thenReturn(List.of(config));
        when(configApi.getTableJsonColumnNames(any(), eq("t_order")))
                .thenReturn(Set.of("dynamic_tag_col"));

        ArgumentCaptor<QueryReq> captor = ArgumentCaptor.forClass(QueryReq.class);
        DataRow row = new DataRow();
        row.putTable("t_order", Map.of("id", 100L));
        when(dataApi.queryList(captor.capture()))
                .thenReturn(DataBlock.builder().view("ORDER").records(List.of(row)).build());

        Optional<Condition> condOpt = resolver.resolve(moduleTree, null);
        assertTrue(condOpt.isPresent());

        QueryReq capturedReq = captor.getValue();
        assertNotNull(capturedReq);
        Predicate filter = capturedReq.getFilter();
        assertNotNull(filter);

        assertTrue(filter instanceof CompoundPredicate);
        CompoundPredicate compound = (CompoundPredicate) filter;
        boolean foundJsonContains =
                compound.getPredicates().stream()
                        .anyMatch(
                                p ->
                                        p instanceof ComparisonPredicate cp
                                                && "dynamic_tag_col".equals(cp.getColumnName())
                                                && "JSON_CONTAINS".equals(cp.getOperator()));
        assertTrue(foundJsonContains, "来自 INFORMATION_SCHEMA 的 JSON 列必须使用 JSON_CONTAINS 操作符");
    }
}
