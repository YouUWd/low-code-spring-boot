package com.jdec.platform.shared.validation.dynamic;

import java.util.Collection;

/**
 * 校验规则定义提供者（SPI）。
 *
 * <p>各业务系统只需声明一个 Spring Bean 实现本接口，返回本系统的模块字段定义；共享校验框架会在启动时 自动收集全部定义并做配置校验（字段引用、重复字段、条件循环依赖等）。
 *
 * <pre>{@code
 * @Component
 * public class CompanyValidationDefinition implements ValidationDefinitionProvider {
 *     @Override
 *     public Collection<ModuleDefinition> moduleDefinitions() {
 *         return List.of(new ModuleDefinition("1001", List.of(
 *             new FieldDefinition("1001", "company.company_type", ValueType.STRING, true,
 *                 AlwaysApplicable.INSTANCE,
 *                 List.of(new RequiredRule(), new EnumRule(Set.of("enterprise", "individual")))),
 *             new FieldDefinition("1001", "company.social_credit_code", ValueType.STRING, true,
 *                 new ConditionApplicability(FieldCompareCondition.eq(
 *                     FieldRef.current("company.company_type"), "enterprise")),
 *                 List.of(new RequiredRule(), new StringLengthRule(18, 18)))
 *         )));
 *     }
 * }
 * }</pre>
 *
 * <p>定义应当为静态配置，不要在方法中访问数据库或执行耗时操作。
 */
public interface ValidationDefinitionProvider {

    Collection<ModuleDefinition> moduleDefinitions();
}
