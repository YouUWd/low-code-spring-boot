package com.jdec.platform.validation.externalobject;

import com.jdec.platform.shared.exception.BusinessException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 外部对象必填校验（提交整单校验与保存校验共用）。
 *
 * <p>规则来源：{@code docs/external/外部对象必填校验.md}。以对象性质（{@code info.nature}）为前置条件，按模块树从上到下、
 * 同级模块从前到后的顺序遍历整单数据，收集全部未填写的必填项后一次性抛出 {@link BusinessException}，提示语之间用换行符分隔。
 *
 * <p>约定：
 *
 * <ul>
 *   <li>对象性质本身最先校验；对象性质无值时，依赖对象性质的必填项按“不适用”处理；
 *   <li>一对多子表（member、company_contact、financial_account、digital_account）没有记录时不参与必填校验，
 *       存在记录时逐行校验行内必填项；
 *   <li>人员构成按类型判断校验字段：部门行只校验部门名称，员工行（类型为空按默认员工）校验手机号码，个人性质只校验手机号码；
 *   <li>一对一子表（basic_info、address、credit_rating）没有记录时，视为一行所有字段均为空， 按该子表的必填规则逐项收集字段级提示，不再提示“数据不存在”；
 *   <li>三级子表（member_contact）没有记录时不参与必填校验；
 *   <li>contact_name 是“联系人行”的标识：其他联系方式行为空、联系人行有值，因此不再反向要求必填；
 *   <li>保存校验（{@link #validateOnSave}）只校验前端提交的模块，未提交的模块不产生提示； 无主单ID且未提交基本信息数据时，基本信息的所有必填规则视为未满足；
 *   <li>提交校验的主单ID没有生成时（{@link #validateOnSubmit}）无法读取业务库，使用前端提交的数据校验：
 *       所有需要校验的模块都参与，模块没有提交任何数据时该模块的必填项全部不通过，有记录时逐条记录校验。
 * </ul>
 */
@Component
public class ExternalObjectRequiredValidator {
    /** 一对一子表无记录时按“一行空值”参与字段级校验，以下为其空行。 */
    private static final ExternalObjectSnapshot.BasicInfoRow EMPTY_BASIC_INFO =
            new ExternalObjectSnapshot.BasicInfoRow(
                    null, null, null, null, null, null, null, null, null, null);

    private static final ExternalObjectSnapshot.AddressRow EMPTY_ADDRESS =
            new ExternalObjectSnapshot.AddressRow(null, null, null, null);

    private static final ExternalObjectSnapshot.CreditRatingRow EMPTY_CREDIT_RATING =
            new ExternalObjectSnapshot.CreditRatingRow(null, null);

    /** 模块表名，用于保存校验时按提交模块收集必填项。 */
    private static final String INFO_TABLE = "info";

    private static final String BASIC_INFO_TABLE = "basic_info";

    private static final String MEMBER_TABLE = "member";

    private static final String ADDRESS_TABLE = "address";

    private static final String COMPANY_CONTACT_TABLE = "company_contact";

    private static final String FINANCIAL_ACCOUNT_TABLE = "financial_account";

    private static final String DIGITAL_ACCOUNT_TABLE = "digital_account";

    private static final String CREDIT_RATING_TABLE = "credit_rating";

    /** 一对多子表模块缺失时按“一行空值”参与校验，以下为其空行。 */
    private static final ExternalObjectSnapshot.MemberRow EMPTY_MEMBER =
            new ExternalObjectSnapshot.MemberRow(null, null, null, null, null, null);

    private static final ExternalObjectSnapshot.CompanyContactRow EMPTY_COMPANY_CONTACT =
            new ExternalObjectSnapshot.CompanyContactRow(null, null, null, null);

    private static final ExternalObjectSnapshot.FinancialAccountRow EMPTY_FINANCIAL_ACCOUNT =
            new ExternalObjectSnapshot.FinancialAccountRow(null, null);

    private static final ExternalObjectSnapshot.DigitalAccountRow EMPTY_DIGITAL_ACCOUNT =
            new ExternalObjectSnapshot.DigitalAccountRow(null, null);

    /** 提交整单校验时参与校验的全部模块。 */
    private static final Set<String> ALL_TABLES =
            Set.of(
                    INFO_TABLE,
                    BASIC_INFO_TABLE,
                    MEMBER_TABLE,
                    ADDRESS_TABLE,
                    COMPANY_CONTACT_TABLE,
                    FINANCIAL_ACCOUNT_TABLE,
                    DIGITAL_ACCOUNT_TABLE,
                    CREDIT_RATING_TABLE);

    /**
     * 执行必填校验（提交整单校验）。
     *
     * @param snapshot 外部对象整单数据
     * @throws BusinessException 存在未填写的必填项，提示语以换行符拼接
     */
    public void validate(ExternalObjectSnapshot snapshot) {
        validateOnSave(snapshot, ALL_TABLES, false);
    }

    /**
     * 执行必填校验（提交校验的主单ID没有生成时，基于前端提交参数）。
     *
     * <p>主单ID没有值时代码无法从业务库查到整单数据，改为使用前端提交的数据构造整单快照。全部需要校验的模块都参与：
     *
     * <ul>
     *   <li>模块没有提交任何数据时，该模块按“一行空值”处理，模块下的必填项全部不通过；
     *   <li>模块有记录时，逐条记录校验每个需要校验的字段规则。
     * </ul>
     *
     * @param snapshot 由前端提交参数转换得到的整单数据
     * @param basicInfoAbsentOnCreate 无主单ID且未提交基本信息数据：基本信息的所有校验规则视为不满足
     * @throws BusinessException 存在未填写的必填项，提示语以换行符拼接
     */
    public void validateOnSubmit(ExternalObjectSnapshot snapshot, boolean basicInfoAbsentOnCreate) {
        Set<String> missing = new LinkedHashSet<>();
        ExternalObjectSnapshot.InfoRow info = snapshot.info();
        NatureFlags flags = NatureFlags.of(info.nature());

        collectInfo(info, flags, missing);
        if (basicInfoAbsentOnCreate) {
            collectAbsentBasicInfo(info.nature(), flags, missing);
        } else {
            collectBasicInfo(snapshot.basicInfo(), flags, missing);
        }
        // 一对多子表：模块没有提交数据时按一行空值收集，模块下的必填项全部不通过
        collectMembers(
                snapshot.members().isEmpty() ? List.of(EMPTY_MEMBER) : snapshot.members(),
                flags,
                missing);
        collectAddress(snapshot.address(), flags, missing);
        collectCompanyContacts(
                snapshot.companyContacts().isEmpty()
                        ? List.of(EMPTY_COMPANY_CONTACT)
                        : snapshot.companyContacts(),
                missing);
        collectFinancialAccounts(
                snapshot.financialAccounts().isEmpty()
                        ? List.of(EMPTY_FINANCIAL_ACCOUNT)
                        : snapshot.financialAccounts(),
                missing);
        collectDigitalAccounts(
                snapshot.digitalAccounts().isEmpty()
                        ? List.of(EMPTY_DIGITAL_ACCOUNT)
                        : snapshot.digitalAccounts(),
                missing);
        collectCreditRating(snapshot.creditRating(), flags, missing);
        if (!missing.isEmpty()) {
            throw new BusinessException(String.join("\n", missing));
        }
    }

    /**
     * 执行必填校验（保存校验）。
     *
     * <p>保存接口只拿到前端提交的参数，因此只校验本次提交的模块，未提交的模块不产生提示。
     *
     * @param snapshot 由前端提交参数转换得到的整单数据
     * @param tables 本次提交且需要校验的模块表名
     * @param basicInfoAbsentOnCreate 无主单ID且未提交基本信息数据：基本信息的所有校验规则视为不满足
     * @throws BusinessException 存在未填写的必填项，提示语以换行符拼接
     */
    public void validateOnSave(
            ExternalObjectSnapshot snapshot, Set<String> tables, boolean basicInfoAbsentOnCreate) {
        Set<String> missing = new LinkedHashSet<>();
        ExternalObjectSnapshot.InfoRow info = snapshot.info();
        NatureFlags flags = NatureFlags.of(info.nature());

        if (tables.contains(INFO_TABLE)) {
            collectInfo(info, flags, missing);
        }
        if (basicInfoAbsentOnCreate) {
            collectAbsentBasicInfo(info.nature(), flags, missing);
        } else if (tables.contains(BASIC_INFO_TABLE)) {
            collectBasicInfo(snapshot.basicInfo(), flags, missing);
        }
        if (tables.contains(MEMBER_TABLE)) {
            collectMembers(snapshot.members(), flags, missing);
        }
        if (tables.contains(ADDRESS_TABLE)) {
            collectAddress(snapshot.address(), flags, missing);
        }
        if (tables.contains(COMPANY_CONTACT_TABLE)) {
            collectCompanyContacts(snapshot.companyContacts(), missing);
        }
        if (tables.contains(FINANCIAL_ACCOUNT_TABLE)) {
            collectFinancialAccounts(snapshot.financialAccounts(), missing);
        }
        if (tables.contains(DIGITAL_ACCOUNT_TABLE)) {
            collectDigitalAccounts(snapshot.digitalAccounts(), missing);
        }
        if (tables.contains(CREDIT_RATING_TABLE)) {
            collectCreditRating(snapshot.creditRating(), flags, missing);
        }
        if (!missing.isEmpty()) {
            throw new BusinessException(String.join("\n", missing));
        }
    }

    /** 主档字段的必填项。 */
    private void collectInfo(
            ExternalObjectSnapshot.InfoRow info, NatureFlags flags, Set<String> missing) {
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
    }

    /**
     * 无主单ID且未提交基本信息数据时的必填项。
     *
     * <p>对象性质有值时按该性质的必填规则收集；对象性质也未提交时，基本信息的全部候选必填规则都视为未满足。
     */
    private void collectAbsentBasicInfo(String nature, NatureFlags flags, Set<String> missing) {
        if (!ExternalObjectValues.isBlank(nature)) {
            collectBasicInfo(EMPTY_BASIC_INFO, flags, missing);
            return;
        }
        missing.add("基本信息 - 统一社会信用代码");
        missing.add("基本信息 - 身份证号");
        missing.add("基本信息 - 法定代表人/负责人");
        missing.add("基本信息 - 参保人数");
        missing.add("基本信息 - 注册资本");
        missing.add("基本信息 - 实缴资本");
        missing.add("基本信息 - 注册登记日期");
    }

    private void collectBasicInfo(
            ExternalObjectSnapshot.BasicInfoRow basicInfo, NatureFlags flags, Set<String> missing) {
        // 子表无记录时按一行空值处理，由必填规则决定需要提示哪些字段
        ExternalObjectSnapshot.BasicInfoRow row = basicInfo == null ? EMPTY_BASIC_INFO : basicInfo;
        if (flags.usccRequired()) {
            requireText(row.uscc(), "基本信息 - 统一社会信用代码", missing);
        }
        if (flags.individual()) {
            requireText(row.idCardNo(), "基本信息 - 身份证号", missing);
        }
        if (flags.legalPersonRequired()) {
            requireText(row.legalPerson(), "基本信息 - 法定代表人/负责人", missing);
        }
        if (flags.enterprise()) {
            requireNotNull(row.insuredCount(), "基本信息 - 参保人数", missing);
        }
        if (flags.regCapitalRequired()) {
            requireNotNull(row.regCapital(), "基本信息 - 注册资本", missing);
        }
        if (flags.enterprise()) {
            requireNotNull(row.paidInCapital(), "基本信息 - 实缴资本", missing);
        }
        if (flags.regDateRequired()) {
            requireNotNull(row.regDate(), "基本信息 - 注册登记日期", missing);
        }
    }

    /** 人员构成：无记录时不校验；有记录时按类型判断需要校验的字段。 */
    private void collectMembers(
            List<ExternalObjectSnapshot.MemberRow> members,
            NatureFlags flags,
            Set<String> missing) {
        for (ExternalObjectSnapshot.MemberRow member : members) {
            if (!flags.individual()) {
                requireJson(member.type(), "人员构成 - 类型", missing);
            }
            if (!flags.individual() && ExternalObjectValues.isDepartmentType(member.type())) {
                // 部门行只校验部门名称
                requireText(member.name(), "人员构成 - 部门名称", missing);
            } else {
                // 员工行（类型为空按默认员工处理）与个人性质的人员行校验手机号码
                requireText(member.mobilePhoneNo(), "人员构成 - 手机号码", missing);
            }
        }
    }

    private void collectAddress(
            ExternalObjectSnapshot.AddressRow address, NatureFlags flags, Set<String> missing) {
        if (!flags.addressRequired()) {
            return;
        }
        // 子表无记录时按一行空值处理，通讯地址、注册地址均视为未填
        ExternalObjectSnapshot.AddressRow row = address == null ? EMPTY_ADDRESS : address;
        if (ExternalObjectValues.isBlank(row.commRegion())
                || ExternalObjectValues.isBlank(row.commDetail())) {
            missing.add("联系方式 - 通讯地址");
        }
        if (ExternalObjectValues.isBlank(row.regRegion())
                || ExternalObjectValues.isBlank(row.regDetail())) {
            missing.add("联系方式 - 注册地址");
        }
    }

    /** 公司联系方式：无记录时不校验；有记录时按行区分“其他联系方式”与“联系人”。 */
    private void collectCompanyContacts(
            List<ExternalObjectSnapshot.CompanyContactRow> contacts, Set<String> missing) {
        for (ExternalObjectSnapshot.CompanyContactRow contact : contacts) {
            boolean contactPerson = !ExternalObjectValues.isBlankJson(contact.contactName());
            String prefix = contactPerson ? "联系方式" : "人员构成 - 其他联系方式";
            requireJson(contact.contactType(), prefix + " - 联系方式", missing);
            requireText(contact.contactValue(), prefix + " - 号码", missing);
        }
    }

    /** 银行卡：无记录时不校验，有记录时逐行校验账号状态。 */
    private void collectFinancialAccounts(
            List<ExternalObjectSnapshot.FinancialAccountRow> financialAccounts,
            Set<String> missing) {
        for (ExternalObjectSnapshot.FinancialAccountRow account : financialAccounts) {
            requireJson(account.accountStatus(), "财务信息 - 银行卡 - 账号状态", missing);
        }
    }

    /** 电子账户：无记录时不校验，有记录时逐行校验账号状态。 */
    private void collectDigitalAccounts(
            List<ExternalObjectSnapshot.DigitalAccountRow> digitalAccounts, Set<String> missing) {
        for (ExternalObjectSnapshot.DigitalAccountRow account : digitalAccounts) {
            requireJson(account.accountStatus(), "财务信息 - 电子账户 - 账号状态", missing);
        }
    }

    private void collectCreditRating(
            ExternalObjectSnapshot.CreditRatingRow creditRating,
            NatureFlags flags,
            Set<String> missing) {
        // 子表无记录时按一行空值处理，内部信用等级、企查查评分视为未填
        ExternalObjectSnapshot.CreditRatingRow row =
                creditRating == null ? EMPTY_CREDIT_RATING : creditRating;
        requireJson(row.internalLevel(), "信用等级 - 内部信用等级", missing);
        if (flags.qccScoreRequired()) {
            requireText(row.qccScore(), "信用等级 - 企查查评分", missing);
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
