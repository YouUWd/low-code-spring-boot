package com.jdec.platform.validation.externalobject;

import com.jdec.platform.shared.exception.BusinessException;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 外部对象提交业务规则校验。
 *
 * <p>由 external-object 的字段实时校验（{@code
 * ExternalObjectRealtimeValidateService}）改写为整单校验：原先前端逐字段提交，现在一次性 读取整单数据后逐项校验，命中第一个不通过的规则即抛出 {@link
 * BusinessException}。
 *
 * <p>覆盖规则：合作类型与对象性质、登记状态可选值、统一社会信用代码格式与前缀、身份证号格式、参保人数正整数、同一主单下人员信息一致性。 对象性质无值时依赖对象性质的规则全部跳过。
 */
@Component
public class ExternalObjectBusinessValidator {

    /** 合作类型中的“监管”选项 */
    private static final String COOPERATION_SUPERVISE = "监管";

    /** 统一社会信用代码：18 位数字/大写字母，剔除 I、O、S、V、Z */
    private static final Pattern USCC_PATTERN = Pattern.compile("^[0-9A-HJ-NPQRTUWXY]{18}$");

    /** 18 位身份证号 */
    private static final Pattern ID_CARD_PATTERN =
            Pattern.compile(
                    "^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$");

    private static final List<String> REG_STATUS_ENTERPRISE = List.of("存续", "注销", "破产");

    private static final List<String> REG_STATUS_INDIVIDUAL_BUSINESS = List.of("存续", "注销");

    private static final List<String> REG_STATUS_INSTITUTION = List.of("存续", "注销");

    private static final List<String> REG_STATUS_SOCIAL_GROUP = List.of("存续", "注销");

    private static final List<String> REG_STATUS_AGENCY = List.of("存续", "撤销");

    /**
     * 执行业务规则校验。
     *
     * @param snapshot 外部对象整单数据
     * @throws BusinessException 第一个不通过的规则
     */
    public void validate(ExternalObjectSnapshot snapshot) {
        ExternalObjectSnapshot.InfoRow info = snapshot.info();
        String nature = info.nature();
        if (ExternalObjectValues.isBlank(nature)) {
            return;
        }
        validateCooperationTypes(nature, info.cooperationTypes());
        validateRegStatus(nature, info.regStatus());
        validateUscc(nature, snapshot.basicInfo());
        validateIdCardNo(nature, snapshot.basicInfo());
        validateInsuredCount(snapshot.basicInfo());
        validateMemberConsistency(snapshot.members(), nature);
    }

    /** 合作类型：选择“监管”时对象性质必须为机关。 */
    private void validateCooperationTypes(String nature, String cooperationTypes) {
        if (ExternalObjectValues.containsTitle(cooperationTypes, COOPERATION_SUPERVISE)
                && !ExternalObjectValues.isAgencyNature(nature)) {
            throw new BusinessException("非机关不可选择监管");
        }
    }

    /** 登记状态：按对象性质限定可选值。 */
    private void validateRegStatus(String nature, String regStatus) {
        RegStatusRule rule = regStatusRule(nature);
        if (rule == null) {
            return;
        }
        for (String title : ExternalObjectValues.titles(regStatus)) {
            boolean allowed = rule.allowed().stream().anyMatch(title::contains);
            if (!allowed) {
                throw new BusinessException(
                        rule.nature() + "只能选择" + String.join("、", rule.allowed()));
            }
        }
    }

    /** 对象性质对应的登记状态规则；未匹配（如个人）返回 {@code null}。 */
    private static RegStatusRule regStatusRule(String nature) {
        if (ExternalObjectValues.isIndividualBusinessNature(nature)) {
            return new RegStatusRule("个体工商户", REG_STATUS_INDIVIDUAL_BUSINESS);
        }
        if (ExternalObjectValues.isInstitutionNature(nature)) {
            return new RegStatusRule("事业单位", REG_STATUS_INSTITUTION);
        }
        if (ExternalObjectValues.isSocialGroupNature(nature)) {
            return new RegStatusRule("社会团体", REG_STATUS_SOCIAL_GROUP);
        }
        if (ExternalObjectValues.isAgencyNature(nature)) {
            return new RegStatusRule("机关", REG_STATUS_AGENCY);
        }
        if (ExternalObjectValues.isEnterpriseNature(nature)) {
            return new RegStatusRule("企业", REG_STATUS_ENTERPRISE);
        }
        return null;
    }

    /** 统一社会信用代码：个人填写身份证号，跳过；其余性质校验通用格式与对象性质前缀。 */
    private void validateUscc(String nature, ExternalObjectSnapshot.BasicInfoRow basicInfo) {
        if (ExternalObjectValues.isIndividualNature(nature)) {
            return;
        }
        String uscc =
                ExternalObjectValues.compactUpper(basicInfo == null ? null : basicInfo.uscc());
        if (uscc.isEmpty()) {
            return;
        }
        if (!USCC_PATTERN.matcher(uscc).matches()) {
            throw new BusinessException("统一社会信用代码格式错误");
        }
        String prefix = usccPrefix(nature);
        if (prefix != null && !uscc.startsWith(prefix)) {
            throw new BusinessException("统一社会信用代码格式错误");
        }
    }

    /** 对象性质对应的统一社会信用代码前缀；未收录的性质返回 {@code null}（只校验通用格式）。 */
    private static String usccPrefix(String nature) {
        if (ExternalObjectValues.isIndividualBusinessNature(nature)) {
            return "92";
        }
        if (ExternalObjectValues.isInstitutionNature(nature)) {
            return "12";
        }
        if (ExternalObjectValues.isSocialGroupNature(nature)) {
            return "51";
        }
        if (ExternalObjectValues.isLawyerNature(nature)) {
            return "31";
        }
        if (ExternalObjectValues.isVillageCommitteeNature(nature)) {
            return "54";
        }
        if (ExternalObjectValues.containsTitle(nature, "居民委员会")) {
            return "55";
        }
        if (ExternalObjectValues.isAgencyNature(nature)) {
            return "11";
        }
        if (ExternalObjectValues.isEnterpriseNature(nature)) {
            return "91";
        }
        return null;
    }

    /** 身份证号：仅在对象性质为个人时按正则校验。 */
    private void validateIdCardNo(String nature, ExternalObjectSnapshot.BasicInfoRow basicInfo) {
        if (!ExternalObjectValues.isIndividualNature(nature)) {
            return;
        }
        String idCardNo =
                ExternalObjectValues.compactUpper(basicInfo == null ? null : basicInfo.idCardNo());
        if (idCardNo.isEmpty()) {
            return;
        }
        if (!ID_CARD_PATTERN.matcher(idCardNo).matches()) {
            throw new BusinessException("身份证号错误");
        }
    }

    /** 参保人数：填写时必须为正整数。 */
    private void validateInsuredCount(ExternalObjectSnapshot.BasicInfoRow basicInfo) {
        Integer insuredCount = basicInfo == null ? null : basicInfo.insuredCount();
        if (insuredCount != null && insuredCount < 1) {
            throw new BusinessException("参保人数必须为正整数");
        }
    }

    /**
     * 同一主单下人员信息一致性：员工（或个人性质的人员行）两两比对。
     *
     * <p>姓名、手机号、证件号三者都有值的记录才参与比对；命中“两者相同、第三者不同”时提示，比对时排除自身。
     */
    private void validateMemberConsistency(
            List<ExternalObjectSnapshot.MemberRow> members, String nature) {
        boolean individual = ExternalObjectValues.isIndividualNature(nature);
        List<ExternalObjectSnapshot.MemberRow> persons =
                members.stream()
                        .filter(
                                member ->
                                        (individual
                                                        || ExternalObjectValues.isEmployeeType(
                                                                member.type()))
                                                && !ExternalObjectValues.isBlank(member.name())
                                                && !ExternalObjectValues.isBlank(
                                                        member.mobilePhoneNo())
                                                && !ExternalObjectValues.isBlank(
                                                        member.identityNo()))
                        .toList();
        for (int i = 0; i < persons.size(); i++) {
            ExternalObjectSnapshot.MemberRow current = persons.get(i);
            String name = ExternalObjectValues.trim(current.name());
            String mobile = normalizePhone(current.mobilePhoneNo());
            String identity = normalizeIdentity(current.identityNo());
            for (int j = 0; j < persons.size(); j++) {
                if (i == j) {
                    continue;
                }
                ExternalObjectSnapshot.MemberRow other = persons.get(j);
                boolean sameName = name.equals(ExternalObjectValues.trim(other.name()));
                boolean sameMobile = mobile.equals(normalizePhone(other.mobilePhoneNo()));
                boolean sameIdentity = identity.equals(normalizeIdentity(other.identityNo()));
                if (sameName && sameMobile && !sameIdentity) {
                    throw new BusinessException("姓名和手机号相同，证件号不同！");
                }
                if (sameName && sameIdentity && !sameMobile) {
                    throw new BusinessException("姓名和证件号相同，手机号不同！");
                }
                if (sameMobile && sameIdentity && !sameName) {
                    throw new BusinessException("手机号和证件号相同，姓名不同！");
                }
            }
        }
    }

    private static String normalizePhone(String value) {
        return ExternalObjectValues.compact(value);
    }

    private static String normalizeIdentity(String value) {
        return ExternalObjectValues.compactUpper(value);
    }

    /**
     * 对象性质对应的登记状态规则。
     *
     * @param nature 对象性质名称（用于提示语）
     * @param allowed 允许的登记状态
     */
    private record RegStatusRule(String nature, List<String> allowed) {}
}
