package com.jdec.platform.validation.externalobject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 外部对象提交查重校验。
 *
 * <p>由 external-object 的字段查重（{@code ExternalObjectDuplicateCheckService}）与旧整单查重改写为整单校验：一次性加载当前外部对象
 * 与查重范围内的已保存数据后逐项比对，把全部命中重复的提示写入 {@link ExternalObjectValidationMessages}， 由调用方在本层收集完成后一次性抛出。
 *
 * <p>本层在必填校验与业务规则校验全部通过后执行，只有已填写值的字段参与查重：空值已在必填层提示， 不会再当作重复值比对，避免“字段没填却提示重复”。
 *
 * <p>查重范围：{@code active=1} 的外部对象；尚未终结的业务编号再补充最后一个启用版本（由 {@link
 * ExternalObjectSnapshotRepository#findDuplicateInfos(Long)} 负责收集）。当前外部对象按主键、来源主键、业务编号排除（含历史版本）。
 *
 * <p>查重项与提示：
 *
 * <ul>
 *   <li>外部对象全称、统一社会信用代码、外部对象简称、英文全称、英文简称不可与已保存数据重复；
 *   <li>人员查重不是单字段查重，而是姓名、手机号、证件号三者组合判断：三者都一样视为同一人员，提示“是否继续添加”；
 *       手机号一样而姓名或证件号不一样、证件号一样而姓名或手机号不一样，分别提示手机号、证件号已存在；
 *   <li>人员比对范围为同一主单下的其它人员行与查重范围内的其它外部对象，自身记录不参与比对； 对象性质非个人时，部门行不参与人员查重。
 *   <li>同一主单下的人员行取“本次提交的人员行 ∪ 库中该主单已保存的人员行”：保存校验拿不到库里未提交的行，只靠提交数据会漏判。
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ExternalObjectDuplicateValidator {

    /** 姓名、手机号、证件号全部相同的提示 */
    private static final String SAME_PERSON_MESSAGE = "姓名【%s】的人员信息在外部对象【%s】中已存在，是否继续添加？";

    /** 手机号相同、姓名或证件号不同的提示 */
    private static final String SAME_MOBILE_MESSAGE = "手机号【%s】在外部对象【%s】中已存在，请核对后重新填写！";

    /** 证件号相同、姓名或手机号不同的提示 */
    private static final String SAME_IDENTITY_MESSAGE = "证件号【%s】在外部对象【%s】中已存在，请核对后重新填写！";

    private final ExternalObjectSnapshotRepository repository;

    /**
     * 执行查重校验。
     *
     * @param snapshot 外部对象整单数据
     * @param messages 校验提示收集器，命中重复的提示写入该集合
     */
    public void validate(
            ExternalObjectSnapshot snapshot, ExternalObjectValidationMessages messages) {
        ExternalObjectSnapshot.InfoRow info = snapshot.info();
        List<ExternalObjectSnapshot.InfoBrief> candidates =
                repository.findDuplicateInfos(info.subjectId()).stream()
                        .filter(Objects::nonNull)
                        .filter(candidate -> !sameObject(candidate, info))
                        .toList();
        List<Long> companyIds =
                candidates.stream().map(ExternalObjectSnapshot.InfoBrief::id).toList();
        if (!candidates.isEmpty()) {
            validateCompanyName(info.companyName(), candidates, messages);
            validateBasicInfo(snapshot.basicInfo(), companyIds, messages);
        }

        // 查重是全局的：库里同一主单的人员行即使本次没有提交，也要参与组合比对
        validateMembers(
                mergeStoredMembers(info, snapshot.members()),
                info.nature(),
                info.companyName(),
                companyIds,
                messages);
    }

    /**
     * 合并本次提交的人员行与库中同一主单已保存的人员行。
     *
     * <p>保存校验只能拿到前端提交的新增/更新行，而查重必须是全局的，库中该主单的其它人员行同样要参与比对。合并规则为按行ID覆盖：
     * 提交行带行ID时覆盖库中同ID行（更新后的行比库中旧值新），不带行ID时作为新增行追加；提交行之外的库中行原样保留参与比对。
     *
     * <p>整单校验时提交行本来就来自库，合并结果与原集合一致（幂等）；本次未提交任何人员行时返回空集合，不查库。
     */
    private List<ExternalObjectSnapshot.MemberRow> mergeStoredMembers(
            ExternalObjectSnapshot.InfoRow info, List<ExternalObjectSnapshot.MemberRow> submitted) {
        if (submitted.isEmpty()) {
            return submitted;
        }
        List<ExternalObjectSnapshot.MemberRow> stored =
                repository.findMembersOfCompany(storedCompanyId(info));
        if (stored.isEmpty()) {
            return submitted;
        }

        Map<Long, ExternalObjectSnapshot.MemberRow> merged = new LinkedHashMap<>();
        for (ExternalObjectSnapshot.MemberRow row : stored) {
            if (row.id() != null) {
                merged.put(row.id(), row);
            }
        }
        List<ExternalObjectSnapshot.MemberRow> appended = new ArrayList<>();
        for (ExternalObjectSnapshot.MemberRow row : submitted) {
            Long id = row.id();
            if (id != null && merged.containsKey(id)) {
                merged.put(id, row);
            } else {
                appended.add(row);
            }
        }
        List<ExternalObjectSnapshot.MemberRow> result = new ArrayList<>(merged.values());
        result.addAll(appended);
        return result;
    }

    /** 合并库中人员行时的主单ID：优先当前主单，其次来源主单（变更申请）。 */
    private static Long storedCompanyId(ExternalObjectSnapshot.InfoRow info) {
        if (info.id() != null && info.id() > 0) {
            return info.id();
        }
        return info.sourceId() != null && info.sourceId() > 0 ? info.sourceId() : null;
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
            String companyName,
            List<ExternalObjectSnapshot.InfoBrief> candidates,
            ExternalObjectValidationMessages messages) {
        String value = ExternalObjectValues.trim(companyName);
        if (value.isEmpty()) {
            return;
        }
        for (ExternalObjectSnapshot.InfoBrief candidate : candidates) {
            if (value.equals(ExternalObjectValues.trim(candidate.companyName()))) {
                messages.add("外部对象全称【" + value + "】已存在，请核对后重新填写!");
            }
        }
    }

    /** 基本信息查重：统一社会信用代码、简称、英文全称、英文简称。 */
    private void validateBasicInfo(
            ExternalObjectSnapshot.BasicInfoRow basicInfo,
            List<Long> companyIds,
            ExternalObjectValidationMessages messages) {
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
            messages.add(duplicate("统一社会信用代码", uscc));
        }
        if (!shortName.isEmpty()
                && candidates.stream()
                        .anyMatch(
                                candidate ->
                                        shortName.equals(
                                                ExternalObjectValues.trim(
                                                        candidate.shortName())))) {
            messages.add(duplicate("外部对象简称", shortName));
        }
        if (!enFullName.isEmpty()
                && candidates.stream()
                        .anyMatch(
                                candidate ->
                                        enFullName.equals(
                                                ExternalObjectValues.trim(
                                                        candidate.enFullName())))) {
            messages.add(duplicate("外部对象英文全称", enFullName));
        }
        if (!enShortName.isEmpty()
                && candidates.stream()
                        .anyMatch(
                                candidate ->
                                        enShortName.equals(
                                                ExternalObjectValues.trim(
                                                        candidate.enShortName())))) {
            messages.add(duplicate("外部对象英文简称", enShortName));
        }
    }

    /**
     * 人员组合查重：姓名、手机号、证件号三者组合判断。
     *
     * <p>比对范围为同一主单下的其它人员行与查重范围内的其它外部对象，自身记录不参与比对； 对象性质非个人时，部门行不参与人员查重。
     */
    private void validateMembers(
            List<ExternalObjectSnapshot.MemberRow> members,
            String nature,
            String companyName,
            List<Long> companyIds,
            ExternalObjectValidationMessages messages) {
        if (members.isEmpty()) {
            return;
        }
        boolean individual = ExternalObjectValues.isIndividualNature(nature);
        String selfObjectName =
                ExternalObjectValues.isBlank(companyName)
                        ? "当前外部对象"
                        : ExternalObjectValues.trim(companyName);
        List<ExternalObjectSnapshot.MemberBrief> candidates =
                repository.findDuplicateMembers(companyIds);

        for (int i = 0; i < members.size(); i++) {
            ExternalObjectSnapshot.MemberRow member = members.get(i);
            if (!individual && ExternalObjectValues.isDepartmentType(member.type())) {
                continue;
            }
            String name = ExternalObjectValues.trim(member.name());
            String mobile = ExternalObjectValues.compact(member.mobilePhoneNo());
            String identity = ExternalObjectValues.compactUpper(member.identityNo());
            if (name.isEmpty() && mobile.isEmpty() && identity.isEmpty()) {
                continue;
            }

            // 同一主单下的其它人员行
            for (int j = 0; j < members.size(); j++) {
                if (i == j) {
                    continue;
                }
                ExternalObjectSnapshot.MemberRow other = members.get(j);
                if (!individual && ExternalObjectValues.isDepartmentType(other.type())) {
                    continue;
                }
                if (sameRow(member, other)) {
                    continue;
                }
                collectMessages(
                        name,
                        mobile,
                        identity,
                        ExternalObjectValues.trim(other.name()),
                        ExternalObjectValues.compact(other.mobilePhoneNo()),
                        ExternalObjectValues.compactUpper(other.identityNo()),
                        selfObjectName,
                        messages);
            }

            // 其它已保存的外部对象
            for (ExternalObjectSnapshot.MemberBrief candidate : candidates) {
                collectMessages(
                        name,
                        mobile,
                        identity,
                        candidateName(candidate),
                        ExternalObjectValues.compact(candidate.mobilePhoneNo()),
                        ExternalObjectValues.compactUpper(candidate.identityNo()),
                        objectName(candidate.companyName()),
                        messages);
            }
        }
    }

    /** 是否为同一条人员记录（排除自身）。 */
    private static boolean sameRow(
            ExternalObjectSnapshot.MemberRow member, ExternalObjectSnapshot.MemberRow other) {
        return member.id() != null && member.id().equals(other.id());
    }

    /** 按姓名、手机号、证件号组合收集重复提示。 */
    private static void collectMessages(
            String name,
            String mobile,
            String identity,
            String otherName,
            String otherMobile,
            String otherIdentity,
            String objectName,
            ExternalObjectValidationMessages messages) {
        boolean sameName = !name.isEmpty() && name.equals(otherName);
        boolean sameMobile = !mobile.isEmpty() && mobile.equals(otherMobile);
        boolean sameIdentity = !identity.isEmpty() && identity.equals(otherIdentity);
        if (sameName && sameMobile && sameIdentity) {
            messages.add(SAME_PERSON_MESSAGE.formatted(name, objectName));
            return;
        }
        if (sameMobile) {
            messages.add(SAME_MOBILE_MESSAGE.formatted(mobile, objectName));
        }
        if (sameIdentity) {
            messages.add(SAME_IDENTITY_MESSAGE.formatted(identity, objectName));
        }
    }

    /** 比对人员所属的外部对象全称；为空时用“其他外部对象”兜底。 */
    private static String objectName(String companyName) {
        return ExternalObjectValues.isBlank(companyName)
                ? "其他外部对象"
                : ExternalObjectValues.trim(companyName);
    }

    /** 候选人员姓名：优先 name，为空时取 member_name。 */
    private static String candidateName(ExternalObjectSnapshot.MemberBrief candidate) {
        String name = ExternalObjectValues.trim(candidate.name());
        return name.isEmpty() ? ExternalObjectValues.trim(candidate.memberName()) : name;
    }

    private static String duplicate(String label, String value) {
        return label + "【" + value + "】已存在，请核对后重新填写!";
    }
}
