package com.jdec.platform.validation.externalobject;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 外部对象提交业务规则校验。
 *
 * <p>由 external-object 的字段实时校验（{@code
 * ExternalObjectRealtimeValidateService}）改写为整单校验：原先前端逐字段提交，现在一次性 读取整单数据后逐项校验，把全部不通过的规则提示写入 {@link
 * ExternalObjectValidationMessages}，由调用方在本层收集完成后一次性抛出。
 *
 * <p>本层在必填校验全部通过后执行，只校验有值的字段：未填写的字段已由必填层提示，不再做格式或取值校验。
 *
 * <p>覆盖规则：合作类型与对象性质、登记状态可选值、统一社会信用代码格式与前缀、身份证号格式、参保人数正整数（仅企业性质）。 对象性质无值时依赖对象性质的规则全部跳过。
 *
 * <p>人员姓名、手机号、证件号的重复判断不是单字段格式问题，已统一放入查重层（{@link ExternalObjectDuplicateValidator}），本层不再校验人员信息。
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
     * <p>各项规则互不影响，全部执行完毕；不通过的提示按规则顺序写入收集器。
     *
     * @param snapshot 外部对象整单数据
     * @param messages 校验提示收集器，不通过的规则提示写入该集合
     */
    public void validate(
            ExternalObjectSnapshot snapshot, ExternalObjectValidationMessages messages) {
        ExternalObjectSnapshot.InfoRow info = snapshot.info();
        String nature = info.nature();
        if (ExternalObjectValues.isBlank(nature)) {
            return;
        }
        validateCooperationTypes(nature, info.cooperationTypes(), messages);
        validateRegStatus(nature, info.regStatus(), messages);
        validateUscc(nature, snapshot.basicInfo(), messages);
        validateIdCardNo(nature, snapshot.basicInfo(), messages);
        validateInsuredCount(nature, snapshot.basicInfo(), messages);
    }

    /** 合作类型：选择“监管”时对象性质必须为机关。 */
    private void validateCooperationTypes(
            String nature, String cooperationTypes, ExternalObjectValidationMessages messages) {
        if (ExternalObjectValues.containsTitle(cooperationTypes, COOPERATION_SUPERVISE)
                && !ExternalObjectValues.isAgencyNature(nature)) {
            messages.add("非机关不可选择监管");
        }
    }

    /** 登记状态：按对象性质限定可选值。 */
    private void validateRegStatus(
            String nature, String regStatus, ExternalObjectValidationMessages messages) {
        RegStatusRule rule = regStatusRule(nature);
        if (rule == null) {
            return;
        }
        for (String title : ExternalObjectValues.titles(regStatus)) {
            boolean allowed = rule.allowed().stream().anyMatch(title::contains);
            if (!allowed) {
                messages.add(rule.nature() + "只能选择" + String.join("、", rule.allowed()));
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
    private void validateUscc(
            String nature,
            ExternalObjectSnapshot.BasicInfoRow basicInfo,
            ExternalObjectValidationMessages messages) {
        if (ExternalObjectValues.isIndividualNature(nature)) {
            return;
        }
        String uscc =
                ExternalObjectValues.compactUpper(basicInfo == null ? null : basicInfo.uscc());
        if (uscc.isEmpty()) {
            return;
        }
        if (!USCC_PATTERN.matcher(uscc).matches()) {
            messages.add("统一社会信用代码格式错误");
            return;
        }
        String prefix = usccPrefix(nature);
        if (prefix != null && !uscc.startsWith(prefix)) {
            messages.add("统一社会信用代码格式错误");
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
    private void validateIdCardNo(
            String nature,
            ExternalObjectSnapshot.BasicInfoRow basicInfo,
            ExternalObjectValidationMessages messages) {
        if (!ExternalObjectValues.isIndividualNature(nature)) {
            return;
        }
        String idCardNo =
                ExternalObjectValues.compactUpper(basicInfo == null ? null : basicInfo.idCardNo());
        if (idCardNo.isEmpty()) {
            return;
        }
        if (!ID_CARD_PATTERN.matcher(idCardNo).matches()) {
            messages.add("身份证号错误");
        }
    }

    /**
     * 参保人数：仅企业性质校验，填写时必须为正整数。
     *
     * <p>与必填层口径一致：参保人数只在对象性质为企业时必填，非企业性质前端不展示该字段，库中该列默认值为 {@code 0}， 不能当作已填写的非法值处理。
     *
     * <p>企业性质下 {@code 0} 及负数已由必填层按“未填”拦截（{@code 基本信息 - 参保人数}），此处的校验仅作为绕过必填层时的防御。
     */
    private void validateInsuredCount(
            String nature,
            ExternalObjectSnapshot.BasicInfoRow basicInfo,
            ExternalObjectValidationMessages messages) {
        if (!ExternalObjectValues.isEnterpriseNature(nature)) {
            return;
        }
        Integer insuredCount = basicInfo == null ? null : basicInfo.insuredCount();
        if (insuredCount != null && insuredCount < 1) {
            messages.add("参保人数必须为正整数");
        }
    }

    /**
     * 对象性质对应的登记状态规则。
     *
     * @param nature 对象性质名称（用于提示语）
     * @param allowed 允许的登记状态
     */
    private record RegStatusRule(String nature, List<String> allowed) {}
}
