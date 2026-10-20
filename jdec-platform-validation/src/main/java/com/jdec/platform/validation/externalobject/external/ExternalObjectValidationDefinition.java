package com.jdec.platform.validation.externalobject.external;

import com.jdec.platform.shared.validation.dynamic.AlwaysApplicable;
import com.jdec.platform.shared.validation.dynamic.AnyOf;
import com.jdec.platform.shared.validation.dynamic.ApplicabilityPolicy;
import com.jdec.platform.shared.validation.dynamic.Condition;
import com.jdec.platform.shared.validation.dynamic.ConditionApplicability;
import com.jdec.platform.shared.validation.dynamic.FieldDefinition;
import com.jdec.platform.shared.validation.dynamic.FieldRule;
import com.jdec.platform.shared.validation.dynamic.ModuleDefinition;
import com.jdec.platform.shared.validation.dynamic.NumberRangeRule;
import com.jdec.platform.shared.validation.dynamic.RequiredRule;
import com.jdec.platform.shared.validation.dynamic.StringLengthRule;
import com.jdec.platform.shared.validation.dynamic.ValidationDefinitionProvider;
import com.jdec.platform.shared.validation.dynamic.ValueType;
import com.jdec.platform.validation.externalobject.external.rule.AmountRule;
import com.jdec.platform.validation.externalobject.external.rule.BankAccountNumberRule;
import com.jdec.platform.validation.externalobject.external.rule.CooperationTypeRule;
import com.jdec.platform.validation.externalobject.external.rule.DateOrderRule;
import com.jdec.platform.validation.externalobject.external.rule.IdCardApplicabilityCondition;
import com.jdec.platform.validation.externalobject.external.rule.IdCardRule;
import com.jdec.platform.validation.externalobject.external.rule.IdentityNoRule;
import com.jdec.platform.validation.externalobject.external.rule.MemberDuplicateRule;
import com.jdec.platform.validation.externalobject.external.rule.MemberTypeCondition;
import com.jdec.platform.validation.externalobject.external.rule.MobileNumberRule;
import com.jdec.platform.validation.externalobject.external.rule.NatureCondition;
import com.jdec.platform.validation.externalobject.external.rule.ParentNotSelfRule;
import com.jdec.platform.validation.externalobject.external.rule.RegStatusRule;
import com.jdec.platform.validation.externalobject.external.rule.TextPatternRule;
import com.jdec.platform.validation.externalobject.external.rule.UsccApplicabilityCondition;
import com.jdec.platform.validation.externalobject.external.rule.UsccFormatRule;
import com.jdec.platform.validation.externalobject.support.JsonRequiredRule;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 外部对象表单校验规则。
 *
 * <p>规则来源：{@code docs/external/外部对象字段校验.xlsx}（登记单、变更申请单）、 {@code docs/external/外部对象表单校验.md}。
 *
 * <p>本类只定义可由请求数据判定的规则；依赖数据库的规则由 {@link ExternalObjectDuplicateChecker} 补充：
 *
 * <ul>
 *   <li>外部对象全称、简称、英文全称/简称、统一社会信用代码与已保存数据的查重；
 *   <li>人员手机号、证件号与已保存外部对象数据的查重（本类只校验同一请求内的重复）；
 *   <li>词云配置项是否为最新生效版本（仅校验是否填写）；
 *   <li>父级外部对象是否启用、人员/联系人/产线负责人是否为生效数据；
 *   <li>外部部门号按 W+5 位自动生成（由保存侧生成，不属于校验）；
 *   <li>身份证号出生年月的自动填充（由前端/保存侧回填，校验侧只校验一致性）；
 *   <li>图片单张大小。
 * </ul>
 *
 * <p>变更申请单中的【修改原因】不属于外部对象表字段，由审批/流程侧保存，未在模块定义中声明。
 */
public class ExternalObjectValidationDefinition implements ValidationDefinitionProvider {

    private static final String INFO = ExternalObjectModules.INFO;
    private static final String BASIC_INFO = ExternalObjectModules.BASIC_INFO;
    private static final String MEMBER = ExternalObjectModules.MEMBER;
    private static final String MEMBER_CONTACT = ExternalObjectModules.MEMBER_CONTACT;
    private static final String ADDRESS = ExternalObjectModules.ADDRESS;
    private static final String COMPANY_CONTACT = ExternalObjectModules.COMPANY_CONTACT;
    private static final String FINANCIAL_ACCOUNT = ExternalObjectModules.FINANCIAL_ACCOUNT;
    private static final String DIGITAL_ACCOUNT = ExternalObjectModules.DIGITAL_ACCOUNT;
    private static final String CREDIT_RATING = ExternalObjectModules.CREDIT_RATING;

    private static final String INDIVIDUAL = "个人";
    private static final String ENTERPRISE = "企业";
    private static final String LAWYER = "律师执业机构";

    private static final String URL_REGEX = "(?i)^https?://.+";
    private static final String DIGITS_REGEX = "^\\d+$";

    @Override
    public Collection<ModuleDefinition> moduleDefinitions() {
        return List.of(
                new ModuleDefinition(INFO, infoFields()),
                new ModuleDefinition(BASIC_INFO, basicInfoFields()),
                new ModuleDefinition(MEMBER, memberFields()),
                new ModuleDefinition(MEMBER_CONTACT, memberContactFields()),
                new ModuleDefinition(ADDRESS, addressFields()),
                new ModuleDefinition(COMPANY_CONTACT, companyContactFields()),
                // 财务信息、联系方式是结构模块，没有独立字段。
                new ModuleDefinition(ExternalObjectModules.FINANCE, List.of()),
                new ModuleDefinition(FINANCIAL_ACCOUNT, financialAccountFields()),
                new ModuleDefinition(DIGITAL_ACCOUNT, digitalAccountFields()),
                new ModuleDefinition(CREDIT_RATING, creditRatingFields()),
                new ModuleDefinition(ExternalObjectModules.CONTACT_GROUP, List.of()));
    }

    private List<FieldDefinition> infoFields() {
        return List.of(
                new FieldDefinition(
                        INFO,
                        "info.company_name",
                        ValueType.STRING,
                        true,
                        AlwaysApplicable.INSTANCE,
                        List.of(new RequiredRule(), new StringLengthRule(0, 50))),
                requiredJson(INFO, "info.nature", AlwaysApplicable.INSTANCE),
                new FieldDefinition(
                        INFO,
                        "info.gender",
                        ValueType.STRING,
                        true,
                        new ConditionApplicability(NatureCondition.in(INDIVIDUAL)),
                        List.of(new RequiredRule())),
                // 个人不显示登记状态；个体工商户只能选择存续、注销。
                new FieldDefinition(
                        INFO,
                        "info.reg_status",
                        ValueType.ANY,
                        true,
                        new ConditionApplicability(NatureCondition.notIn(INDIVIDUAL)),
                        List.of(new RequiredRule(), new JsonRequiredRule(), new RegStatusRule())),
                new FieldDefinition(
                        INFO,
                        "info.cooperation_types",
                        ValueType.ANY,
                        true,
                        AlwaysApplicable.INSTANCE,
                        List.of(
                                new RequiredRule(),
                                new JsonRequiredRule(),
                                new CooperationTypeRule())),
                new FieldDefinition(
                        INFO,
                        "info.parent_id",
                        ValueType.ANY,
                        true,
                        AlwaysApplicable.INSTANCE,
                        List.of(new ParentNotSelfRule())),
                // 行业标签仅企业必填。
                requiredJson(
                        INFO,
                        "info.industry_tags",
                        new ConditionApplicability(NatureCondition.in(ENTERPRISE))),
                requiredJson(INFO, "info.industry_types", AlwaysApplicable.INSTANCE));
    }

    private List<FieldDefinition> basicInfoFields() {
        return List.of(
                new FieldDefinition(
                        BASIC_INFO,
                        "basic_info.uscc",
                        ValueType.STRING,
                        true,
                        new ConditionApplicability(new UsccApplicabilityCondition()),
                        List.of(new RequiredRule(), new UsccFormatRule())),
                new FieldDefinition(
                        BASIC_INFO,
                        "basic_info.id_card_no",
                        ValueType.STRING,
                        true,
                        new ConditionApplicability(new IdCardApplicabilityCondition()),
                        List.of(new RequiredRule(), new IdCardRule())),
                optionalText(BASIC_INFO, "basic_info.short_name", 50),
                optionalText(BASIC_INFO, "basic_info.en_full_name", 50),
                optionalText(BASIC_INFO, "basic_info.en_short_name", 50),
                optionalText(BASIC_INFO, "basic_info.former_name", 50),
                optionalText(BASIC_INFO, "basic_info.former_short_name", 50),
                optionalText(BASIC_INFO, "basic_info.en_former_name", 50),
                optionalText(BASIC_INFO, "basic_info.en_former_short_name", 50),
                new FieldDefinition(
                        BASIC_INFO,
                        "basic_info.website",
                        ValueType.STRING,
                        true,
                        AlwaysApplicable.INSTANCE,
                        List.of(new TextPatternRule(URL_REGEX, "网址格式不正确，必须包含http://或https://"))),
                requiredTextWhen(
                        BASIC_INFO,
                        "basic_info.legal_person",
                        50,
                        NatureCondition.in(ENTERPRISE, "个体工商户", LAWYER)),
                new FieldDefinition(
                        BASIC_INFO,
                        "basic_info.insured_count",
                        ValueType.INTEGER,
                        true,
                        new ConditionApplicability(NatureCondition.in(ENTERPRISE)),
                        List.of(new RequiredRule(), new NumberRangeRule(1, Integer.MAX_VALUE))),
                requiredAmountWhen(
                        BASIC_INFO,
                        "basic_info.reg_capital",
                        NatureCondition.in(ENTERPRISE, LAWYER)),
                requiredAmountWhen(
                        BASIC_INFO, "basic_info.paid_in_capital", NatureCondition.in(ENTERPRISE)),
                requiredTextWhen(
                        BASIC_INFO,
                        "basic_info.reg_date",
                        0,
                        NatureCondition.in(ENTERPRISE, LAWYER)),
                new FieldDefinition(
                        BASIC_INFO,
                        "basic_info.business_term_start",
                        ValueType.STRING,
                        true,
                        AlwaysApplicable.INSTANCE,
                        List.of()),
                new FieldDefinition(
                        BASIC_INFO,
                        "basic_info.business_term_end",
                        ValueType.STRING,
                        true,
                        AlwaysApplicable.INSTANCE,
                        List.of(
                                new DateOrderRule(
                                        "basic_info.business_term_start", "营业期限截止时间必须大于开始时间"))));
    }

    private List<FieldDefinition> memberFields() {
        return List.of(
                new FieldDefinition(
                        MEMBER,
                        "member.type",
                        ValueType.ANY,
                        true,
                        new ConditionApplicability(NatureCondition.notIn(INDIVIDUAL)),
                        List.of(new RequiredRule())),
                // 部门名称/员工姓名共用一个字段：部门节点必填且最多50个字符。
                new FieldDefinition(
                        MEMBER,
                        "member.name",
                        ValueType.STRING,
                        true,
                        new ConditionApplicability(MemberTypeCondition.department()),
                        List.of(new RequiredRule(), new StringLengthRule(0, 50))),
                new FieldDefinition(
                        MEMBER,
                        "member.identity_no",
                        ValueType.STRING,
                        true,
                        AlwaysApplicable.INSTANCE,
                        List.of(new IdentityNoRule(), MemberDuplicateRule.identityNo())),
                // 对象性质非个人且人员类型非员工（部门）时，不校验手机号。
                new FieldDefinition(
                        MEMBER,
                        "member.mobile_phone_no",
                        ValueType.STRING,
                        true,
                        new ConditionApplicability(
                                new AnyOf(
                                        List.of(
                                                NatureCondition.in(INDIVIDUAL),
                                                MemberTypeCondition.employee()))),
                        List.of(
                                new RequiredRule(),
                                new MobileNumberRule(),
                                MemberDuplicateRule.mobile())),
                optionalText(MEMBER, "member.position", 50),
                optionalText(MEMBER, "member.work_field", 200),
                optionalText(MEMBER, "member.expertise_field", 200));
    }

    private List<FieldDefinition> memberContactFields() {
        return List.of(
                requiredJson(
                        MEMBER_CONTACT, "member_contact.contact_type", AlwaysApplicable.INSTANCE),
                requiredText(MEMBER_CONTACT, "member_contact.contact_value", 50));
    }

    private List<FieldDefinition> addressFields() {
        NatureCondition addressRequired =
                NatureCondition.in(ENTERPRISE, "个体工商户", "社会团体", LAWYER, "村民委员会");
        return List.of(
                requiredTextWhen(ADDRESS, "address.comm_detail", 0, addressRequired),
                new FieldDefinition(
                        ADDRESS,
                        "address.comm_postcode",
                        ValueType.STRING,
                        true,
                        AlwaysApplicable.INSTANCE,
                        List.of(new TextPatternRule(DIGITS_REGEX, "邮政编码只能包含数字"))),
                requiredTextWhen(ADDRESS, "address.reg_detail", 0, addressRequired));
    }

    private List<FieldDefinition> companyContactFields() {
        return List.of(
                requiredJson(
                        COMPANY_CONTACT, "company_contact.contact_name", AlwaysApplicable.INSTANCE),
                requiredJson(
                        COMPANY_CONTACT, "company_contact.contact_type", AlwaysApplicable.INSTANCE),
                requiredText(COMPANY_CONTACT, "company_contact.contact_value", 0));
    }

    private List<FieldDefinition> financialAccountFields() {
        return List.of(
                new FieldDefinition(
                        FINANCIAL_ACCOUNT,
                        "financial_account.account_no",
                        ValueType.STRING,
                        true,
                        AlwaysApplicable.INSTANCE,
                        List.of(new BankAccountNumberRule())),
                optionalText(FINANCIAL_ACCOUNT, "financial_account.bank_branch", 100),
                requiredJson(
                        FINANCIAL_ACCOUNT,
                        "financial_account.account_status",
                        AlwaysApplicable.INSTANCE));
    }

    private List<FieldDefinition> digitalAccountFields() {
        return List.of(
                optionalText(DIGITAL_ACCOUNT, "digital_account.account_no", 50),
                optionalText(DIGITAL_ACCOUNT, "digital_account.real_name", 50),
                requiredJson(
                        DIGITAL_ACCOUNT,
                        "digital_account.account_status",
                        AlwaysApplicable.INSTANCE));
    }

    private List<FieldDefinition> creditRatingFields() {
        return List.of(
                requiredJson(
                        CREDIT_RATING, "credit_rating.internal_level", AlwaysApplicable.INSTANCE),
                // 企查查评分：企业、个体工商户、律师执业机构必填。
                requiredJson(
                        CREDIT_RATING,
                        "credit_rating.qcc_score",
                        new ConditionApplicability(
                                NatureCondition.in(ENTERPRISE, "个体工商户", LAWYER))));
    }

    private FieldDefinition requiredText(
            String moduleId, String fieldKey, int maxLength, FieldRule... extraRules) {
        return requiredTextWhen(
                moduleId, fieldKey, maxLength, AlwaysApplicable.INSTANCE, extraRules);
    }

    private FieldDefinition requiredTextWhen(
            String moduleId,
            String fieldKey,
            int maxLength,
            Condition condition,
            FieldRule... extraRules) {
        return requiredTextWhen(
                moduleId, fieldKey, maxLength, new ConditionApplicability(condition), extraRules);
    }

    private FieldDefinition requiredTextWhen(
            String moduleId,
            String fieldKey,
            int maxLength,
            ApplicabilityPolicy applicability,
            FieldRule... extraRules) {
        List<FieldRule> rules = new ArrayList<>();
        rules.add(new RequiredRule());
        if (maxLength > 0) {
            rules.add(new StringLengthRule(0, maxLength));
        }
        rules.addAll(List.of(extraRules));
        return new FieldDefinition(
                moduleId, fieldKey, ValueType.STRING, true, applicability, rules);
    }

    private FieldDefinition requiredAmountWhen(
            String moduleId, String fieldKey, Condition condition) {
        return new FieldDefinition(
                moduleId,
                fieldKey,
                ValueType.ANY,
                true,
                new ConditionApplicability(condition),
                List.of(new RequiredRule(), new AmountRule()));
    }

    private FieldDefinition requiredJson(
            String moduleId, String fieldKey, ApplicabilityPolicy applicability) {
        return new FieldDefinition(
                moduleId,
                fieldKey,
                ValueType.ANY,
                true,
                applicability,
                List.of(new RequiredRule(), new JsonRequiredRule()));
    }

    private FieldDefinition optionalText(String moduleId, String fieldKey, int maxLength) {
        return new FieldDefinition(
                moduleId,
                fieldKey,
                ValueType.STRING,
                true,
                AlwaysApplicable.INSTANCE,
                List.of(new StringLengthRule(0, maxLength)));
    }
}
