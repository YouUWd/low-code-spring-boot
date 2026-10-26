package com.jdec.platform.validation.externalobject;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 外部对象整单数据快照。
 *
 * <p>提交校验以主单（{@code info.id}，即 companyId）为入口，一次性加载外部对象信息申请涉及的各表数据，子表只包含未删除记录。
 */
public record ExternalObjectSnapshot(
        InfoRow info,
        BasicInfoRow basicInfo,
        List<MemberRow> members,
        AddressRow address,
        List<CompanyContactRow> companyContacts,
        List<FinancialAccountRow> financialAccounts,
        List<DigitalAccountRow> digitalAccounts,
        CreditRatingRow creditRating) {

    public ExternalObjectSnapshot {
        members = members == null ? List.of() : List.copyOf(members);
        companyContacts = companyContacts == null ? List.of() : List.copyOf(companyContacts);
        financialAccounts = financialAccounts == null ? List.of() : List.copyOf(financialAccounts);
        digitalAccounts = digitalAccounts == null ? List.of() : List.copyOf(digitalAccounts);
    }

    /** 外部对象主档（info）。 */
    public record InfoRow(
            Long id,
            String businessNo,
            Integer active,
            Long subjectId,
            Long sourceId,
            String companyName,
            Long parentId,
            String nature,
            String regStatus,
            String cooperationTypes,
            String industryTags,
            String industryTypes,
            String gender,
            Integer businessStatus) {}

    /** 工商与法定资质（basic_info），与主档一对一。 */
    public record BasicInfoRow(
            String uscc,
            String idCardNo,
            String legalPerson,
            Integer insuredCount,
            BigDecimal regCapital,
            BigDecimal paidInCapital,
            LocalDate regDate,
            String shortName,
            String enFullName,
            String enShortName) {}

    /** 组织与人员（member）。 */
    public record MemberRow(
            Long id,
            String type,
            String name,
            String memberName,
            String mobilePhoneNo,
            String identityNo) {}

    /** 公司地址（address），与主档一对一。 */
    public record AddressRow(
            String commRegion, String commDetail, String regRegion, String regDetail) {}

    /** 公司联系方式（company_contact）。 */
    public record CompanyContactRow(
            Long id, String contactName, String contactType, String contactValue) {}

    /** 银行卡（financial_account）。 */
    public record FinancialAccountRow(Long id, String accountStatus) {}

    /** 电子账户（digital_account）。 */
    public record DigitalAccountRow(Long id, String accountStatus) {}

    /** 信用等级（credit_rating），与主档一对一。 */
    public record CreditRatingRow(String internalLevel, String qccScore) {}

    /** 查重候选主档（info）。 */
    public record InfoBrief(Long id, String businessNo, String companyName) {}

    /** 查重候选基本信息（basic_info）。 */
    public record BasicInfoBrief(
            Long companyId,
            String companyName,
            String uscc,
            String shortName,
            String enFullName,
            String enShortName) {}

    /** 查重候选人员（member）。 */
    public record MemberBrief(
            Long companyId,
            String companyName,
            String name,
            String memberName,
            String mobilePhoneNo,
            String identityNo) {}
}
