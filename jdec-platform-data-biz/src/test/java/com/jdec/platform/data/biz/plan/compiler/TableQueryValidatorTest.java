package com.jdec.platform.data.biz.plan.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.data.api.dto.request.SortItem;
import com.jdec.platform.data.api.model.predicate.ComparisonPredicate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TableQueryValidator 白名单与内置辅助列放行测试")
class TableQueryValidatorTest {

    @Test
    @DisplayName("内置辅助字段(id, active, node_id等)即便未在fields字典中配置，也应当允许投影、过滤和排序")
    void shouldAllowBuiltinSystemColumnsEvenIfNotConfiguredInModuleFields() {
        ModuleTreeDTO tree = buildSampleTree();

        QueryReq req = new QueryReq();
        req.setView("1001");
        req.setColumns(
                List.of(
                        "info.id",
                        "info.company_name",
                        "info.active",
                        "info.version_no",
                        "info.deleted"));
        req.setFilter(ComparisonPredicate.eq("info.active", 1));

        QueryReq memberReq = new QueryReq();
        memberReq.setView("1003");
        memberReq.setColumns(
                List.of(
                        "member.id",
                        "member.name",
                        "member.node_id",
                        "member.parent_node_id",
                        "member.sort_order"));
        memberReq.setFilter(ComparisonPredicate.eq("member.node_id", "NODE_001"));
        memberReq.setSort(List.of(SortItem.asc("member.sort_order")));

        req.setChildren(List.of(memberReq));

        // 校验应顺利通过，不抛出任何异常
        assertThatCode(() -> TableQueryValidator.validate(req, tree)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("非内置且未配置在 fields 字典中的非法列，依然应被严格拦截")
    void shouldRejectUnconfiguredNonBuiltinColumn() {
        ModuleTreeDTO tree = buildSampleTree();

        QueryReq req = new QueryReq();
        req.setView("1001");
        req.setColumns(List.of("info.company_name", "info.secret_unauthorized_column"));

        assertThatThrownBy(() -> TableQueryValidator.validate(req, tree))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不属于当前模块查询范围");
    }

    @Test
    @DisplayName("不在模块树中的非法物理表，依然应被严格拦截")
    void shouldRejectUnauthorizedTable() {
        ModuleTreeDTO tree = buildSampleTree();

        QueryReq req = new QueryReq();
        req.setView("1001");
        req.setColumns(List.of("sys_user_password.id"));

        assertThatThrownBy(() -> TableQueryValidator.validate(req, tree))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不属于当前模块查询范围");
    }

    @Test
    @DisplayName("isBuiltinSystemColumn 正确识别内置辅助列")
    void testIsBuiltinSystemColumn() {
        assertThat(TableQueryValidator.isBuiltinSystemColumn("id")).isTrue();
        assertThat(TableQueryValidator.isBuiltinSystemColumn("ID")).isTrue();
        assertThat(TableQueryValidator.isBuiltinSystemColumn("active")).isTrue();
        assertThat(TableQueryValidator.isBuiltinSystemColumn("node_id")).isTrue();
        assertThat(TableQueryValidator.isBuiltinSystemColumn("parent_node_id")).isTrue();
        assertThat(TableQueryValidator.isBuiltinSystemColumn("deleted")).isTrue();
        assertThat(TableQueryValidator.isBuiltinSystemColumn("non_builtin")).isFalse();
        assertThat(TableQueryValidator.isBuiltinSystemColumn(null)).isFalse();
    }

    private ModuleTreeDTO buildSampleTree() {
        ModuleTreeDTO tree = new ModuleTreeDTO();
        Map<Long, ModuleInfo> map = new HashMap<>();

        // 主模块 info: 只配置了业务字段 company_name，未配置 id, active, deleted 等
        ModuleInfo info = new ModuleInfo();
        info.setModuleId(1001L);
        info.setPrimaryTable("info");
        ModuleFieldDTO companyNameField = new ModuleFieldDTO();
        companyNameField.setTableName("info");
        companyNameField.setColumnName("company_name");
        info.setFields(List.of(companyNameField));
        map.put(1001L, info);

        // 子模块 member: 只配置了业务字段 name，未配置 node_id, parent_node_id, sort_order 等
        ModuleInfo member = new ModuleInfo();
        member.setModuleId(1003L);
        member.setPrimaryTable("member");
        ModuleFieldDTO nameField = new ModuleFieldDTO();
        nameField.setTableName("member");
        nameField.setColumnName("name");
        member.setFields(List.of(nameField));
        map.put(1003L, member);

        tree.setRootModule(info);
        tree.setModuleMap(map);
        return tree;
    }
}
