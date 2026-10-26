package com.jdec.platform.externalobject.biz.service.support;

import java.util.Set;

/**
 * 外部对象人员组合查重规则。
 *
 * <p>人员查重不是常规的单字段查重，而是姓名、手机号、证件号三个字段组合起来判断（见 {@code docs/external/外部对象实时校验.md} 的“member表(改)”）：
 *
 * <ul>
 *   <li>姓名：三者都一样，视为同一人员，提示“是否继续添加”；
 *   <li>手机号：手机号一样，姓名或证件号不一样；
 *   <li>证件号：证件号一样，姓名或手机号不一样。
 * </ul>
 *
 * <p>比较前统一去空白：姓名去首尾空白，手机号去全部空白，证件号去全部空白并转大写；空值不参与相等判断，避免未填字段被误判为相同。
 */
public final class ExternalObjectMemberDuplicateRule {

    /** 支持人员组合查重的字段。 */
    public static final String NAME_FIELD = "member.name";

    public static final String MOBILE_FIELD = "member.mobile_phone_no";

    public static final String IDENTITY_FIELD = "member.identity_no";

    /** 人员查重涉及的字段集合。 */
    public static final Set<String> FIELDS = Set.of(NAME_FIELD, MOBILE_FIELD, IDENTITY_FIELD);

    /** 姓名、手机号、证件号全部相同的提示 */
    private static final String SAME_PERSON_MESSAGE = "姓名【%s】的人员信息在外部对象【%s】中已存在，是否继续添加？";

    /** 手机号相同、姓名或证件号不同的提示 */
    private static final String SAME_MOBILE_MESSAGE = "手机号【%s】在外部对象【%s】中已存在，请核对后重新填写！";

    /** 证件号相同、姓名或手机号不同的提示 */
    private static final String SAME_IDENTITY_MESSAGE = "证件号【%s】在外部对象【%s】中已存在，请核对后重新填写！";

    private ExternalObjectMemberDuplicateRule() {}

    /**
     * 按字段判断当前人员与比对人员是否命中重复规则。
     *
     * @param field 需要校验的字段（{@code member.name}/{@code member.mobile_phone_no}/{@code
     *     member.identity_no}）
     * @param name 当前人员姓名（已规范化）
     * @param mobilePhoneNo 当前人员手机号（已规范化）
     * @param identityNo 当前人员证件号（已规范化）
     * @param otherName 比对人员姓名（已规范化）
     * @param otherMobilePhoneNo 比对人员手机号（已规范化）
     * @param otherIdentityNo 比对人员证件号（已规范化）
     * @param companyName 比对人员所属的外部对象全称
     * @return 命中时的提示语；未命中返回 {@code null}
     */
    public static String message(
            String field,
            String name,
            String mobilePhoneNo,
            String identityNo,
            String otherName,
            String otherMobilePhoneNo,
            String otherIdentityNo,
            String companyName) {
        boolean sameName = !name.isEmpty() && name.equals(otherName);
        boolean sameMobile = !mobilePhoneNo.isEmpty() && mobilePhoneNo.equals(otherMobilePhoneNo);
        boolean sameIdentity = !identityNo.isEmpty() && identityNo.equals(otherIdentityNo);
        boolean samePerson = sameName && sameMobile && sameIdentity;
        return switch (field) {
            case NAME_FIELD ->
                    samePerson ? String.format(SAME_PERSON_MESSAGE, name, companyName) : null;
            case MOBILE_FIELD ->
                    sameMobile && !samePerson
                            ? String.format(SAME_MOBILE_MESSAGE, mobilePhoneNo, companyName)
                            : null;
            case IDENTITY_FIELD ->
                    sameIdentity && !samePerson
                            ? String.format(SAME_IDENTITY_MESSAGE, identityNo, companyName)
                            : null;
            default -> null;
        };
    }

    /** 姓名规范化：去首尾空白。 */
    public static String normalizeName(String name) {
        return ExternalObjectWordValues.trim(name);
    }

    /** 手机号规范化：去全部空白。 */
    public static String normalizeMobilePhoneNo(String mobilePhoneNo) {
        return ExternalObjectWordValues.compact(mobilePhoneNo);
    }

    /** 证件号规范化：去全部空白并转大写。 */
    public static String normalizeIdentityNo(String identityNo) {
        return ExternalObjectWordValues.compactUpper(identityNo);
    }

    /** 外部对象全称，为空时返回兜底名称。 */
    public static String objectName(String companyName, String fallback) {
        String text = ExternalObjectWordValues.trim(companyName);
        return text.isEmpty() ? fallback : text;
    }
}
