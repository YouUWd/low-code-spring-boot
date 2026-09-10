package com.jdec.platform.config.biz.check;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 模块被引用依赖检查器（声明式规则） */
@Component
@RequiredArgsConstructor
public class SysModuleReferenceChecker implements ReferenceChecker {

    private final JdbcTemplate jdbcTemplate;

    public record ReferenceRule(String tableName, String foreignKeyColumn, String message) {}

    private static final List<ReferenceRule> RULES =
            List.of(
                    new ReferenceRule("sys_menu", "module_id", "该模块已被菜单引用，请先解除关联"),
                    new ReferenceRule(
                            "sys_approval_chain_type", "module_id", "该模块已配置审批链，请先删除审批链配置"),
                    new ReferenceRule("sys_wechat_template", "module_id", "该模块已关联微信模板，请先删除模板配置"),
                    new ReferenceRule("sys_field_value", "module_id", "该模块已配置字段值，请先删除字段值配置"),
                    new ReferenceRule(
                            "sys_interaction_permission", "module_id", "该模块已配置交互权限，请先删除交互权限配置"),
                    new ReferenceRule(
                            "sys_role_module_field_permission",
                            "module_id",
                            "该模块已被角色字段权限引用，请先解除关联"),
                    new ReferenceRule("sys_module", "parent_id", "该模块下存在子模块，请先删除或移走子模块"));

    @Override
    public List<String> targetType() {
        return List.of(CheckConstant.SYS_MODULE);
    }

    @Override
    public ReferenceCheckResult check(ReferenceContext referenceContext) {
        Long moduleId = referenceContext.getTargetId();
        for (ReferenceRule rule : RULES) {
            String sql =
                    String.format(
                            "SELECT COUNT(1) FROM %s WHERE %s = ?",
                            rule.tableName(), rule.foreignKeyColumn());
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, moduleId);
            if (count != null && count > 0) {
                return ReferenceCheckResult.builder()
                        .referenced(true)
                        .count(count.longValue())
                        .message(rule.message())
                        .build();
            }
        }
        return ReferenceCheckResult.empty();
    }
}
