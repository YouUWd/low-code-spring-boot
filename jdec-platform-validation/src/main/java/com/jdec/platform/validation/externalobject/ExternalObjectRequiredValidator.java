package com.jdec.platform.validation.externalobject;

import com.jdec.platform.shared.exception.BusinessException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 外部对象提交必填校验。
 *
 * <p>规则来源：{@code docs/external/外部对象必填校验.md}。以对象性质（{@code info.nature}）为前置条件，按模块树从上到下、
 * 同级模块从前到后的顺序遍历整单数据，收集全部未填写的必填项后一次性抛出 {@link BusinessException}，提示语之间用换行符分隔。
 *
 * <p>约定：
 *
 * <ul>
 *   <li>对象性质本身最先校验；对象性质无值时，依赖对象性质的必填项按“不适用”处理；
 *   <li>子表（basic_info、member、address、company_contact、financial_account、digital_account、credit_rating）
 *       通过关联主单ID未查到任何数据时，该子表的校验规则一律判定为不通过；
 *   <li>一对多子表存在明细行时逐行校验行内必填项；
 *   <li>手机号码仅校验员工行与个人性质的人员行（部门行不要求手机号）；
 *   <li>contact_name 是“联系人行”的标识：其他联系方式行为空、联系人行有值，因此不再反向要求必填。
 * </ul>
 */
@Component
public class ExternalObjectRequiredValidator {

    /** 子表未查到数据时的提示模块名（按模块树顺序）。 */
    private static final String MODULE_BASIC_INFO = "基本信息";

    private static final String MODULE_MEMBER = "人员构成";

    private static final String MODULE_ADDRESS = "联系方式 - 公司地址";

    private static final String MODULE_COMPANY_CONTACT = "联系方式 - 公司联系方式";

    private static final String MODULE_FINANCIAL_ACCOUNT = "财务信息 - 银行卡";

    private static final String MODULE_DIGITAL_ACCOUNT = "财务信息 - 电子账户";

    private static final String MODULE_CREDIT_RATING = "信用等级";

    /**
     * 执行必填校验。
     *
     * @param snapshot 外部对象整单数据
     * @throws BusinessException 存在未填写的必填项，提示语以换行符拼接
     */
    public void validate(ExternalObjectSnapshot snapshot) {
        Set<String> missing = new LinkedHashSet<>();
        collectMissing(snapshot, missing);
        if (!missing.isEmpty()) {
            throw new BusinessException(String.join("\n", missing));
        }
    }

    /** 按模块树顺序收集全部未填写的必填项。 */
    private void collectMissing(ExternalObjectSnapshot snapshot, Set<String> missing) {
        ExternalObjectSnapshot.InfoRow info = snapshot.info();
        NatureFlags flags = NatureFlags.of(info.nature());

        requireText(info.companyName(), "外部对象全称", missing);
        requireJson(info.nature(), "对象性质", missing);
        if (flags.individual()) {
            requireText(info.gender(), "性别", missing);
        } else {
            requireJson(info.regStatus(), "登记状态", missing);
        }
        requireJson(info.cooperationTypes(), "合作类型", missing);
        if (flags.enterprise()) {
            requireJson(info.industryTags(), "行业标签", missing);
        }
        requireJson(info.industryTypes(), "行业类型", missing);

        collectBasicInfo(snapshot.basicInfo(), flags, missing);
        collectMembers(snapshot.members(), flags, missing);
        collectAddress(snapshot.address(), flags, missing);
        collectCompanyContacts(snapshot.companyContacts(), missing);
        collectAccounts(snapshot.financialAccounts(), snapshot.digitalAccounts(), missing);
        collectCreditRating(snapshot.creditRating(), flags, missing);
    }

    private void collectBasicInfo(
            ExternalObjectSnapshot.BasicInfoRow basicInfo, NatureFlags flags, Set<String> missing) {
        if (basicInfo == null) {
            missing.add(notFound(MODULE_BASIC_INFO));
            return;
        }
        if (flags.usccRequired()) {
            requireText(basicInfo.uscc(), "基本信息 - 统一社会信用代码", missing);
        }
        if (flags.individual()) {
            requireText(basicInfo.idCardNo(), "基本信息 - 身份证号", missing);
        }
        if (flags.legalPersonRequired()) {
            requireText(basicInfo.legalPerson(), "基本信息 - 法定代表人/负责人", missing);
        }
        if (flags.enterprise()) {
            requireNotNull(basicInfo.insuredCount(), "基本信息 - 参保人数", missing);
        }
        if (flags.regCapitalRequired()) {
            requireNotNull(basicInfo.regCapital(), "基本信息 - 注册资本", missing);
        }
        if (flags.enterprise()) {
            requireNotNull(basicInfo.paidInCapital(), "基本信息 - 实缴资本", missing);
        }
        if (flags.regDateRequired()) {
            requireNotNull(basicInfo.regDate(), "基本信息 - 注册登记日期", missing);
        }
    }

    private void collectMembers(
            List<ExternalObjectSnapshot.MemberRow> members,
            NatureFlags flags,
            Set<String> missing) {
        if (members.isEmpty()) {
            missing.add(notFound(MODULE_MEMBER));
            return;
        }
        for (ExternalObjectSnapshot.MemberRow member : members) {
            if (!flags.individual()) {
                requireJson(member.type(), "人员构成 - 类型", missing);
                if (ExternalObjectValues.isDepartmentType(member.type())) {
                    requireText(member.name(), "人员构成 - 部门名称", missing);
                }
            }
            if (flags.individual() || ExternalObjectValues.isEmployeeType(member.type())) {
                requireText(member.mobilePhoneNo(), "人员构成 - 手机号码", missing);
            }
        }
    }

    private void collectAddress(
            ExternalObjectSnapshot.AddressRow address, NatureFlags flags, Set<String> missing) {
        if (address == null) {
            missing.add(notFound(MODULE_ADDRESS));
            return;
        }
        if (!flags.addressRequired()) {
            return;
        }
        if (ExternalObjectValues.isBlank(address.commRegion())
                || ExternalObjectValues.isBlank(address.commDetail())) {
            missing.add("联系方式 - 通讯地址");
        }
        if (ExternalObjectValues.isBlank(address.regRegion())
                || ExternalObjectValues.isBlank(address.regDetail())) {
            missing.add("联系方式 - 注册地址");
        }
    }

    private void collectCompanyContacts(
            List<ExternalObjectSnapshot.CompanyContactRow> contacts, Set<String> missing) {
        if (contacts.isEmpty()) {
            missing.add(notFound(MODULE_COMPANY_CONTACT));
            return;
        }
        for (ExternalObjectSnapshot.CompanyContactRow contact : contacts) {
            boolean contactPerson = !ExternalObjectValues.isBlankJson(contact.contactName());
            String prefix = contactPerson ? "联系方式" : "人员构成 - 其他联系方式";
            requireJson(contact.contactType(), prefix + " - 联系方式", missing);
            requireText(contact.contactValue(), prefix + " - 号码", missing);
        }
    }

    private void collectAccounts(
            List<ExternalObjectSnapshot.FinancialAccountRow> financialAccounts,
            List<ExternalObjectSnapshot.DigitalAccountRow> digitalAccounts,
            Set<String> missing) {
        if (financialAccounts.isEmpty()) {
            missing.add(notFound(MODULE_FINANCIAL_ACCOUNT));
        } else {
            for (ExternalObjectSnapshot.FinancialAccountRow account : financialAccounts) {
                requireJson(account.accountStatus(), "财务信息 - 银行卡 - 账号状态", missing);
            }
        }
        if (digitalAccounts.isEmpty()) {
            missing.add(notFound(MODULE_DIGITAL_ACCOUNT));
        } else {
            for (ExternalObjectSnapshot.DigitalAccountRow account : digitalAccounts) {
                requireJson(account.accountStatus(), "财务信息 - 电子账户 - 账号状态", missing);
            }
        }
    }

    private void collectCreditRating(
            ExternalObjectSnapshot.CreditRatingRow creditRating,
            NatureFlags flags,
            Set<String> missing) {
        if (creditRating == null) {
            missing.add(notFound(MODULE_CREDIT_RATING));
            return;
        }
        requireJson(creditRating.internalLevel(), "信用等级 - 内部信用等级", missing);
        if (flags.qccScoreRequired()) {
            requireText(creditRating.qccScore(), "信用等级 - 企查查评分", missing);
        }
    }

    private static void requireText(String value, String label, Set<String> missing) {
        if (ExternalObjectValues.isBlank(value)) {
            missing.add(label);
        }
    }

    private static void requireJson(String raw, String label, Set<String> missing) {
        if (ExternalObjectValues.isBlankJson(raw)) {
            missing.add(label);
        }
    }

    private static void requireNotNull(Object value, String label, Set<String> missing) {
        if (value == null) {
            missing.add(label);
        }
    }

    /** 子表通过关联主单ID未查到数据时的提示。 */
    private static String notFound(String moduleLabel) {
        return "「" + moduleLabel + "」数据不存在";
    }

    /** 对象性质判定结果，避免在各类校验中重复解析词云 JSON。 */
    private record NatureFlags(
            boolean individual,
            boolean enterprise,
            boolean individualBusiness,
            boolean socialGroup,
            boolean lawyer,
            boolean villageCommittee) {

        static NatureFlags of(String nature) {
            return new NatureFlags(
                    ExternalObjectValues.isIndividualNature(nature),
                    ExternalObjectValues.isEnterpriseNature(nature),
                    ExternalObjectValues.isIndividualBusinessNature(nature),
                    ExternalObjectValues.isSocialGroupNature(nature),
                    ExternalObjectValues.isLawyerNature(nature),
                    ExternalObjectValues.isVillageCommitteeNature(nature));
        }

        /** 统一社会信用代码与注册地址的必填范围一致。 */
        boolean usccRequired() {
            return enterprise || individualBusiness || socialGroup || lawyer || villageCommittee;
        }

        boolean addressRequired() {
            return usccRequired();
        }

        boolean legalPersonRequired() {
            return enterprise || individualBusiness || lawyer;
        }

        boolean regCapitalRequired() {
            return enterprise || lawyer;
        }

        boolean regDateRequired() {
            return enterprise || lawyer;
        }

        boolean qccScoreRequired() {
            return enterprise || individualBusiness || lawyer;
        }
    }
}
