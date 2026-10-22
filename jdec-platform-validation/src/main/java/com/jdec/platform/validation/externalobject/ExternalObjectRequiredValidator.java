package com.jdec.platform.validation.externalobject;

import com.jdec.platform.shared.exception.BusinessException;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 外部对象提交必填校验。
 *
 * <p>规则来源：{@code docs/external/外部对象必填校验.md}。以对象性质（{@code info.nature}）为前置条件，按表单从上到下的顺序逐项校验，
 * 命中第一个未填项即抛出 {@link BusinessException}，由全局异常处理转换为前端提示，不做错误收集。
 *
 * <p>约定：
 *
 * <ul>
 *   <li>对象性质本身最先校验；对象性质无值时直接提示，不再继续；
 *   <li>一对多子表（member、company_contact、financial_account、digital_account）没有明细行时不校验，存在明细行时逐行校验；
 *   <li>手机号码仅校验员工行与个人性质的人员行（部门行不要求手机号）；
 *   <li>contact_name 是“联系人行”的标识：其他联系方式行为空、联系人行有值，因此不再反向要求必填。
 * </ul>
 */
@Component
public class ExternalObjectRequiredValidator {

    /**
     * 执行必填校验。
     *
     * @param snapshot 外部对象整单数据
     * @throws BusinessException 第一个必填项未填写
     */
    public void validate(ExternalObjectSnapshot snapshot) {
        ExternalObjectSnapshot.InfoRow info = snapshot.info();
        String nature = info.nature();
        requireJson(nature, "对象性质");
        NatureFlags flags = NatureFlags.of(nature);

        requireText(info.companyName(), "外部对象全称");
        if (flags.individual()) {
            requireText(info.gender(), "性别");
        } else {
            requireJson(info.regStatus(), "登记状态");
        }
        requireJson(info.cooperationTypes(), "合作类型");
        if (flags.enterprise()) {
            requireJson(info.industryTags(), "行业标签");
        }
        requireJson(info.industryTypes(), "行业类型");

        validateBasicInfo(snapshot.basicInfo(), flags);
        validateMembers(snapshot.members(), flags);
        validateCompanyContacts(snapshot.companyContacts());
        validateAddress(snapshot.address(), flags);
        validateAccounts(snapshot.financialAccounts(), snapshot.digitalAccounts());
        validateCreditRating(snapshot.creditRating(), flags);
    }

    private void validateBasicInfo(
            ExternalObjectSnapshot.BasicInfoRow basicInfo, NatureFlags flags) {
        if (flags.usccRequired()) {
            requireText(basicInfo == null ? null : basicInfo.uscc(), "基本信息 - 统一社会信用代码");
        }
        if (flags.individual()) {
            requireText(basicInfo == null ? null : basicInfo.idCardNo(), "基本信息 - 身份证号");
        }
        if (flags.legalPersonRequired()) {
            requireText(basicInfo == null ? null : basicInfo.legalPerson(), "基本信息 - 法定代表人/负责人");
        }
        if (flags.enterprise()) {
            requireNotNull(basicInfo == null ? null : basicInfo.insuredCount(), "基本信息 - 参保人数");
        }
        if (flags.regCapitalRequired()) {
            requireNotNull(basicInfo == null ? null : basicInfo.regCapital(), "基本信息 - 注册资本");
        }
        if (flags.enterprise()) {
            requireNotNull(basicInfo == null ? null : basicInfo.paidInCapital(), "基本信息 - 实缴资本");
        }
        if (flags.regDateRequired()) {
            requireNotNull(basicInfo == null ? null : basicInfo.regDate(), "基本信息 - 注册登记日期");
        }
    }

    private void validateMembers(
            List<ExternalObjectSnapshot.MemberRow> members, NatureFlags flags) {
        for (ExternalObjectSnapshot.MemberRow member : members) {
            if (!flags.individual()) {
                requireJson(member.type(), "人员构成 - 类型");
                if (ExternalObjectValues.isDepartmentType(member.type())) {
                    requireText(member.name(), "人员构成 - 部门名称");
                }
            }
            if (flags.individual() || ExternalObjectValues.isEmployeeType(member.type())) {
                requireText(member.mobilePhoneNo(), "人员构成 - 手机号码");
            }
        }
    }

    private void validateCompanyContacts(List<ExternalObjectSnapshot.CompanyContactRow> contacts) {
        for (ExternalObjectSnapshot.CompanyContactRow contact : contacts) {
            boolean contactPerson = !ExternalObjectValues.isBlankJson(contact.contactName());
            String prefix = contactPerson ? "联系方式" : "人员构成 - 其他联系方式";
            requireJson(contact.contactType(), prefix + " - 联系方式");
            requireText(contact.contactValue(), prefix + " - 号码");
        }
    }

    private void validateAddress(ExternalObjectSnapshot.AddressRow address, NatureFlags flags) {
        if (!flags.addressRequired()) {
            return;
        }
        if (ExternalObjectValues.isBlank(address == null ? null : address.commRegion())
                || ExternalObjectValues.isBlank(address == null ? null : address.commDetail())) {
            throw empty("联系方式 - 通讯地址");
        }
        if (ExternalObjectValues.isBlank(address == null ? null : address.regRegion())
                || ExternalObjectValues.isBlank(address == null ? null : address.regDetail())) {
            throw empty("联系方式 - 注册地址");
        }
    }

    private void validateAccounts(
            List<ExternalObjectSnapshot.FinancialAccountRow> financialAccounts,
            List<ExternalObjectSnapshot.DigitalAccountRow> digitalAccounts) {
        for (ExternalObjectSnapshot.FinancialAccountRow account : financialAccounts) {
            requireJson(account.accountStatus(), "财务信息 - 银行卡 - 账号状态");
        }
        for (ExternalObjectSnapshot.DigitalAccountRow account : digitalAccounts) {
            requireJson(account.accountStatus(), "财务信息 - 电子账户 - 账号状态");
        }
    }

    private void validateCreditRating(
            ExternalObjectSnapshot.CreditRatingRow creditRating, NatureFlags flags) {
        requireJson(creditRating == null ? null : creditRating.internalLevel(), "信用等级 - 内部信用等级");
        if (flags.qccScoreRequired()) {
            requireText(creditRating == null ? null : creditRating.qccScore(), "信用等级 - 企查查评分");
        }
    }

    private static void requireText(String value, String label) {
        if (ExternalObjectValues.isBlank(value)) {
            throw empty(label);
        }
    }

    private static void requireJson(String raw, String label) {
        if (ExternalObjectValues.isBlankJson(raw)) {
            throw empty(label);
        }
    }

    private static void requireNotNull(Object value, String label) {
        if (value == null) {
            throw empty(label);
        }
    }

    private static BusinessException empty(String label) {
        return new BusinessException(label + "不能为空");
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
