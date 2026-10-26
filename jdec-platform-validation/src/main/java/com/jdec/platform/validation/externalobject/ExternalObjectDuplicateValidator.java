package com.jdec.platform.validation.externalobject;

import com.jdec.platform.shared.exception.BusinessException;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 外部对象提交查重校验。
 *
 * <p>由 external-object 的字段查重（{@code ExternalObjectDuplicateCheckService}）与旧整单查重改写为整单校验：一次性加载当前外部对象
 * 与查重范围内的已保存数据后逐项比对，命中第一个重复项即抛出 {@link BusinessException}。
 *
 * <p>查重范围：{@code active=1} 的外部对象；尚未终结的业务编号再补充最后一个启用版本（由 {@link
 * ExternalObjectSnapshotRepository#findDuplicateInfos(Long)} 负责收集）。当前外部对象按主键、来源主键、业务编号排除（含历史版本）。
 *
 * <p>查重项与提示：
 *
 * <ul>
 *   <li>外部对象全称、统一社会信用代码、外部对象简称、英文全称、英文简称不可与已保存数据重复；
 *   <li>人员手机号、证件号不可与已保存数据重复；姓名、手机号、证件号全部相同的视为同一人员，允许提交；
 *   <li>对象性质非个人时，部门行不参与手机号查重。
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ExternalObjectDuplicateValidator {

    /** 姓名与手机号相同、证件号不同的提示 */
    private static final String SAME_NAME_MOBILE_MESSAGE = "姓名与手机号码一致，但证件号码不同，请核对后重新填写!";

    /** 姓名与证件号相同、手机号不同的提示 */
    private static final String SAME_NAME_IDENTITY_MESSAGE = "姓名与证件号一致，但手机号码不同，请核对后重新填写!";

    private final ExternalObjectSnapshotRepository repository;

    /**
     * 执行查重校验。
     *
     * @param snapshot 外部对象整单数据
     * @throws BusinessException 第一个重复项
     */
    public void validate(ExternalObjectSnapshot snapshot) {
        ExternalObjectSnapshot.InfoRow info = snapshot.info();
        List<ExternalObjectSnapshot.InfoBrief> candidates =
                repository.findDuplicateInfos(info.subjectId()).stream()
                        .filter(Objects::nonNull)
                        .filter(candidate -> !sameObject(candidate, info))
                        .toList();
        if (candidates.isEmpty()) {
            return;
        }

        validateCompanyName(info.companyName(), candidates);
        List<Long> companyIds =
                candidates.stream().map(ExternalObjectSnapshot.InfoBrief::id).toList();
        validateBasicInfo(snapshot.basicInfo(), companyIds);
        validateMembers(snapshot.members(), info.nature(), companyIds);
    }

    /** 排除当前外部对象自身及其历史版本。 */
    private static boolean sameObject(
            ExternalObjectSnapshot.InfoBrief candidate, ExternalObjectSnapshot.InfoRow info) {
        if (candidate.id() != null
                && (candidate.id().equals(info.id()) || candidate.id().equals(info.sourceId()))) {
            return true;
        }
        return !ExternalObjectValues.isBlank(info.businessNo())
                && info.businessNo().equals(candidate.businessNo());
    }

    /** 外部对象全称查重。 */
    private void validateCompanyName(
            String companyName, List<ExternalObjectSnapshot.InfoBrief> candidates) {
        String value = ExternalObjectValues.trim(companyName);
        if (value.isEmpty()) {
            return;
        }
        for (ExternalObjectSnapshot.InfoBrief candidate : candidates) {
            if (value.equals(ExternalObjectValues.trim(candidate.companyName()))) {
                throw new BusinessException("外部对象全称【" + value + "】已存在，请核对后重新填写!");
            }
        }
    }

    /** 基本信息查重：统一社会信用代码、简称、英文全称、英文简称。 */
    private void validateBasicInfo(
            ExternalObjectSnapshot.BasicInfoRow basicInfo, List<Long> companyIds) {
        if (basicInfo == null) {
            return;
        }
        String uscc = ExternalObjectValues.compactUpper(basicInfo.uscc());
        String shortName = ExternalObjectValues.trim(basicInfo.shortName());
        String enFullName = ExternalObjectValues.trim(basicInfo.enFullName());
        String enShortName = ExternalObjectValues.trim(basicInfo.enShortName());
        if (uscc.isEmpty()
                && shortName.isEmpty()
                && enFullName.isEmpty()
                && enShortName.isEmpty()) {
            return;
        }

        List<ExternalObjectSnapshot.BasicInfoBrief> candidates =
                repository.findDuplicateBasicInfos(companyIds);
        if (!uscc.isEmpty()
                && candidates.stream()
                        .anyMatch(
                                candidate ->
                                        uscc.equals(
                                                ExternalObjectValues.compactUpper(
                                                        candidate.uscc())))) {
            throw duplicate("统一社会信用代码", uscc);
        }
        if (!shortName.isEmpty()
                && candidates.stream()
                        .anyMatch(
                                candidate ->
                                        shortName.equals(
                                                ExternalObjectValues.trim(
                                                        candidate.shortName())))) {
            throw duplicate("外部对象简称", shortName);
        }
        if (!enFullName.isEmpty()
                && candidates.stream()
                        .anyMatch(
                                candidate ->
                                        enFullName.equals(
                                                ExternalObjectValues.trim(
                                                        candidate.enFullName())))) {
            throw duplicate("外部对象英文全称", enFullName);
        }
        if (!enShortName.isEmpty()
                && candidates.stream()
                        .anyMatch(
                                candidate ->
                                        enShortName.equals(
                                                ExternalObjectValues.trim(
                                                        candidate.enShortName())))) {
            throw duplicate("外部对象英文简称", enShortName);
        }
    }

    /** 人员手机号、证件号查重；姓名、手机号、证件号全部相同的视为同一人员。 */
    private void validateMembers(
            List<ExternalObjectSnapshot.MemberRow> members, String nature, List<Long> companyIds) {
        if (members.isEmpty()) {
            return;
        }
        boolean individual = ExternalObjectValues.isIndividualNature(nature);
        List<ExternalObjectSnapshot.MemberBrief> candidates =
                repository.findDuplicateMembers(companyIds);
        if (candidates.isEmpty()) {
            return;
        }

        for (ExternalObjectSnapshot.MemberRow member : members) {
            boolean departmentWithoutMobile =
                    !individual && ExternalObjectValues.isDepartmentType(member.type());
            String name = ExternalObjectValues.trim(member.name());
            String mobile = ExternalObjectValues.compact(member.mobilePhoneNo());
            String identity = ExternalObjectValues.compactUpper(member.identityNo());

            if (!mobile.isEmpty() && !departmentWithoutMobile) {
                validateMobile(name, identity, mobile, candidates);
            }
            if (!identity.isEmpty()) {
                validateIdentity(name, mobile, identity, candidates);
            }
        }
    }

    /** 手机号查重：同名同证件视为同一人员；仅同名时提示证件号不同。 */
    private void validateMobile(
            String name,
            String identity,
            String mobile,
            List<ExternalObjectSnapshot.MemberBrief> candidates) {
        for (ExternalObjectSnapshot.MemberBrief candidate : candidates) {
            if (!mobile.equals(ExternalObjectValues.compact(candidate.mobilePhoneNo()))) {
                continue;
            }
            boolean sameName = !name.isEmpty() && name.equals(candidateName(candidate));
            boolean sameIdentity =
                    !identity.isEmpty()
                            && identity.equals(
                                    ExternalObjectValues.compactUpper(candidate.identityNo()));
            if (sameName && sameIdentity) {
                // 姓名、手机号、证件号全部相同，视为同一人员。
                continue;
            }
            if (sameName) {
                throw new BusinessException(SAME_NAME_MOBILE_MESSAGE);
            }
            throw duplicate("手机号", mobile, candidate.companyName());
        }
    }

    /** 证件号查重：同名同手机号视为同一人员；仅同名时提示手机号不同。 */
    private void validateIdentity(
            String name,
            String mobile,
            String identity,
            List<ExternalObjectSnapshot.MemberBrief> candidates) {
        for (ExternalObjectSnapshot.MemberBrief candidate : candidates) {
            if (!identity.equals(ExternalObjectValues.compactUpper(candidate.identityNo()))) {
                continue;
            }
            boolean sameName = !name.isEmpty() && name.equals(candidateName(candidate));
            boolean sameMobile =
                    !mobile.isEmpty()
                            && mobile.equals(
                                    ExternalObjectValues.compact(candidate.mobilePhoneNo()));
            if (sameName && sameMobile) {
                continue;
            }
            if (sameName) {
                throw new BusinessException(SAME_NAME_IDENTITY_MESSAGE);
            }
            throw duplicate("证件号", identity, candidate.companyName());
        }
    }

    /** 候选人员姓名：优先 name，为空时取 member_name。 */
    private static String candidateName(ExternalObjectSnapshot.MemberBrief candidate) {
        String name = ExternalObjectValues.trim(candidate.name());
        return name.isEmpty() ? ExternalObjectValues.trim(candidate.memberName()) : name;
    }

    private static BusinessException duplicate(String label, String value, String companyName) {
        String objectName = ExternalObjectValues.isBlank(companyName) ? "其他外部对象" : companyName;
        return new BusinessException(
                label + "【" + value + "】在外部对象【" + objectName + "】中已经存在，请核对后重新填写!");
    }

    private static BusinessException duplicate(String label, String value) {
        return new BusinessException(label + "【" + value + "】已存在，请核对后重新填写!");
    }
}
